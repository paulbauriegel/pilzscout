package de.pilzscout.app.ml

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.SystemClock
import android.util.Log
import com.google.ai.edge.litert.Accelerator
import com.google.ai.edge.litert.CompiledModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.math.abs
import kotlin.random.Random

/**
 * Debug-build benchmark of LiteRT accelerator/precision variants on model files pushed to the device.
 *
 * Trigger (debug builds only; the app keeps running normally, results land in <dir>/report.txt):
 *   adb push model.tflite /data/local/tmp/x.tflite
 *   adb shell run-as de.pilzscout.app sh -c 'mkdir -p files/bench && cp /data/local/tmp/x.tflite files/bench/'
 *   (a sibling x.json in the packs/model/model.json format is optional; "batch" > 1 is honoured)
 *   adb shell am start -n de.pilzscout.app/.MainActivity --es pilzscout.benchmark files/bench
 *   adb shell run-as de.pilzscout.app cat files/bench/report.txt
 *
 * Each variant runs WARMUP + RUNS inferences on the same seeded random input; the report lists the median and
 * minimum time per image (run + readback) and the largest logit difference against the 4-thread CPU result.
 */
object DeviceBenchmark {
    private const val TAG = "DeviceBenchmark"
    private const val WARMUP = 2
    private const val RUNS = 10
    private const val PRELOAD_GRACE_MS = 20_000L

    fun isDebuggable(context: Context): Boolean = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

    private fun variants(): List<Pair<String, CompiledModel.Options>> {
        val fp32 = CompiledModel.GpuOptions.Precision.FP32
        val fp16 = CompiledModel.GpuOptions.Precision.FP16
        val mixed = CompiledModel.GpuOptions.Precision.FP16_WITH_FP32_ACCUM
        fun gpu(o: CompiledModel.GpuOptions) = CompiledModel.Options(Accelerator.GPU).also { it.gpuOptions = o }
        return listOf(
            "cpu_${LiteRtClassifier.CPU_THREADS}threads" to CompiledModel.Options(Accelerator.CPU).also { it.cpuOptions = CompiledModel.CpuOptions(numThreads = LiteRtClassifier.CPU_THREADS) },
            "gpu_fp32" to gpu(CompiledModel.GpuOptions(precision = fp32, infiniteFloatCapping = true)),
            "gpu_fp16" to gpu(CompiledModel.GpuOptions(precision = fp16)),
            "gpu_fp16_acc32" to gpu(CompiledModel.GpuOptions(precision = mixed)),
            "gpu_fp32_highprio" to gpu(CompiledModel.GpuOptions(precision = fp32, infiniteFloatCapping = true, priority = CompiledModel.GpuOptions.Priority.HIGH)),
        )
    }

    suspend fun run(context: Context, dir: File): File = withContext(Dispatchers.Default) {
        kotlinx.coroutines.delay(PRELOAD_GRACE_MS) // let the app's own classifier preload (GPU compile + warm-up) finish first
        val report = File(dir, "report.txt").also { it.writeText("") }
        fun note(line: String) {
            Log.i(TAG, line)
            report.appendText(line + "\n")
        }
        val json = Json { ignoreUnknownKeys = true }
        val defaultMeta = json.decodeFromString(ModelMeta.serializer(), context.assets.open("packs/model/model.json").bufferedReader().readText())
        val models = dir.listFiles { f -> f.name.endsWith(".tflite") }?.sortedBy { it.name }.orEmpty()
        note("device benchmark: ${models.size} model(s), $WARMUP warm-up + $RUNS timed runs per variant")
        for (modelFile in models) {
            val metaFile = File(dir, modelFile.name.removeSuffix(".tflite") + ".json")
            val meta = if (metaFile.exists()) json.decodeFromString(ModelMeta.serializer(), metaFile.readText()) else defaultMeta
            val batch = meta.batch.coerceAtLeast(1)
            val input = FloatArray(batch * meta.inputSize * meta.inputSize * 3).also { a -> val r = Random(7); for (i in a.indices) a[i] = r.nextFloat() * 255f }
            note("== ${modelFile.name} (${modelFile.length() / 1_000_000} MB, ${meta.inputSize} px, batch $batch)")
            var reference: FloatArray? = null
            for ((label, options) in variants()) {
                val model = try {
                    CompiledModel.create(modelFile.absolutePath, options)
                } catch (e: Exception) {
                    note("$label: could not compile (${e.message})"); continue
                }
                val ins = model.createInputBuffers()
                val outs = model.createOutputBuffers()
                try {
                    val times = ArrayList<Long>()
                    var logits = FloatArray(0)
                    repeat(WARMUP + RUNS) { i ->
                        ins[0].writeFloat(input)
                        val t0 = SystemClock.elapsedRealtimeNanos()
                        model.run(ins, outs)
                        logits = outs[0].readFloat()
                        if (i >= WARMUP) times += (SystemClock.elapsedRealtimeNanos() - t0) / 1_000_000
                    }
                    val perImage = times.sorted().map { it.toDouble() / batch }
                    val nonFinite = logits.count { !it.isFinite() }
                    val ref = reference
                    val diff = if (ref != null && nonFinite == 0 && ref.size == logits.size) logits.indices.maxOf { abs(logits[it] - ref[it]) } else Float.NaN
                    val classes = logits.size / batch
                    val tops = (0 until batch).map { b -> (0 until classes).maxBy { logits[b * classes + it] } }
                    if (reference == null && nonFinite == 0) reference = logits
                    note("$label: median ${"%.0f".format(perImage[perImage.size / 2])} ms/image, min ${"%.0f".format(perImage.first())} ms, non-finite $nonFinite, max |diff vs cpu| $diff, top-1 $tops")
                } finally {
                    ins.forEach { runCatching { it.close() } }
                    outs.forEach { runCatching { it.close() } }
                    runCatching { model.close() }
                }
            }
        }
        note("done")
        report
    }
}
