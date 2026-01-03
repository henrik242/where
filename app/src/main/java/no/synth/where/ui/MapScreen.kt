package no.synth.where.ui

import android.Manifest
import android.content.Context
import android.location.Location
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.launch
import no.synth.where.data.MapStyle
import no.synth.where.data.RulerState
import no.synth.where.data.Track
import no.synth.where.data.TrackRepository
import no.synth.where.service.LocationTrackingService
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.location.LocationComponent
import org.maplibre.android.location.LocationComponentActivationOptions
import org.maplibre.android.location.modes.RenderMode
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import java.text.SimpleDateFormat
import java.util.*

enum class MapLayer {
    OSM,
    OPENTOPOMAP,
    KARTVERKET,
    TOPORASTER,
    SJOKARTRASTER
}

@Composable
fun MapScreen(
    onSettingsClick: () -> Unit,
    showCountyBorders: Boolean
) {
    val context = LocalContext.current
    val trackRepository = remember { TrackRepository.getInstance(context) }
    val isRecording by trackRepository.isRecording
    val currentTrack by trackRepository.currentTrack.collectAsState()
    val viewingTrack by trackRepository.viewingTrack.collectAsState()

    var mapInstance by remember { mutableStateOf<MapLibreMap?>(null) }
    var selectedLayer by remember { mutableStateOf(MapLayer.KARTVERKET) }
    var showLayerMenu by remember { mutableStateOf(false) }
    var hasLocationPermission by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var showStopTrackDialog by remember { mutableStateOf(false) }
    var trackNameInput by remember { mutableStateOf("") }
    var hasZoomedToLocation by rememberSaveable { mutableStateOf(false) }

    // Save camera position across navigation
    var savedCameraLat by rememberSaveable { mutableStateOf(65.0) }
    var savedCameraLon by rememberSaveable { mutableStateOf(10.0) }
    var savedCameraZoom by rememberSaveable { mutableStateOf(5.0) }

    var rulerState by remember { mutableStateOf(RulerState()) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasLocationPermission = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
    }

    LaunchedEffect(Unit) {
        permissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
        )
    }

    LaunchedEffect(currentTrack, viewingTrack, mapInstance) {
        val viewing = viewingTrack
        val trackToShow = currentTrack ?: viewing
        val map = mapInstance

        map?.style?.let { style ->
            updateTrackOnMap(style, trackToShow, isCurrentTrack = currentTrack != null)

            if (viewing != null && viewing.points.isNotEmpty()) {
                kotlinx.coroutines.delay(100)
                val points = viewing.points.map { it.latLng }
                if (points.isNotEmpty()) {
                    val bounds = org.maplibre.android.geometry.LatLngBounds.Builder()
                        .includes(points)
                        .build()
                    map.animateCamera(
                        org.maplibre.android.camera.CameraUpdateFactory.newLatLngBounds(bounds, 100)
                    )
                }
            }
        }
    }

    LaunchedEffect(mapInstance) {
        val map = mapInstance
        map?.addOnCameraMoveListener {
            map.cameraPosition.target?.let { target ->
                savedCameraLat = target.latitude
                savedCameraLon = target.longitude
                savedCameraZoom = map.cameraPosition.zoom
            }
        }
    }

    LaunchedEffect(hasLocationPermission, mapInstance) {
        val map = mapInstance
        if (hasLocationPermission && map != null && !hasZoomedToLocation && viewingTrack == null && currentTrack == null) {
            try {
                val locationManager = context.getSystemService(android.content.Context.LOCATION_SERVICE) as android.location.LocationManager
                val lastKnownLocation = try {
                    locationManager.getLastKnownLocation(android.location.LocationManager.GPS_PROVIDER)
                        ?: locationManager.getLastKnownLocation(android.location.LocationManager.NETWORK_PROVIDER)
                        ?: locationManager.getLastKnownLocation(android.location.LocationManager.FUSED_PROVIDER)
                } catch (e: SecurityException) {
                    null
                }

                lastKnownLocation?.let { location ->
                    kotlinx.coroutines.delay(500)
                    map.animateCamera(
                        org.maplibre.android.camera.CameraUpdateFactory.newLatLngZoom(
                            LatLng(location.latitude, location.longitude),
                            12.0
                        )
                    )
                    hasZoomedToLocation = true
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End) {
                SmallFloatingActionButton(
                    onClick = { showLayerMenu = true },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.Filled.Layers, contentDescription = "Select Layer")
                }

                DropdownMenu(
                    expanded = showLayerMenu,
                    onDismissRequest = { showLayerMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(if (selectedLayer == MapLayer.KARTVERKET) "✓ Kartverket" else "Kartverket") },
                        onClick = {
                            selectedLayer = MapLayer.KARTVERKET
                            showLayerMenu = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(if (selectedLayer == MapLayer.TOPORASTER) "✓ Toporaster (Hiking)" else "Toporaster (Hiking)") },
                        onClick = {
                            selectedLayer = MapLayer.TOPORASTER
                            showLayerMenu = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(if (selectedLayer == MapLayer.SJOKARTRASTER) "✓ Sjøkartraster (Nautical)" else "Sjøkartraster (Nautical)") },
                        onClick = {
                            selectedLayer = MapLayer.SJOKARTRASTER
                            showLayerMenu = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(if (selectedLayer == MapLayer.OSM) "✓ OpenStreetMap" else "OpenStreetMap") },
                        onClick = {
                            selectedLayer = MapLayer.OSM
                            showLayerMenu = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(if (selectedLayer == MapLayer.OPENTOPOMAP) "✓ OpenTopoMap (Hiking)" else "OpenTopoMap (Hiking)") },
                        onClick = {
                            selectedLayer = MapLayer.OPENTOPOMAP
                            showLayerMenu = false
                        }
                    )
                }

                Spacer(modifier = Modifier.size(8.dp))

                SmallFloatingActionButton(
                    onClick = {
                        mapInstance?.let { map ->
                            val currentZoom = map.cameraPosition.zoom
                            map.animateCamera(
                                org.maplibre.android.camera.CameraUpdateFactory.zoomTo(currentZoom + 1)
                            )
                        }
                    },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "Zoom In")
                }

                Spacer(modifier = Modifier.size(8.dp))

                SmallFloatingActionButton(
                    onClick = {
                        mapInstance?.let { map ->
                            val currentZoom = map.cameraPosition.zoom
                            map.animateCamera(
                                org.maplibre.android.camera.CameraUpdateFactory.zoomTo(currentZoom - 1)
                            )
                        }
                    },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.Filled.Remove, contentDescription = "Zoom Out")
                }

                Spacer(modifier = Modifier.size(8.dp))

                // Record/Stop button
                SmallFloatingActionButton(
                    onClick = {
                        if (isRecording) {
                            // Set default name to current track name and show dialog
                            trackNameInput = currentTrack?.name ?: ""
                            showStopTrackDialog = true
                        } else {
                            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                            val trackName = dateFormat.format(Date())
                            trackRepository.startNewTrack(trackName)
                            LocationTrackingService.start(context)
                            scope.launch {
                                snackbarHostState.showSnackbar("Recording...")
                            }
                        }
                    },
                    modifier = Modifier.size(48.dp),
                    containerColor = if (isRecording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primaryContainer
                ) {
                    Icon(
                        if (isRecording) Icons.Filled.Stop else Icons.Filled.FiberManualRecord,
                        contentDescription = if (isRecording) "Stop Recording" else "Start Recording",
                        tint = if (isRecording) Color.White else Color.Red
                    )
                }

                Spacer(modifier = Modifier.size(8.dp))

                // Go to my location button
                SmallFloatingActionButton(
                    onClick = {
                        mapInstance?.let { map ->
                            val locationComponent = map.locationComponent
                            if (locationComponent.isLocationComponentEnabled) {
                                locationComponent.lastKnownLocation?.let { location ->
                                    map.animateCamera(
                                        org.maplibre.android.camera.CameraUpdateFactory.newLatLngZoom(
                                            LatLng(location.latitude, location.longitude),
                                            15.0
                                        )
                                    )
                                }
                            }
                        }
                    },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.Filled.MyLocation, contentDescription = "My Location")
                }

                Spacer(modifier = Modifier.size(8.dp))

                SmallFloatingActionButton(
                    onClick = {
                        rulerState = if (rulerState.isActive) {
                            rulerState.clear()
                        } else {
                            rulerState.copy(isActive = true)
                        }
                    },
                    modifier = Modifier.size(48.dp),
                    containerColor = if (rulerState.isActive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Icon(
                        Icons.Filled.Straighten,
                        contentDescription = "Ruler",
                        tint = if (rulerState.isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.size(8.dp))

                SmallFloatingActionButton(
                    onClick = onSettingsClick,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.Filled.Settings, contentDescription = "Settings")
                }
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues).fillMaxSize()) {
            MapLibreMapView(
                onMapReady = { mapInstance = it },
                selectedLayer = selectedLayer,
                hasLocationPermission = hasLocationPermission,
                showCountyBorders = showCountyBorders,
                currentTrack = currentTrack,
                viewingTrack = viewingTrack,
                savedCameraLat = savedCameraLat,
                savedCameraLon = savedCameraLon,
                savedCameraZoom = savedCameraZoom,
                rulerState = rulerState,
                onRulerPointAdded = { latLng ->
                    rulerState = rulerState.addPoint(latLng)
                }
            )

            if (rulerState.isActive) {
                Card(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(bottom = 80.dp)
                        .padding(start = 16.dp, end = 72.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            val totalDistance = rulerState.getTotalDistanceMeters()
                            Text(
                                text = if (rulerState.points.isEmpty()) "Tap to measure" else formatDistance(totalDistance),
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                            if (rulerState.points.size > 1) {
                                Text(
                                    text = "${rulerState.points.size} points",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            if (rulerState.points.size > 1) {
                                SmallFloatingActionButton(
                                    onClick = { rulerState = rulerState.removeLastPoint() },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        Icons.Filled.Undo,
                                        contentDescription = "Remove Last Point",
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                            SmallFloatingActionButton(
                                onClick = { rulerState = rulerState.clear() },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    Icons.Filled.Clear,
                                    contentDescription = "Clear All",
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }

            val viewing = viewingTrack
            if (viewing != null) {
                Card(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 16.dp)
                        .padding(horizontal = 16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Filled.Map,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = viewing.name,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { trackRepository.clearViewingTrack() }
                        ) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = "Close Track View"
                            )
                        }
                    }
                }
            }
        }
    }

    if (showStopTrackDialog) {
        AlertDialog(
            onDismissRequest = { showStopTrackDialog = false },
            title = { Text("Save Track") },
            text = {
                Column {
                    Text(
                        text = "Enter a name for your track:",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = trackNameInput,
                        onValueChange = { trackNameInput = it },
                        label = { Text("Track Name") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        onClick = {
                            trackRepository.discardRecording()
                            LocationTrackingService.stop(context)
                            scope.launch {
                                snackbarHostState.showSnackbar("Track discarded")
                            }
                            showStopTrackDialog = false
                            trackNameInput = ""
                        },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("Discard")
                    }
                    TextButton(
                        onClick = {
                            val currentTrackValue = currentTrack
                            if (currentTrackValue != null && trackNameInput.isNotBlank()) {
                                trackRepository.renameTrack(currentTrackValue, trackNameInput)
                            }
                            trackRepository.stopRecording()
                            LocationTrackingService.stop(context)
                            scope.launch {
                                snackbarHostState.showSnackbar("Recording stopped")
                            }
                            showStopTrackDialog = false
                            trackNameInput = ""
                        }
                    ) {
                        Text("Save")
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showStopTrackDialog = false
                        trackNameInput = ""
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

@SuppressWarnings("MissingPermission")
private fun enableLocationComponent(map: MapLibreMap, style: Style, context: Context, hasPermission: Boolean) {
    if (!hasPermission) return

    try {
        val locationComponent = map.locationComponent

        if (locationComponent.isLocationComponentActivated) {
            locationComponent.isLocationComponentEnabled = true
            locationComponent.renderMode = RenderMode.COMPASS
            if (locationComponent.lastKnownLocation == null && isEmulator()) {
                forceLocationOnEmulator(locationComponent)
            }
            return
        }

        locationComponent.activateLocationComponent(
            LocationComponentActivationOptions.builder(context, style)
                .useDefaultLocationEngine(true)
                .build()
        )

        locationComponent.isLocationComponentEnabled = true
        locationComponent.renderMode = RenderMode.COMPASS

        if (locationComponent.lastKnownLocation == null && isEmulator()) {
            forceLocationOnEmulator(locationComponent)
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

private fun isEmulator(): Boolean {
    return (Build.FINGERPRINT.startsWith("google/sdk_gphone")
            || Build.FINGERPRINT.startsWith("generic")
            || Build.FINGERPRINT.contains("unknown")
            || Build.MODEL.contains("google_sdk")
            || Build.MODEL.contains("Emulator")
            || Build.MODEL.contains("Android SDK built for")
            || Build.MANUFACTURER.contains("Genymotion")
            || Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic")
            || "google_sdk" == Build.PRODUCT)
}

@SuppressWarnings("MissingPermission")
private fun forceLocationOnEmulator(locationComponent: LocationComponent) {
    try {
        val mockLocation = Location("emulator_mock").apply {
            latitude = 59.9139
            longitude = 10.7522
            accuracy = 10f
            time = System.currentTimeMillis()
            elapsedRealtimeNanos = android.os.SystemClock.elapsedRealtimeNanos()
        }
        locationComponent.forceLocationUpdate(mockLocation)
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

private fun updateTrackOnMap(style: Style, track: Track?, isCurrentTrack: Boolean = true) {
    try {
        val sourceId = "track-source"
        val layerId = "track-layer"

        style.getLayer(layerId)?.let { style.removeLayer(it) }
        style.getSource(sourceId)?.let { style.removeSource(it) }

        if (track != null && track.points.size >= 2) {
            val points = track.points.map { Point.fromLngLat(it.latLng.longitude, it.latLng.latitude) }
            val lineString = LineString.fromLngLats(points)
            val feature = Feature.fromGeometry(lineString)

            val source = GeoJsonSource(sourceId, feature)
            style.addSource(source)

            val lineColor = if (isCurrentTrack) "#FF0000" else "#0000FF"

            val lineLayer = LineLayer(layerId, sourceId).withProperties(
                PropertyFactory.lineColor(lineColor),
                PropertyFactory.lineWidth(4f),
                PropertyFactory.lineOpacity(0.8f)
            )
            style.addLayer(lineLayer)
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

private fun updateRulerOnMap(style: Style, rulerState: RulerState) {
    try {
        val lineSourceId = "ruler-line-source"
        val lineLayerId = "ruler-line-layer"
        val pointSourceId = "ruler-point-source"
        val pointLayerId = "ruler-point-layer"

        style.getLayer(lineLayerId)?.let { style.removeLayer(it) }
        style.getSource(lineSourceId)?.let { style.removeSource(it) }
        style.getLayer(pointLayerId)?.let { style.removeLayer(it) }
        style.getSource(pointSourceId)?.let { style.removeSource(it) }

        if (rulerState.points.isNotEmpty()) {
            if (rulerState.points.size >= 2) {
                val points = rulerState.points.map {
                    Point.fromLngLat(it.latLng.longitude, it.latLng.latitude)
                }
                val lineString = LineString.fromLngLats(points)
                val lineFeature = Feature.fromGeometry(lineString)

                val lineSource = GeoJsonSource(lineSourceId, lineFeature)
                style.addSource(lineSource)

                val lineLayer = LineLayer(lineLayerId, lineSourceId).withProperties(
                    PropertyFactory.lineColor("#FFA500"),
                    PropertyFactory.lineWidth(3f),
                    PropertyFactory.lineOpacity(0.9f),
                    PropertyFactory.lineDasharray(arrayOf(2f, 2f))
                )
                style.addLayer(lineLayer)
            }

            val pointFeatures = rulerState.points.map { rulerPoint ->
                Feature.fromGeometry(Point.fromLngLat(
                    rulerPoint.latLng.longitude,
                    rulerPoint.latLng.latitude
                ))
            }
            val pointSource = GeoJsonSource(pointSourceId,
                com.google.gson.Gson().toJson(
                    mapOf("type" to "FeatureCollection", "features" to pointFeatures)
                )
            )
            style.addSource(pointSource)

            val pointLayer = org.maplibre.android.style.layers.CircleLayer(pointLayerId, pointSourceId).withProperties(
                org.maplibre.android.style.layers.PropertyFactory.circleRadius(6f),
                org.maplibre.android.style.layers.PropertyFactory.circleColor("#FFA500"),
                org.maplibre.android.style.layers.PropertyFactory.circleStrokeWidth(2f),
                org.maplibre.android.style.layers.PropertyFactory.circleStrokeColor("#FFFFFF")
            )
            style.addLayer(pointLayer)
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

private fun formatDistance(meters: Double): String {
    return when {
        meters < 1000 -> "%.0f m".format(meters)
        meters < 10000 -> "%.2f km".format(meters / 1000)
        else -> "%.1f km".format(meters / 1000)
    }
}

@Composable
fun MapLibreMapView(
    onMapReady: (MapLibreMap) -> Unit = {},
    selectedLayer: MapLayer = MapLayer.KARTVERKET,
    hasLocationPermission: Boolean = false,
    showCountyBorders: Boolean = true,
    currentTrack: Track? = null,
    viewingTrack: Track? = null,
    savedCameraLat: Double = 65.0,
    savedCameraLon: Double = 10.0,
    savedCameraZoom: Double = 5.0,
    rulerState: RulerState = RulerState(),
    onRulerPointAdded: (LatLng) -> Unit = {}
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    var mapView by remember { mutableStateOf<MapView?>(null) }
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    val context = LocalContext.current

    LaunchedEffect(selectedLayer, showCountyBorders, map) {
        map?.let { mapInstance ->
            try {
                val styleJson = MapStyle.getStyle(context, selectedLayer, showCountyBorders)
                val viewing = viewingTrack
                val current = currentTrack
                mapInstance.setStyle(Style.Builder().fromJson(styleJson), object : Style.OnStyleLoaded {
                    override fun onStyleLoaded(style: Style) {
                        enableLocationComponent(mapInstance, style, context, hasLocationPermission)
                        val trackToShow = current ?: viewing
                        updateTrackOnMap(style, trackToShow, isCurrentTrack = current != null)
                        updateRulerOnMap(style, rulerState)

                        if (viewing == null && current == null) {
                            mapInstance.cameraPosition = CameraPosition.Builder()
                                .target(LatLng(savedCameraLat, savedCameraLon))
                                .zoom(savedCameraZoom)
                                .build()
                        }
                    }
                })
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    LaunchedEffect(hasLocationPermission, map) {
        val mapInstance = map
        if (hasLocationPermission && mapInstance != null) {
            mapInstance.style?.let { style ->
                enableLocationComponent(mapInstance, style, context, hasLocationPermission)
            }
        }
    }

    LaunchedEffect(rulerState, map) {
        map?.style?.let { style ->
            updateRulerOnMap(style, rulerState)
        }
    }

    LaunchedEffect(rulerState.isActive, map) {
        map?.let { mapInstance ->
            mapInstance.addOnMapClickListener { point ->
                if (rulerState.isActive) {
                    onRulerPointAdded(point)
                    true
                } else {
                    false
                }
            }
        }
    }

    AndroidView(
        factory = { ctx ->
            MapView(ctx).also { mapView = it }.apply {
                onCreate(null)
                getMapAsync { mapInstance ->
                    map = mapInstance
                    onMapReady(mapInstance)

                    mapInstance.cameraPosition = CameraPosition.Builder()
                        .target(LatLng(savedCameraLat, savedCameraLon))
                        .zoom(savedCameraZoom)
                        .build()

                    mapInstance.addOnMapClickListener { point ->
                        if (rulerState.isActive) {
                            onRulerPointAdded(point)
                            true
                        } else {
                            false
                        }
                    }

                    try {
                        val styleJson = MapStyle.getStyle(ctx, selectedLayer)
                        val viewing = viewingTrack
                        val current = currentTrack
                        mapInstance.setStyle(Style.Builder().fromJson(styleJson), object : Style.OnStyleLoaded {
                            override fun onStyleLoaded(style: Style) {
                                enableLocationComponent(mapInstance, style, ctx, hasLocationPermission)
                                val trackToShow = current ?: viewing
                                updateTrackOnMap(style, trackToShow, isCurrentTrack = current != null)
                                updateRulerOnMap(style, rulerState)
                                mapInstance.triggerRepaint()
                            }
                        })
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        },
        modifier = Modifier.fillMaxSize()
    )

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            mapView?.let {
                when (event) {
                    Lifecycle.Event.ON_START -> it.onStart()
                    Lifecycle.Event.ON_RESUME -> it.onResume()
                    Lifecycle.Event.ON_PAUSE -> it.onPause()
                    Lifecycle.Event.ON_STOP -> it.onStop()
                    Lifecycle.Event.ON_DESTROY -> it.onDestroy()
                    else -> {}
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView?.onDestroy()
        }
    }
}
