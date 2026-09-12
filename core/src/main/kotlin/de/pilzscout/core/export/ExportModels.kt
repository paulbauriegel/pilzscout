package de.pilzscout.core.export

import de.pilzscout.core.compare.ComparisonResult
import de.pilzscout.core.identify.Agreement
import de.pilzscout.core.identify.ConfidenceDescriptor
import de.pilzscout.core.model.ViewType
import kotlinx.serialization.Serializable

/** observation.json inside an exported ZIP. Species are identified by slug + GBIF key, never by a display name alone. */
@Serializable
data class ExportObservation(
    val schemaVersion: Int = SCHEMA_VERSION,
    val observationId: String,
    val capturedAt: String,
    val exportedAt: String,
    val approxLocation: ExportLocation?,
    val photos: List<ExportPhoto>,
    val combined: List<ExportCandidate>,
    val descriptor: ConfidenceDescriptor,
    val contextualInputs: ExportContext,
    val comparison: ComparisonResult?,
    val model: ExportModel,
    val userCorrection: ExportCorrection?,
    val userConfirmed: Boolean,
    val mode: String,
    val appVersion: String,
) {
    companion object {
        const val SCHEMA_VERSION = 1
    }
}

@Serializable
data class ExportLocation(val lat: Double, val lon: Double, val precisionKm: Double = 1.0)

@Serializable
data class ExportSpeciesRef(val speciesId: String?, val scientificName: String?, val gbifKey: Long?, val edibility: de.pilzscout.core.model.Edibility? = null)

@Serializable
data class ExportCandidate(val rank: Int, val species: ExportSpeciesRef, val prob: Float)

@Serializable
data class ExportPhotoPrediction(val top: ExportCandidate, val others: List<ExportCandidate>)

@Serializable
data class ExportPhoto(
    val file: String,
    val viewType: ViewType,
    val position: Int,
    val width: Int,
    val height: Int,
    val prediction: ExportPhotoPrediction?,
    val agreement: Agreement?,
    val inferenceMs: Long,
)

@Serializable
data class ExportContext(
    val month: Int?,
    val locationUsed: Boolean,
    val germanyFilter: Boolean,
    val seasonPriorWeight: Float,
    val nPhotos: Int,
)

@Serializable
data class ExportModel(val version: String, val name: String?, val precision: String, val inputSize: Int?, val totalInferenceMs: Long)

@Serializable
data class ExportCorrection(val species: ExportSpeciesRef, val correctedAt: String)

/** index.json for multi-observation exports. */
@Serializable
data class ExportIndex(val schemaVersion: Int = ExportObservation.SCHEMA_VERSION, val exportedAt: String, val observations: List<String>)
