package de.pilzscout.app.ui.history

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.pilzscout.app.R
import de.pilzscout.core.model.Edibility
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.maplibre.compose.camera.CameraMoveReason
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.compose.expressions.dsl.asNumber
import org.maplibre.compose.expressions.dsl.asString
import org.maplibre.compose.expressions.dsl.case
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.expressions.dsl.feature
import org.maplibre.compose.expressions.dsl.format
import org.maplibre.compose.expressions.dsl.not
import org.maplibre.compose.expressions.dsl.span
import org.maplibre.compose.expressions.dsl.step
import org.maplibre.compose.expressions.dsl.switch
import org.maplibre.compose.interaction.ClickResult
import org.maplibre.compose.interaction.MapInteractions
import org.maplibre.compose.layers.CircleLayer
import org.maplibre.compose.layers.SymbolLayer
import org.maplibre.compose.map.AndroidRenderMode
import org.maplibre.compose.map.MapState
import org.maplibre.compose.map.MapUiOptions
import org.maplibre.compose.map.renderMode
import org.maplibre.compose.map.MaplibreMap
import org.maplibre.compose.map.rememberMapState
import org.maplibre.compose.overlay.DisappearingCompassButton
import org.maplibre.compose.overlay.DisappearingScaleBar
import org.maplibre.compose.overlay.AttributionDefaults
import org.maplibre.compose.overlay.MapOverlayScope
import org.maplibre.compose.overlay.attributions
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.compose.sources.GeoJsonOptions
import org.maplibre.compose.sources.rememberGeoJsonSource
import org.maplibre.compose.style.BaseStyle
import org.maplibre.spatialk.geojson.BoundingBox
import org.maplibre.spatialk.geojson.Feature
import org.maplibre.spatialk.geojson.FeatureCollection
import org.maplibre.spatialk.geojson.Point
import org.maplibre.spatialk.geojson.Position

/** Key-free OpenFreeMap vector style (OpenStreetMap data). Tiles are the only thing the app ever fetches from the network. */
private const val STYLE_URI = "https://tiles.openfreemap.org/styles/liberty"

/** Rough centre of Germany, used until observations are fitted. */
private val GERMANY = Position(longitude = 10.45, latitude = 51.16)

/**
 * Map of the observations that carry a location. Clusters overlapping points, colours
 * single markers by the reference danger class of the shown species and shows [card] for a tapped marker.
 */
@Composable
fun HistoryMap(
    items: List<HistoryItem>,
    total: Int,
    bottomPadding: Dp,
    card: @Composable (HistoryItem) -> Unit,
) {
    val located = remember(items) { items.filter { it.located } }
    if (located.isEmpty()) {
        Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                stringResource(if (total == 0) R.string.history_empty else R.string.history_map_no_location),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(8.dp),
            )
        }
        return
    }

    val scheme = MaterialTheme.colorScheme
    val dangerColor = scheme.error
    val cautionColor = scheme.tertiary
    val okColor = scheme.primary
    val scope = rememberCoroutineScope()
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    val selected = located.firstOrNull { it.entry.observation.id == selectedId }

    val data = remember(located) { GeoJsonData.Features(FeatureCollection(located.map { it.toFeature() })) }
    // The layer click handlers below are declared inside the map state's own content, so they reach it through this holder.
    val mapRef = remember { arrayOfNulls<MapState>(1) }

    val mapState = rememberMapState(
        baseStyle = BaseStyle.Uri(STYLE_URI),
        initialCameraPosition = CameraPosition(target = GERMANY, zoom = 5.0),
    ) {
        val source = rememberGeoJsonSource(data, GeoJsonOptions(cluster = true, clusterRadius = 40))
        val isCluster = feature.has("point_count")

        CircleLayer(
            id = "obs-clusters",
            source = source,
            filter = isCluster,
            color = const(okColor),
            radius = step(feature["point_count"].asNumber(), const(16.dp), 10 to const(20.dp), 50 to const(26.dp)),
            strokeColor = const(Color.White),
            strokeWidth = const(2.dp),
            hitPadding = 8.dp,
            onClick = { features ->
                val target = (features.firstOrNull()?.geometry as? Point)?.coordinates
                val map = mapRef[0]
                if (target != null && map != null) {
                    scope.launch {
                        val cam = map.cameraPosition
                        map.animateCameraPosition(cam.copy(target = target, zoom = cam.zoom + 2.0))
                    }
                }
                ClickResult.Consume
            },
        )
        SymbolLayer(
            id = "obs-cluster-count",
            source = source,
            filter = isCluster,
            textField = format(span(feature["point_count_abbreviated"].asString())),
            // Must be a font stack the style's glyph server hosts; the library default (Open Sans) 404s on OpenFreeMap.
            textFont = const(listOf("Noto Sans Bold")),
            textColor = const(Color.White),
            textSize = const(12.sp),
            textAllowOverlap = const(true),
            textIgnorePlacement = const(true),
        )
        CircleLayer(
            id = "obs-points",
            source = source,
            filter = !isCluster,
            color = switch(
                feature["danger"],
                case("danger", const(dangerColor)),
                case("caution", const(cautionColor)),
                fallback = const(okColor),
            ),
            radius = const(9.dp),
            strokeColor = const(Color.White),
            strokeWidth = const(2.dp),
            hitPadding = 12.dp,
            onClick = { features ->
                val id = features.firstOrNull()?.properties?.get("id")?.jsonPrimitive?.contentOrNull
                if (id != null) {
                    selectedId = id
                    ClickResult.Consume
                } else {
                    ClickResult.Pass
                }
            },
        )
    }

    mapRef[0] = mapState

    // Fit the camera once per distinct set of located observations; the saved camera survives rotation.
    val fitKey = remember(located) { located.joinToString(",") { it.entry.observation.id } }
    var fittedKey by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(fitKey) {
        if (fittedKey == fitKey) return@LaunchedEffect
        fittedKey = fitKey
        val lats = located.map { it.entry.observation.lat!! }
        val lons = located.map { it.entry.observation.lon!! }
        if (located.size == 1) {
            mapState.animateCameraPosition(CameraPosition(target = Position(longitude = lons[0], latitude = lats[0]), zoom = 11.0))
        } else {
            val margin = 0.01
            mapState.animateCameraToBounds(
                BoundingBox(west = lons.min() - margin, south = lats.min() - margin, east = lons.max() + margin, north = lats.max() + margin),
                padding = PaddingValues(48.dp),
            )
        }
    }

    val interactions = remember {
        MapInteractions {
            callbacks { click { onUnhandled { selectedId = null; ClickResult.Consume } } }
        }
    }
    // TextureView mode draws through Compose's frame pipeline and honours the rounded clip below; the
    // SurfaceView default skipped the redraw after tiles arrived until the user touched the map.
    val uiOptions = remember { MapUiOptions(MapUiOptions.Standard) { renderMode = AndroidRenderMode.Texture } }

    Box(Modifier.fillMaxSize().padding(start = 16.dp, end = 16.dp, bottom = bottomPadding + 8.dp)) {
        MaplibreMap(
            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh),
            state = mapState,
            interactions = interactions,
            uiOptions = uiOptions,
            // Scale bar, compass and the OpenStreetMap attribution (licence requirement); no MapLibre logo.
            overlay = {
                DisappearingScaleBar(metersPerDp = mapState.viewport?.metersPerDpAtTarget ?: 0.0, zoom = mapState.cameraPosition.zoom, modifier = Modifier.align(Alignment.TopStart))
                DisappearingCompassButton(modifier = Modifier.align(Alignment.TopEnd))
                CompactAttributionButton(Modifier.align(Alignment.BottomEnd))
            },
        )
        if (selected != null) {
            Surface(
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(12.dp),
                shape = RoundedCornerShape(18.dp),
                shadowElevation = 6.dp,
                color = MaterialTheme.colorScheme.surface,
            ) { card(selected) }
        }
    }
}

/**
 * Attribution that starts as a small info button and only shows the licence text while tapped open.
 * The library's [org.maplibre.compose.overlay.ExpandingAttributionButton] always opens expanded, which
 * covered the bottom of the map on every visit. Any map gesture folds it back up.
 */
@Composable
private fun MapOverlayScope.CompactAttributionButton(modifier: Modifier = Modifier) {
    val attributions by remember { derivedStateOf { style.attributions() } }
    if (attributions.isEmpty()) return
    var expanded by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(mapState.isCameraMoving, mapState.cameraMoveReason) {
        if (mapState.isCameraMoving && mapState.cameraMoveReason == CameraMoveReason.GESTURE) expanded = false
    }
    val expandedStyle = AttributionDefaults.expandedStyle()
    Row(modifier.padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
        AnimatedVisibility(expanded, enter = fadeIn() + expandHorizontally(expandFrom = Alignment.End), exit = fadeOut() + shrinkHorizontally(shrinkTowards = Alignment.End)) {
            Surface(
                shape = expandedStyle.shape,
                color = expandedStyle.containerColor,
                contentColor = expandedStyle.contentColor,
                shadowElevation = expandedStyle.shadowElevation,
                border = expandedStyle.border,
            ) {
                Box(Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) { AttributionDefaults.content(attributions, expandedStyle.textStyle) }
            }
        }
        AttributionDefaults.button { expanded = !expanded }
    }
}

private fun HistoryItem.toFeature(): Feature<Point, JsonObject> {
    val o = entry.observation
    val danger = when {
        edibility?.dangerous == true -> "danger"
        edibility == Edibility.CAUTION || edibility == Edibility.INEDIBLE -> "caution"
        else -> "ok"
    }
    return Feature(
        geometry = Point(Position(longitude = o.lon!!, latitude = o.lat!!)),
        properties = buildJsonObject {
            put("id", o.id)
            put("danger", danger)
        },
    )
}
