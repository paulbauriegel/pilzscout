package de.pilzscout.app.ml

import android.util.Log
import com.google.ai.edge.litert.Accelerator
import de.pilzscout.app.pack.PackFiles
import de.pilzscout.app.pack.PackRepository
import de.pilzscout.app.settings.SettingsRepository
import de.pilzscout.core.model.PackComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/** Why the model runs on the CPU although GPU acceleration was requested. */
enum class GpuFallbackReason {
    /** LiteRT could not compile the model for the GPU (unsupported ops, no OpenCL, ...). */
    COMPILE_FAILED,

    /** The GPU compiled the model but returned NaN or infinite logits. */
    NON_FINITE_OUTPUT,

    /** Half precision returned NaN or infinite logits; the GPU keeps running, in full precision. */
    FP16_NON_FINITE,
}

sealed interface ClassifierState {
    data object NotInstalled : ClassifierState
    data object Loading : ClassifierState
    data class Ready(val info: ModelInfo, val gpuFallback: GpuFallbackReason? = null) : ClassifierState
    data class Failed(val message: String) : ClassifierState
}

/**
 * Lazily loads the classifier from the installed model component and reloads it when the
 * component or the GPU setting changes. Loading happens on first [get].
 *
 * GPU acceleration is best effort: if the model cannot be compiled for the GPU the CPU is used
 * for this load, and if the GPU produces garbage at run time ([NonFiniteLogitsException]) the
 * caller invokes [disableGpu], which switches the preference off so the problem does not recur.
 */
@Singleton
class ClassifierProvider @Inject constructor(
    private val packFiles: PackFiles,
    private val packRepository: PackRepository,
    private val settings: SettingsRepository,
    private val json: Json,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutex = Mutex()
    private var classifier: LiteRtClassifier? = null
    private var loadedKey: String? = null

    /** Remembered across reloads so the settings screen can explain why the GPU switch is off. */
    private var gpuFallback: GpuFallbackReason? = null

    private val _state = MutableStateFlow<ClassifierState>(ClassifierState.NotInstalled)
    val state: StateFlow<ClassifierState> = _state

    /** Changes whenever the model files or the accelerator preference change. */
    private val key = combine(packRepository.state, settings.useGpu) { pack, gpu ->
        val installed = pack.installed[PackComponent.MODEL] ?: return@combine null
        "${installed.version}@${installed.installedAt}:${if (gpu) "gpu" else "cpu"}"
    }.distinctUntilChanged()

    init {
        scope.launch {
            key.collect { k ->
                mutex.withLock {
                    if (k != loadedKey) {
                        classifier?.close()
                        classifier = null
                        loadedKey = null
                        // Forget a compile failure when the user flips the switch either way; a
                        // NaN fallback turned the switch off itself and its note must survive that.
                        if (k != null && (k.endsWith(":gpu") || gpuFallback == GpuFallbackReason.COMPILE_FAILED)) gpuFallback = null
                        _state.value = if (k == null) ClassifierState.NotInstalled else ClassifierState.Loading
                    }
                }
                // Preload: compiling for the GPU and the first inference take a second or two; do it now,
                // in the background, so the first identification does not pay for it.
                if (k != null) launch { runCatching { get() }.onFailure { Log.w(TAG, "preload failed", it) } }
            }
        }
    }

    suspend fun get(): MushroomClassifier = mutex.withLock {
        classifier?.let { return it }
        val k = key.first() ?: throw IllegalStateException("Model component is not installed")
        _state.value = ClassifierState.Loading
        try {
            val meta = json.decodeFromString(ModelMeta.serializer(), packFiles.modelFile("model.json").readText())
            val gpu = settings.useGpu.first()
            val version = packRepository.state.value.installed[PackComponent.MODEL]?.version ?: "unknown"
            val modelFile = packFiles.modelFile(meta.modelFile)
            val precision = if (meta.gpuFp16Safe && gpuFallback != GpuFallbackReason.FP16_NON_FINITE) GpuPrecision.FP16 else GpuPrecision.FP32
            val loaded = if (gpu) {
                try {
                    LiteRtClassifier(modelFile, meta, version, Accelerator.GPU, precision)
                } catch (e: Exception) {
                    Log.w(TAG, "GPU compilation failed, falling back to CPU", e)
                    gpuFallback = GpuFallbackReason.COMPILE_FAILED
                    LiteRtClassifier(modelFile, meta, version, Accelerator.CPU)
                }
            } else {
                LiteRtClassifier(modelFile, meta, version, Accelerator.CPU)
            }
            loaded.warmUp()
            classifier = loaded
            loadedKey = k
            _state.value = ClassifierState.Ready(loaded.info, gpuFallback)
            loaded
        } catch (e: Throwable) {
            _state.value = ClassifierState.Failed(e.message ?: e.toString())
            throw e
        }
    }

    /**
     * Drops the GPU classifier after it returned non-finite logits and turns the preference off.
     * The next [get] loads the model on the CPU. Safe to call when the GPU is already off.
     */
    suspend fun disableGpu(reason: GpuFallbackReason) {
        mutex.withLock {
            Log.w(TAG, "Disabling GPU acceleration: $reason")
            gpuFallback = reason
            classifier?.close()
            classifier = null
            loadedKey = null
            _state.value = ClassifierState.Loading
        }
        settings.setUseGpu(false)
    }

    /**
     * Called when the GPU returned non-finite logits. In half precision the next load uses fp32 on the GPU
     * (the switch stays on); in full precision the GPU is given up, see [disableGpu]. Returns the classifier
     * to retry with.
     */
    suspend fun onNonFiniteLogits(failed: MushroomClassifier): MushroomClassifier {
        val halfPrecision = (failed as? LiteRtClassifier)?.let { it.accelerator == Accelerator.GPU && it.gpuPrecision == GpuPrecision.FP16 } ?: false
        if (halfPrecision) {
            mutex.withLock {
                Log.w(TAG, "GPU fp16 returned non-finite logits; reloading in fp32")
                gpuFallback = GpuFallbackReason.FP16_NON_FINITE
                classifier?.close()
                classifier = null
                loadedKey = null
                _state.value = ClassifierState.Loading
            }
        } else {
            disableGpu(GpuFallbackReason.NON_FINITE_OUTPUT)
        }
        return get()
    }

    suspend fun labels(): List<String> = packFiles.modelFile("labels.txt").readLines()

    private companion object {
        const val TAG = "ClassifierProvider"
    }
}
