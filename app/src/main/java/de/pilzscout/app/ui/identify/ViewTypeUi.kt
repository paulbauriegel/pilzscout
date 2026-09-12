package de.pilzscout.app.ui.identify

import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.stringResource
import de.pilzscout.app.R
import de.pilzscout.core.model.ViewType

@Composable
fun ViewType.label(): String = stringResource(
    when (this) {
        ViewType.CAP -> R.string.view_cap
        ViewType.UNDERSIDE -> R.string.view_underside
        ViewType.STEM_BASE -> R.string.view_stem_base
        ViewType.HABITAT -> R.string.view_habitat
    },
)

@Composable
fun ViewType.shortLabel(): String = stringResource(
    when (this) {
        ViewType.CAP -> R.string.view_cap_short
        ViewType.UNDERSIDE -> R.string.view_underside_short
        ViewType.STEM_BASE -> R.string.view_stem_base_short
        ViewType.HABITAT -> R.string.view_habitat_short
    },
)

/** Each suggested view gets its own expressive shape so the four slots are recognisable at a glance. */
@Composable
fun ViewType.slotShape(): Shape = when (this) {
    ViewType.CAP -> MaterialShapes.Cookie9Sided.toShape()
    ViewType.UNDERSIDE -> MaterialShapes.Cookie12Sided.toShape()
    ViewType.STEM_BASE -> MaterialShapes.Pill.toShape()
    ViewType.HABITAT -> MaterialShapes.Clover4Leaf.toShape()
}

val ViewType.number: Int get() = ordinal + 1
