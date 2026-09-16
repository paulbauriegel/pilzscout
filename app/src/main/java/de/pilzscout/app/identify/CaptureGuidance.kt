package de.pilzscout.app.identify

/**
 * What the Identify tab asks for next, derived purely from how many photos the draft holds. Photos
 * are an untyped sequence for now; once a view-type model exists the guidance can key off the
 * views actually covered instead of the count.
 */
enum class CaptureGuidance { START, SECOND, THIRD, COVERED }

fun guidanceFor(photoCount: Int): CaptureGuidance = when {
    photoCount <= 0 -> CaptureGuidance.START
    photoCount == 1 -> CaptureGuidance.SECOND
    photoCount == 2 -> CaptureGuidance.THIRD
    else -> CaptureGuidance.COVERED
}
