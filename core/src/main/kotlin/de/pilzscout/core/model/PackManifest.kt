package de.pilzscout.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class PackComponent(val id: String) {
    MODEL("model"),
    CORE("core"),
    WIKI("wiki"),
    FUNGITASTIC("fungitastic"),
    IMAGES_HD("images-hd");

    companion object {
        fun fromId(id: String): PackComponent = entries.first { it.id == id }
    }
}

@Serializable
data class PackFile(val path: String, val sha256: String, val bytes: Long)

/** One download holding all files of a component (an uncompressed ustar archive with manifest-relative names). */
@Serializable
data class PackArchive(val path: String, val sha256: String, val bytes: Long, val format: String = "tar")

@Serializable
data class PackComponentManifest(
    val id: String,
    val version: String,
    val bytes: Long,
    val required: Boolean,
    val bundled: Boolean = true,
    val files: List<PackFile>,
    val archive: PackArchive? = null,
    val license: String? = null,
    val attribution: String? = null,
) {
    val component: PackComponent get() = PackComponent.fromId(id)

    /** Bytes a remote install transfers: the archive if there is one, else the loose files. */
    val downloadBytes: Long get() = archive?.bytes ?: bytes
}

@Serializable
data class PackManifest(
    val packVersion: String,
    val region: String,
    /** Room schema version of core/species.db (SpeciesDatabase.VERSION). */
    val schemaVersion: Int,
    val components: List<PackComponentManifest>,
    val formatVersion: Int = 1,
)

/**
 * catalog.json at the root of the pack repository: every published pack and the commit it was
 * published in. The app downloads all other files by [PackCatalogEntry.revision], never by branch.
 */
@Serializable
data class PackCatalog(val formatVersion: Int = 1, val packs: List<PackCatalogEntry>) {

    /**
     * The newest pack this app can read: same Room schema, a manifest format it understands, and not
     * newer than the app allows. [packs] is newest first, as written by `packs publish`.
     */
    fun newestCompatible(schemaVersion: Int, appVersionCode: Int, region: String = "DE"): PackCatalogEntry? =
        packs.firstOrNull {
            it.region == region &&
                it.schemaVersion == schemaVersion &&
                it.formatVersion <= SUPPORTED_MANIFEST_FORMAT &&
                it.minAppVersionCode <= appVersionCode
        }

    companion object {
        const val SUPPORTED_FORMAT = 1
        const val SUPPORTED_MANIFEST_FORMAT = 2
    }
}

@Serializable
data class PackCatalogEntry(
    val packVersion: String,
    val region: String,
    val schemaVersion: Int,
    val formatVersion: Int = 1,
    val minAppVersionCode: Int = 1,
    val revision: String,
    val manifest: String,
    val manifestSha256: String,
    val bytes: Map<String, Long> = emptyMap(),
)
