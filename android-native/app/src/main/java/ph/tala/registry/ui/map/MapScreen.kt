package ph.tala.registry.ui.map

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.drawText
import java.text.DateFormat
import java.util.Date
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon
import ph.tala.registry.domain.model.CoordinateSource
import ph.tala.registry.domain.model.HouseholdId
import ph.tala.registry.domain.model.MapRules
import ph.tala.registry.ui.location.LocationWritePhase

private val InkBlue = Color(0xFF172944)
private val AccentBlue = Color(0xFF1B4FA0)
private val AlertRed = Color(0xFFB3261E)

/**
 * D16 household map. Online the OpenStreetMap basemap renders behind the saved pins
 * ("© OpenStreetMap contributors" stays visible); offline the same pins plot onto an
 * extent-accurate fallback canvas, so airplane mode never makes a saved coordinate
 * inaccessible. The barangay boundary is labeled approximate everywhere it appears.
 */
@Composable
fun MapScreen(
    state: MapUiState,
    onOpenHousehold: (HouseholdId) -> Unit,
    onSelect: (HouseholdId?) -> Unit,
    onStartAdjust: (HouseholdId) -> Unit,
    onPlacePin: (Double, Double) -> Unit,
    onEditLatitude: (String) -> Unit,
    onEditLongitude: (String) -> Unit,
    onSaveAdjust: () -> Unit,
    onCancelAdjust: () -> Unit,
    onStartPinHousehold: (Double, Double) -> Unit,
    onShowPinHouseholdPicker: () -> Unit,
    onPinHousehold: (HouseholdId) -> Unit,
    onCancelPinHousehold: () -> Unit,
    onExternalMapUnavailable: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Map", style = MaterialTheme.typography.titleLarge, modifier = Modifier.testTag("map-title"))
            Text(
                if (state.basemap == BasemapMode.Online) "Saved household pins over the Cogon working area."
                else "Basemap unavailable offline — saved coordinates stay plotted and editable.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag(if (state.basemap == BasemapMode.Online) "map-online-note" else "map-offline-note"),
            )
            Text(MapRules.EXTENT_NOTE, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        MapSurface(
            state = state,
            onSelect = onSelect,
            onPlacePin = onPlacePin,
            onStartPinHousehold = onStartPinHousehold,
            onShowPinHouseholdPicker = onShowPinHouseholdPicker,
            onCancelPinHousehold = onCancelPinHousehold,
        )
        StatusLines(state)
        val selected = state.selectedPin
        when {
            state.adjust != null -> AdjustPanel(state.adjust!!, state.writePhase, onEditLatitude, onEditLongitude, onSaveAdjust, onCancelAdjust)
            selected != null -> SelectedCard(
                pin = selected,
                onOpenHousehold = onOpenHousehold,
                onStartAdjust = onStartAdjust,
                onExternalMapUnavailable = onExternalMapUnavailable,
            )
        }
        PinLists(state, onSelect, onOpenHousehold, Modifier.weight(1f))
        PinHouseholdPicker(state, onPinHousehold, onCancelPinHousehold)
    }
}

/**
 * Online basemap: an osmdroid MapView rendering OpenStreetMap Mapnik tiles. Markers
 * come from the saved coordinates; the approximate working extent is outlined. While
 * a marker drag is in progress the overlay sync is skipped so osmdroid keeps the
 * gesture, and the draft commits when the drag ends.
 */
@Composable
private fun OsmTileMap(state: MapUiState, onSelect: (HouseholdId?) -> Unit, onPlacePin: (Double, Double) -> Unit, onStartPinHousehold: (Double, Double) -> Unit) {
    val context = LocalContext.current
    val dragging = remember { mutableStateOf(false) }
    val map = remember(context) {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            isTilesScaledToDpi = true
            zoomController.setVisibility(org.osmdroid.views.CustomZoomButtonsController.Visibility.NEVER)
            setMinZoomLevel(MapRules.MIN_ZOOM)
            setMaxZoomLevel(MapRules.MAX_ZOOM)
            controller.setZoom(MapRules.DEFAULT_ZOOM)
            controller.setCenter(GeoPoint(MapRules.CENTER_LATITUDE, MapRules.CENTER_LONGITUDE))
            overlays.add(extentPolygon())
        }
    }
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(map, lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            when (event) {
                androidx.lifecycle.Lifecycle.Event.ON_RESUME -> map.onResume()
                androidx.lifecycle.Lifecycle.Event.ON_PAUSE -> map.onPause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) map.onResume()
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            map.onPause()
            map.onDetach()
        }
    }
    AndroidView(
        modifier = Modifier.fillMaxSize().clipToBounds().testTag("osm-map").semantics { contentDescription = "Household map with saved pins" },
        factory = { map },
        update = { map ->
            if (dragging.value) return@AndroidView
            syncOverlays(map, state, context, onSelect, onPlacePin, onStartPinHousehold, dragging)
        },
    )
}

/** Replaces marker/event overlays with the current pins; the extent outline persists. */
private fun syncOverlays(
    map: MapView,
    state: MapUiState,
    context: Context,
    onSelect: (HouseholdId?) -> Unit,
    onPlacePin: (Double, Double) -> Unit,
    onStartPinHousehold: (Double, Double) -> Unit,
    dragging: MutableState<Boolean>,
) {
    val keep = map.overlays.filterNot { it is Marker || it is MapEventsOverlay }.toMutableList()
    // Tap to place while adjusting; taps otherwise fall through to marker selection.
    keep.add(
        MapEventsOverlay(object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(point: GeoPoint?): Boolean {
                if (point != null && state.adjust != null) {
                    onPlacePin(point.latitude, point.longitude)
                    return true
                }
                return false
            }
            override fun longPressHelper(point: GeoPoint?): Boolean {
                if (point != null && state.adjust == null && state.pendingPinLocation == null && !state.showPinHouseholdPicker) {
                    onStartPinHousehold(point.latitude, point.longitude)
                    return true
                }
                return false
            }
        }),
    )
    for (pin in state.pins) {
        if (pin.outOfArea) continue // retained and flagged in the list; never re-centers the map
        val marker = Marker(map).apply {
            position = GeoPoint(pin.coordinate.latitude, pin.coordinate.longitude)
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            icon = pinIcon(context, selected = pin.householdId == state.selectedId)
            title = "${pin.displayCode} · ${pin.headName}"
            isDraggable = state.adjust?.householdId == pin.householdId
            setOnMarkerClickListener { _, _ ->
                onSelect(pin.householdId)
                true
            }
            if (isDraggable) {
                setOnMarkerDragListener(object : Marker.OnMarkerDragListener {
                    override fun onMarkerDrag(marker: Marker) {
                        dragging.value = true
                        onPlacePin(marker.position.latitude, marker.position.longitude)
                    }
                    override fun onMarkerDragEnd(marker: Marker) {
                        dragging.value = false
                        onPlacePin(marker.position.latitude, marker.position.longitude)
                    }
                    override fun onMarkerDragStart(marker: Marker) {
                        dragging.value = true
                    }
                })
            }
        }
        keep.add(marker)
    }
    state.pendingPinLocation?.let { (latitude, longitude) ->
        keep.add(Marker(map).apply {
            position = GeoPoint(latitude, longitude)
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            icon = pinIcon(context, selected = true)
            title = "New household pin location"
        })
    }
    map.overlays.clear()
    map.overlays.addAll(keep)
    map.invalidate()
}

/** Outline-only polygon of the approximate working extent. */
private fun extentPolygon(): Polygon = Polygon().apply {
    points = listOf(
        GeoPoint(MapRules.SOUTH_WEST_LATITUDE, MapRules.SOUTH_WEST_LONGITUDE),
        GeoPoint(MapRules.SOUTH_WEST_LATITUDE, MapRules.NORTH_EAST_LONGITUDE),
        GeoPoint(MapRules.NORTH_EAST_LATITUDE, MapRules.NORTH_EAST_LONGITUDE),
        GeoPoint(MapRules.NORTH_EAST_LATITUDE, MapRules.SOUTH_WEST_LONGITUDE),
    )
    outlinePaint.color = InkBlue.copy(alpha = 0.85f).toArgb()
    outlinePaint.strokeWidth = 4f
    fillPaint.color = android.graphics.Color.TRANSPARENT
}

/** Runtime-drawn teardrop pin (no icon font in this build). */
private fun pinIcon(context: Context, selected: Boolean): android.graphics.drawable.BitmapDrawable {
    val density = context.resources.displayMetrics.density
    val width = (24 * density).toInt().coerceAtLeast(24)
    val height = (32 * density).toInt().coerceAtLeast(32)
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)
    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = (if (selected) AccentBlue else InkBlue).toArgb()
    }
    val radius = width / 2.6f
    val center = radius + 2 * density
    canvas.drawCircle(width / 2f, center, radius, paint)
    val tip = android.graphics.Path().apply {
        moveTo(width / 2f - radius * 0.55f, center + radius * 0.55f)
        lineTo(width / 2f + radius * 0.55f, center + radius * 0.55f)
        lineTo(width / 2f, height - density)
        close()
    }
    canvas.drawPath(tip, paint)
    canvas.drawCircle(width / 2f, center, radius * 0.42f, android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
    })
    return android.graphics.drawable.BitmapDrawable(context.resources, bitmap).apply { setBounds(0, 0, width, height) }
}

/** Shared projection for the offline plot: Web Mercator over the working extent. */
private class ExtentTransform(canvasSize: IntSize, density: Float) {
    private val pad = (12 * density).coerceAtLeast(6f)
    private val x0 = MapRules.worldX(MapRules.SOUTH_WEST_LONGITUDE)
    private val x1 = MapRules.worldX(MapRules.NORTH_EAST_LONGITUDE)
    private val y0 = MapRules.worldY(MapRules.NORTH_EAST_LATITUDE)
    private val y1 = MapRules.worldY(MapRules.SOUTH_WEST_LATITUDE)
    private val scale = minOf((canvasSize.width - 2 * pad) / (x1 - x0).toFloat(), (canvasSize.height - 2 * pad) / (y1 - y0).toFloat())
    private val offsetX = (canvasSize.width - (x1 - x0).toFloat() * scale) / 2f
    private val offsetY = (canvasSize.height - (y1 - y0).toFloat() * scale) / 2f

    fun offset(latitude: Double, longitude: Double): Offset = Offset(
        offsetX + (MapRules.worldX(longitude) - x0).toFloat() * scale,
        offsetY + (MapRules.worldY(latitude) - y0).toFloat() * scale,
    )

    fun latitude(y: Float): Double = MapRules.latitudeAt(((y - offsetY) / scale + y0).toDouble())

    fun longitude(x: Float): Double = MapRules.longitudeAt(((x - offsetX) / scale + x0).toDouble())

    /** Ground meters per pixel at the extent latitude, for accuracy circles. */
    fun metresPerPixel(): Double {
        val worldUnitsPerPixel = ((x1 - x0) / scale)
        return 40_075_016.686 * Math.cos(Math.toRadians(MapRules.CENTER_LATITUDE)) * worldUnitsPerPixel
    }

    fun graticuleStep(): Double = 0.003
}

/**
 * Offline basemap state: an extent-accurate plot of every saved in-extent pin on a
 * graticule canvas. No tile is fetched, so airplane mode cannot blank the surface or
 * lose a household edit. Taps select the nearest pin; while adjusting, taps and drags
 * move the draft pin.
 */
@Composable
private fun OfflinePlot(state: MapUiState, onSelect: (HouseholdId?) -> Unit, onPlacePin: (Double, Double) -> Unit, onStartPinHousehold: (Double, Double) -> Unit) {
    val density = LocalDensity.current.density
    val textMeasurer = rememberTextMeasurer()
    // Hoisted out of the draw scope: MaterialTheme reads are composable-only.
    val labelStyle = TextStyle(fontSize = MaterialTheme.typography.labelSmall.fontSize, color = InkBlue)
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    val transform = if (canvasSize == IntSize.Zero) null else ExtentTransform(canvasSize, density)
    Canvas(
        Modifier
            .fillMaxSize()
            .onSizeChanged { canvasSize = it }
            .testTag("map-offline-plot")
            .semantics { contentDescription = "Offline plot of saved household coordinates" }
            .pointerInput(state.pins, state.adjust, state.pendingPinLocation, state.showPinHouseholdPicker) {
                detectTapGestures(
                    onTap = { tap ->
                    val projection = transform ?: return@detectTapGestures
                    val hitThreshold = 26 * density
                    val hit = state.pins.filter { !it.outOfArea }
                        .map { pin -> pin to projection.offset(pin.coordinate.latitude, pin.coordinate.longitude) }
                        .minByOrNull { (_, offset) -> (offset - tap).getDistance() }
                        ?.takeIf { (_, offset) -> (offset - tap).getDistance() <= hitThreshold }
                    when {
                        hit != null -> onSelect(hit.first.householdId)
                        state.adjust != null -> onPlacePin(projection.latitude(tap.y), projection.longitude(tap.x))
                    }
                    },
                    onLongPress = { press ->
                        val projection = transform ?: return@detectTapGestures
                        if (state.adjust == null && state.pendingPinLocation == null && !state.showPinHouseholdPicker) {
                            onStartPinHousehold(projection.latitude(press.y), projection.longitude(press.x))
                        }
                    },
                )
            }
            .pointerInput(state.adjust) {
                if (state.adjust == null) return@pointerInput
                detectDragGestures { change, _ ->
                    val projection = transform ?: return@detectDragGestures
                    change.consume()
                    onPlacePin(projection.latitude(change.position.y), projection.longitude(change.position.x))
                }
            },
    ) {
        val projection = transform ?: return@Canvas
        val step = projection.graticuleStep()
        var latitude = Math.ceil(MapRules.SOUTH_WEST_LATITUDE / step) * step
        while (latitude <= MapRules.NORTH_EAST_LATITUDE + 1e-9) {
            val line = projection.offset(latitude, MapRules.SOUTH_WEST_LONGITUDE)
            drawLine(Color(0x33172944), Offset(0f, line.y), Offset(size.width, line.y), strokeWidth = 1f)
            latitude += step
        }
        var longitude = Math.ceil(MapRules.SOUTH_WEST_LONGITUDE / step) * step
        while (longitude <= MapRules.NORTH_EAST_LONGITUDE + 1e-9) {
            val line = projection.offset(MapRules.SOUTH_WEST_LATITUDE, longitude)
            drawLine(Color(0x33172944), Offset(line.x, 0f), Offset(line.x, size.height), strokeWidth = 1f)
            longitude += step
        }
        val corners = listOf(
            projection.offset(MapRules.SOUTH_WEST_LATITUDE, MapRules.SOUTH_WEST_LONGITUDE),
            projection.offset(MapRules.SOUTH_WEST_LATITUDE, MapRules.NORTH_EAST_LONGITUDE),
            projection.offset(MapRules.NORTH_EAST_LATITUDE, MapRules.NORTH_EAST_LONGITUDE),
            projection.offset(MapRules.NORTH_EAST_LATITUDE, MapRules.SOUTH_WEST_LONGITUDE),
        )
        drawPath(
            Path().apply {
                moveTo(corners[0].x, corners[0].y)
                lineTo(corners[1].x, corners[1].y)
                lineTo(corners[2].x, corners[2].y)
                lineTo(corners[3].x, corners[3].y)
                close()
            },
            InkBlue,
            style = Stroke(width = 2.dp.toPx()),
        )
        val draft = state.adjust
        if (draft != null) {
            val draftLatitude = draft.latitudeText.toDoubleOrNull()
            val draftLongitude = draft.longitudeText.toDoubleOrNull()
            if (draftLatitude != null && draftLongitude != null && MapRules.contains(draftLatitude, draftLongitude)) {
                val point = projection.offset(draftLatitude, draftLongitude)
                drawCircle(AccentBlue.copy(alpha = 0.35f), radius = 11 * density, center = point)
                drawCircle(AccentBlue, radius = 5 * density, center = point)
                drawCircle(AccentBlue, radius = 18 * density, center = point, style = Stroke(width = 2.dp.toPx()))
            }
        }
        state.pendingPinLocation?.let { (latitude, longitude) ->
            val point = projection.offset(latitude, longitude)
            drawCircle(AccentBlue.copy(alpha = 0.3f), radius = 14 * density, center = point)
            drawCircle(AccentBlue, radius = 6 * density, center = point)
        }
        for (pin in state.pins.filter { !it.outOfArea }) {
            val point = projection.offset(pin.coordinate.latitude, pin.coordinate.longitude)
            val selected = pin.householdId == state.selectedId
            pin.coordinate.accuracyMetres?.let { accuracy ->
                val radius = (accuracy / projection.metresPerPixel()).toFloat().coerceAtLeast(2f)
                drawCircle(AccentBlue.copy(alpha = 0.22f), radius = radius, center = point)
            }
            drawCircle(if (selected) AccentBlue else InkBlue, radius = (if (selected) 9f else 6f) * density, center = point)
            drawCircle(Color.White, radius = 2.4f * density, center = point)
            if (selected) {
                drawCircle(AccentBlue, radius = 14 * density, center = point, style = Stroke(width = 2.dp.toPx()))
                drawText(
                    textMeasurer,
                    "${pin.displayCode} · ${pin.headName}",
                    topLeft = Offset(point.x + 12 * density, point.y - 26 * density),
                    style = labelStyle,
                )
            }
        }
    }
}

/** Pin adjustment: explicit fields, first-error reporting and honest write phases. */
@Composable
private fun AdjustPanel(
    draft: PinAdjustDraft,
    writePhase: LocationWritePhase,
    onEditLatitude: (String) -> Unit,
    onEditLongitude: (String) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            "Adjust Pin · ${draft.displayCode}",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.testTag("adjust-title"),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = draft.latitudeText,
                onValueChange = onEditLatitude,
                enabled = writePhase != LocationWritePhase.Saving,
                label = if (draft.latitudeText.isEmpty()) {
                    { Text("Latitude") }
                } else {
                    { Text("", Modifier.testTag("invisible")) }
                },
                singleLine = true,
                isError = draft.latitudeError != null,
                supportingText = { draft.latitudeError?.let { Text(it, Modifier.testTag("hint-adjust-lat")) } },
                modifier = Modifier.weight(1f).testTag("adjust-lat"),
            )
            OutlinedTextField(
                value = draft.longitudeText,
                onValueChange = onEditLongitude,
                enabled = writePhase != LocationWritePhase.Saving,
                label = if (draft.longitudeText.isEmpty()) {
                    { Text("Longitude") }
                } else {
                    { Text("", Modifier.testTag("invisible")) }
                },
                singleLine = true,
                isError = draft.longitudeError != null,
                supportingText = { draft.longitudeError?.let { Text(it, Modifier.testTag("hint-adjust-lon")) } },
                modifier = Modifier.weight(1f).testTag("adjust-lon"),
            )
        }
        Text(
            "Tap the map or drag the pin to the dwelling. Saving records the pin as a manual correction: the measured accuracy claim is cleared.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onSave,
                enabled = writePhase != LocationWritePhase.Saving,
                modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("save-adjust-pin"),
            ) { Text(if (writePhase == LocationWritePhase.Saving) "Saving…" else "Save Pin Position") }
            OutlinedButton(
                onClick = onCancel,
                enabled = writePhase != LocationWritePhase.Saving,
                modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("cancel-adjust-pin"),
            ) { Text("Cancel") }
        }
    }
}

/** The selected household: provenance, external hand-off and the adjust entry point. */
@Composable
private fun SelectedCard(
    pin: HouseholdPin,
    onOpenHousehold: (HouseholdId) -> Unit,
    onStartAdjust: (HouseholdId) -> Unit,
    onExternalMapUnavailable: () -> Unit,
) {
    val context = LocalContext.current
    fun openExternal(directions: Boolean) {
        val url = MapRules.externalMapUrl(pin.coordinate.latitude, pin.coordinate.longitude, directions)
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: ActivityNotFoundException) {
            onExternalMapUnavailable()
        }
    }
    Surface(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("${pin.displayCode} · ${pin.headName}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag("selected-household"))
            Text(pin.address, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                "%.6f, %.6f".format(pin.coordinate.latitude, pin.coordinate.longitude),
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.testTag("selected-coords"),
            )
            Text(
                when (pin.coordinate.source) {
                    CoordinateSource.Gps -> "Device fix · ±%s m · captured %s".format(
                        formatAccuracy(pin.coordinate.accuracyMetres), formatTime(pin.coordinate.capturedAt))
                    CoordinateSource.Manual -> "Manually corrected · entered %s".format(formatTime(pin.coordinate.capturedAt))
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("selected-metadata"),
            )
            if (pin.outOfArea) Text(
                MapRules.OUT_OF_AREA_FLAG,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.testTag("out-of-area-warning"),
            )
            Button(
                onClick = { onOpenHousehold(pin.householdId) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("open-household-from-map"),
            ) { Text("Open Household Record") }
            OutlinedButton(
                onClick = { onStartAdjust(pin.householdId) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("start-adjust-pin"),
            ) { Text("Adjust Pin on Map") }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { openExternal(directions = false) },
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("open-external-map"),
                ) { Text("Open in Google Maps") }
                OutlinedButton(
                    onClick = { openExternal(directions = true) },
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("open-external-directions"),
                ) { Text("Directions") }
            }
            Text(
                "External map links carry only the saved coordinates — never names or answers.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Accessible registry lists: plotted pins, out-of-area flags and untagged households. */
@Composable
private fun PinLists(
    state: MapUiState,
    onSelect: (HouseholdId?) -> Unit,
    onOpenHousehold: (HouseholdId) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        item {
            Text(
                "Households with Saved Pins (${state.pins.size} · ${state.plottedCount} in the working area)",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = 8.dp).testTag("pin-list-title"),
            )
        }
        items(state.pins, key = { it.householdId.value }) { pin ->
            PinRow(pin, selected = pin.householdId == state.selectedId, onSelect = onSelect)
        }
        if (state.untagged.isNotEmpty()) {
            item {
                Text(
                    "Not Plotted Yet (${state.untagged.size})",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 12.dp).testTag("untagged-title"),
                )
            }
            items(state.untagged, key = { it.householdId.value }) { household ->
                Surface(
                    Modifier.fillMaxWidth().testTag("map-untagged-${household.displayCode}").clickable { onOpenHousehold(household.householdId) },
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 1.dp,
                ) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("${household.displayCode} · ${household.headName}", style = MaterialTheme.typography.bodyMedium)
                            Text("No Coordinates Recorded Yet", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text("Open Record", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
        item { Spacer(Modifier.height(12.dp)) }
    }
}

@Composable
private fun PinRow(pin: HouseholdPin, selected: Boolean, onSelect: (HouseholdId?) -> Unit) {
    Surface(
        Modifier.fillMaxWidth().testTag("map-pin-${pin.displayCode}").clickable { onSelect(if (selected) null else pin.householdId) },
        shape = RoundedCornerShape(10.dp),
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
        tonalElevation = if (selected) 0.dp else 1.dp,
    ) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("${pin.displayCode} · ${pin.headName}", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "%.6f, %.6f · %s".format(
                        pin.coordinate.latitude,
                        pin.coordinate.longitude,
                        if (pin.coordinate.source == CoordinateSource.Gps) "device fix" else "manual",
                    ),
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (pin.outOfArea) Text(
                "Outside working area",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.testTag("row-out-of-area"),
            )
        }
    }
}

private fun formatAccuracy(accuracy: Double?): String =
    if (accuracy == null) "?" else if (accuracy >= 10) "%.0f".format(accuracy) else "%.1f".format(accuracy)

private fun formatTime(capturedAt: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(capturedAt))

@Composable
private fun PinDropdownItem(
    household: UntaggedHousehold,
    onClick: () -> Unit,
) {
    val pinColor = MaterialTheme.colorScheme.primary
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("pin-dropdown-${household.displayCode}")
            .clickable { onClick() }
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
    ) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    household.displayCode,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    household.headName,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            // Simple pin indicator (no material-icons artifact in this build)
            Canvas(Modifier.size(24.dp)) {
                val centerX = size.width / 2f
                val centerY = size.height / 2f
                val radius = size.minDimension / 3f
                drawCircle(
                    pinColor,
                    radius = radius,
                    center = Offset(centerX, centerY - radius * 0.3f)
                )
                val tipPath = Path().apply {
                    moveTo(centerX - radius * 0.5f, centerY - radius * 0.3f + radius * 0.5f)
                    lineTo(centerX + radius * 0.5f, centerY - radius * 0.3f + radius * 0.5f)
                    lineTo(centerX, centerY + radius * 0.7f)
                    close()
                }
                drawPath(tipPath, pinColor)
            }
        }
    }
}

/**
 * Bottom-sheet picker opened only after the user confirms the long-pressed point.
 * Each row is an active, unarchived household without saved coordinates.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PinHouseholdPicker(
    state: MapUiState,
    onPinHousehold: (HouseholdId) -> Unit,
    onCancel: () -> Unit,
) {
    if (state.showPinHouseholdPicker && state.untagged.isNotEmpty()) {
        ModalBottomSheet(onDismissRequest = onCancel) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp)
                    .statusBarsPadding()
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        "Pin a Household",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.testTag("pin-dropdown-title"),
                    )
                    TextButton(onClick = onCancel, modifier = Modifier.testTag("pin-dropdown-close")) {
                        Text("Close", style = MaterialTheme.typography.labelLarge)
                    }
                }
                Text(
                    "Long-pressed location: " + (state.pendingPinLocation?.let { "%.6f, %.6f".format(it.first, it.second) } ?: "Unknown"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                        .padding(12.dp)
                        .testTag("pin-dropdown-location"),
                )
                Text(
                    "Select a household without coordinates:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                LazyColumn(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(state.untagged, key = { it.householdId.value }) { household ->
                        PinDropdownItem(
                            household = household,
                            onClick = { onPinHousehold(household.householdId) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MapSurface(
    state: MapUiState,
    onSelect: (HouseholdId?) -> Unit,
    onPlacePin: (Double, Double) -> Unit,
    onStartPinHousehold: (Double, Double) -> Unit,
    onShowPinHouseholdPicker: () -> Unit,
    onCancelPinHousehold: () -> Unit,
) {
    val mapShape = RoundedCornerShape(12.dp)
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .heightIn(min = 300.dp)
            .height(320.dp)
            .clip(mapShape)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, mapShape)
            .testTag("map-surface"),
    ) {
        when (state.basemap) {
            BasemapMode.Online -> OsmTileMap(state, onSelect, onPlacePin, onStartPinHousehold)
            BasemapMode.Offline -> OfflinePlot(state, onSelect, onPlacePin, onStartPinHousehold)
        }
        if (state.basemap == BasemapMode.Online) {
            Text(
                "Basemap © OpenStreetMap contributors",
                style = MaterialTheme.typography.labelSmall,
                color = InkBlue,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(8.dp)
                    .background(Color(0xCCFFFFFF), RoundedCornerShape(6.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
                    .testTag("map-attribution"),
            )
        }
        Text(
            MapRules.EXTENT_NOTE,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 6.dp)
                .background(Color(0xCCFFFFFF), RoundedCornerShape(6.dp))
                .padding(horizontal = 6.dp, vertical = 2.dp)
                .testTag("map-extent-label"),
        )
        if (state.pendingPinLocation != null && !state.showPinHouseholdPicker) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 36.dp),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
                shadowElevation = 6.dp,
            ) {
                Row(
                    modifier = Modifier.padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Button(
                        onClick = onShowPinHouseholdPicker,
                        modifier = Modifier.testTag("pin-household-action"),
                    ) {
                        Text("Pin a household")
                    }
                    TextButton(
                        onClick = onCancelPinHousehold,
                        modifier = Modifier.testTag("cancel-pending-pin"),
                    ) {
                        Text("Cancel")
                    }
                }
            }
        }
    }
}

/** Honest status lines: never mistake a rejected write for a saved one. */
@Composable
private fun StatusLines(state: MapUiState) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        if (state.message.isNotBlank()) Text(
            state.message,
            color = if (state.writePhase == LocationWritePhase.Failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.testTag("map-status"),
        )
        if (state.writePhase != LocationWritePhase.Idle) Text(
            when (state.writePhase) {
                LocationWritePhase.Saving -> "Saving…"
                LocationWritePhase.Saved -> "Saved on this device."
                LocationWritePhase.Failed -> "Could not save the pin position."
                LocationWritePhase.Idle -> ""
            },
            color = if (state.writePhase == LocationWritePhase.Failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.testTag("map-write-status"),
        )
    }
}


