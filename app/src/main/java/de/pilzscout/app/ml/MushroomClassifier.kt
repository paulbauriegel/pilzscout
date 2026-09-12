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
 * LiteRT CompiledModel wrapper. One instance per loaded model; calls are serialised because the
 * input/output buffers are reused. Timing covers only the native run, not preprocessing.
 */
class LiteRtClassifier(
    modelFile: File,
    private val meta: ModelMeta,
    version: String,
    accelerator: Accelerator,
) : MushroomClassifier {

    private val model: CompiledModel = CompiledModel.create(modelFile.absolutePath, CompiledModel.Options(accelerator))
    private val inputs: List<TensorBuffer> = model.createInputBuffers()
    private val outputs: List<TensorBuffer> = model.createOutputBuffers()
    private val mutex = Mutex()

    override val info = ModelInfo(
        version = version,
        name = meta.name ?: meta.modelFile,
        precision = meta.precision,
        inputSize = meta.inputSize,
        numClasses = meta.numClasses,
        accelerator = accelerator.name,
    )

    override val preprocessor = ImagePreprocessor(meta.inputSize, meta.evalResize)

    override suspend fun classify(input: FloatArray): PhotoLogits = withContext(Dispatchers.Default) {
        require(input.size == meta.inputSize * meta.inputSize * 3) { "input has ${input.size} values" }
        mutex.withLock {
            inputs[0].writeFloat(input)
            val start = SystemClock.elapsedRealtimeNanos()
            model.run(inputs, outputs)
            val ms = (SystemClock.elapsedRealtimeNanos() - start) / 1_000_000
            val logits = outputs[0].readFloat()
            check(logits.size == meta.numClasses) { "model returned ${logits.size} logits, expected ${meta.numClasses}" }
            PhotoLogits(logits, ms)
        }
    }

    override fun close() {
        inputs.forEach { runCatching { it.close() } }
        outputs.forEach { runCatching { it.close() } }
        runCatching { model.close() }
    }
}
