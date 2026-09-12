package de.pilzscout.app.pack

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import de.pilzscout.core.model.PackManifest
import kotlinx.serialization.json.Json
import java.io.InputStream
import javax.inject.Inject

/** Where pack bytes come from. v1 ships them as APK assets; a remote HTTP source can implement this later. */
interface PackSource {
    val id: String
    suspend fun manifest(): PackManifest
    fun open(path: String): InputStream
}

class AssetPackSource @Inject constructor(
    @ApplicationContext private val context: Context,
    private val json: Json,
) : PackSource {
    override val id: String = "assets"

    override suspend fun manifest(): PackManifest =
        context.assets.open("$ROOT/manifest.json").use { json.decodeFromString(PackManifest.serializer(), it.readBytes().decodeToString()) }

    override fun open(path: String): InputStream = context.assets.open("$ROOT/$path")

    companion object {
        const val ROOT = "packs"
    }
}
