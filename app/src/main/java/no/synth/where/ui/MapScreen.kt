package no.synth.where.ui

import android.Manifest
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import no.synth.where.data.PlaceSearchClient
import no.synth.where.data.RulerPoint
import no.synth.where.data.RulerState
import no.synth.where.service.LocationTrackingService
import no.synth.where.ui.map.MapDialogs
import no.synth.where.ui.map.MapLayer
import no.synth.where.ui.map.MapLibreMapView
import no.synth.where.ui.map.MapRenderUtils
import no.synth.where.ui.map.MapScreenContent
import no.synth.where.ui.map.RecordingCard
import no.synth.where.ui.map.RulerCard
import no.synth.where.ui.map.SearchOverlay
import no.synth.where.ui.map.ViewingPointBanner
import no.synth.where.ui.map.ViewingTrackBanner
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
    val viewModel: MapScreenViewModel = hiltViewModel()
    val savedPoints by viewModel.savedPoints.collectAsState()
    val isRecording by viewModel.isRecording.collectAsState()
    val currentTrack by viewModel.currentTrack.collectAsState()
    val viewingTrack by viewModel.viewingTrack.collectAsState()
    val onlineTrackingEnabled by viewModel.onlineTrackingEnabled.collectAsState()
    val rulerState by viewModel.rulerState.collectAsState()
    val showSearch by viewModel.showSearch.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()
    val showStopTrackDialog by viewModel.showStopTrackDialog.collectAsState()
    val trackNameInput by viewModel.trackNameInput.collectAsState()
    val isResolvingTrackName by viewModel.isResolvingTrackName.collectAsState()
    val showSavePointDialog by viewModel.showSavePointDialog.collectAsState()
    val savePointLatLng by viewModel.savePointLatLng.collectAsState()
    val savePointName by viewModel.savePointName.collectAsState()
    val isResolvingPointName by viewModel.isResolvingPointName.collectAsState()
    val clickedPoint by viewModel.clickedPoint.collectAsState()
    val showPointInfoDialog by viewModel.showPointInfoDialog.collectAsState()
    val showSaveRulerAsTrackDialog by viewModel.showSaveRulerAsTrackDialog.collectAsState()
    val rulerTrackName by viewModel.rulerTrackName.collectAsState()
    val isResolvingRulerName by viewModel.isResolvingRulerName.collectAsState()

    var mapInstance by remember { mutableStateOf<MapLibreMap?>(null) }
    var selectedLayer by remember { mutableStateOf(MapLayer.KARTVERKET) }
    var showLayerMenu by remember { mutableStateOf(false) }
    var showWaymarkedTrails by remember { mutableStateOf(false) }
    var hasLocationPermission by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

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
                Timber.e(e, "Map screen error")
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
        onSearchClick = { viewModel.openSearch() },
        onLayerMenuToggle = { showLayerMenu = it },
        onLayerSelected = { selectedLayer = it; showLayerMenu = false },
        onWaymarkedTrailsToggle = {
            showWaymarkedTrails = !showWaymarkedTrails; showLayerMenu = false
        },
        onCountyBordersToggle = {
            onShowCountyBordersChange(!showCountyBorders); showLayerMenu = false
        },
        onSavedPointsToggle = { onShowSavedPointsChange(!showSavedPoints); showLayerMenu = false },
        onRecordStopClick = {
            if (isRecording) {
                viewModel.openStopTrackDialog()
            } else {
                val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                val trackName = dateFormat.format(Date())
                viewModel.startRecording(trackName)
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
        onRulerToggle = { viewModel.toggleRuler() },
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
        onRulerUndo = { viewModel.removeLastRulerPoint() },
        onRulerClear = { viewModel.clearRuler() },
        onRulerSaveAsTrack = { viewModel.openSaveRulerAsTrackDialog() },
        onOnlineTrackingChange = { newValue ->
            viewModel.updateOnlineTracking(newValue)
            if (newValue) {
                LocationTrackingService.enableOnlineTracking(context)
                scope.launch { snackbarHostState.showSnackbar("Online tracking enabled") }
            } else {
                LocationTrackingService.disableOnlineTracking(context)
                scope.launch { snackbarHostState.showSnackbar("Online tracking disabled") }
            }
        },
        onCloseViewingTrack = { viewModel.clearViewingTrack() },
        onCloseViewingPoint = onClearViewingPoint,
        onSearchQueryChange = { viewModel.updateSearchQuery(it) },
        onSearchResultClick = { result ->
            mapInstance?.animateCamera(
                org.maplibre.android.camera.CameraUpdateFactory.newLatLngZoom(result.latLng, 14.0)
            )
            viewModel.onSearchResultClicked()
        },
        onSearchClose = { viewModel.closeSearch() },
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
                onRulerPointAdded = { latLng -> viewModel.addRulerPoint(latLng) },
                onLongPress = { latLng -> viewModel.openSavePointDialog(latLng) },
                onPointClick = { point -> viewModel.openPointInfoDialog(point) }
            )
        }
    )

    if (showStopTrackDialog) {
        MapDialogs.StopTrackDialog(
            trackNameInput = trackNameInput,
            onTrackNameChange = { viewModel.updateTrackNameInput(it) },
            isLoading = isResolvingTrackName,
            onDiscard = {
                viewModel.discardRecording()
                LocationTrackingService.stop(context)
                scope.launch {
                    snackbarHostState.showSnackbar("Track discarded")
                }
            },
            onSave = {
                viewModel.saveRecording()
                LocationTrackingService.stop(context)
                scope.launch {
                    snackbarHostState.showSnackbar("Track saved")
                }
            },
            onDismiss = { viewModel.dismissStopTrackDialog() }
        )
    }

    if (showSavePointDialog && savePointLatLng != null) {
        val latLng = savePointLatLng!!
        MapDialogs.SavePointDialog(
            pointName = savePointName,
            onPointNameChange = { viewModel.updateSavePointName(it) },
            isLoading = isResolvingPointName,
            coordinates = "${latLng.latitude.toString().take(10)}, ${
                latLng.longitude.toString().take(10)
            }",
            onSave = {
                viewModel.savePoint()
                scope.launch {
                    snackbarHostState.showSnackbar("Point saved")
                }
            },
            onDismiss = { viewModel.dismissSavePointDialog() }
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
                clickedPoint?.let { viewModel.deletePoint(it.id) }
                scope.launch {
                    snackbarHostState.showSnackbar("Point deleted")
                }
            },
            onSave = {
                clickedPoint?.let {
                    viewModel.updatePoint(it.id, editName, editDescription, editColor)
                }
                scope.launch {
                    snackbarHostState.showSnackbar("Point updated")
                }
            },
            onDismiss = { viewModel.dismissPointInfoDialog() }
        )
    }

    if (showSaveRulerAsTrackDialog) {
        MapDialogs.SaveRulerAsTrackDialog(
            trackName = rulerTrackName,
            rulerState = rulerState,
            onTrackNameChange = { viewModel.updateRulerTrackName(it) },
            isLoading = isResolvingRulerName,
            onSave = {
                val name = rulerTrackName
                viewModel.saveRulerAsTrack()
                scope.launch {
                    snackbarHostState.showSnackbar("Saved as track: $name")
                }
            },
            onDismiss = { viewModel.dismissSaveRulerAsTrackDialog() }
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
    PlaceSearchClient.SearchResult(
        "Trondheim lufthavn",
        "Flyplass",
        "Stjørdal",
        LatLng(63.46, 10.92)
    ),
    PlaceSearchClient.SearchResult("Trondheimsfjorden", "Fjord", "Trondheim", LatLng(63.50, 10.50)),
)

@Preview(showBackground = true)
@Composable
private fun SearchOverlayPreview() {
    MaterialTheme {
        SearchOverlay(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
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
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
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
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
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
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
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
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
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
