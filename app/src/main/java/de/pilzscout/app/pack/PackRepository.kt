package de.pilzscout.app.pack

import android.content.Context
import android.util.Log
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import dagger.hilt.android.qualifiers.ApplicationContext
import de.pilzscout.app.settings.SettingsRepository
import de.pilzscout.core.model.PackComponent
import de.pilzscout.core.model.PackComponentManifest
import de.pilzscout.core.model.PackManifest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
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
    val errors: Map<PackComponent, PackError> = emptyMap(),
    val loaded: Boolean = false,
    /** True for the `play` flavour: the pack is downloaded, not copied from the APK. */
    val remote: Boolean = false,
    /** Components the source can install, in manifest order. */
    val offered: List<PackComponentManifest> = emptyList(),
    /** Set when the manifest itself could not be loaded (offline on first start, nothing published, ...). */
    val manifestError: PackError? = null,
    val checking: Boolean = false,
) {
    val identificationReady: Boolean
        get() = PackComponent.MODEL in installed && PackComponent.CORE in installed

    fun isInstalled(component: PackComponent) = component in installed

    /**
     * True when the source's manifest carries a different component version, or a newer pack version (a pack
     * rebuild can change a component's files, e.g. model.json, without changing its version string).
     * Unchanged files are reused, so an update of the latter kind transfers nothing.
     */
    fun isOutdated(component: PackComponent): Boolean {
        val m = manifest ?: return false
        val offeredComponent = offered.firstOrNull { it.component == component } ?: return false
        val current = installed[component] ?: return false
        return current.version != offeredComponent.version || current.packVersion != m.packVersion
    }

    val updatesAvailable: Boolean get() = installed.keys.any { isOutdated(it) }

    /** Bytes still to transfer for the components not installed yet (archives for remote sources). */
    fun missingBytes(): Long = offered.filter { it.component !in installed }.sumOf { if (remote) it.downloadBytes else it.bytes }
}

/**
 * Single source of truth for which pack components are installed. Installs run in this singleton's
 * scope so they survive navigation; PackInstallWorker additionally keeps the process alive.
 */
@Singleton
class PackRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val source: PackSource,
    private val installer: PackInstaller,
    private val settings: SettingsRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private val _state = MutableStateFlow(PackState(remote = source.remote))
    val state: StateFlow<PackState> = _state

    init {
        scope.launch {
            installer.cleanUp()
            refresh()
        }
    }

    /**
     * Reloads the manifest and the installed markers. [checkForUpdates] makes a remote source ask the
     * repository for a newer pack now instead of at most once a day.
     */
    suspend fun refresh(checkForUpdates: Boolean = false) {
        _state.update { it.copy(checking = true) }
        mutex.withLock {
            val result = runCatching { source.manifest(refresh = checkForUpdates) }
            result.exceptionOrNull()?.let { Log.w(TAG, "Pack manifest from ${source.id} unavailable", it) }
            val manifest = result.getOrNull()
            val installed = PackComponent.entries.mapNotNull { c -> installer.readInstalled(c)?.let { c to it } }.toMap()
            _state.update {
                it.copy(
                    manifest = manifest ?: it.manifest,
                    offered = (manifest ?: it.manifest)?.components?.filter(source::offers).orEmpty(),
                    manifestError = result.exceptionOrNull()?.let(PackError::from),
                    installed = installed,
                    loaded = true,
                    checking = false,
                )
            }
        }
        // The bundled pack is local, so newer versions of already-installed components are applied automatically.
        // Remote updates are offered in the UI instead of being downloaded unasked.
        if (!source.remote) {
            val outdated = _state.value.let { s -> s.installed.keys.filter { s.isOutdated(it) } }
            if (outdated.isNotEmpty()) install(outdated)
        }
    }

    fun checkForUpdates(): Job = scope.launch { refresh(checkForUpdates = true) }

    /** Installs the given components sequentially (required ones first). Safe to call repeatedly. */
    fun install(components: List<PackComponent>): Job = scope.launch {
        mutex.withLock {
            val manifest = _state.value.manifest ?: runCatching { source.manifest() }.getOrElse { e ->
                _state.update { it.copy(manifestError = PackError.from(e), errors = it.errors + components.associateWith { _ -> PackError.from(e) }) }
                return@withLock
            }.also { m -> _state.update { it.copy(manifest = m, offered = m.components.filter(source::offers), manifestError = null) } }
            // Skips what is already current, e.g. when the worker follows an install started from the UI.
            val ordered = manifest.components
                .filter { it.component in components && source.offers(it) }
                .filter { _state.value.let { s -> !s.isInstalled(it.component) || s.isOutdated(it.component) } }
                .sortedByDescending { it.required }
            networkProblem()?.let { problem ->
                _state.update { it.copy(errors = it.errors + ordered.associate { c -> c.component to problem }) }
                return@withLock
            }
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
                } catch (e: kotlinx.coroutines.CancellationException) {
                    _state.update { it.copy(installing = it.installing - kind) }
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "Installing ${kind.id} from ${source.id} failed", e)
                    _state.update { it.copy(installing = it.installing - kind, errors = it.errors + (kind to PackError.from(e))) }
                }
            }
        }
    }

    /** Installs every offered component that is missing or outdated. */
    fun installAll(): Job {
        val s = _state.value
        val wanted = if (s.manifest == null) PackComponent.entries
        else s.offered.map { it.component }.filter { !s.isInstalled(it) || s.isOutdated(it) }
        return install(wanted)
    }

    fun uninstall(component: PackComponent) {
        scope.launch {
            mutex.withLock {
                installer.uninstall(component)
                _state.update { it.copy(installed = it.installed - component, errors = it.errors - component) }
            }
        }
    }

    /** Null when a remote install may start now. */
    private suspend fun networkProblem(): PackError? {
        if (!source.remote) return null
        val cm = context.getSystemService(ConnectivityManager::class.java)
        val caps = cm.activeNetwork?.let(cm::getNetworkCapabilities) ?: return PackError.Offline
        if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) return PackError.Offline
        val metered = !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
        return if (metered && settings.packWifiOnly.first()) PackError.WifiRequired else null
    }

    private companion object {
        const val TAG = "PackRepository"
    }
}
