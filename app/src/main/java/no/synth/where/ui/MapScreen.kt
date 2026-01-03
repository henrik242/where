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

    // Update track line on map when current track, viewing track, or map instance changes
    LaunchedEffect(currentTrack, viewingTrack, mapInstance) {
        val viewing = viewingTrack
        val trackToShow = currentTrack ?: viewing
        val map = mapInstance

        map?.style?.let { style ->
            // Show current recording track or viewing track
            updateTrackOnMap(style, trackToShow, isCurrentTrack = currentTrack != null)

            // If viewing a track, center the map on it
            if (viewing != null && viewing.points.isNotEmpty()) {
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

    // Save camera position whenever it changes
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

    // Zoom to location when permission is granted (only once on startup)
    LaunchedEffect(hasLocationPermission, mapInstance) {
        val map = mapInstance
        android.util.Log.d("MapScreen", "Permission effect triggered - hasPermission: $hasLocationPermission, mapInstance: ${map != null}, hasZoomedToLocation: $hasZoomedToLocation")
        if (hasLocationPermission && map != null && !hasZoomedToLocation && viewingTrack == null && currentTrack == null) {
            android.util.Log.d("MapScreen", "Attempting to zoom to user location (first time)")
            try {
                val locationManager = context.getSystemService(android.content.Context.LOCATION_SERVICE) as android.location.LocationManager
                val lastKnownLocation = try {
                    val gps = locationManager.getLastKnownLocation(android.location.LocationManager.GPS_PROVIDER)
                    val network = locationManager.getLastKnownLocation(android.location.LocationManager.NETWORK_PROVIDER)
                    val fused = locationManager.getLastKnownLocation(android.location.LocationManager.FUSED_PROVIDER)
                    android.util.Log.d("MapScreen", "Locations - GPS: $gps, Network: $network, Fused: $fused")
                    gps ?: network ?: fused
                } catch (e: SecurityException) {
                    android.util.Log.e("MapScreen", "SecurityException", e)
                    null
                }

                lastKnownLocation?.let { location ->
                    android.util.Log.d("MapScreen", "Found location, zooming to: ${location.latitude}, ${location.longitude}")
                    // Give time for location component to initialize
                    kotlinx.coroutines.delay(500)
                    map.animateCamera(
                        org.maplibre.android.camera.CameraUpdateFactory.newLatLngZoom(
                            LatLng(location.latitude, location.longitude),
                            12.0
                        )
                    )
                    hasZoomedToLocation = true
                    android.util.Log.d("MapScreen", "Zoom completed, hasZoomedToLocation set to true")
                } ?: android.util.Log.d("MapScreen", "No last known location available")
            } catch (e: Exception) {
                android.util.Log.e("MapScreen", "Error in permission effect", e)
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End) {
                // Layer selector dropdown
                Box {
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
                            text = {
                                Text(if (selectedLayer == MapLayer.KARTVERKET) "✓ Kartverket" else "Kartverket")
                            },
                            onClick = {
                                selectedLayer = MapLayer.KARTVERKET
                                showLayerMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = {
                                Text(if (selectedLayer == MapLayer.TOPORASTER) "✓ Toporaster (Hiking)" else "Toporaster (Hiking)")
                            },
                            onClick = {
                                selectedLayer = MapLayer.TOPORASTER
                                showLayerMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = {
                                Text(if (selectedLayer == MapLayer.SJOKARTRASTER) "✓ Sjøkartraster (Nautical)" else "Sjøkartraster (Nautical)")
                            },
                            onClick = {
                                selectedLayer = MapLayer.SJOKARTRASTER
                                showLayerMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = {
                                Text(if (selectedLayer == MapLayer.OSM) "✓ OpenStreetMap" else "OpenStreetMap")
                            },
                            onClick = {
                                selectedLayer = MapLayer.OSM
                                showLayerMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = {
                                Text(if (selectedLayer == MapLayer.OPENTOPOMAP) "✓ OpenTopoMap (Hiking)" else "OpenTopoMap (Hiking)")
                            },
                            onClick = {
                                selectedLayer = MapLayer.OPENTOPOMAP
                                showLayerMenu = false
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.size(8.dp))

                // Zoom controls
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
                savedCameraZoom = savedCameraZoom
            )

            // Show track info and close button when viewing a track
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

    // Stop track dialog with rename option
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
                            // Discard the track without saving
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

        // Remove existing layer and source if present
        style.getLayer(layerId)?.let { style.removeLayer(it) }
        style.getSource(sourceId)?.let { style.removeSource(it) }

        if (track != null && track.points.size >= 2) {
            val points = track.points.map { Point.fromLngLat(it.latLng.longitude, it.latLng.latitude) }
            val lineString = LineString.fromLngLats(points)
            val feature = Feature.fromGeometry(lineString)

            val source = GeoJsonSource(sourceId, feature)
            style.addSource(source)

            // Use red for current recording track, blue for viewing track
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
    savedCameraZoom: Double = 5.0
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
                        android.util.Log.d("MapScreen", "LaunchedEffect style loaded")
                        enableLocationComponent(mapInstance, style, context, hasLocationPermission)
                        // Draw the track after style loads
                        val trackToShow = current ?: viewing
                        updateTrackOnMap(style, trackToShow, isCurrentTrack = current != null)

                        // Restore camera position from saved state
                        mapInstance.cameraPosition = CameraPosition.Builder()
                            .target(LatLng(savedCameraLat, savedCameraLon))
                            .zoom(savedCameraZoom)
                            .build()
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

    AndroidView(
        factory = { ctx ->
            MapView(ctx).also { mv ->
                mapView = mv
                mv.onCreate(null)

                mv.getMapAsync { mapInstance ->
                    map = mapInstance
                    onMapReady(mapInstance)

                    // Set initial camera position from saved state (or default to Norway)
                    mapInstance.cameraPosition = CameraPosition.Builder()
                        .target(LatLng(savedCameraLat, savedCameraLon))
                        .zoom(savedCameraZoom)
                        .build()

                    try {
                        val styleJson = MapStyle.getStyle(ctx, selectedLayer)
                        val viewing = viewingTrack
                        val current = currentTrack
                        mapInstance.setStyle(Style.Builder().fromJson(styleJson), object : Style.OnStyleLoaded {
                            override fun onStyleLoaded(style: Style) {
                                android.util.Log.d("MapScreen", "Style loaded - hasLocationPermission: $hasLocationPermission, viewing: ${viewing?.name}, current: ${current?.name}")
                                enableLocationComponent(mapInstance, style, ctx, hasLocationPermission)
                                // Draw the track after initial style loads
                                val trackToShow = current ?: viewing
                                updateTrackOnMap(style, trackToShow, isCurrentTrack = current != null)

                                // Zoom to current location if available and no viewing track
                                android.util.Log.d("MapScreen", "Checking zoom conditions - permission: $hasLocationPermission, viewing null: ${viewing == null}, current null: ${current == null}")
                                if (hasLocationPermission && viewing == null && current == null) {
                                    android.util.Log.d("MapScreen", "Attempting to get last known location")
                                    // Try to get location from system LocationManager
                                    try {
                                        val locationManager = ctx.getSystemService(android.content.Context.LOCATION_SERVICE) as android.location.LocationManager
                                        val lastKnownLocation = try {
                                            val gps = locationManager.getLastKnownLocation(android.location.LocationManager.GPS_PROVIDER)
                                            val network = locationManager.getLastKnownLocation(android.location.LocationManager.NETWORK_PROVIDER)
                                            val fused = locationManager.getLastKnownLocation(android.location.LocationManager.FUSED_PROVIDER)
                                            android.util.Log.d("MapScreen", "GPS: $gps, Network: $network, Fused: $fused")
                                            gps ?: network ?: fused
                                        } catch (e: SecurityException) {
                                            android.util.Log.e("MapScreen", "SecurityException getting location", e)
                                            null
                                        }

                                        lastKnownLocation?.let { location ->
                                            android.util.Log.d("MapScreen", "Zooming to location: ${location.latitude}, ${location.longitude}")
                                            // Use a small delay to ensure map is ready
                                            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                                                mapInstance.animateCamera(
                                                    org.maplibre.android.camera.CameraUpdateFactory.newLatLngZoom(
                                                        LatLng(location.latitude, location.longitude),
                                                        12.0
                                                    )
                                                )
                                            }, 300)
                                        } ?: android.util.Log.d("MapScreen", "No last known location available")
                                    } catch (e: Exception) {
                                        android.util.Log.e("MapScreen", "Error getting location", e)
                                    }
                                } else {
                                    android.util.Log.d("MapScreen", "Skipping zoom to location - conditions not met")
                                }

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

    // Manage Lifecycle
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            mapView?.let { mv ->
                when (event) {
                    Lifecycle.Event.ON_START -> mv.onStart()
                    Lifecycle.Event.ON_RESUME -> mv.onResume()
                    Lifecycle.Event.ON_PAUSE -> mv.onPause()
                    Lifecycle.Event.ON_STOP -> mv.onStop()
                    Lifecycle.Event.ON_DESTROY -> mv.onDestroy()
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
