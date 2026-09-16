package de.pilzscout.app.ui.identify

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import de.pilzscout.app.identify.DraftPhoto
import de.pilzscout.core.model.ViewType

private val TILE = 68.dp
private val TILE_SHAPE = RoundedCornerShape(14.dp)

/** Thumbnails of the sequence with their view badges; the current slider page is outlined. */
@Composable
fun PhotoStrip(photos: List<DraftPhoto>, currentIndex: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    LazyRow(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        itemsIndexed(photos, key = { _, p -> p.id }) { index, photo ->
            val current = index == currentIndex
            Box(
                Modifier
                    .size(TILE)
                    .clip(TILE_SHAPE)
                    .then(if (current) Modifier.border(2.5.dp, MaterialTheme.colorScheme.primary, TILE_SHAPE) else Modifier)
                    .clickable(role = Role.Button) { onSelect(index) },
            ) {
                AsyncImage(model = photo.file, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().padding(if (current) 2.5.dp else 0.dp).clip(TILE_SHAPE))
                if (photo.viewType != ViewType.OTHER) {
                    Box(Modifier.fillMaxSize().clip(TILE_SHAPE).background(Brush.verticalGradient(0.45f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.65f))))
                    Text(
                        photo.viewType.badgeLabel(),
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 4.dp, vertical = 5.dp),
                    )
                }
            }
        }
    }
}
