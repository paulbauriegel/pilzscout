package de.pilzscout.app.ml

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.ai.edge.litert.Accelerator
import com.google.ai.edge.litert.CompiledModel
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.math.abs

/**
 * Runs the bundled model on the device GPU and checks that the logits are finite and agree with
 * the CPU. Note that the per-variant timings here are single runs after one warm-up and understate
 * fp16 (they once suggested fp16 was slower than fp32); use DeviceBenchmark for medians over 10 runs.
 * Needs a physical device; the emulator has no usable GPU. Do not install this test on the user's
 * Pixel via Gradle (see AGENTS.md); it stays useful on the emulator and for the report-file mechanism.
 */
@RunWith(AndroidJUnit4::class)
class GpuInferenceTest {

    companion object {
        private const val TAG = "GpuInferenceTest"
        /** Every JPEG in androidTest/assets/gpu; personal photos (pxl_*.jpg) are git-ignored but used when present. */
        private lateinit var photos: List<String>

        lateinit var modelFile: File
        lateinit var meta: ModelMeta
        lateinit var labels: List<String>
        lateinit var inputs: Map<String, FloatArray>
        lateinit var cpuLogits: Map<String, FloatArray>
        private lateinit var report: File

        /** Logcat can drop lines; the same text is appended to files/gpu_test_report.txt (read with run-as). */
        fun note(line: String) {
            Log.i(TAG, line)
            report.appendText(line + "\n")
        }

        @JvmStatic
        @BeforeClass
        fun setUp() {
            val app = InstrumentationRegistry.getInstrumentation().targetContext
            val test = InstrumentationRegistry.getInstrumentation().context
            report = File(app.filesDir, "gpu_test_report.txt").also { it.writeText("") }
            photos = test.assets.list("gpu")!!.filter { it.endsWith(".jpg") }.map { it.removeSuffix(".jpg") }.sorted()
            val json = Json { ignoreUnknownKeys = true }
            val metaOverride = File(app.filesDir, "gpu_test_model.json") // pushed next to gpu_test_model.tflite
            meta = json.decodeFromString(
                ModelMeta.serializer(),
                if (metaOverride.exists()) metaOverride.readText() else app.assets.open("packs/model/model.json").bufferedReader().readText(),
            )
            labels = app.assets.open("packs/model/labels.txt").bufferedReader().readLines()
            // A candidate export can be tested without rebuilding the app (plus an optional gpu_test_model.json
            // in the same format as packs/model/model.json when input size or class count differ):
            //   adb push model.tflite /data/local/tmp/m.tflite
            //   adb shell run-as de.pilzscout.app cp /data/local/tmp/m.tflite files/gpu_test_model.tflite
            val override = File(app.filesDir, "gpu_test_model.tflite")
            modelFile = if (override.exists() && override.length() > 0L) override else File(app.cacheDir, meta.modelFile)
            if (modelFile !== override) { // always a fresh copy: the bundled model may have changed since the last run
                app.assets.open("packs/model/${meta.modelFile}").use { src -> modelFile.outputStream().use { src.copyTo(it) } }
            }
            note("model under test: ${modelFile.path} (${modelFile.length() / 1_000_000} MB)")
            // Same path as the app: decode from a file with EXIF orientation and bounded memory.
            val pre = ImagePreprocessor(meta.inputSize, meta.evalResize)
            inputs = photos.associateWith { name ->
                val f = File(app.cacheDir, "$name.jpg")
                test.assets.open("gpu/$name.jpg").use { src -> f.outputStream().use { src.copyTo(it) } }
                val input = pre.prepare(f)
                note("input $name: ${input.count { !it.isFinite() }} non-finite of ${input.size}, range ${input.min()}..${input.max()}")
                input
            }
            LiteRtClassifier(modelFile, meta, "test", Accelerator.CPU).use { cpu ->
                cpuLogits = inputs.mapValues { (name, input) ->
                    val out = runBlocking { cpu.classify(input) }
                    note("CPU $name -> ${labels[argmax(out.logits)]} in ${out.inferenceMs} ms, logits ${out.logits.min()}..${out.logits.max()}")
                    out.logits
                }
            }
        }

        fun argmax(a: FloatArray): Int = a.indices.maxBy { a[it] }
    }

    /** Diagnostic only: shows whether the default (fp16) GPU precision produces NaN on this device. */
    @Test
    fun gpuDefaultPrecisionDiagnostic() {
        val options = CompiledModel.Options(Accelerator.GPU)
        val model = try {
            CompiledModel.create(modelFile.absolutePath, options)
        } catch (e: Exception) {
            note("GPU (default precision) could not compile the model: ${e.message}")
            return
        }
        val ins = model.createInputBuffers()
        val outs = model.createOutputBuffers()
        try {
            for ((name, input) in inputs) {
                ins[0].writeFloat(input)
                model.run(ins, outs)
                val logits = outs[0].readFloat()
                val nonFinite = logits.count { !it.isFinite() }
                note("GPU default precision $name -> $nonFinite/${logits.size} non-finite logits")
            }
        } finally {
            ins.forEach { it.close() }
            outs.forEach { it.close() }
            model.close()
        }
    }

    /** Diagnostic only: how the model behaves under other GPU option sets (storage type, weight placement, precision). */
    @Test
    fun gpuOptionVariantsDiagnostic() {
        val fp32 = CompiledModel.GpuOptions.Precision.FP32
        val variants = listOf(
            "fp16" to CompiledModel.GpuOptions(precision = CompiledModel.GpuOptions.Precision.FP16),
            "fp16+capping" to CompiledModel.GpuOptions(precision = CompiledModel.GpuOptions.Precision.FP16, infiniteFloatCapping = true),
            "fp16storage+fp32accum" to CompiledModel.GpuOptions(precision = CompiledModel.GpuOptions.Precision.FP16_WITH_FP32_ACCUM, infiniteFloatCapping = true),
            "fp32+buffer" to CompiledModel.GpuOptions(precision = fp32, infiniteFloatCapping = true, bufferStorageType = CompiledModel.GpuOptions.BufferStorageType.BUFFER),
        )
        for ((label, gpuOptions) in variants) for ((name, input) in inputs) {
            val cpu = cpuLogits.getValue(name)
            val options = CompiledModel.Options(Accelerator.GPU).also { it.gpuOptions = gpuOptions }
            val model = try {
                CompiledModel.create(modelFile.absolutePath, options)
            } catch (e: Exception) {
                note("variant $label: could not compile (${e.message})")
                continue
            }
            val ins = model.createInputBuffers()
            val outs = model.createOutputBuffers()
            try {
                ins[0].writeFloat(input)
                model.run(ins, outs) // warm-up
                val t0 = android.os.SystemClock.elapsedRealtimeNanos()
                model.run(ins, outs)
                val logits = outs[0].readFloat()
                val ms = (android.os.SystemClock.elapsedRealtimeNanos() - t0) / 1_000_000
                val nonFinite = logits.count { !it.isFinite() }
                val maxDiff = if (nonFinite == 0) logits.indices.maxOf { abs(logits[it] - cpu[it]) } else Float.NaN
                note("variant $label on $name: $nonFinite non-finite, top-1 ${labels[argmax(logits)]}, max |gpu-cpu| = $maxDiff, $ms ms")
            } finally {
                ins.forEach { it.close() }
                outs.forEach { it.close() }
                model.close()
            }
        }
    }

    @Test
    fun gpuFp32MatchesCpu() {
        val gpu = try {
            LiteRtClassifier(modelFile, meta, "test", Accelerator.GPU)
        } catch (e: Exception) {
            note("GPU could not compile the model; nothing to verify here: ${e.message}")
            return
        }
        gpu.use {
            for ((name, input) in inputs) {
                val out = runBlocking { gpu.classify(input) } // throws NonFiniteLogitsException on NaN
                val cpu = cpuLogits.getValue(name)
                val maxDiff = out.logits.indices.maxOf { abs(out.logits[it] - cpu[it]) }
                val gpuTop = argmax(out.logits)
                val cpuTop = argmax(cpu)
                note("GPU fp32 $name -> ${labels[gpuTop]} in ${out.inferenceMs} ms, max |gpu-cpu| = $maxDiff")
                assertTrue("all logits finite for $name", out.logits.all { it.isFinite() })
                assertEquals("top-1 class for $name", labels[cpuTop], labels[gpuTop])
                assertTrue("GPU logits within 0.1 of CPU for $name (max diff $maxDiff)", maxDiff < 0.1f)
            }
        }
    }
}
