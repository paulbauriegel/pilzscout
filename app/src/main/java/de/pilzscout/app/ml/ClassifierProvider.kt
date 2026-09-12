package de.pilzscout.app.ml

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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

sealed interface ClassifierState {
    data object NotInstalled : ClassifierState
    data object Loading : ClassifierState
    data class Ready(val info: ModelInfo) : ClassifierState
    data class Failed(val message: String) : ClassifierState
}

/**
 * Lazily loads the classifier from the installed model component and reloads it when the
 * component or the GPU setting changes. Loading happens on first [get].
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
                        _state.value = if (k == null) ClassifierState.NotInstalled else ClassifierState.Loading
                    }
                }
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
            val loaded = LiteRtClassifier(
                modelFile = packFiles.modelFile(meta.modelFile),
                meta = meta,
                version = version,
                accelerator = if (gpu) Accelerator.GPU else Accelerator.CPU,
            )
            classifier = loaded
            loadedKey = k
            _state.value = ClassifierState.Ready(loaded.info)
            loaded
        } catch (e: Throwable) {
            _state.value = ClassifierState.Failed(e.message ?: e.toString())
            throw e
        }
    }

    suspend fun labels(): List<String> = packFiles.modelFile("labels.txt").readLines()
}
