package de.pilzscout.app.ui.identify

import androidx.annotation.StringRes
import de.pilzscout.app.R
import de.pilzscout.app.identify.CaptureGuidance

/** Headline shown in the viewfinder card and over the camera. */
val CaptureGuidance.titleRes: Int
    @StringRes get() = when (this) {
        CaptureGuidance.START -> R.string.identify_guidance_start_title
        CaptureGuidance.SECOND -> R.string.identify_guidance_second_title
        CaptureGuidance.THIRD -> R.string.identify_guidance_third_title
        CaptureGuidance.COVERED -> R.string.identify_guidance_covered_title
    }

/** One-line explanation under the headline. */
val CaptureGuidance.bodyRes: Int
    @StringRes get() = when (this) {
        CaptureGuidance.START -> R.string.identify_guidance_start_body
        CaptureGuidance.SECOND -> R.string.identify_guidance_second_body
        CaptureGuidance.THIRD -> R.string.identify_guidance_third_body
        CaptureGuidance.COVERED -> R.string.identify_guidance_covered_body
    }

/** Supporting text under the "identify now" button. */
val CaptureGuidance.hintRes: Int
    @StringRes get() = when (this) {
        CaptureGuidance.START -> R.string.identify_hint_flexible
        CaptureGuidance.SECOND -> R.string.identify_hint_one
        CaptureGuidance.THIRD -> R.string.identify_hint_two
        CaptureGuidance.COVERED -> R.string.identify_hint_covered
    }
