package de.pilzscout.app.ml

import de.pilzscout.core.model.ViewType
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** A guess which view (cap, underside, stem base, habitat) a photo shows, with the model's confidence in 0..1. */
data class ViewTypeGuess(val viewType: ViewType, val confidence: Float)

/**
 * Recognises the view type of a captured photo. Every photo entering the draft passes through
 * [PhotoIntake][de.pilzscout.app.identify.PhotoIntake], which asks this detector and tags the photo
 * with the guess. A LiteRT implementation trained on FungiTastic masks can replace the no-op
 * binding in `MlModule` without touching the UI (see docs/view-type-model.md).
 */
interface ViewTypeDetector {
    /** Returns null when the view cannot be determined (or no model is installed). */
    suspend fun detect(file: File): ViewTypeGuess?
}

/** Placeholder until a view-type model ships: never tags a photo, so everything stays [ViewType.OTHER]. */
@Singleton
class NoOpViewTypeDetector @Inject constructor() : ViewTypeDetector {
    override suspend fun detect(file: File): ViewTypeGuess? = null
}
