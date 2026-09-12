package de.pilzscout.app.export

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import de.pilzscout.app.BuildConfig
import de.pilzscout.app.data.history.HistoryRepository
import de.pilzscout.app.data.history.ObservationWithPhotos
import de.pilzscout.app.data.species.SpeciesEntity
import de.pilzscout.app.data.species.SpeciesRepository
import de.pilzscout.app.identify.ContextualInputs
import de.pilzscout.core.compare.ComparisonResult
import de.pilzscout.core.export.ExportCandidate
import de.pilzscout.core.export.ExportContext
import de.pilzscout.core.export.ExportCorrection
import de.pilzscout.core.export.ExportIndex
import de.pilzscout.core.export.ExportLocation
import de.pilzscout.core.export.ExportModel
import de.pilzscout.core.export.ExportObservation
import de.pilzscout.core.export.ExportPhoto
import de.pilzscout.core.export.ExportPhotoPrediction
import de.pilzscout.core.export.ExportSpeciesRef
import de.pilzscout.core.identify.Agreement
import de.pilzscout.core.identify.Candidate
import de.pilzscout.core.identify.ConfidenceDescriptor
import de.pilzscout.core.identify.FusionResult
import de.pilzscout.core.model.ViewType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject

/**
 * Writes one or more observations into a ZIP chosen by the user through the document picker.
 * Single observation: observation.json + photos/<view>.jpg. Several: one folder per observation + index.json.
 */
class ObservationExporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val history: HistoryRepository,
    private val species: SpeciesRepository,
) {
    private val json = Json { prettyPrint = true; encodeDefaults = true; ignoreUnknownKeys = true }

    suspend fun export(ids: List<String>, target: Uri) = withContext(Dispatchers.IO) {
        val observations = history.byIds(ids).sortedByDescending { it.observation.capturedAt }
        require(observations.isNotEmpty()) { "nothing to export" }
        val speciesIds = observations.flatMap { collectSpeciesIds(it) }.distinct()
        val speciesMap = species.byIds(speciesIds)
        val exportedAt = now()
        context.contentResolver.openOutputStream(target, "w")?.use { out ->
            ZipOutputStream(out.buffered()).use { zip ->
                val single = observations.size == 1
                for (obs in observations) {
                    val prefix = if (single) "" else "${obs.observation.id}/"
                    val (doc, files) = build(obs, speciesMap, exportedAt)
                    zip.putNextEntry(ZipEntry("${prefix}observation.json"))
                    zip.write(json.encodeToString(ExportObservation.serializer(), doc).toByteArray())
                    zip.closeEntry()
                    for ((entryName, file) in files) {
                        if (!file.exists()) continue
                        zip.putNextEntry(ZipEntry(prefix + entryName))
                        file.inputStream().use { it.copyTo(zip) }
                        zip.closeEntry()
                    }
                }
                if (!single) {
                    zip.putNextEntry(ZipEntry("index.json"))
                    zip.write(json.encodeToString(ExportIndex.serializer(), ExportIndex(exportedAt = exportedAt, observations = observations.map { it.observation.id })).toByteArray())
                    zip.closeEntry()
                }
            }
        } ?: error("Could not open $target for writing")
    }

    private fun collectSpeciesIds(obs: ObservationWithPhotos): List<String> {
        val fusion = fusion(obs)
        return buildList {
            add(obs.observation.primarySpeciesId)
            obs.observation.correctedSpeciesId?.let(::add)
            fusion?.combined?.forEach { it.speciesId?.let(::add) }
            fusion?.photos?.forEach { p -> p.top.forEach { it.speciesId?.let(::add) } }
        }
    }

    private fun fusion(obs: ObservationWithPhotos): FusionResult? =
        runCatching { json.decodeFromString(FusionResult.serializer(), obs.observation.fusionJson) }.getOrNull()

    /** Returns the JSON document and the (zip entry name -> file) pairs. Duplicate view types get a numeric suffix. */
    fun build(obs: ObservationWithPhotos, speciesMap: Map<String, SpeciesEntity>, exportedAt: String): Pair<ExportObservation, List<Pair<String, File>>> {
        val o = obs.observation
        val fusion = fusion(obs)
        val contextual = runCatching { json.decodeFromString(ContextualInputs.serializer(), o.contextualJson) }.getOrNull()
        val comparison = runCatching { json.decodeFromString(ComparisonResult.serializer(), o.comparisonJson) }.getOrNull()
        fun ref(id: String?): ExportSpeciesRef {
            val sp = id?.let { speciesMap[it] }
            return ExportSpeciesRef(id, sp?.scientificName ?: sp?.binomial, sp?.gbifKey, de.pilzscout.core.model.Edibility.parse(sp?.edibility))
        }
        fun cand(rank: Int, c: Candidate) = ExportCandidate(rank, ref(c.speciesId), c.prob)

        val used = mutableMapOf<String, Int>()
        val files = mutableListOf<Pair<String, File>>()
        val photos = obs.photos.sortedBy { it.position }.map { p ->
            val view = runCatching { ViewType.valueOf(p.viewType) }.getOrDefault(ViewType.HABITAT)
            val n = (used[view.exportName] ?: 0) + 1
            used[view.exportName] = n
            val name = "photos/${view.exportName}${if (n > 1) "-$n" else ""}.jpg"
            files += name to File(p.filePath)
            val top = runCatching { json.decodeFromString(ListSerializer(Candidate.serializer()), p.topkJson) }.getOrDefault(emptyList())
            ExportPhoto(
                file = name, viewType = view, position = p.position, width = p.width, height = p.height,
                prediction = top.firstOrNull()?.let { ExportPhotoPrediction(cand(0, it), top.drop(1).mapIndexed { i, c -> cand(i + 1, c) }) },
                agreement = p.agreement?.let { runCatching { Agreement.valueOf(it) }.getOrNull() },
                inferenceMs = p.inferenceMs,
            )
        }
        val doc = ExportObservation(
            observationId = o.id,
            capturedAt = iso(o.capturedAt),
            exportedAt = exportedAt,
            approxLocation = if (o.locationIncluded && o.lat != null && o.lon != null) ExportLocation(o.lat, o.lon, placeName = o.placeName) else null,
            photos = photos,
            combined = fusion?.combined?.mapIndexed { i, c -> cand(i, c) } ?: obs.candidates.sortedBy { it.rank }.map { ExportCandidate(it.rank, ref(it.speciesId), it.prob) },
            descriptor = runCatching { ConfidenceDescriptor.valueOf(o.descriptor) }.getOrDefault(ConfidenceDescriptor.UNCERTAIN),
            contextualInputs = ExportContext(contextual?.month, contextual?.locationUsed ?: o.locationIncluded, contextual?.germanyFilter ?: true, contextual?.seasonPriorWeight ?: 0f, o.nPhotos),
            comparison = comparison,
            model = ExportModel(o.modelVersion, contextual?.modelName, o.modelPrecision, contextual?.modelInputSize, o.totalInferenceMs),
            userCorrection = o.correctedSpeciesId?.let { ExportCorrection(ref(it), iso(o.correctedAt ?: o.createdAt)) },
            userConfirmed = o.userConfirmed,
            mode = o.mode,
            appVersion = BuildConfig.VERSION_NAME,
        )
        return doc to files
    }

    private fun iso(epochMs: Long): String = DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()))
    private fun now(): String = iso(System.currentTimeMillis())
}
