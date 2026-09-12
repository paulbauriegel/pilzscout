package de.pilzscout.app.identify

import de.pilzscout.app.compare.CompareUseCase
import de.pilzscout.app.data.history.CandidateEntity
import de.pilzscout.app.data.history.HistoryRepository
import de.pilzscout.app.data.history.ObservationEntity
import de.pilzscout.app.data.history.PhotoEntity
import de.pilzscout.app.data.species.SpeciesRepository
import de.pilzscout.app.ml.ClassifierProvider
import de.pilzscout.core.identify.Agreement
import de.pilzscout.core.identify.Candidate
import de.pilzscout.core.identify.Descriptors
import de.pilzscout.core.identify.Fusion
import de.pilzscout.core.identify.FusionResult
import de.pilzscout.core.identify.PhotoPrediction
import de.pilzscout.core.identify.RecommendationEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject

/** Inputs that were combined with the photos; stored for the result screen and the export. */
@Serializable
data class ContextualInputs(
    val month: Int?,
    val locationUsed: Boolean,
    val germanyFilter: Boolean,
    val seasonPriorWeight: Float,
    val nPhotos: Int,
    val modelName: String? = null,
    val modelInputSize: Int? = null,
    val modelAccelerator: String? = null,
)

/** Runs the whole pipeline for a draft and stores the observation. Returns the observation id. */
class IdentifyUseCase @Inject constructor(
    private val classifierProvider: ClassifierProvider,
    private val species: SpeciesRepository,
    private val photoStore: PhotoStore,
    private val history: HistoryRepository,
    private val compare: CompareUseCase,
    private val json: Json,
) {
    suspend fun run(draft: Draft, onProgress: suspend (done: Int, total: Int) -> Unit = { _, _ -> }): String = withContext(Dispatchers.Default) {
        require(draft.photos.isNotEmpty()) { "no photos" }
        val classifier = classifierProvider.get()
        val n = classifier.info.numClasses
        val observationId = UUID.randomUUID().toString()
        val month = Instant.ofEpochMilli(draft.capturedAt).atZone(ZoneId.systemDefault()).monthValue
        val mask = species.germanyMask(n)
        val ids = species.classIndexToId(n)
        val prior = species.monthPrior(n, month)

        val stored = ArrayList<StoredPhoto>()
        val logits = ArrayList<FloatArray>()
        val timings = ArrayList<Long>()
        draft.photos.forEachIndexed { i, photo ->
            val s = photoStore.store(observationId, photo.file, photo.viewType, i)
            stored += s
            val input = classifier.preprocessor.prepare(s.file)
            val out = classifier.classify(input)
            logits += out.logits
            timings += out.inferenceMs
            onProgress(i + 1, draft.photos.size)
        }

        val combined = Fusion.fuse(logits, mask, prior, Fusion.DEFAULT_SEASON_WEIGHT)
        val top = Fusion.topK(combined, TOP_N).map { Candidate(it, ids[it], combined[it]) }
        val descriptor = Descriptors.describe(top.map { it.prob }.toFloatArray())
        val alternatives = Descriptors.alternatives(top)
        val primary = top.first()

        val photoPredictions = draft.photos.mapIndexed { i, photo ->
            val probs = Fusion.softmax(logits[i], mask)
            val photoTop = Fusion.topK(probs, TOP_PHOTO).map { Candidate(it, ids[it], probs[it]) }
            PhotoPrediction(photo.viewType, i, photoTop, Descriptors.agreement(photoTop, primary.classIndex), timings[i])
        }
        // Offline comparison of primary vs. top alternative: stored for export and used for the photo recommendation.
        val photoRanking = photoPredictions.groupBy { it.viewType }.mapValues { (_, ps) -> ps.flatMap { p -> p.top.mapNotNull { it.speciesId } }.distinct() }
        val comparison = primary.speciesId?.let { pid ->
            alternatives.firstOrNull()?.speciesId?.let { aid ->
                runCatching { compare.compare(pid, aid, draft.language, draft.capturedViews, photoRanking, month) }.getOrNull()
            }
        }
        val recommended = RecommendationEngine.recommend(draft.capturedViews, descriptor, differingFeatures = comparison?.differingFeatures ?: emptySet())
        val fusion = FusionResult(
            combined = top,
            descriptor = descriptor,
            alternatives = alternatives,
            photos = photoPredictions,
            germanyFilter = true,
            maskedOutClasses = mask.count { !it },
            month = month,
            seasonPriorWeight = if (prior != null) Fusion.DEFAULT_SEASON_WEIGHT else 0f,
            recommendedView = recommended,
        )
        val contextual = ContextualInputs(
            month, draft.includeLocation && draft.location != null, true, fusion.seasonPriorWeight, draft.photos.size,
            classifier.info.name, classifier.info.inputSize, classifier.info.accelerator,
        )
        val location = draft.location?.takeIf { draft.includeLocation }

        val observation = ObservationEntity(
            id = observationId,
            createdAt = System.currentTimeMillis(),
            capturedAt = draft.capturedAt,
            lat = location?.lat,
            lon = location?.lon,
            locationIncluded = location != null,
            primarySpeciesId = primary.speciesId ?: "class-${primary.classIndex}",
            primaryProb = primary.prob,
            descriptor = descriptor.name,
            nPhotos = draft.photos.size,
            modelVersion = classifier.info.version,
            modelPrecision = classifier.info.precision,
            totalInferenceMs = timings.sum(),
            mode = "offline",
            userConfirmed = false,
            correctedSpeciesId = null,
            correctedAt = null,
            leadPhotoId = "$observationId-0",
            fusionJson = json.encodeToString(FusionResult.serializer(), fusion),
            comparisonJson = comparison?.let { json.encodeToString(de.pilzscout.core.compare.ComparisonResult.serializer(), it) } ?: "{}",
            contextualJson = json.encodeToString(ContextualInputs.serializer(), contextual),
        )
        val photos = stored.mapIndexed { i, s ->
            val p = photoPredictions[i]
            PhotoEntity(
                id = "$observationId-$i",
                observationId = observationId,
                viewType = p.viewType.name,
                position = i,
                filePath = s.file.path,
                thumbPath = s.thumb.path,
                width = s.width,
                height = s.height,
                top1SpeciesId = p.top.firstOrNull()?.speciesId,
                top1Prob = p.top.firstOrNull()?.prob,
                topkJson = json.encodeToString(kotlinx.serialization.builtins.ListSerializer(Candidate.serializer()), p.top),
                inferenceMs = p.inferenceMs,
                agreement = p.agreement.name,
            )
        }
        val candidates = top.mapIndexed { rank, c -> CandidateEntity(observationId, rank, c.speciesId ?: "class-${c.classIndex}", c.prob) }
        history.save(observation, photos, candidates)
        observationId
    }

    companion object {
        const val TOP_N = 10
        const val TOP_PHOTO = 5
    }
}

@Suppress("unused")
private val agreementValues = Agreement.entries
