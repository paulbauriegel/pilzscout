package de.pilzscout.app.data.species

import android.content.Context
import androidx.room.Room
import dagger.hilt.android.qualifiers.ApplicationContext
import de.pilzscout.app.pack.PackFiles
import de.pilzscout.app.pack.PackRepository
import de.pilzscout.core.model.PackComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Opens the read-only species DB from the installed "core" component and reopens it whenever
 * that component is (re)installed. Room's prepackaged-DB validation runs on first open.
 */
@Singleton
class SpeciesDatabaseHolder @Inject constructor(
    @ApplicationContext private val context: Context,
    private val packFiles: PackFiles,
    packRepository: PackRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _db = MutableStateFlow<SpeciesDatabase?>(null)
    val db: StateFlow<SpeciesDatabase?> = _db

    init {
        scope.launch {
            packRepository.state
                .map { it.installed[PackComponent.CORE]?.installedAt }
                .distinctUntilChanged()
                .collect { installedAt ->
                    _db.value?.close()
                    _db.value = if (installedAt != null && packFiles.speciesDb().exists()) open() else null
                }
        }
    }

    private fun open(): SpeciesDatabase {
        // Copy-on-open would double the storage; Room can open the installed file in place via createFromFile,
        // which copies into the app's database dir once. We use a name tied to the install time so a
        // reinstall gets a fresh copy.
        val name = "species-${packFiles.speciesDb().lastModified()}.db"
        context.getDatabasePath(name).parentFile?.listFiles()
            ?.filter { it.name.startsWith("species-") && !it.name.startsWith(name) }
            ?.forEach { it.delete() }
        return Room.databaseBuilder(context, SpeciesDatabase::class.java, name)
            .createFromFile(packFiles.speciesDb())
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()
    }

    suspend fun await(): SpeciesDatabase = db.first { it != null }!!
}
