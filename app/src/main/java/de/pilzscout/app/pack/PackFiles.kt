package de.pilzscout.app.pack

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import de.pilzscout.core.model.PackComponent
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** Resolves installed pack files under filesDir/packs/<component>/... */
@Singleton
class PackFiles @Inject constructor(@ApplicationContext context: Context) {
    val root: File = File(context.filesDir, "packs")

    /** Components are assembled here and moved into [root] only once every file is verified. */
    val stagingRoot: File = File(root, ".staging")

    /** Replaced component directories, deleted right after a swap (or on the next start after a crash). */
    val trash: File = File(root, ".trash")

    /** Partially downloaded archives, named by their SHA-256 so a resumed download never mixes versions. */
    val downloads: File = File(context.cacheDir, "pack-downloads")

    /** Last manifest and catalog entry fetched by RemotePackSource, so the app knows its pack offline. */
    val remoteManifest: File = File(root, "remote-manifest.json")
    val remoteEntry: File = File(root, "remote-entry.json")

    fun componentDir(component: PackComponent): File = File(root, component.id)

    fun stagingDir(component: PackComponent): File = File(stagingRoot, component.id)

    fun installedMarker(component: PackComponent): File = File(componentDir(component), "installed.json")

    /** Absolute file for a manifest-relative path like "core/species.db" or "wiki/thumbs/x.webp". */
    fun file(relativePath: String): File = File(root, relativePath)

    /**
     * The staging location of a manifest path. Manifests can come from the network, so the path must stay
     * inside the component's directory.
     */
    fun stagingFile(component: PackComponent, relativePath: String): File {
        val dir = stagingDir(component)
        val target = File(stagingRoot, relativePath)
        if (!relativePath.startsWith("${component.id}/") || !target.canonicalPath.startsWith(dir.canonicalPath + File.separator)) {
            throw IOException("Pack path outside its component: $relativePath")
        }
        return target
    }

    fun speciesDb(): File = file("core/species.db")

    fun modelFile(name: String): File = file("model/$name")

    /** Returns the file only if it exists (thumbnails may belong to a component that is not installed yet). */
    fun existing(relativePath: String?): File? = relativePath?.let { file(it) }?.takeIf { it.exists() }
}
