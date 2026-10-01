package de.pilzscout.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import de.pilzscout.app.R
import de.pilzscout.app.ui.theme.LocalDarkTheme
import kotlin.math.roundToInt

/** The three horizontally tileable forest layers for one theme, rendered by `packs backdrop` in tools/. */
@Immutable
class ForestLayers(val far: ImageBitmap, val mid: ImageBitmap, val near: ImageBitmap)

/**
 * Decodes the layers for the current theme. Call this high in the tree (the navigation host) so the
 * bitmaps survive detail screens being pushed on top; [imageResource] only remembers within its scope.
 */
@Composable
fun rememberForestLayers(dark: Boolean = LocalDarkTheme.current): ForestLayers {
    val far = ImageBitmap.imageResource(if (dark) R.drawable.forest_far_dark else R.drawable.forest_far_light)
    val mid = ImageBitmap.imageResource(if (dark) R.drawable.forest_mid_dark else R.drawable.forest_mid_light)
    val near = ImageBitmap.imageResource(if (dark) R.drawable.forest_near_dark else R.drawable.forest_near_light)
    return remember(far, mid, near) { ForestLayers(far, mid, near) }
}

/**
 * Layered forest with parallax. [progress] is the number of pages scrolled (0f on the first tab) and is
 * read only inside the draw pass, so a scrolling pager redraws the backdrop each frame without recomposing.
 * Far layers move less than near ones; every layer wraps seamlessly, so any width and progress works.
 */
@Composable
fun ParallaxForestBackdrop(
    progress: () -> Float,
    modifier: Modifier = Modifier,
    heightDp: Int = 200,
    layers: ForestLayers = rememberForestLayers(),
    factors: FloatArray = DefaultParallaxFactors,
) {
    Canvas(modifier.fillMaxWidth().height(heightDp.dp)) {
        val shift = progress() * size.width
        drawTiled(layers.far, shift * factors[0])
        drawTiled(layers.mid, shift * factors[1])
        drawTiled(layers.near, shift * factors[2])
    }
}

/** Fraction of the page width each layer moves per page: far, mid, near (content moves 1.0). */
val DefaultParallaxFactors = floatArrayOf(0.15f, 0.3f, 0.5f)

private fun DrawScope.drawTiled(layer: ImageBitmap, shift: Float) {
    val tileH = size.height.roundToInt()
    // Integer tile width for both size and step, otherwise rounding opens one-pixel seams between tiles.
    val tileW = (layer.width * size.height / layer.height).roundToInt().coerceAtLeast(1)
    var x = -(shift.mod(tileW.toFloat()))
    while (x < size.width) {
        drawImage(layer, dstOffset = IntOffset(x.roundToInt(), 0), dstSize = IntSize(tileW, tileH), filterQuality = FilterQuality.Medium)
        x += tileW
    }
}
