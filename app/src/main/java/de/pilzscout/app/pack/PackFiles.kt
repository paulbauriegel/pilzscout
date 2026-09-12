package de.pilzscout.app.pack

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import de.pilzscout.core.model.PackComponent
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** Resolves installed pack files under filesDir/packs/<component>/... */
@Singleton
class PackFiles @Inject constructor(@ApplicationContext context: Context) {
    val root: File = File(context.filesDir, "packs")

    fun componentDir(component: PackComponent): File = File(root, component.id)

    fun installedMarker(component: PackComponent): File = File(componentDir(component), "installed.json")

    /** Absolute file for a manifest-relative path like "core/species.db" or "wiki/thumbs/x.webp". */
    fun file(relativePath: String): File = File(root, relativePath)

    fun speciesDb(): File = file("core/species.db")

    fun modelFile(name: String): File = file("model/$name")

    /** Returns the file only if it exists (thumbnails may belong to a component that is not installed yet). */
    fun existing(relativePath: String?): File? = relativePath?.let { file(it) }?.takeIf { it.exists() }
}
