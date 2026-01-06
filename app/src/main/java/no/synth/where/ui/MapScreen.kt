package no.synth.where.ui

import android.Manifest
import android.content.Context
import android.location.Location
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.graphics.toColorInt
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.launch
import no.synth.where.data.GeocodingHelper
import no.synth.where.data.MapStyle
import no.synth.where.data.RulerState
import no.synth.where.data.SavedPointsRepository
import no.synth.where.data.Track
import no.synth.where.data.TrackRepository
import no.synth.where.data.UserPreferences
import no.synth.where.service.LocationTrackingService
import no.synth.where.util.DeviceUtils
import no.synth.where.util.NamingUtils
import no.synth.where.util.formatDistance
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
import java.util.Date
import java.util.Locale

enum class MapLayer {
    OSM,
    OPENTOPOMAP,
    KARTVERKET,
    TOPORASTER,
    SJOKARTRASTER
}

@Composable
private fun LayerMenuItem(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    DropdownMenuItem(
        text = { Text((if (isSelected) "✓ " else "") + text) },
        onClick = onClick
    )
}

@Composable
private fun MenuSection(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelSmall,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
fun MapScreen(
    onSettingsClick: () -> Unit,
    showCountyBorders: Boolean,
    onShowCountyBordersChange: (Boolean) -> Unit,
    showSavedPoints: Boolean,
    onShowSavedPointsChange: (Boolean) -> Unit,
    viewingPoint: no.synth.where.data.SavedPoint? = null,
    onClearViewingPoint: () -> Unit = {},
    regionsLoadedTrigger: Int = 0
) {
    val context = LocalContext.current
    val trackRepository = remember { TrackRepository.getInstance(context) }
    val savedPointsRepository = remember { SavedPointsRepository.getInstance(context) }
    val userPreferences = remember { UserPreferences.getInstance(context) }
    val savedPoints = savedPointsRepository.savedPoints
    val isRecording by trackRepository.isRecording
    val currentTrack by trackRepository.currentTrack.collectAsState()
    val viewingTrack by trackRepository.viewingTrack.collectAsState()
    var onlineTrackingEnabled by remember { mutableStateOf(userPreferences.onlineTrackingEnabled) }

    var mapInstance by remember { mutableStateOf<MapLibreMap?>(null) }
    var selectedLayer by remember { mutableStateOf(MapLayer.KARTVERKET) }
    var showLayerMenu by remember { mutableStateOf(false) }
    var showWaymarkedTrails by remember { mutableStateOf(false) }
    var hasLocationPermission by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var showStopTrackDialog by remember { mutableStateOf(false) }
    var trackNameInput by remember { mutableStateOf("") }

    // Geocode location when stop dialog opens
    LaunchedEffect(showStopTrackDialog) {
        if (showStopTrackDialog) {
            val track = currentTrack
            if (track != null && trackNameInput.isBlank() && track.points.isNotEmpty()) {
                // Get start and end locations
                val firstPoint = track.points.first()
                val lastPoint = track.points.last()

                val startName = GeocodingHelper.reverseGeocode(firstPoint.latLng)

                // Check if start and end are different (more than 100 meters apart)
                val distance = FloatArray(1)
                Location.distanceBetween(
                    firstPoint.latLng.latitude, firstPoint.latLng.longitude,
                    lastPoint.latLng.latitude, lastPoint.latLng.longitude,
                    distance
                )

                val baseName = if (distance[0] > 100 && startName != null) {
                    val endName = GeocodingHelper.reverseGeocode(lastPoint.latLng)
                    if (endName != null && startName != endName) {
                        "$startName → $endName"
                    } else {
                        startName
                    }
                } else {
                    startName
                }

                if (baseName != null) {
                    trackNameInput =
                        NamingUtils.makeUnique(baseName, trackRepository.tracks.map { it.name })
                }
            }
        }
    }
    var hasZoomedToLocation by rememberSaveable { mutableStateOf(false) }

    var showSavePointDialog by remember { mutableStateOf(false) }
    var savePointLatLng by remember { mutableStateOf<LatLng?>(null) }
    var savePointName by remember { mutableStateOf("") }

    LaunchedEffect(showSavePointDialog, savePointLatLng) {
        if (showSavePointDialog && savePointLatLng != null && savePointName.isBlank()) {
            val locationName = savePointLatLng?.let { GeocodingHelper.reverseGeocode(it) }
            if (locationName != null) {
                savePointName = NamingUtils.makeUnique(
                    locationName,
                    savedPointsRepository.savedPoints.map { it.name })
            }
        }
    }

    var clickedPoint by remember { mutableStateOf<no.synth.where.data.SavedPoint?>(null) }
    var showPointInfoDialog by remember { mutableStateOf(false) }

    var showSaveRulerAsTrackDialog by remember { mutableStateOf(false) }
    var rulerTrackName by remember { mutableStateOf("") }

    // Save camera position across navigation
    var savedCameraLat by rememberSaveable { mutableStateOf(65.0) }
    var savedCameraLon by rememberSaveable { mutableStateOf(10.0) }
    var savedCameraZoom by rememberSaveable { mutableStateOf(5.0) }

    var rulerState by remember { mutableStateOf(RulerState()) }

    LaunchedEffect(showSaveRulerAsTrackDialog) {
        if (showSaveRulerAsTrackDialog && rulerState.points.isNotEmpty()) {
            val firstPoint = rulerState.points.first()
            val lastPoint = rulerState.points.last()

            val startName = GeocodingHelper.reverseGeocode(firstPoint.latLng)

            val baseName = if (rulerState.points.size > 1) {
                val distance = FloatArray(1)
                Location.distanceBetween(
                    firstPoint.latLng.latitude, firstPoint.latLng.longitude,
                    lastPoint.latLng.latitude, lastPoint.latLng.longitude,
                    distance
                )

                if (distance[0] > 100 && startName != null) {
                    val endName = GeocodingHelper.reverseGeocode(lastPoint.latLng)
                    if (endName != null && startName != endName) {
                        "$startName → $endName"
                    } else {
                        startName
                    }
                } else {
                    startName
                }
            } else {
                startName
            }

            if (baseName != null) {
                rulerTrackName =
                    NamingUtils.makeUnique(baseName, trackRepository.tracks.map { it.name })
            }
        }
    }


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
                Manifest.permission.ACCESS_COARSE_LOCATION
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
                hasZoomedToLocation = true  // Prevent auto-zoom to location
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

    LaunchedEffect(hasLocationPermission, mapInstance, viewingTrack, currentTrack) {
        val map = mapInstance
        if (hasLocationPermission && map != null && !hasZoomedToLocation && viewingTrack == null && currentTrack == null) {
            try {
                val locationManager =
                    context.getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager
                val lastKnownLocation = try {
                    locationManager.getLastKnownLocation(android.location.LocationManager.GPS_PROVIDER)
                        ?: locationManager.getLastKnownLocation(android.location.LocationManager.NETWORK_PROVIDER)
                        ?: locationManager.getLastKnownLocation(android.location.LocationManager.FUSED_PROVIDER)
                } catch (_: SecurityException) {
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

    LaunchedEffect(viewingPoint, mapInstance) {
        val point = viewingPoint
        val map = mapInstance
        if (point != null && map != null) {
            kotlinx.coroutines.delay(100)
            map.animateCamera(
                org.maplibre.android.camera.CameraUpdateFactory.newLatLngZoom(
                    point.latLng,
                    15.0
                )
            )
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
                    Icon(Icons.Filled.Layers, contentDescription = "Layers & Overlays")
                }

                DropdownMenu(
                    expanded = showLayerMenu,
                    onDismissRequest = { showLayerMenu = false }
                ) {
                    MenuSection("Map Layers")

                    LayerMenuItem(
                        text = "Kartverket (Norway)",
                        isSelected = selectedLayer == MapLayer.KARTVERKET,
                        onClick = {
                            selectedLayer = MapLayer.KARTVERKET
                            showLayerMenu = false
                        }
                    )
                    LayerMenuItem(
                        text = "Kartverket toporaster",
                        isSelected = selectedLayer == MapLayer.TOPORASTER,
                        onClick = {
                            selectedLayer = MapLayer.TOPORASTER
                            showLayerMenu = false
                        }
                    )
                    LayerMenuItem(
                        text = "Kartverket sjøkart",
                        isSelected = selectedLayer == MapLayer.SJOKARTRASTER,
                        onClick = {
                            selectedLayer = MapLayer.SJOKARTRASTER
                            showLayerMenu = false
                        }
                    )
                    LayerMenuItem(
                        text = "OpenStreetMap",
                        isSelected = selectedLayer == MapLayer.OSM,
                        onClick = {
                            selectedLayer = MapLayer.OSM
                            showLayerMenu = false
                        }
                    )
                    LayerMenuItem(
                        text = "OpenTopoMap",
                        isSelected = selectedLayer == MapLayer.OPENTOPOMAP,
                        onClick = {
                            selectedLayer = MapLayer.OPENTOPOMAP
                            showLayerMenu = false
                        }
                    )

                    HorizontalDivider()
                    MenuSection("Overlays")

                    LayerMenuItem(
                        text = "Waymarked Trails (OSM)",
                        isSelected = showWaymarkedTrails,
                        onClick = {
                            showWaymarkedTrails = !showWaymarkedTrails
                            showLayerMenu = false
                        }
                    )
                    LayerMenuItem(
                        text = "County Borders (Norway)",
                        isSelected = showCountyBorders,
                        onClick = {
                            onShowCountyBordersChange(!showCountyBorders)
                            showLayerMenu = false
                        }
                    )
                    LayerMenuItem(
                        text = "Saved Points",
                        isSelected = showSavedPoints,
                        onClick = {
                            onShowSavedPointsChange(!showSavedPoints)
                            showLayerMenu = false
                        }
                    )
                }

                Spacer(modifier = Modifier.size(8.dp))


                // Record/Stop button
                SmallFloatingActionButton(
                    onClick = {
                        if (isRecording) {
                            // Clear name input so geocoding can pre-fill it
                            trackNameInput = ""
                            showStopTrackDialog = true
                        } else {
                            val dateFormat =
                                SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
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
        Box(modifier = Modifier
            .padding(paddingValues)
            .fillMaxSize()) {
            MapLibreMapView(
                onMapReady = { mapInstance = it },
                selectedLayer = selectedLayer,
                hasLocationPermission = hasLocationPermission,
                showCountyBorders = showCountyBorders,
                showWaymarkedTrails = showWaymarkedTrails,
                showSavedPoints = showSavedPoints,
                savedPoints = savedPoints,
                currentTrack = currentTrack,
                viewingTrack = viewingTrack,
                savedCameraLat = savedCameraLat,
                savedCameraLon = savedCameraLon,
                savedCameraZoom = savedCameraZoom,
                rulerState = rulerState,
                regionsLoadedTrigger = regionsLoadedTrigger,
                onRulerPointAdded = { latLng ->
                    rulerState = rulerState.addPoint(latLng)
                },
                onLongPress = { latLng ->
                    savePointLatLng = latLng
                    savePointName = ""
                    showSavePointDialog = true
                },
                onPointClick = { point ->
                    clickedPoint = point
                    showPointInfoDialog = true
                }
            )

            // Zoom controls in top-left corner
            Column(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
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
            }

            if (rulerState.isActive) {
                Card(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 16.dp, end = 80.dp, bottom = 16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
                    )
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                val totalDistance = rulerState.getTotalDistanceMeters()
                                Text(
                                    text = if (rulerState.points.isEmpty()) "Tap to measure" else totalDistance.formatDistance(),
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
                                            Icons.AutoMirrored.Filled.Undo,
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

                        if (rulerState.points.size >= 2) {
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedButton(
                                onClick = {
                                    showSaveRulerAsTrackDialog = true
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(
                                    Icons.Filled.Save,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Save as Track")
                            }
                        }
                    }
                }
            }

            currentTrack?.let { track ->
                if (isRecording) {
                    Card(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(start = 16.dp, end = 80.dp, bottom = 16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.95f)
                        )
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    Icons.Filled.FiberManualRecord,
                                    contentDescription = null,
                                    tint = Color.Red
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Recording",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                    val distance = track.getDistanceMeters()
                                    Text(
                                        text = distance.formatDistance(),
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                            }

                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 8.dp),
                                color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.2f)
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        Icons.Filled.CloudUpload,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onErrorContainer,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text(
                                        text = "Online Tracking",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                                Switch(
                                    checked = onlineTrackingEnabled,
                                    onCheckedChange = { newValue ->
                                        userPreferences.updateOnlineTrackingEnabled(newValue)
                                        onlineTrackingEnabled = newValue

                                        if (newValue) {
                                            LocationTrackingService.enableOnlineTracking(context)
                                            scope.launch {
                                                snackbarHostState.showSnackbar("Online tracking enabled")
                                            }
                                        } else {
                                            LocationTrackingService.disableOnlineTracking(context)
                                            scope.launch {
                                                snackbarHostState.showSnackbar("Online tracking disabled")
                                            }
                                        }
                                    }
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

            viewingPoint?.let { point ->
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
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .background(
                                    color = Color((point.color ?: "#FF5722").toColorInt()),
                                    shape = CircleShape
                                )
                        )
                        Text(
                            text = point.name,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = onClearViewingPoint
                        ) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = "Close Point View"
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
                                snackbarHostState.showSnackbar("Track saved")
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

    if (showSavePointDialog && savePointLatLng != null) {
        AlertDialog(
            onDismissRequest = { showSavePointDialog = false },
            title = { Text("Save Point") },
            text = {
                Column {
                    Text(
                        text = "Enter a name for this point:",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = savePointName,
                        onValueChange = { savePointName = it },
                        label = { Text("Point Name") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (savePointName.isNotBlank()) {
                            savePointLatLng?.let {
                                savedPointsRepository.addPoint(
                                    name = savePointName,
                                    latLng = it
                                )
                            }
                            scope.launch {
                                snackbarHostState.showSnackbar("Point saved")
                            }
                        }
                        showSavePointDialog = false
                        savePointName = ""
                        savePointLatLng = null
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showSavePointDialog = false
                        savePointName = ""
                        savePointLatLng = null
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showPointInfoDialog && clickedPoint != null) {
        var editName by remember { mutableStateOf(clickedPoint!!.name) }
        var editDescription by remember { mutableStateOf(clickedPoint!!.description ?: "") }
        var editColor by remember { mutableStateOf(clickedPoint!!.color ?: "#FF5722") }

        val colors = listOf(
            "#FF5722" to "Red",
            "#2196F3" to "Blue",
            "#4CAF50" to "Green",
            "#FFC107" to "Yellow",
            "#9C27B0" to "Purple",
            "#FF9800" to "Orange",
            "#00BCD4" to "Cyan",
            "#E91E63" to "Pink"
        )

        AlertDialog(
            onDismissRequest = { showPointInfoDialog = false },
            title = { Text("Edit Point") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = editName,
                        onValueChange = { editName = it },
                        label = { Text("Name") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = editDescription,
                        onValueChange = { editDescription = it },
                        label = { Text("Description (optional)") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text("Color", style = MaterialTheme.typography.labelMedium)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        colors.forEach { (colorHex, _) ->
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(
                                        color = Color(colorHex.toColorInt()),
                                        shape = CircleShape
                                    )
                                    .clickable { editColor = colorHex }
                                    .then(
                                        if (editColor == colorHex) {
                                            Modifier.padding(4.dp)
                                        } else Modifier
                                    )
                            )
                        }
                    }

                    Text(
                        text = "${
                            clickedPoint!!.latLng.latitude.toString().take(10)
                        }, ${clickedPoint!!.latLng.longitude.toString().take(10)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        onClick = {
                            clickedPoint?.let {
                                savedPointsRepository.deletePoint(it.id)
                            }
                            showPointInfoDialog = false
                            clickedPoint = null
                            scope.launch {
                                snackbarHostState.showSnackbar("Point deleted")
                            }
                        },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("Delete")
                    }
                    TextButton(
                        onClick = {
                            if (editName.isNotBlank()) {
                                clickedPoint?.let {
                                    savedPointsRepository.updatePoint(
                                        it.id,
                                        editName,
                                        editDescription,
                                        editColor
                                    )
                                }
                                scope.launch {
                                    snackbarHostState.showSnackbar("Point updated")
                                }
                            }
                            showPointInfoDialog = false
                            clickedPoint = null
                        }
                    ) {
                        Text("Save")
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showPointInfoDialog = false
                        clickedPoint = null
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showSaveRulerAsTrackDialog) {
        AlertDialog(
            onDismissRequest = { showSaveRulerAsTrackDialog = false },
            title = { Text("Save Route as Track") },
            text = {
                Column {
                    Text(
                        text = "Enter a name for this route:",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = rulerTrackName,
                        onValueChange = { rulerTrackName = it },
                        label = { Text("Track Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "${rulerState.points.size} points • ${
                            rulerState.getTotalDistanceMeters().formatDistance()
                        }",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (rulerTrackName.isNotBlank()) {
                            trackRepository.createTrackFromPoints(rulerTrackName, rulerState.points)
                            rulerState = rulerState.clear()
                            scope.launch {
                                snackbarHostState.showSnackbar("Saved as track: $rulerTrackName")
                            }
                        }
                        showSaveRulerAsTrackDialog = false
                        rulerTrackName = ""
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showSaveRulerAsTrackDialog = false
                        rulerTrackName = ""
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

@SuppressWarnings("MissingPermission")
private fun enableLocationComponent(
    map: MapLibreMap,
    style: Style,
    context: Context,
    hasPermission: Boolean
) {
    if (!hasPermission) return

    try {
        val locationComponent = map.locationComponent

        if (locationComponent.isLocationComponentActivated) {
            locationComponent.isLocationComponentEnabled = true
            locationComponent.renderMode = RenderMode.COMPASS
            if (locationComponent.lastKnownLocation == null && DeviceUtils.isEmulator()) {
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

        if (locationComponent.lastKnownLocation == null && DeviceUtils.isEmulator()) {
            forceLocationOnEmulator(locationComponent)
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
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
            val points =
                track.points.map { Point.fromLngLat(it.latLng.longitude, it.latLng.latitude) }
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
                Feature.fromGeometry(
                    Point.fromLngLat(
                        rulerPoint.latLng.longitude,
                        rulerPoint.latLng.latitude
                    )
                )
            }
            val pointSource = GeoJsonSource(
                pointSourceId,
                com.google.gson.Gson().toJson(
                    mapOf("type" to "FeatureCollection", "features" to pointFeatures)
                )
            )
            style.addSource(pointSource)

            val pointLayer =
                org.maplibre.android.style.layers.CircleLayer(pointLayerId, pointSourceId)
                    .withProperties(
                        PropertyFactory.circleRadius(6f),
                        PropertyFactory.circleColor("#FFA500"),
                        PropertyFactory.circleStrokeWidth(2f),
                        PropertyFactory.circleStrokeColor("#FFFFFF")
                    )
            style.addLayer(pointLayer)
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

private fun updateSavedPointsOnMap(
    style: Style,
    savedPoints: List<no.synth.where.data.SavedPoint>
) {
    try {
        val sourceId = "saved-points-source"
        val layerId = "saved-points-layer"

        style.getLayer(layerId)?.let { style.removeLayer(it) }
        style.getSource(sourceId)?.let { style.removeSource(it) }

        if (savedPoints.isNotEmpty()) {
            val features = savedPoints.map { point ->
                Feature.fromGeometry(
                    Point.fromLngLat(point.latLng.longitude, point.latLng.latitude)
                ).apply {
                    addStringProperty("name", point.name)
                    addStringProperty("color", point.color ?: "#FF5722")
                }
            }

            val source = GeoJsonSource(
                sourceId,
                com.google.gson.Gson().toJson(
                    mapOf("type" to "FeatureCollection", "features" to features)
                )
            )
            style.addSource(source)

            val circleLayer = org.maplibre.android.style.layers.CircleLayer(layerId, sourceId)
                .withProperties(
                    PropertyFactory.circlePitchAlignment("viewport"),
                    PropertyFactory.circleRadius(6f),
                    PropertyFactory.circleColor(
                        org.maplibre.android.style.expressions.Expression.get("color")
                    ),
                    PropertyFactory.circleStrokeWidth(2f),
                    PropertyFactory.circleStrokeColor("#FFFFFF")
                )
            style.addLayer(circleLayer)
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
    showWaymarkedTrails: Boolean = false,
    showSavedPoints: Boolean = true,
    savedPoints: List<no.synth.where.data.SavedPoint> = emptyList(),
    currentTrack: Track? = null,
    viewingTrack: Track? = null,
    savedCameraLat: Double = 65.0,
    savedCameraLon: Double = 10.0,
    savedCameraZoom: Double = 5.0,
    rulerState: RulerState = RulerState(),
    regionsLoadedTrigger: Int = 0,
    onRulerPointAdded: (LatLng) -> Unit = {},
    onLongPress: (LatLng) -> Unit = {},
    onPointClick: (no.synth.where.data.SavedPoint) -> Unit = {}
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    var mapView by remember { mutableStateOf<MapView?>(null) }
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    val context = LocalContext.current
    var isOnline by remember { mutableStateOf(true) }
    var wasInitialized by remember { mutableStateOf(false) }

    DisposableEffect(context) {
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        val networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                isOnline = true
            }

            override fun onLost(network: Network) {
                val activeNetwork = connectivityManager.activeNetwork
                isOnline = activeNetwork != null
            }
        }

        val networkRequest = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        connectivityManager.registerNetworkCallback(networkRequest, networkCallback)

        val activeNetwork = connectivityManager.activeNetwork
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
        isOnline = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true

        onDispose {
            connectivityManager.unregisterNetworkCallback(networkCallback)
        }
    }

    // Track click listeners to replace them when ruler state changes
    var clickListener by remember { mutableStateOf<MapLibreMap.OnMapClickListener?>(null) }
    var longClickListener by remember { mutableStateOf<MapLibreMap.OnMapLongClickListener?>(null) }

    LaunchedEffect(
        selectedLayer,
        showCountyBorders,
        showWaymarkedTrails,
        showSavedPoints,
        savedPoints.size,
        isOnline,
        regionsLoadedTrigger,
        map
    ) {
        map?.let { mapInstance ->
            try {
                val styleJson = MapStyle.getStyle(
                    context,
                    selectedLayer,
                    showCountyBorders,
                    showWaymarkedTrails
                )
                val viewing = viewingTrack
                val current = currentTrack
                mapInstance.setStyle(
                    Style.Builder().fromJson(styleJson),
                    object : Style.OnStyleLoaded {
                        override fun onStyleLoaded(style: Style) {
                            enableLocationComponent(
                                mapInstance,
                                style,
                                context,
                                hasLocationPermission
                            )
                            val trackToShow = current ?: viewing
                            updateTrackOnMap(style, trackToShow, isCurrentTrack = current != null)
                            updateRulerOnMap(style, rulerState)

                            if (showSavedPoints && savedPoints.isNotEmpty()) {
                                updateSavedPointsOnMap(style, savedPoints)
                            }

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

    LaunchedEffect(isOnline, map) {
        if (wasInitialized && isOnline && map != null) {

            map?.let { mapInstance ->
                val styleJson = MapStyle.getStyle(
                    context,
                    selectedLayer,
                    showCountyBorders,
                    showWaymarkedTrails
                )
                val viewing = viewingTrack
                val current = currentTrack

                try {
                    mapInstance.setStyle(
                        Style.Builder().fromJson(styleJson),
                        object : Style.OnStyleLoaded {
                            override fun onStyleLoaded(style: Style) {
                                enableLocationComponent(
                                    mapInstance,
                                    style,
                                    context,
                                    hasLocationPermission
                                )
                                val trackToShow = current ?: viewing
                                updateTrackOnMap(
                                    style,
                                    trackToShow,
                                    isCurrentTrack = current != null
                                )
                                updateRulerOnMap(style, rulerState)

                                if (showSavedPoints && savedPoints.isNotEmpty()) {
                                    updateSavedPointsOnMap(style, savedPoints)
                                }
                            }
                        })
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        if (map != null) {
            wasInitialized = true
        }
    }

    // Update saved points on map when they change (including color changes)
    LaunchedEffect(savedPoints.toList(), showSavedPoints) {
        map?.getStyle { style ->
            if (showSavedPoints) {
                updateSavedPointsOnMap(style, savedPoints)
            } else {
                // Remove saved points layer when hidden
                try {
                    style.getLayer("saved-points-layer")?.let { style.removeLayer(it) }
                    style.getSource("saved-points-source")?.let { style.removeSource(it) }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    LaunchedEffect(rulerState.isActive, savedPoints.size, map) {
        map?.let { mapInstance ->
            // Remove old listeners if they exist
            clickListener?.let { mapInstance.removeOnMapClickListener(it) }
            longClickListener?.let { mapInstance.removeOnMapLongClickListener(it) }

            // Create and add new click listener
            val newClickListener = MapLibreMap.OnMapClickListener { point ->
                if (rulerState.isActive) {
                    // When ruler is active, add ruler points and ignore saved point clicks
                    onRulerPointAdded(point)
                    true
                } else {
                    // Check if a saved point was clicked (only when ruler is NOT active)
                    val clickedSavedPoint = savedPoints.minByOrNull { savedPoint ->
                        val distance = FloatArray(1)
                        Location.distanceBetween(
                            point.latitude, point.longitude,
                            savedPoint.latLng.latitude, savedPoint.latLng.longitude,
                            distance
                        )
                        distance[0]
                    }?.let { closestPoint ->
                        val distance = FloatArray(1)
                        Location.distanceBetween(
                            point.latitude, point.longitude,
                            closestPoint.latLng.latitude, closestPoint.latLng.longitude,
                            distance
                        )
                        if (distance[0] < 500) closestPoint else null
                    }

                    if (clickedSavedPoint != null) {
                        onPointClick(clickedSavedPoint)
                        true
                    } else {
                        false
                    }
                }
            }
            mapInstance.addOnMapClickListener(newClickListener)
            clickListener = newClickListener

            // Create and add new long click listener
            val newLongClickListener = MapLibreMap.OnMapLongClickListener { point ->
                if (!rulerState.isActive) {
                    onLongPress(point)
                    true
                } else {
                    false
                }
            }
            mapInstance.addOnMapLongClickListener(newLongClickListener)
            longClickListener = newLongClickListener
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

                    // Click listeners are handled by LaunchedEffect above
                    // Don't add any click listeners here to avoid conflicts

                    try {
                        val styleJson = MapStyle.getStyle(
                            ctx,
                            selectedLayer,
                            showCountyBorders,
                            showWaymarkedTrails
                        )
                        val viewing = viewingTrack
                        val current = currentTrack
                        mapInstance.setStyle(
                            Style.Builder().fromJson(styleJson),
                            object : Style.OnStyleLoaded {
                                override fun onStyleLoaded(style: Style) {
                                    enableLocationComponent(
                                        mapInstance,
                                        style,
                                        ctx,
                                        hasLocationPermission
                                    )
                                    val trackToShow = current ?: viewing
                                    updateTrackOnMap(
                                        style,
                                        trackToShow,
                                        isCurrentTrack = current != null
                                    )
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
