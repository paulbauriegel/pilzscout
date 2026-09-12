package de.pilzscout.app.ui.identify

import android.Manifest
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.draw.scale
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.LocationOn
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
import androidx.compose.material3.InputChip
import androidx.compose.material3.LargeExtendedFloatingActionButton
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import de.pilzscout.app.R
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
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(4)) { uris ->
        if (uris.isNotEmpty()) viewModel.importFromGallery(uris)
    }

    // Ask for the approximate location once per draft, as soon as the first photo exists.
    LaunchedEffect(draft.photos.isNotEmpty(), draft.locationRequested) {
        if (draft.photos.isNotEmpty() && !draft.locationRequested && draft.includeLocation) {
            if (viewModel.hasLocationPermission()) viewModel.fetchLocation()
            else locationPermission.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
    }

    TabScaffold(title = stringResource(R.string.identify_title), onOpenSettings = onOpenSettings) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Spacer(Modifier.height(4.dp))
            LargeExtendedFloatingActionButton(
                onClick = { onOpenCamera(draft.nextSuggestedView() ?: ViewType.HABITAT, null) },
                icon = { Icon(Icons.Filled.PhotoCamera, contentDescription = null) },
                text = { Text(stringResource(R.string.identify_take_photo)) },
                modifier = Modifier.fillMaxWidth(),
            )
            FilledTonalButton(
                onClick = { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shapes = ButtonDefaults.shapes(),
            ) {
                Icon(Icons.Outlined.PhotoLibrary, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.identify_import))
            }

            Text(stringResource(R.string.identify_suggested_views), style = MaterialTheme.typography.titleMedium)
            PhotoSlotRow(draft.photos, onSlotClick = { view -> onOpenCamera(view, null) })
            Text(stringResource(R.string.identify_hint_flexible), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

            if (draft.photos.isNotEmpty()) {
                Text(stringResource(R.string.identify_review), style = MaterialTheme.typography.titleMedium)
                ReviewStrip(
                    photos = draft.photos,
                    onRetake = { p -> onOpenCamera(p.viewType, p.id) },
                    onRemove = viewModel::remove,
                    onMove = viewModel::move,
                    onSetViewType = viewModel::setViewType,
                    onAdd = { onOpenCamera(draft.nextSuggestedView() ?: ViewType.HABITAT, null) },
                )
                ContextRow(
                    date = DateFormat.getMediumDateFormat(context).format(Date(draft.capturedAt)),
                    location = draft.location?.let { "%.2f, %.2f".format(it.lat, it.lon) },
                    locationIncluded = draft.includeLocation,
                    locationRequested = draft.locationRequested,
                    onRemoveLocation = viewModel::removeLocation,
                    onAddLocation = {
                        if (viewModel.hasLocationPermission()) viewModel.includeLocation()
                        else locationPermission.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                    },
                )
            }

            when (val c = classifier) {
                is ClassifierState.Failed -> Text(stringResource(R.string.identify_model_failed, c.message), color = MaterialTheme.colorScheme.error)
                ClassifierState.NotInstalled -> Text(stringResource(R.string.identify_model_missing), color = MaterialTheme.colorScheme.error)
                else -> Unit
            }
            Button(
                onClick = onIdentify,
                enabled = draft.photos.isNotEmpty() && classifier !is ClassifierState.NotInstalled,
                modifier = Modifier.fillMaxWidth().height(60.dp),
                shapes = ButtonDefaults.shapes(),
            ) {
                Text(stringResource(R.string.identify_action), style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

/** Four differently shaped slots; a slot morphs into a filled photo once captured. */
@Composable
private fun PhotoSlotRow(photos: List<DraftPhoto>, onSlotClick: (ViewType) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        ViewType.entries.forEach { view ->
            val photo = photos.firstOrNull { it.viewType == view }
            // Expressive acknowledgement: a freshly captured slot pops in with a bouncy spring.
            val pop by animateFloatAsState(
                targetValue = if (photo != null) 1f else 0.92f,
                animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
                label = "slot-pop",
            )
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(
                    Modifier
                        .size(76.dp)
                        .scale(pop)
                        .animateContentSize()
                        .clip(view.slotShape())
                        .background(if (photo != null) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh)
                        .border(if (photo == null) 2.dp else 0.dp, MaterialTheme.colorScheme.outlineVariant, view.slotShape())
                        .clickable { onSlotClick(view) },
                    contentAlignment = Alignment.Center,
                ) {
                    if (photo != null) {
                        AsyncImage(model = photo.file, contentDescription = view.label(), contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    } else {
                        Text("${view.number}", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Text(view.shortLabel(), style = MaterialTheme.typography.labelMedium, maxLines = 2, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        }
    }
}

@Composable
private fun ReviewStrip(
    photos: List<DraftPhoto>,
    onRetake: (DraftPhoto) -> Unit,
    onRemove: (String) -> Unit,
    onMove: (String, Int) -> Unit,
    onSetViewType: (String, ViewType) -> Unit,
    onAdd: () -> Unit,
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(photos, key = { it.id }) { photo ->
            var menu by remember { mutableStateOf(false) }
            Column(Modifier.width(150.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                AsyncImage(
                    model = photo.file,
                    contentDescription = photo.viewType.label(),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().height(150.dp).clip(MaterialTheme.shapes.large),
                )
                Box {
                    AssistChip(onClick = { menu = true }, label = { Text(photo.viewType.shortLabel()) })
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        ViewType.entries.forEach { v ->
                            DropdownMenuItem(text = { Text(v.label()) }, onClick = { onSetViewType(photo.id, v); menu = false })
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    IconButton(onClick = { onMove(photo.id, -1) }) { Icon(Icons.Outlined.KeyboardArrowLeft, stringResource(R.string.identify_move_left)) }
                    IconButton(onClick = { onRetake(photo) }) { Icon(Icons.Outlined.Refresh, stringResource(R.string.identify_retake)) }
                    IconButton(onClick = { onRemove(photo.id) }) { Icon(Icons.Outlined.Delete, stringResource(R.string.identify_remove)) }
                    IconButton(onClick = { onMove(photo.id, 1) }) { Icon(Icons.Outlined.KeyboardArrowRight, stringResource(R.string.identify_move_right)) }
                }
            }
        }
        if (photos.size < 8) {
            item {
                Box(
                    Modifier
                        .size(150.dp)
                        .clip(MaterialTheme.shapes.large)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .clickable(onClick = onAdd),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Filled.Add, contentDescription = null)
                        Text(stringResource(R.string.identify_add_view), style = MaterialTheme.typography.labelMedium, textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.padding(8.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun ContextRow(
    date: String,
    location: String?,
    locationIncluded: Boolean,
    locationRequested: Boolean,
    onRemoveLocation: () -> Unit,
    onAddLocation: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        AssistChip(onClick = {}, label = { Text(date) }, leadingIcon = { Icon(Icons.Outlined.CalendarToday, contentDescription = null) })
        when {
            locationIncluded && location != null -> InputChip(
                selected = false,
                onClick = onRemoveLocation,
                label = { Text(location) },
                leadingIcon = { Icon(Icons.Outlined.LocationOn, contentDescription = null) },
                trailingIcon = { Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.identify_location_remove)) },
            )
            locationIncluded && !locationRequested -> AssistChip(onClick = {}, label = { Text(stringResource(R.string.identify_location_pending)) })
            else -> AssistChip(
                onClick = onAddLocation,
                label = { Text(stringResource(if (locationIncluded) R.string.identify_location_unavailable else R.string.identify_location_add)) },
                leadingIcon = { Icon(Icons.Outlined.LocationOn, contentDescription = null) },
            )
        }
    }
}

@Suppress("unused")
@Composable
private fun photosLabel(n: Int) = pluralStringResource(R.plurals.photos_count, n, n)
