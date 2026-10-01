package de.pilzscout.app.ml

import android.os.SystemClock
import com.google.ai.edge.litert.Accelerator
import com.google.ai.edge.litert.CompiledModel
import com.google.ai.edge.litert.TensorBuffer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

class PhotoLogits(val logits: FloatArray, val inferenceMs: Long)

interface MushroomClassifier : AutoCloseable {
    val info: ModelInfo
    val preprocessor: ImagePreprocessor
    suspend fun classify(input: FloatArray): PhotoLogits
}

/**
 * Thrown when the accelerator returns NaN or infinite logits. Seen with the GPU backend on the
 * ViT model; the caller should reload the model on the CPU and retry instead of feeding the
 * values into the fusion maths (softmax would turn them into NaN probabilities).
 */
class NonFiniteLogitsException(val accelerator: Accelerator, val nonFinite: Int, total: Int) :
    IllegalStateException("${accelerator.name} returned $nonFinite of $total non-finite logits")

/**
 * LiteRT CompiledModel wrapper. One instance per loaded model; calls are serialised because the
 * input/output buffers are reused. Timing covers the native run plus the output readback, not preprocessing.
 */
/** GPU arithmetic. FP16 (with fp32 accumulation) only for exports whose graph was made fp16-safe. */
enum class GpuPrecision { FP32, FP16 }

class LiteRtClassifier(
    modelFile: File,
    private val meta: ModelMeta,
    version: String,
    val accelerator: Accelerator,
    val gpuPrecision: GpuPrecision = GpuPrecision.FP32,
) : MushroomClassifier {

    private val model: CompiledModel = CompiledModel.create(modelFile.absolutePath, compileOptions(accelerator, gpuPrecision))
    private val inputs: List<TensorBuffer> = model.createInputBuffers()
    private val outputs: List<TensorBuffer> = model.createOutputBuffers()
    private val mutex = Mutex()

    override val info = ModelInfo(
        version = version,
        name = meta.name ?: meta.modelFile,
        precision = meta.precision,
        inputSize = meta.inputSize,
        numClasses = meta.numClasses,
        accelerator = if (accelerator == Accelerator.GPU) "GPU ${gpuPrecision.name.lowercase()}" else accelerator.name,
    )

    override val preprocessor = ImagePreprocessor(meta.inputSize, meta.evalResize)

    override suspend fun classify(input: FloatArray): PhotoLogits = withContext(Dispatchers.Default) {
        require(input.size == meta.inputSize * meta.inputSize * 3) { "input has ${input.size} values" }
        mutex.withLock {
            inputs[0].writeFloat(input)
            // Timing includes the readback: on the GPU, run() can return before the result is on the host.
            val start = SystemClock.elapsedRealtimeNanos()
            model.run(inputs, outputs)
            val logits = outputs[0].readFloat()
            val ms = (SystemClock.elapsedRealtimeNanos() - start) / 1_000_000
            check(logits.size == meta.numClasses) { "model returned ${logits.size} logits, expected ${meta.numClasses}" }
            val nonFinite = logits.count { !it.isFinite() }
            if (nonFinite > 0) throw NonFiniteLogitsException(accelerator, nonFinite, logits.size)
            PhotoLogits(logits, ms)
        }
    }

    /** One inference on a black image so kernel set-up and first-run costs are paid before the user waits. */
    suspend fun warmUp() {
        runCatching { classify(FloatArray(meta.inputSize * meta.inputSize * 3)) }
    }

    override fun close() {
        inputs.forEach { runCatching { it.close() } }
        outputs.forEach { runCatching { it.close() } }
        runCatching { model.close() }
    }

    companion object {
        /**
         * The GPU backend defaults to fp16 storage and arithmetic. The stock ViT overflows in
         * attention/LayerNorm at that precision and produces NaN logits, so fp32 is the safe default.
         * Exports that were calibrated for fp16 (model.json gpuFp16Safe) run FP16_WITH_FP32_ACCUM:
         * Pixel 7 Pro 126 ms instead of 209 ms per photo. The CPU path gets four XNNPACK threads.
         */
        private fun compileOptions(accelerator: Accelerator, precision: GpuPrecision): CompiledModel.Options {
            val options = CompiledModel.Options(accelerator)
            if (accelerator == Accelerator.GPU) {
                options.gpuOptions = when (precision) {
                    // Capping would turn an overflow into wrong numbers instead of NaN; leave it off so the
                    // NonFiniteLogits guard can fall back to fp32.
                    GpuPrecision.FP16 -> CompiledModel.GpuOptions(precision = CompiledModel.GpuOptions.Precision.FP16_WITH_FP32_ACCUM)
                    GpuPrecision.FP32 -> CompiledModel.GpuOptions(precision = CompiledModel.GpuOptions.Precision.FP32, infiniteFloatCapping = true)
                }
            } else {
                // XNNPACK defaults to a single thread here; four (the big/mid cores) cut the ViT from ~1.5 s to ~0.3 s.
                options.cpuOptions = CompiledModel.CpuOptions(numThreads = CPU_THREADS)
            }
            return options
        }

        val CPU_THREADS: Int = Runtime.getRuntime().availableProcessors().coerceIn(2, 4)
    }
}
