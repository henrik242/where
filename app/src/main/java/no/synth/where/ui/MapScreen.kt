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
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.graphics.toColorInt
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import no.synth.where.data.GeocodingHelper
import no.synth.where.data.MapStyle
import no.synth.where.data.PlaceSearchClient
import no.synth.where.data.RulerPoint
import no.synth.where.data.RulerState
import no.synth.where.data.SavedPointsRepository
import no.synth.where.data.Track
import no.synth.where.data.TrackRepository
import no.synth.where.data.UserPreferences
import no.synth.where.service.LocationTrackingService
import no.synth.where.ui.map.MapDialogs
import no.synth.where.ui.map.MapLayer
import no.synth.where.ui.map.MapRenderUtils
import no.synth.where.util.NamingUtils
import no.synth.where.util.formatDistance
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


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
    var isResolvingTrackName by remember { mutableStateOf(false) }

    // Geocode location when stop dialog opens
    LaunchedEffect(showStopTrackDialog) {
        if (showStopTrackDialog) {
            val track = currentTrack
            if (track != null && trackNameInput.isBlank() && track.points.isNotEmpty()) {
                isResolvingTrackName = true
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
                isResolvingTrackName = false
            }
        }
    }
    var hasZoomedToLocation by rememberSaveable { mutableStateOf(false) }

    var showSavePointDialog by remember { mutableStateOf(false) }
    var savePointLatLng by remember { mutableStateOf<LatLng?>(null) }
    var savePointName by remember { mutableStateOf("") }
    var isResolvingPointName by remember { mutableStateOf(false) }

    LaunchedEffect(showSavePointDialog, savePointLatLng) {
        if (showSavePointDialog && savePointLatLng != null && savePointName.isBlank()) {
            isResolvingPointName = true
            val locationName = savePointLatLng?.let { GeocodingHelper.reverseGeocode(it) }
            if (locationName != null) {
                savePointName = NamingUtils.makeUnique(
                    locationName,
                    savedPointsRepository.savedPoints.map { it.name })
            }
            isResolvingPointName = false
        }
    }

    var clickedPoint by remember { mutableStateOf<no.synth.where.data.SavedPoint?>(null) }
    var showPointInfoDialog by remember { mutableStateOf(false) }

    var showSaveRulerAsTrackDialog by remember { mutableStateOf(false) }
    var rulerTrackName by remember { mutableStateOf("") }
    var isResolvingRulerName by remember { mutableStateOf(false) }

    // Save camera position across navigation
    var savedCameraLat by rememberSaveable { mutableStateOf(65.0) }
    var savedCameraLon by rememberSaveable { mutableStateOf(10.0) }
    var savedCameraZoom by rememberSaveable { mutableStateOf(5.0) }

    var rulerState by remember { mutableStateOf(RulerState()) }

    // Place search state
    var showSearch by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<PlaceSearchClient.SearchResult>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }

    // Debounced search
    LaunchedEffect(searchQuery) {
        if (searchQuery.length < 2) {
            searchResults = emptyList()
            isSearching = false
            return@LaunchedEffect
        }
        isSearching = true
        delay(300)
        searchResults = PlaceSearchClient.search(searchQuery)
        isSearching = false
    }

    LaunchedEffect(showSaveRulerAsTrackDialog) {
        if (showSaveRulerAsTrackDialog && rulerState.points.isNotEmpty()) {
            isResolvingRulerName = true
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
            isResolvingRulerName = false
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
            MapRenderUtils.updateTrackOnMap(
                style,
                trackToShow,
                isCurrentTrack = currentTrack != null
            )

            if (viewing != null && viewing.points.isNotEmpty()) {
                hasZoomedToLocation = true  // Prevent auto-zoom to location
                delay(100)
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
                    delay(500)
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
            delay(100)
            map.animateCamera(
                org.maplibre.android.camera.CameraUpdateFactory.newLatLngZoom(
                    point.latLng,
                    15.0
                )
            )
        }
    }

    MapScreenContent(
        snackbarHostState = snackbarHostState,
        isRecording = isRecording,
        rulerState = rulerState,
        showLayerMenu = showLayerMenu,
        selectedLayer = selectedLayer,
        showWaymarkedTrails = showWaymarkedTrails,
        showCountyBorders = showCountyBorders,
        showSavedPoints = showSavedPoints,
        onlineTrackingEnabled = onlineTrackingEnabled,
        recordingDistance = currentTrack?.getDistanceMeters(),
        viewingTrackName = viewingTrack?.name,
        viewingPointName = viewingPoint?.name,
        viewingPointColor = viewingPoint?.color ?: "#FF5722",
        showViewingPoint = viewingPoint != null,
        showSearch = showSearch,
        searchQuery = searchQuery,
        searchResults = searchResults,
        isSearching = isSearching,
        onSearchClick = { showSearch = true },
        onLayerMenuToggle = { showLayerMenu = it },
        onLayerSelected = { selectedLayer = it; showLayerMenu = false },
        onWaymarkedTrailsToggle = { showWaymarkedTrails = !showWaymarkedTrails; showLayerMenu = false },
        onCountyBordersToggle = { onShowCountyBordersChange(!showCountyBorders); showLayerMenu = false },
        onSavedPointsToggle = { onShowSavedPointsChange(!showSavedPoints); showLayerMenu = false },
        onRecordStopClick = {
            if (isRecording) {
                trackNameInput = ""
                showStopTrackDialog = true
            } else {
                val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                val trackName = dateFormat.format(Date())
                trackRepository.startNewTrack(trackName)
                LocationTrackingService.start(context)
                scope.launch { snackbarHostState.showSnackbar("Recording...") }
            }
        },
        onMyLocationClick = {
            mapInstance?.let { map ->
                val locationComponent = map.locationComponent
                if (locationComponent.isLocationComponentEnabled) {
                    locationComponent.lastKnownLocation?.let { location ->
                        map.animateCamera(
                            org.maplibre.android.camera.CameraUpdateFactory.newLatLngZoom(
                                LatLng(location.latitude, location.longitude), 15.0
                            )
                        )
                    }
                }
            }
        },
        onRulerToggle = {
            rulerState = if (rulerState.isActive) rulerState.clear() else rulerState.copy(isActive = true)
        },
        onSettingsClick = onSettingsClick,
        onZoomIn = {
            mapInstance?.let { map ->
                map.animateCamera(
                    org.maplibre.android.camera.CameraUpdateFactory.zoomTo(map.cameraPosition.zoom + 1)
                )
            }
        },
        onZoomOut = {
            mapInstance?.let { map ->
                map.animateCamera(
                    org.maplibre.android.camera.CameraUpdateFactory.zoomTo(map.cameraPosition.zoom - 1)
                )
            }
        },
        onRulerUndo = { rulerState = rulerState.removeLastPoint() },
        onRulerClear = { rulerState = rulerState.clear() },
        onRulerSaveAsTrack = { showSaveRulerAsTrackDialog = true },
        onOnlineTrackingChange = { newValue ->
            userPreferences.updateOnlineTrackingEnabled(newValue)
            onlineTrackingEnabled = newValue
            if (newValue) {
                LocationTrackingService.enableOnlineTracking(context)
                scope.launch { snackbarHostState.showSnackbar("Online tracking enabled") }
            } else {
                LocationTrackingService.disableOnlineTracking(context)
                scope.launch { snackbarHostState.showSnackbar("Online tracking disabled") }
            }
        },
        onCloseViewingTrack = { trackRepository.clearViewingTrack() },
        onCloseViewingPoint = onClearViewingPoint,
        onSearchQueryChange = { searchQuery = it },
        onSearchResultClick = { result ->
            mapInstance?.animateCamera(
                org.maplibre.android.camera.CameraUpdateFactory.newLatLngZoom(result.latLng, 14.0)
            )
            showSearch = false
            searchQuery = ""
            searchResults = emptyList()
        },
        onSearchClose = {
            showSearch = false
            searchQuery = ""
            searchResults = emptyList()
        },
        mapContent = {
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
        }
    )

    if (showStopTrackDialog) {
        MapDialogs.StopTrackDialog(
            trackNameInput = trackNameInput,
            onTrackNameChange = { trackNameInput = it },
            isLoading = isResolvingTrackName,
            onDiscard = {
                trackRepository.discardRecording()
                LocationTrackingService.stop(context)
                scope.launch {
                    snackbarHostState.showSnackbar("Track discarded")
                }
                showStopTrackDialog = false
                trackNameInput = ""
            },
            onSave = {
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
            },
            onDismiss = {
                showStopTrackDialog = false
                trackNameInput = ""
            }
        )
    }

    if (showSavePointDialog && savePointLatLng != null) {
        val latLng = savePointLatLng!!  // Non-null assertion since we checked above
        MapDialogs.SavePointDialog(
            pointName = savePointName,
            onPointNameChange = { savePointName = it },
            isLoading = isResolvingPointName,
            coordinates = "${latLng.latitude.toString().take(10)}, ${
                latLng.longitude.toString().take(10)
            }",
            onSave = {
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
            },
            onDismiss = {
                showSavePointDialog = false
                savePointName = ""
                savePointLatLng = null
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

        MapDialogs.PointInfoDialog(
            pointName = editName,
            pointDescription = editDescription,
            pointColor = editColor,
            coordinates = "${
                clickedPoint!!.latLng.latitude.toString().take(10)
            }, ${clickedPoint!!.latLng.longitude.toString().take(10)}",
            availableColors = colors,
            onNameChange = { editName = it },
            onDescriptionChange = { editDescription = it },
            onColorChange = { editColor = it },
            onDelete = {
                clickedPoint?.let {
                    savedPointsRepository.deletePoint(it.id)
                }
                showPointInfoDialog = false
                clickedPoint = null
                scope.launch {
                    snackbarHostState.showSnackbar("Point deleted")
                }
            },
            onSave = {
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
            },
            onDismiss = {
                showPointInfoDialog = false
                clickedPoint = null
            }
        )
    }

    if (showSaveRulerAsTrackDialog) {
        MapDialogs.SaveRulerAsTrackDialog(
            trackName = rulerTrackName,
            rulerState = rulerState,
            onTrackNameChange = { rulerTrackName = it },
            isLoading = isResolvingRulerName,
            onSave = {
                if (rulerTrackName.isNotBlank()) {
                    trackRepository.createTrackFromPoints(rulerTrackName, rulerState.points)
                    rulerState = rulerState.clear()
                    scope.launch {
                        snackbarHostState.showSnackbar("Saved as track: $rulerTrackName")
                    }
                }
                showSaveRulerAsTrackDialog = false
                rulerTrackName = ""
            },
            onDismiss = {
                showSaveRulerAsTrackDialog = false
                rulerTrackName = ""
            }
        )
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
                            MapRenderUtils.enableLocationComponent(
                                mapInstance,
                                style,
                                context,
                                hasLocationPermission
                            )
                            val trackToShow = current ?: viewing
                            MapRenderUtils.updateTrackOnMap(
                                style,
                                trackToShow,
                                isCurrentTrack = current != null
                            )
                            MapRenderUtils.updateRulerOnMap(style, rulerState)

                            if (showSavedPoints && savedPoints.isNotEmpty()) {
                                MapRenderUtils.updateSavedPointsOnMap(style, savedPoints)
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
                MapRenderUtils.enableLocationComponent(
                    mapInstance,
                    style,
                    context,
                    hasLocationPermission
                )
            }
        }
    }

    LaunchedEffect(rulerState, map) {
        map?.style?.let { style ->
            MapRenderUtils.updateRulerOnMap(style, rulerState)
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
                                MapRenderUtils.enableLocationComponent(
                                    mapInstance,
                                    style,
                                    context,
                                    hasLocationPermission
                                )
                                val trackToShow = current ?: viewing
                                MapRenderUtils.updateTrackOnMap(
                                    style,
                                    trackToShow,
                                    isCurrentTrack = current != null
                                )
                                MapRenderUtils.updateRulerOnMap(style, rulerState)

                                if (showSavedPoints && savedPoints.isNotEmpty()) {
                                    MapRenderUtils.updateSavedPointsOnMap(style, savedPoints)
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
                MapRenderUtils.updateSavedPointsOnMap(style, savedPoints)
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
                                    MapRenderUtils.enableLocationComponent(
                                        mapInstance,
                                        style,
                                        ctx,
                                        hasLocationPermission
                                    )
                                    val trackToShow = current ?: viewing
                                    MapRenderUtils.updateTrackOnMap(
                                        style,
                                        trackToShow,
                                        isCurrentTrack = current != null
                                    )
                                    MapRenderUtils.updateRulerOnMap(style, rulerState)
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

@Composable
fun SearchOverlay(
    modifier: Modifier = Modifier,
    query: String,
    onQueryChange: (String) -> Unit,
    isSearching: Boolean,
    results: List<PlaceSearchClient.SearchResult>,
    focusRequester: FocusRequester = remember { FocusRequester() },
    onResultClick: (PlaceSearchClient.SearchResult) -> Unit,
    onClose: () -> Unit
) {
    Column(modifier = modifier) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
            )
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester),
                    placeholder = { Text("Search places...") },
                    singleLine = true,
                    trailingIcon = {
                        if (isSearching) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                        } else if (query.isNotEmpty()) {
                            IconButton(onClick = { onQueryChange("") }) {
                                Icon(Icons.Filled.Clear, contentDescription = "Clear")
                            }
                        }
                    }
                )
                IconButton(onClick = onClose) {
                    Icon(Icons.Filled.Close, contentDescription = "Close Search")
                }
            }
        }

        if (results.isNotEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
                )
            ) {
                LazyColumn {
                    items(results) { result ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onResultClick(result) }
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                        ) {
                            Text(
                                text = result.name,
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                text = listOf(result.type, result.municipality)
                                    .filter { it.isNotBlank() }
                                    .joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (result != results.last()) {
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ZoomControls(
    modifier: Modifier = Modifier,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SmallFloatingActionButton(
            onClick = onZoomIn,
            modifier = Modifier.size(48.dp),
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Icon(Icons.Filled.Add, contentDescription = "Zoom In")
        }
        SmallFloatingActionButton(
            onClick = onZoomOut,
            modifier = Modifier.size(48.dp),
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Icon(Icons.Filled.Remove, contentDescription = "Zoom Out")
        }
    }
}

@Composable
fun RulerCard(
    modifier: Modifier = Modifier,
    rulerState: RulerState,
    onUndo: () -> Unit,
    onClear: () -> Unit,
    onSaveAsTrack: () -> Unit
) {
    Card(
        modifier = modifier,
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
                            onClick = onUndo,
                            modifier = Modifier.size(32.dp),
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.Undo,
                                contentDescription = "Remove Last Point",
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    SmallFloatingActionButton(
                        onClick = onClear,
                        modifier = Modifier.size(32.dp),
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
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
                    onClick = onSaveAsTrack,
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

@Composable
fun RecordingCard(
    modifier: Modifier = Modifier,
    distance: Double,
    onlineTrackingEnabled: Boolean,
    onOnlineTrackingChange: (Boolean) -> Unit
) {
    Card(
        modifier = modifier,
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
                    onCheckedChange = onOnlineTrackingChange
                )
            }
        }
    }
}

@Composable
fun ViewingTrackBanner(
    modifier: Modifier = Modifier,
    trackName: String,
    onClose: () -> Unit
) {
    Card(
        modifier = modifier,
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
                text = trackName,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onClose) {
                Icon(Icons.Filled.Close, contentDescription = "Close Track View")
            }
        }
    }
}

@Composable
fun ViewingPointBanner(
    modifier: Modifier = Modifier,
    pointName: String,
    pointColor: String,
    onClose: () -> Unit
) {
    Card(
        modifier = modifier,
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
                        color = Color(pointColor.toColorInt()),
                        shape = CircleShape
                    )
            )
            Text(
                text = pointName,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onClose) {
                Icon(Icons.Filled.Close, contentDescription = "Close Point View")
            }
        }
    }
}

@Composable
fun MapScreenContent(
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    // FAB state
    isRecording: Boolean,
    rulerState: RulerState,
    showLayerMenu: Boolean,
    selectedLayer: MapLayer,
    showWaymarkedTrails: Boolean,
    showCountyBorders: Boolean,
    showSavedPoints: Boolean,
    // Overlay state
    onlineTrackingEnabled: Boolean,
    recordingDistance: Double?,
    viewingTrackName: String?,
    viewingPointName: String?,
    viewingPointColor: String,
    showViewingPoint: Boolean,
    showSearch: Boolean,
    searchQuery: String,
    searchResults: List<PlaceSearchClient.SearchResult>,
    isSearching: Boolean,
    // FAB callbacks
    onSearchClick: () -> Unit,
    onLayerMenuToggle: (Boolean) -> Unit,
    onLayerSelected: (MapLayer) -> Unit,
    onWaymarkedTrailsToggle: () -> Unit,
    onCountyBordersToggle: () -> Unit,
    onSavedPointsToggle: () -> Unit,
    onRecordStopClick: () -> Unit,
    onMyLocationClick: () -> Unit,
    onRulerToggle: () -> Unit,
    onSettingsClick: () -> Unit,
    // Overlay callbacks
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onRulerUndo: () -> Unit,
    onRulerClear: () -> Unit,
    onRulerSaveAsTrack: () -> Unit,
    onOnlineTrackingChange: (Boolean) -> Unit,
    onCloseViewingTrack: () -> Unit,
    onCloseViewingPoint: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onSearchResultClick: (PlaceSearchClient.SearchResult) -> Unit,
    onSearchClose: () -> Unit,
    // Map slot
    mapContent: @Composable () -> Unit
) {
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            MapFabColumn(
                isRecording = isRecording,
                rulerActive = rulerState.isActive,
                showLayerMenu = showLayerMenu,
                selectedLayer = selectedLayer,
                showWaymarkedTrails = showWaymarkedTrails,
                showCountyBorders = showCountyBorders,
                showSavedPoints = showSavedPoints,
                onSearchClick = onSearchClick,
                onLayerMenuToggle = onLayerMenuToggle,
                onLayerSelected = onLayerSelected,
                onWaymarkedTrailsToggle = onWaymarkedTrailsToggle,
                onCountyBordersToggle = onCountyBordersToggle,
                onSavedPointsToggle = onSavedPointsToggle,
                onRecordStopClick = onRecordStopClick,
                onMyLocationClick = onMyLocationClick,
                onRulerToggle = onRulerToggle,
                onSettingsClick = onSettingsClick
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
        ) {
            mapContent()

            MapOverlays(
                rulerState = rulerState,
                isRecording = isRecording,
                recordingDistance = recordingDistance,
                onlineTrackingEnabled = onlineTrackingEnabled,
                viewingTrackName = viewingTrackName,
                viewingPointName = viewingPointName,
                viewingPointColor = viewingPointColor,
                showSearch = showSearch,
                searchQuery = searchQuery,
                searchResults = searchResults,
                isSearching = isSearching,
                showViewingPoint = showViewingPoint,
                onZoomIn = onZoomIn,
                onZoomOut = onZoomOut,
                onRulerUndo = onRulerUndo,
                onRulerClear = onRulerClear,
                onRulerSaveAsTrack = onRulerSaveAsTrack,
                onOnlineTrackingChange = onOnlineTrackingChange,
                onCloseViewingTrack = onCloseViewingTrack,
                onCloseViewingPoint = onCloseViewingPoint,
                onSearchQueryChange = onSearchQueryChange,
                onSearchResultClick = onSearchResultClick,
                onSearchClose = onSearchClose
            )
        }
    }
}

@Composable
fun MapFabColumn(
    isRecording: Boolean,
    rulerActive: Boolean,
    showLayerMenu: Boolean,
    selectedLayer: MapLayer,
    showWaymarkedTrails: Boolean,
    showCountyBorders: Boolean,
    showSavedPoints: Boolean,
    onSearchClick: () -> Unit,
    onLayerMenuToggle: (Boolean) -> Unit,
    onLayerSelected: (MapLayer) -> Unit,
    onWaymarkedTrailsToggle: () -> Unit,
    onCountyBordersToggle: () -> Unit,
    onSavedPointsToggle: () -> Unit,
    onRecordStopClick: () -> Unit,
    onMyLocationClick: () -> Unit,
    onRulerToggle: () -> Unit,
    onSettingsClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.End) {
        SmallFloatingActionButton(
            onClick = onSearchClick,
            modifier = Modifier.size(48.dp),
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Icon(Icons.Filled.Search, contentDescription = "Search Places")
        }

        Spacer(modifier = Modifier.size(8.dp))

        SmallFloatingActionButton(
            onClick = { onLayerMenuToggle(true) },
            modifier = Modifier.size(48.dp),
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Icon(Icons.Filled.Layers, contentDescription = "Layers & Overlays")
        }

        DropdownMenu(
            expanded = showLayerMenu,
            onDismissRequest = { onLayerMenuToggle(false) }
        ) {
            MenuSection("Map Layers")
            LayerMenuItem("Kartverket (Norway)", selectedLayer == MapLayer.KARTVERKET) { onLayerSelected(MapLayer.KARTVERKET) }
            LayerMenuItem("Kartverket toporaster", selectedLayer == MapLayer.TOPORASTER) { onLayerSelected(MapLayer.TOPORASTER) }
            LayerMenuItem("Kartverket sjøkart", selectedLayer == MapLayer.SJOKARTRASTER) { onLayerSelected(MapLayer.SJOKARTRASTER) }
            LayerMenuItem("OpenStreetMap", selectedLayer == MapLayer.OSM) { onLayerSelected(MapLayer.OSM) }
            LayerMenuItem("OpenTopoMap", selectedLayer == MapLayer.OPENTOPOMAP) { onLayerSelected(MapLayer.OPENTOPOMAP) }
            HorizontalDivider()
            MenuSection("Overlays")
            LayerMenuItem("Waymarked Trails (OSM)", showWaymarkedTrails) { onWaymarkedTrailsToggle() }
            LayerMenuItem("County Borders (Norway)", showCountyBorders) { onCountyBordersToggle() }
            LayerMenuItem("Saved Points", showSavedPoints) { onSavedPointsToggle() }
        }

        Spacer(modifier = Modifier.size(8.dp))

        SmallFloatingActionButton(
            onClick = onRecordStopClick,
            modifier = Modifier.size(48.dp),
            containerColor = if (isRecording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.surfaceVariant
        ) {
            Icon(
                if (isRecording) Icons.Filled.Stop else Icons.Filled.FiberManualRecord,
                contentDescription = if (isRecording) "Stop Recording" else "Start Recording",
                tint = if (isRecording) Color.White else Color.Red
            )
        }

        Spacer(modifier = Modifier.size(8.dp))

        SmallFloatingActionButton(
            onClick = onMyLocationClick,
            modifier = Modifier.size(48.dp),
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Icon(Icons.Filled.MyLocation, contentDescription = "My Location")
        }

        Spacer(modifier = Modifier.size(8.dp))

        SmallFloatingActionButton(
            onClick = onRulerToggle,
            modifier = Modifier.size(48.dp),
            containerColor = if (rulerActive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
        ) {
            Icon(
                Icons.Filled.Straighten,
                contentDescription = "Ruler",
                tint = if (rulerActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.size(8.dp))

        SmallFloatingActionButton(
            onClick = onSettingsClick,
            modifier = Modifier.size(48.dp),
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Icon(Icons.Filled.Settings, contentDescription = "Settings")
        }
    }
}

@Composable
fun BoxScope.MapOverlays(
    rulerState: RulerState,
    isRecording: Boolean,
    recordingDistance: Double?,
    onlineTrackingEnabled: Boolean,
    viewingTrackName: String?,
    viewingPointName: String?,
    viewingPointColor: String,
    showSearch: Boolean,
    searchQuery: String,
    searchResults: List<PlaceSearchClient.SearchResult>,
    isSearching: Boolean,
    showViewingPoint: Boolean,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onRulerUndo: () -> Unit,
    onRulerClear: () -> Unit,
    onRulerSaveAsTrack: () -> Unit,
    onOnlineTrackingChange: (Boolean) -> Unit,
    onCloseViewingTrack: () -> Unit,
    onCloseViewingPoint: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onSearchResultClick: (PlaceSearchClient.SearchResult) -> Unit,
    onSearchClose: () -> Unit
) {
    val hasTopOverlay = showSearch || viewingTrackName != null || (showViewingPoint && viewingPointName != null)

    if (!hasTopOverlay) {
        ZoomControls(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp),
            onZoomIn = onZoomIn,
            onZoomOut = onZoomOut
        )
    }

    if (rulerState.isActive || isRecording) {
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 16.dp, end = 80.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (rulerState.isActive) {
                RulerCard(
                    rulerState = rulerState,
                    onUndo = onRulerUndo,
                    onClear = onRulerClear,
                    onSaveAsTrack = onRulerSaveAsTrack
                )
            }
            if (isRecording && recordingDistance != null) {
                RecordingCard(
                    distance = recordingDistance,
                    onlineTrackingEnabled = onlineTrackingEnabled,
                    onOnlineTrackingChange = onOnlineTrackingChange
                )
            }
        }
    }

    if (viewingTrackName != null) {
        ViewingTrackBanner(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 16.dp)
                .padding(horizontal = 16.dp),
            trackName = viewingTrackName,
            onClose = onCloseViewingTrack
        )
    }

    if (showViewingPoint && viewingPointName != null) {
        ViewingPointBanner(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 16.dp)
                .padding(horizontal = 16.dp),
            pointName = viewingPointName,
            pointColor = viewingPointColor,
            onClose = onCloseViewingPoint
        )
    }

    if (showSearch) {
        val searchFocusRequester = remember { FocusRequester() }
        LaunchedEffect(Unit) {
            searchFocusRequester.requestFocus()
        }
        SearchOverlay(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 16.dp)
                .padding(start = 16.dp, end = 16.dp)
                .fillMaxWidth(),
            query = searchQuery,
            onQueryChange = onSearchQueryChange,
            isSearching = isSearching,
            results = searchResults,
            focusRequester = searchFocusRequester,
            onResultClick = onSearchResultClick,
            onClose = onSearchClose
        )
    }
}

// --- Previews ---

private val sampleRulerState = RulerState(
    points = listOf(
        RulerPoint(LatLng(63.43, 10.39)),
        RulerPoint(LatLng(63.44, 10.40)),
    ),
    isActive = true
)

private val sampleSearchResults = listOf(
    PlaceSearchClient.SearchResult("Trondheim", "By", "Trondheim", LatLng(63.43, 10.39)),
    PlaceSearchClient.SearchResult("Trondheim lufthavn", "Flyplass", "Stjørdal", LatLng(63.46, 10.92)),
    PlaceSearchClient.SearchResult("Trondheimsfjorden", "Fjord", "Trondheim", LatLng(63.50, 10.50)),
)

@Preview(showBackground = true)
@Composable
private fun SearchOverlayPreview() {
    MaterialTheme {
        SearchOverlay(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            query = "Trondheim",
            onQueryChange = {},
            isSearching = false,
            results = sampleSearchResults,
            onResultClick = {},
            onClose = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun RulerCardPreview() {
    MaterialTheme {
        RulerCard(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            rulerState = sampleRulerState,
            onUndo = {},
            onClear = {},
            onSaveAsTrack = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun RecordingCardPreview() {
    MaterialTheme {
        RecordingCard(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            distance = 2450.0,
            onlineTrackingEnabled = true,
            onOnlineTrackingChange = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ViewingTrackBannerPreview() {
    MaterialTheme {
        ViewingTrackBanner(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            trackName = "Bymarka → Lian",
            onClose = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ViewingPointBannerPreview() {
    MaterialTheme {
        ViewingPointBanner(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            pointName = "Utsikten",
            pointColor = "#4CAF50",
            onClose = {}
        )
    }
}

@Preview(showSystemUi = true)
@Composable
private fun MapScreenFullPreview() {
    MaterialTheme {
        MapScreenContent(
            isRecording = true,
            rulerState = sampleRulerState,
            showLayerMenu = false,
            selectedLayer = MapLayer.KARTVERKET,
            showWaymarkedTrails = false,
            showCountyBorders = false,
            showSavedPoints = true,
            onlineTrackingEnabled = false,
            recordingDistance = 2450.0,
            viewingTrackName = "Bymarka → Lian",
            viewingPointName = null,
            viewingPointColor = "#FF5722",
            showViewingPoint = false,
            showSearch = false,
            searchQuery = "",
            searchResults = emptyList(),
            isSearching = false,
            onSearchClick = {},
            onLayerMenuToggle = {},
            onLayerSelected = {},
            onWaymarkedTrailsToggle = {},
            onCountyBordersToggle = {},
            onSavedPointsToggle = {},
            onRecordStopClick = {},
            onMyLocationClick = {},
            onRulerToggle = {},
            onSettingsClick = {},
            onZoomIn = {},
            onZoomOut = {},
            onRulerUndo = {},
            onRulerClear = {},
            onRulerSaveAsTrack = {},
            onOnlineTrackingChange = {},
            onCloseViewingTrack = {},
            onCloseViewingPoint = {},
            onSearchQueryChange = {},
            onSearchResultClick = {},
            onSearchClose = {},
            mapContent = {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFFE0E0E0))
                ) {
                    Text(
                        text = "Map",
                        modifier = Modifier.align(Alignment.Center),
                        style = MaterialTheme.typography.headlineLarge,
                        color = Color.Gray
                    )
                }
            }
        )
    }
}
