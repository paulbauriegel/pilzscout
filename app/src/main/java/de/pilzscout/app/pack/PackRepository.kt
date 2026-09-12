package de.pilzscout.app.pack

import de.pilzscout.core.model.PackComponent
import de.pilzscout.core.model.PackManifest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

data class PackState(
    val manifest: PackManifest? = null,
    val installed: Map<PackComponent, InstalledComponent> = emptyMap(),
    val installing: Map<PackComponent, Float> = emptyMap(),
    val errors: Map<PackComponent, String> = emptyMap(),
    val loaded: Boolean = false,
) {
    val identificationReady: Boolean
        get() = PackComponent.MODEL in installed && PackComponent.CORE in installed

    fun isInstalled(component: PackComponent) = component in installed

    /** True when the installed component version differs from the bundled manifest (update available). */
    fun isOutdated(component: PackComponent): Boolean {
        val bundled = manifest?.components?.firstOrNull { it.id == component.id } ?: return false
        val current = installed[component] ?: return false
        return current.version != bundled.version
    }
}

/**
 * Single source of truth for which pack components are installed. Installs run in this singleton's
 * scope so they survive navigation; PackInstallWorker additionally keeps the process alive.
 */
@Singleton
class PackRepository @Inject constructor(
    private val source: PackSource,
    private val installer: PackInstaller,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private val _state = MutableStateFlow(PackState())
    val state: StateFlow<PackState> = _state

    init {
        scope.launch { refresh() }
    }

    suspend fun refresh() {
        val manifest = runCatching { source.manifest() }.getOrNull()
        val installed = PackComponent.entries.mapNotNull { c -> installer.readInstalled(c)?.let { c to it } }.toMap()
        _state.update { it.copy(manifest = manifest, installed = installed, loaded = true) }
    }

    /** Installs the given components sequentially (required ones first). Safe to call repeatedly. */
    fun install(components: List<PackComponent>) {
        scope.launch {
            mutex.withLock {
                val manifest = _state.value.manifest ?: source.manifest().also { m -> _state.update { it.copy(manifest = m) } }
                val ordered = manifest.components
                    .filter { it.component in components && it.bundled }
                    .sortedByDescending { it.required }
                for (component in ordered) {
                    val kind = component.component
                    _state.update { it.copy(installing = it.installing + (kind to 0f), errors = it.errors - kind) }
                    try {
                        installer.install(source, manifest.packVersion, component).collect { p ->
                            _state.update { it.copy(installing = it.installing + (kind to p.fraction)) }
                        }
                        val marker = installer.readInstalled(kind)
                        _state.update {
                            it.copy(
                                installing = it.installing - kind,
                                installed = if (marker != null) it.installed + (kind to marker) else it.installed,
                            )
                        }
                    } catch (e: Exception) {
                        _state.update { it.copy(installing = it.installing - kind, errors = it.errors + (kind to (e.message ?: e.toString()))) }
                    }
                }
            }
        }
    }

    fun installAllBundled() {
        install(PackComponent.entries)
    }

    fun uninstall(component: PackComponent) {
        scope.launch {
            mutex.withLock {
                installer.uninstall(component)
                _state.update { it.copy(installed = it.installed - component) }
            }
        }
    }
}
