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

@Serializable
data class PackComponentManifest(
    val id: String,
    val version: String,
    val bytes: Long,
    val required: Boolean,
    val bundled: Boolean = true,
    val files: List<PackFile>,
) {
    val component: PackComponent get() = PackComponent.fromId(id)
}

@Serializable
data class PackManifest(
    val packVersion: String,
    val region: String,
    val schemaVersion: Int,
    val components: List<PackComponentManifest>,
)
