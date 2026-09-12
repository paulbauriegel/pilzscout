package de.pilzscout.app.data.species

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import de.pilzscout.app.pack.PackFiles
import java.io.File
import java.util.Collections
import javax.inject.Inject
import javax.inject.Singleton

/** A reference image per species: Wikipedia thumbnail in the content language, any language, else the first FungiTastic photo. */
@Singleton
class SpeciesImages @Inject constructor(
    private val repo: SpeciesRepository,
    private val packFiles: PackFiles,
) {
    private val cache = Collections.synchronizedMap(object : LinkedHashMap<String, File?>(256, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, File?>?) = size > 2000
    })

    suspend fun thumb(speciesId: String, lang: String): File? {
        val key = "$speciesId@$lang"
        if (cache.containsKey(key)) return cache[key]
        val file = runCatching {
            repo.wikiArticle(speciesId, lang)?.thumbFile?.let { packFiles.existing(it) }
                ?: repo.wikiLanguages(speciesId).firstNotNullOfOrNull { l -> repo.wikiArticle(speciesId, l)?.thumbFile?.let { packFiles.existing(it) } }
                ?: repo.ftLeadPhoto(speciesId)?.thumbFile?.let { packFiles.existing(it) }
        }.getOrNull()
        cache[key] = file
        return file
    }
}

/** Compose helper: resolves the species image asynchronously; null until loaded or when none exists. */
@Composable
fun SpeciesImages.rememberThumb(speciesId: String?, lang: String): State<File?> =
    produceState<File?>(initialValue = null, speciesId, lang) { value = speciesId?.let { thumb(it, lang) } }
