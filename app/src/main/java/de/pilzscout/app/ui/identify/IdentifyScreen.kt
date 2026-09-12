package de.pilzscout.app.ui.identify

import android.Manifest
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Eco
import androidx.compose.material.icons.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import de.pilzscout.app.R
import de.pilzscout.app.identify.Draft
import de.pilzscout.app.identify.DraftPhoto
import de.pilzscout.app.ml.ClassifierState
import de.pilzscout.app.ui.components.TabScaffold
import de.pilzscout.app.ui.components.contentLanguage
import de.pilzscout.core.model.ViewType
import java.util.Date

@Composable
fun IdentifyScreen(
    onOpenSettings: () -> Unit,
    onOpenCamera: (ViewType, replacePhotoId: String?) -> Unit,
    onIdentify: () -> Unit,
    viewModel: IdentifyViewModel = hiltViewModel(),
) {
    val draft by viewModel.draft.collectAsStateWithLifecycle()
    val classifier by viewModel.classifierState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lang = contentLanguage()
    LaunchedEffect(lang) { viewModel.setLanguage(lang) }

    val locationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) viewModel.fetchLocation() else viewModel.removeLocation()
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(8)) { uris ->
        if (uris.isNotEmpty()) viewModel.importFromGallery(uris)
    }
    LaunchedEffect(draft.photos.isNotEmpty(), draft.locationRequested) {
        if (draft.photos.isNotEmpty() && !draft.locationRequested && draft.includeLocation) {
            if (viewModel.hasLocationPermission()) viewModel.fetchLocation()
            else locationPermission.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
    }

    TabScaffold(
        title = stringResource(R.string.identify_header),
        subtitle = stringResource(R.string.identify_header_sub),
        headerIcon = Icons.Outlined.Eco,
        onOpenSettings = onOpenSettings,
    ) { padding ->
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(padding),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Spacer(Modifier.height(4.dp))
            PhotoGrid(draft, onSlot = { view -> onOpenCamera(view, null) }, onRetake = { p -> onOpenCamera(p.viewType, p.id) })
            if (draft.unassigned.isNotEmpty()) {
                Text(stringResource(R.string.identify_other_photos), style = MaterialTheme.typography.titleSmall)
                OtherPhotosRow(draft.unassigned, viewModel, onRetake = { p -> onOpenCamera(p.viewType, p.id) })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FilledTonalButton(onClick = { onOpenCamera(ViewType.OTHER, null) }, modifier = Modifier.weight(1f).height(48.dp), shapes = ButtonDefaults.shapes()) {
                    Icon(Icons.Filled.PhotoCamera, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.identify_take_photo), maxLines = 1)
                }
                FilledTonalButton(
                    onClick = { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    modifier = Modifier.weight(1f).height(48.dp),
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Icon(Icons.Outlined.PhotoLibrary, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.identify_import), maxLines = 1)
                }
            }
            ContextCard(
                date = DateFormat.getMediumDateFormat(context).format(Date(draft.capturedAt)),
                location = draft.placeName?.let { stringResource(R.string.location_near, it) } ?: draft.location?.let { "%.2f, %.2f".format(it.lat, it.lon) },
                locationIncluded = draft.includeLocation,
                onToggleLocation = {
                    if (draft.includeLocation) viewModel.removeLocation()
                    else if (viewModel.hasLocationPermission()) viewModel.includeLocation()
                    else locationPermission.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                },
            )
            when (val c = classifier) {
                is ClassifierState.Failed -> Text(stringResource(R.string.identify_model_failed, c.message), color = MaterialTheme.colorScheme.error)
                ClassifierState.NotInstalled -> Text(stringResource(R.string.identify_model_missing), color = MaterialTheme.colorScheme.error)
                else -> Unit
            }
            Button(
                onClick = onIdentify,
                enabled = draft.photos.isNotEmpty() && classifier !is ClassifierState.NotInstalled,
                modifier = Modifier.fillMaxWidth().height(58.dp),
                shapes = ButtonDefaults.shapes(),
            ) {
                Icon(Icons.Filled.AutoAwesome, contentDescription = null)
                Spacer(Modifier.width(10.dp))
                Text(stringResource(R.string.identify_action), style = MaterialTheme.typography.titleMedium)
            }
            Text(stringResource(R.string.identify_hint_flexible), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
        }
    }
}

/** 2x2 grid of the four suggested views. Filled tiles show the photo with a label and check; empty ones invite a photo. */
@Composable
private fun PhotoGrid(draft: Draft, onSlot: (ViewType) -> Unit, onRetake: (DraftPhoto) -> Unit) {
    val views = ViewType.suggestedViews
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        views.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { view ->
                    val photo = draft.photos.firstOrNull { it.viewType == view }
                    PhotoTile(view, photo, Modifier.weight(1f), onClick = { if (photo != null) onRetake(photo) else onSlot(view) })
                }
            }
        }
    }
}

@Composable
private fun PhotoTile(view: ViewType, photo: DraftPhoto?, modifier: Modifier, onClick: () -> Unit) {
    val pop by animateFloatAsState(if (photo != null) 1f else 0.97f, MaterialTheme.motionScheme.defaultSpatialSpec(), label = "tile")
    val shape = RoundedCornerShape(20.dp)
    Box(
        modifier
            .aspectRatio(1f)
            .scale(pop)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .then(if (photo == null) Modifier.border(1.5.dp, MaterialTheme.colorScheme.outlineVariant, shape) else Modifier)
            .clickable(onClick = onClick),
    ) {
        if (photo != null) {
            AsyncImage(model = photo.file, contentDescription = view.label(), contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.55f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.55f))))
            Text(view.shortLabel(), color = Color.White, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.align(Alignment.BottomStart).padding(10.dp))
            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.align(Alignment.BottomEnd).padding(10.dp).size(22.dp))
        } else {
            Column(Modifier.fillMaxSize().padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Box(Modifier.size(40.dp).clip(MaterialTheme.shapes.extraLarge).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.PhotoCamera, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                }
                Spacer(Modifier.height(8.dp))
                Text(view.shortLabel(), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
                Text(stringResource(R.string.identify_slot_add), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            }
        }
    }
}

/** Photos that are not assigned to a suggested view. They count fully; a chip lets the user assign one. */
@Composable
private fun OtherPhotosRow(photos: List<DraftPhoto>, viewModel: IdentifyViewModel, onRetake: (DraftPhoto) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(photos, key = { it.id }) { photo ->
            var menu by remember { mutableStateOf(false) }
            Column(Modifier.width(120.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                AsyncImage(model = photo.file, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(120.dp).clip(RoundedCornerShape(16.dp)))
                Box {
                    AssistChip(onClick = { menu = true }, label = { Text(stringResource(R.string.identify_change_view), maxLines = 1) }, leadingIcon = { Icon(Icons.Outlined.Edit, null, Modifier.size(16.dp)) })
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        ViewType.suggestedViews.forEach { v -> DropdownMenuItem(text = { Text(v.label()) }, onClick = { viewModel.setViewType(photo.id, v); menu = false }) }
                    }
                }
                Row {
                    IconButton(onClick = { viewModel.move(photo.id, -1) }, Modifier.size(32.dp)) { Icon(Icons.Outlined.KeyboardArrowLeft, stringResource(R.string.identify_move_left)) }
                    IconButton(onClick = { onRetake(photo) }, Modifier.size(32.dp)) { Icon(Icons.Outlined.Refresh, stringResource(R.string.identify_retake)) }
                    IconButton(onClick = { viewModel.remove(photo.id) }, Modifier.size(32.dp)) { Icon(Icons.Outlined.Delete, stringResource(R.string.identify_remove)) }
                    IconButton(onClick = { viewModel.move(photo.id, 1) }, Modifier.size(32.dp)) { Icon(Icons.Outlined.KeyboardArrowRight, stringResource(R.string.identify_move_right)) }
                }
            }
        }
    }
}

@Composable
private fun ContextCard(date: String, location: String?, locationIncluded: Boolean, onToggleLocation: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceContainerLow).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(Icons.Outlined.LocationOn, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        val locText = when {
            !locationIncluded -> stringResource(R.string.identify_context_no_location)
            location != null -> stringResource(R.string.identify_context_location_approx) + " · " + location
            else -> stringResource(R.string.identify_context_location_approx)
        }
        Text("$date · $locText", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        IconButton(onClick = onToggleLocation) {
            Icon(Icons.Outlined.Edit, contentDescription = stringResource(if (locationIncluded) R.string.identify_location_remove else R.string.identify_location_add))
        }
    }
}
