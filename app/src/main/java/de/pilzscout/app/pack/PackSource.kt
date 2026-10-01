package de.pilzscout.app.pack

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import de.pilzscout.core.model.PackComponentManifest
import de.pilzscout.core.model.PackManifest
import kotlinx.serialization.json.Json
import java.io.InputStream
import javax.inject.Inject

/** An open pack file. [offset] is where [input] starts; a source may ignore a requested offset and return 0. */
class PackStream(val input: InputStream, val offset: Long) : AutoCloseable {
    override fun close() = input.close()
}

/** Where pack bytes come from: the APK assets (`bundled` flavour) or the pack repository (`play` flavour). */
interface PackSource {
    val id: String

    /** True when bytes come over the network: installs need a connection and use component archives. */
    val remote: Boolean

    /**
     * The pack this source offers. [refresh] asks a remote source to check for a newer pack instead of
     * using its cached manifest; local sources ignore it.
     */
    suspend fun manifest(refresh: Boolean = false): PackManifest

    /** Opens a manifest-relative path ("core/species.db", or an archive name such as "wiki.tar"). */
    fun open(path: String, offset: Long = 0): PackStream

    /** Components this source can install. */
    fun offers(component: PackComponentManifest): Boolean = if (remote) component.files.isNotEmpty() else component.bundled
}

class AssetPackSource @Inject constructor(
    @ApplicationContext private val context: Context,
    private val json: Json,
) : PackSource {
    override val id: String = "assets"
    override val remote: Boolean = false

    override suspend fun manifest(refresh: Boolean): PackManifest =
        context.assets.open("$ROOT/manifest.json").use { json.decodeFromString(PackManifest.serializer(), it.readBytes().decodeToString()) }

    override fun open(path: String, offset: Long): PackStream = PackStream(context.assets.open("$ROOT/$path"), 0)

    companion object {
        const val ROOT = "packs"
    }
}
