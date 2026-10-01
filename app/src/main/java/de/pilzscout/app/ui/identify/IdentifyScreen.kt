package de.pilzscout.app.ui.identify

import android.Manifest
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Eco
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.pilzscout.app.R
import de.pilzscout.app.identify.guidanceFor
import de.pilzscout.app.ml.ClassifierState
import de.pilzscout.app.ui.components.TabScaffold
import de.pilzscout.app.ui.components.contentLanguage
import kotlinx.coroutines.launch
import java.util.Date


private val LOCATION_PERMISSIONS = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
private const val IMAGE_MIME = "image/*"
private const val MAX_IMPORT = 8

/**
 * "Neuer Fund" on one screen. Before the first photo the card shows a template and guidance; afterwards it
 * is a slider of the photos with their optional view badges, mirrored by a thumbnail strip. Tapping a photo
 * opens its detail sheet, where the view can be set and the photo retaken, replaced or deleted.
 * [onOpenCamera] gets the id of a photo to retake, or null for a new one.
 */
@Composable
fun IdentifyScreen(
    onOpenSettings: () -> Unit,
    onOpenCamera: (replacePhotoId: String?) -> Unit,
    onIdentify: () -> Unit,
    viewModel: IdentifyViewModel = hiltViewModel(),
) {
    val draft by viewModel.draft.collectAsStateWithLifecycle()
    val classifier by viewModel.classifierState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lang = contentLanguage()
    LaunchedEffect(lang) { viewModel.setLanguage(lang) }

    // Precise location is requested so finds land on the right spot of the history map; Android lets the
    // user downgrade to approximate in the same dialog, which still yields a usable (coarser) fix.
    val locationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        if (granted.values.any { it }) viewModel.includeLocation() else viewModel.removeLocation()
    }
    // Photos are picked through ACTION_GET_CONTENT rather than the ACTION_PICK_IMAGES contract on purpose:
    // the system photo picker serves both, but only the GET_CONTENT flavour of its Uris honours
    // MediaStore.setRequireOriginal, which is the sole way to read a photo's GPS tags since Android 10.
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isNotEmpty()) viewModel.importFromGallery(uris.take(MAX_IMPORT))
    }
    var replaceTarget by rememberSaveable { mutableStateOf<String?>(null) }
    val replacePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val target = replaceTarget
        replaceTarget = null
        if (uri != null && target != null) viewModel.replaceFromGallery(target, uri)
    }
    // Android strips GPS tags from picked photos unless the app holds ACCESS_MEDIA_LOCATION, so it is asked
    // for right before the picker opens. The picker opens either way; a refusal only costs the position.
    val mediaLocationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        if (replaceTarget != null) replacePicker.launch(IMAGE_MIME) else gallery.launch(IMAGE_MIME)
    }
    val openPicker: () -> Unit = {
        if (viewModel.needsMediaLocationPermission()) mediaLocationPermission.launch(Manifest.permission.ACCESS_MEDIA_LOCATION)
        else if (replaceTarget != null) replacePicker.launch(IMAGE_MIME)
        else gallery.launch(IMAGE_MIME)
    }
    LaunchedEffect(draft.photos.isNotEmpty(), draft.locationRequested) {
        if (draft.photos.isNotEmpty() && !draft.locationRequested && draft.includeLocation) {
            if (viewModel.hasPreciseLocationPermission()) viewModel.fetchLocation()
            else locationPermission.launch(LOCATION_PERMISSIONS)
        }
    }

    val photos = draft.photos
    val pagerState = rememberPagerState(pageCount = { draft.photos.size })
    val scope = rememberCoroutineScope()
    // Jump to a photo as soon as it is added, so the slider always shows the latest shot.
    var knownCount by rememberSaveable { mutableIntStateOf(photos.size) }
    LaunchedEffect(photos.size) {
        if (photos.size > knownCount) pagerState.scrollToPage(photos.lastIndex)
        knownCount = photos.size
    }

    var selectedPhotoId by rememberSaveable { mutableStateOf<String?>(null) }
    val selected = photos.firstOrNull { it.id == selectedPhotoId }
    val guidance = guidanceFor(photos.size)

    TabScaffold(
        title = stringResource(R.string.identify_header),
        subtitle = stringResource(R.string.identify_header_sub),
        headerIcon = Icons.Outlined.Eco,
        onOpenSettings = onOpenSettings,
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(horizontal = 16.dp).padding(padding).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PhotoCard(
                photos = photos,
                guidance = guidance,
                pagerState = pagerState,
                onOpenCamera = { onOpenCamera(null) },
                onOpenPhoto = { selectedPhotoId = it.id },
                modifier = Modifier.weight(1f).heightIn(min = 160.dp),
            )
            if (photos.size > 1) {
                PhotoStrip(
                    photos = photos,
                    currentIndex = pagerState.currentPage,
                    onSelect = { index -> scope.launch { pagerState.animateScrollToPage(index) } },
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = { onOpenCamera(null) }, modifier = Modifier.weight(1f).height(56.dp), shapes = ButtonDefaults.shapes()) {
                    Icon(if (photos.isEmpty()) Icons.Filled.PhotoCamera else Icons.Filled.AddAPhoto, contentDescription = null)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        stringResource(if (photos.isEmpty()) R.string.identify_take_photo else R.string.identify_take_more),
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                FilledTonalIconButton(onClick = openPicker, modifier = Modifier.size(56.dp)) {
                    Icon(Icons.Outlined.PhotoLibrary, contentDescription = stringResource(R.string.identify_import))
                }
            }
            ContextCard(
                date = DateFormat.getMediumDateFormat(context).format(Date(draft.capturedAt)),
                location = draft.placeName?.let { stringResource(R.string.location_near, it) } ?: draft.location?.let { "%.5f, %.5f".format(it.lat, it.lon) },
                locationIncluded = draft.includeLocation,
                onToggleLocation = {
                    if (draft.includeLocation) viewModel.removeLocation()
                    else if (viewModel.hasPreciseLocationPermission()) viewModel.includeLocation()
                    else locationPermission.launch(LOCATION_PERMISSIONS)
                },
            )
            when (val c = classifier) {
                is ClassifierState.Failed -> Text(stringResource(R.string.identify_model_failed, c.message), color = MaterialTheme.colorScheme.error)
                ClassifierState.NotInstalled -> Text(stringResource(R.string.identify_model_missing), color = MaterialTheme.colorScheme.error)
                else -> Unit
            }
            Button(
                onClick = onIdentify,
                enabled = photos.isNotEmpty() && classifier !is ClassifierState.NotInstalled,
                modifier = Modifier.fillMaxWidth().height(58.dp),
                shapes = ButtonDefaults.shapes(),
            ) {
                Icon(Icons.Filled.AutoAwesome, contentDescription = null)
                Spacer(Modifier.width(10.dp))
                Text(stringResource(R.string.identify_action), style = MaterialTheme.typography.titleMedium)
            }
            Text(
                stringResource(guidance.hintRes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    if (selected != null) {
        PhotoPreviewSheet(
            photo = selected,
            onViewChange = { viewModel.setViewType(selected.id, it) },
            onRetake = { selectedPhotoId = null; onOpenCamera(selected.id) },
            onReplace = { selectedPhotoId = null; replaceTarget = selected.id; openPicker() },
            onDelete = { selectedPhotoId = null; viewModel.remove(selected.id) },
            onDismiss = { selectedPhotoId = null },
        )
    }
}

@Composable
private fun ContextCard(date: String, location: String?, locationIncluded: Boolean, onToggleLocation: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceContainerLow).padding(horizontal = 14.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(Icons.Outlined.LocationOn, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        val locText = when {
            !locationIncluded -> stringResource(R.string.identify_context_no_location)
            location != null -> stringResource(R.string.identify_context_location_approx) + " · " + location
            else -> stringResource(R.string.identify_context_location_approx)
        }
        Text("$date · $locText", style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        IconButton(onClick = onToggleLocation) {
            Icon(Icons.Outlined.Edit, contentDescription = stringResource(if (locationIncluded) R.string.identify_location_remove else R.string.identify_location_add))
        }
    }
}
