package de.pilzscout.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.dp
import de.pilzscout.app.ui.theme.ForestColors
import kotlin.math.sin

/**
 * Layered hills with fir trees, drawn procedurally so it scales to any width and both themes.
 * Placed at the bottom of top-level screens behind the content.
 */
@Composable
fun ForestBackdrop(modifier: Modifier = Modifier, heightDp: Int = 170) {
    val dark = isSystemInDarkTheme()
    val far = if (dark) ForestColors.hillsFarDark else ForestColors.hillsFarLight
    val mid = if (dark) ForestColors.hillsMidDark else ForestColors.hillsMidLight
    val near = if (dark) ForestColors.hillsNearDark else ForestColors.hillsNearLight
    Canvas(modifier.fillMaxWidth().height(heightDp.dp)) {
        val w = size.width
        val h = size.height
        fun hill(base: Float, amp: Float, freq: Float, phase: Float, color: Color) {
            val path = Path()
            path.moveTo(0f, h)
            path.lineTo(0f, base)
            var x = 0f
            while (x <= w) {
                val y = base - amp * (0.6f * sin((x / w) * freq * 6.283f + phase) + 0.4f * sin((x / w) * freq * 2.1f * 6.283f + phase * 1.7f))
                path.lineTo(x, y)
                x += 6f
            }
            path.lineTo(w, h)
            path.close()
            drawPath(path, color)
        }
        fun trees(base: Float, spacing: Float, height: Float, color: Color, seed: Int) {
            var x = (seed * 17 % 40).toFloat()
            var i = 0
            while (x < w) {
                val hh = height * (0.6f + 0.4f * ((i * 7 + seed) % 5) / 4f)
                val ww = hh * 0.55f
                val y0 = base + 4f * sin(x / w * 12f + seed)
                val path = Path()
                path.moveTo(x, y0 - hh)
                path.lineTo(x - ww / 2, y0 - hh * 0.55f)
                path.lineTo(x - ww * 0.18f, y0 - hh * 0.55f)
                path.lineTo(x - ww * 0.62f, y0 - hh * 0.2f)
                path.lineTo(x - ww * 0.22f, y0 - hh * 0.2f)
                path.lineTo(x - ww * 0.7f, y0)
                path.lineTo(x + ww * 0.7f, y0)
                path.lineTo(x + ww * 0.22f, y0 - hh * 0.2f)
                path.lineTo(x + ww * 0.62f, y0 - hh * 0.2f)
                path.lineTo(x + ww * 0.18f, y0 - hh * 0.55f)
                path.lineTo(x + ww / 2, y0 - hh * 0.55f)
                path.close()
                drawPath(path, color)
                x += spacing * (0.8f + 0.4f * ((i * 3 + seed) % 3) / 2f)
                i++
            }
        }
        hill(h * 0.45f, h * 0.16f, 1.1f, 0.4f, far)
        trees(h * 0.52f, 22f, h * 0.22f, far, 3)
        hill(h * 0.62f, h * 0.12f, 1.6f, 2.2f, mid)
        trees(h * 0.68f, 30f, h * 0.30f, mid, 7)
        hill(h * 0.82f, h * 0.08f, 2.3f, 4.1f, near)
        trees(h * 0.92f, 38f, h * 0.36f, near, 11)
        drawRect(near, topLeft = Offset(0f, h * 0.9f), size = androidx.compose.ui.geometry.Size(w, h * 0.1f))
    }
}
