package de.pilzscout.app.ui.identify

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import de.pilzscout.app.R
import de.pilzscout.app.identify.CaptureGuidance
import de.pilzscout.app.identify.DraftPhoto
import de.pilzscout.core.model.ViewType
import kotlin.math.cos
import kotlin.math.sin

private val CardShape = RoundedCornerShape(24.dp)

/**
 * The big card of the Identify tab. Before the first photo it shows a template illustration with the
 * guidance; afterwards it is a slider of the captured photos, each with its view badge. Tapping a photo
 * opens its detail sheet.
 */
@Composable
fun PhotoCard(
    photos: List<DraftPhoto>,
    guidance: CaptureGuidance,
    pagerState: PagerState,
    onOpenCamera: () -> Unit,
    onOpenPhoto: (DraftPhoto) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (photos.isEmpty()) {
        TemplateCard(guidance, onOpenCamera, modifier)
    } else {
        PhotoSlider(photos, pagerState, onOpenPhoto, modifier)
    }
}

@Composable
private fun TemplateCard(guidance: CaptureGuidance, onOpenCamera: () -> Unit, modifier: Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .clickable(role = Role.Button, onClickLabel = stringResource(R.string.identify_viewfinder_open), onClick = onOpenCamera),
    ) {
        MushroomTemplate(Modifier.align(Alignment.Center).padding(bottom = 40.dp).fillMaxHeight(0.6f).aspectRatio(1f))
        Column(Modifier.align(Alignment.BottomStart).padding(horizontal = 22.dp, vertical = 18.dp)) {
            Text(stringResource(guidance.titleRes), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(stringResource(guidance.bodyRes), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun PhotoSlider(photos: List<DraftPhoto>, pagerState: PagerState, onOpenPhoto: (DraftPhoto) -> Unit, modifier: Modifier) {
    Box(modifier.fillMaxWidth().clip(CardShape).background(MaterialTheme.colorScheme.surfaceContainerHighest)) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            key = { photos.getOrNull(it)?.id ?: it },
            pageSpacing = 8.dp,
        ) { page ->
            val photo = photos.getOrNull(page) ?: return@HorizontalPager
            Box(Modifier.fillMaxSize().clip(CardShape).clickable(role = Role.Button, onClickLabel = stringResource(R.string.identify_preview_open)) { onOpenPhoto(photo) }) {
                AsyncImage(model = photo.file, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.6f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.5f))))
                ViewBadge(photo.viewType, Modifier.align(Alignment.BottomStart).padding(14.dp))
            }
        }
        if (photos.size > 1) {
            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                color = Color.Black.copy(alpha = 0.45f),
                modifier = Modifier.align(Alignment.TopEnd).padding(14.dp),
            ) {
                Text(
                    stringResource(R.string.identify_photo_position, pagerState.currentPage + 1, photos.size),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
        }
    }
}

/** View label on a photo; untyped photos invite the user to choose one. */
@Composable
private fun ViewBadge(view: ViewType, modifier: Modifier) {
    val typed = view != ViewType.OTHER
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = if (typed) MaterialTheme.colorScheme.primaryContainer else Color.Black.copy(alpha = 0.45f),
        contentColor = if (typed) MaterialTheme.colorScheme.onPrimaryContainer else Color.White,
        modifier = modifier,
    ) {
        Row(Modifier.padding(start = 12.dp, end = 10.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(view.badgeLabel(), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            Icon(Icons.Outlined.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
        }
    }
}

/** Soft, theme-coloured mushroom used as the placeholder before the first photo. */
@Composable
private fun MushroomTemplate(modifier: Modifier) {
    val cap = MaterialTheme.colorScheme.primaryContainer
    val outline = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
    val underside = MaterialTheme.colorScheme.secondaryContainer
    val stem = MaterialTheme.colorScheme.surfaceContainerHighest
    val gill = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
    val ground = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
    val grass = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
    val shine = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f)
    Canvas(modifier) {
        val s = size.minDimension / 100f
        val line = 1.6f * s
        fun o(x: Float, y: Float) = Offset(x * s, y * s)

        drawOval(ground, topLeft = o(16f, 84f), size = Size(68f * s, 8f * s))

        val stemPath = Path().apply {
            moveTo(43f * s, 58f * s)
            cubicTo(42f * s, 70f * s, 40f * s, 80f * s, 37f * s, 88f * s)
            quadraticTo(50f * s, 91f * s, 63f * s, 88f * s)
            cubicTo(60f * s, 80f * s, 58f * s, 70f * s, 57f * s, 58f * s)
            close()
        }
        drawPath(stemPath, stem)
        drawPath(stemPath, outline, style = Stroke(line, join = StrokeJoin.Round))

        drawOval(underside, topLeft = o(14f, 52f), size = Size(72f * s, 12f * s))
        for (deg in 20..160 step 14) {
            val a = Math.toRadians(deg.toDouble())
            drawLine(
                gill,
                Offset((50f + 12f * cos(a).toFloat()) * s, (58f + 2f * sin(a).toFloat()) * s),
                Offset((50f + 33f * cos(a).toFloat()) * s, (58f + 5f * sin(a).toFloat()) * s),
                strokeWidth = line * 0.8f,
                cap = StrokeCap.Round,
            )
        }

        val capPath = Path().apply {
            moveTo(12f * s, 57f * s)
            cubicTo(12f * s, 31f * s, 31f * s, 14f * s, 50f * s, 14f * s)
            cubicTo(69f * s, 14f * s, 88f * s, 31f * s, 88f * s, 57f * s)
            cubicTo(70f * s, 59f * s, 30f * s, 59f * s, 12f * s, 57f * s)
            close()
        }
        drawPath(capPath, cap)
        drawPath(capPath, outline, style = Stroke(line, join = StrokeJoin.Round))
        val shinePath = Path().apply {
            moveTo(24f * s, 42f * s)
            quadraticTo(28f * s, 27f * s, 42f * s, 21f * s)
        }
        drawPath(shinePath, shine, style = Stroke(line * 2.2f, cap = StrokeCap.Round))

        listOf(
            floatArrayOf(28f, 88f, 25f, 79f, 21f, 74f),
            floatArrayOf(32f, 88f, 32f, 80f, 30f, 75f),
            floatArrayOf(68f, 88f, 71f, 80f, 76f, 75f),
            floatArrayOf(72f, 88f, 76f, 82f, 81f, 79f),
        ).forEach { b ->
            val blade = Path().apply {
                moveTo(b[0] * s, b[1] * s)
                quadraticTo(b[2] * s, b[3] * s, b[4] * s, b[5] * s)
            }
            drawPath(blade, grass, style = Stroke(line * 1.2f, cap = StrokeCap.Round))
        }
    }
}
