package no.synth.where.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
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
import androidx.compose.runtime.mutableDoubleStateOf
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
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import no.synth.where.data.CrosshairInfo
import no.synth.where.data.PlaceSearchClient
import no.synth.where.data.TerrainClient
import no.synth.where.resources.Res
import no.synth.where.resources.*
import org.jetbrains.compose.resources.stringResource
import no.synth.where.data.RulerPoint
import no.synth.where.data.RulerState
import no.synth.where.data.geo.CoordFormat
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
import no.synth.where.data.geo.LatLng
import no.synth.where.data.geo.toCommon
import no.synth.where.data.geo.toMapLibre
import org.maplibre.android.maps.MapLibreMap
import no.synth.where.util.Logger
import no.synth.where.util.formatDateTime
import no.synth.where.util.currentTimeMillis

@Composable
fun MapScreen(
    onSettingsClick: () -> Unit,
    onOfflineSettingsClick: () -> Unit = {},
    onOnlineTrackingSettingsClick: () -> Unit = {},
    viewingPoint: no.synth.where.data.SavedPoint? = null,
    onClearViewingPoint: () -> Unit = {},
    regionsLoadedTrigger: Int = 0
) {
    val context = LocalContext.current
    val app = context.applicationContext as no.synth.where.WhereApplication
    val viewModel: MapScreenViewModel = viewModel { MapScreenViewModel(app.trackRepository, app.savedPointsRepository, app.userPreferences) }
    val savedPoints by viewModel.savedPoints.collectAsState()
    val isRecording by viewModel.isRecording.collectAsState()
    val currentTrack by viewModel.currentTrack.collectAsState()
    val viewingTrack by viewModel.viewingTrack.collectAsState()
    val onlineTrackingEnabled by viewModel.onlineTrackingEnabled.collectAsState()
    val hasSeenTrackingInfo by viewModel.userPreferences.hasSeenTrackingInfo.collectAsState()
    val offlineModeEnabled by viewModel.userPreferences.offlineModeEnabled.collectAsState()
    val showCountyBorders by viewModel.userPreferences.showCountyBorders.collectAsState()
    val showSavedPoints by viewModel.userPreferences.showSavedPoints.collectAsState()
    val rulerState by viewModel.rulerState.collectAsState()
    val showSearch by viewModel.showSearch.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()
    val searchHistory by viewModel.userPreferences.searchHistory.collectAsState()
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
    var isCompassVisible by remember { mutableStateOf(false) }
    var highlightedSearchResult by remember { mutableStateOf<PlaceSearchClient.SearchResult?>(null) }
    val selectedLayer by viewModel.userPreferences.selectedMapLayer.collectAsState()
    var showLayerMenu by remember { mutableStateOf(false) }
    val showWaymarkedTrails by viewModel.userPreferences.showWaymarkedTrails.collectAsState()
    val showAvalancheZones by viewModel.userPreferences.showAvalancheZones.collectAsState()
    val showHillshade by viewModel.userPreferences.showHillshade.collectAsState()
    var hasLocationPermission by remember { mutableStateOf(false) }
    var hasBackgroundLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    var showBackgroundLocationDisclosure by remember { mutableStateOf(false) }
    var pendingRecordStart by remember { mutableStateOf(false) }
    var showTrackingInfoDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val crosshairActive by viewModel.userPreferences.crosshairActive.collectAsState()
    var crosshairInfo by remember { mutableStateOf(CrosshairInfo()) }
    val coordFormat by viewModel.userPreferences.coordFormat.collectAsState()
    var centerLatLng by remember { mutableStateOf<LatLng?>(null) }

    var hasZoomedToLocation by rememberSaveable { mutableStateOf(false) }

    // Save camera position across navigation
    var savedCameraLat by rememberSaveable { mutableDoubleStateOf(65.0) }
    var savedCameraLon by rememberSaveable { mutableDoubleStateOf(10.0) }
    var savedCameraZoom by rememberSaveable { mutableDoubleStateOf(5.0) }

    // Pre-resolve string resources for use in lambdas
    val recordingMsg = stringResource(Res.string.recording_snackbar)
    val trackDiscardedMsg = stringResource(Res.string.track_discarded)
    val trackSavedMsg = stringResource(Res.string.track_saved)
    val pointSavedMsg = stringResource(Res.string.point_saved)
    val pointDeletedMsg = stringResource(Res.string.point_deleted)
    val pointUpdatedMsg = stringResource(Res.string.point_updated)
    val onlineEnabledMsg = stringResource(Res.string.online_tracking_enabled)
    val onlineDisabledMsg = stringResource(Res.string.online_tracking_disabled)

    val backgroundLocationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasBackgroundLocationPermission = granted
        if (granted && pendingRecordStart) {
            pendingRecordStart = false
            val trackName = formatDateTime(currentTimeMillis(), "yyyy-MM-dd HH:mm")
            viewModel.startRecording(trackName)
            LocationTrackingService.start(context)
            scope.launch { snackbarHostState.showSnackbar(recordingMsg) }
        } else {
            pendingRecordStart = false
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
                        .includes(points.map { it.toMapLibre() })
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
                centerLatLng = LatLng(target.latitude, target.longitude)
            }
            val bearing = map.cameraPosition.bearing
            isCompassVisible = when {
                bearing > 2.0 && bearing < 358.0 -> true
                bearing < 0.5 || bearing > 359.5 -> false
                else -> isCompassVisible
            }
        }
    }

    // Initialize center position when crosshair is activated
    LaunchedEffect(crosshairActive) {
        if (crosshairActive && centerLatLng == null) {
            mapInstance?.cameraPosition?.target?.let { target ->
                centerLatLng = LatLng(target.latitude, target.longitude)
            }
        }
    }

    // Debounced terrain info fetch when crosshair is active
    LaunchedEffect(crosshairActive, centerLatLng) {
        val latLng = centerLatLng ?: return@LaunchedEffect
        if (!crosshairActive) return@LaunchedEffect
        crosshairInfo = CrosshairInfo(isLoading = true)
        delay(500)
        val info = TerrainClient.getTerrainInfo(latLng)
        crosshairInfo = if (info != null) {
            CrosshairInfo(elevation = info.elevation, slopeDegrees = info.slopeDegrees)
        } else {
            CrosshairInfo()
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
                            LatLng(location.latitude, location.longitude).toMapLibre(),
                            12.0
                        )
                    )
                    hasZoomedToLocation = true
                }
            } catch (e: Exception) {
                Logger.e(e, "Map screen error")
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
                    point.latLng.toMapLibre(),
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
        showAvalancheZones = showAvalancheZones,
        showHillshade = showHillshade,
        crosshairActive = crosshairActive,
        crosshairInfo = crosshairInfo,
        centerLatLng = centerLatLng,
        coordFormat = coordFormat,
        onToggleCoordFormat = { viewModel.userPreferences.updateCoordFormat(coordFormat.next()) },
        onCrosshairToggle = { viewModel.userPreferences.updateCrosshairActive(!crosshairActive) },
        offlineModeEnabled = offlineModeEnabled,
        isCompassVisible = isCompassVisible,
        onlineTrackingEnabled = onlineTrackingEnabled,
        recordingDistance = currentTrack?.getDistanceMeters(),
        viewingTrackName = viewingTrack?.name,
        viewingPointName = viewingPoint?.name,
        viewingPointColor = viewingPoint?.color ?: "#FF5722",
        showViewingPoint = viewingPoint != null,
        showSearch = showSearch,
        searchQuery = searchQuery,
        searchResults = searchResults,
        searchHistory = searchHistory,
        isSearching = isSearching,
        onSearchClick = { viewModel.openSearch() },
        onLayerMenuToggle = { showLayerMenu = it },
        onLayerSelected = { viewModel.userPreferences.updateSelectedMapLayer(it); showLayerMenu = false },
        onWaymarkedTrailsToggle = {
            viewModel.userPreferences.updateShowWaymarkedTrails(!showWaymarkedTrails); showLayerMenu = false
        },
        onAvalancheZonesToggle = {
            viewModel.userPreferences.updateShowAvalancheZones(!showAvalancheZones); showLayerMenu = false
        },
        onHillshadeToggle = {
            viewModel.userPreferences.updateShowHillshade(!showHillshade); showLayerMenu = false
        },
        onCountyBordersToggle = {
            viewModel.userPreferences.updateShowCountyBorders(!showCountyBorders); showLayerMenu = false
        },
        onSavedPointsToggle = { viewModel.userPreferences.updateShowSavedPoints(!showSavedPoints); showLayerMenu = false },
        onRecordStopClick = {
            if (isRecording) {
                viewModel.openStopTrackDialog()
            } else if (!hasBackgroundLocationPermission) {
                showBackgroundLocationDisclosure = true
            } else {
                val trackName = formatDateTime(currentTimeMillis(), "yyyy-MM-dd HH:mm")
                viewModel.startRecording(trackName)
                LocationTrackingService.start(context)
                scope.launch { snackbarHostState.showSnackbar(recordingMsg) }
            }
        },
        onMyLocationClick = {
            mapInstance?.let { map ->
                try {
                    val locationComponent = map.locationComponent
                    if (locationComponent.isLocationComponentEnabled) {
                        locationComponent.lastKnownLocation?.let { location ->
                            map.animateCamera(
                                org.maplibre.android.camera.CameraUpdateFactory.newLatLngZoom(
                                    LatLng(location.latitude, location.longitude).toMapLibre(), 15.0
                                )
                            )
                        }
                    }
                } catch (_: Exception) {
                    Logger.w("Location component not initialized yet")
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
        onOfflineIndicatorClick = onOfflineSettingsClick,
        onOnlineTrackingClick = onOnlineTrackingSettingsClick,
        onOnlineTrackingChange = { newValue ->
            if (newValue && !hasSeenTrackingInfo) {
                showTrackingInfoDialog = true
            } else {
                viewModel.updateOnlineTracking(newValue)
                if (newValue) {
                    LocationTrackingService.enableOnlineTracking(context)
                    scope.launch { snackbarHostState.showSnackbar(onlineEnabledMsg) }
                } else {
                    LocationTrackingService.disableOnlineTracking(context)
                    scope.launch { snackbarHostState.showSnackbar(onlineDisabledMsg) }
                }
            }
        },
        onCloseViewingTrack = { viewModel.clearViewingTrack() },
        onCloseViewingPoint = onClearViewingPoint,
        onSearchQueryChange = { viewModel.updateSearchQuery(it) },
        onSearchResultClick = { result ->
            highlightedSearchResult = null
            mapInstance?.animateCamera(
                org.maplibre.android.camera.CameraUpdateFactory.newLatLngZoom(result.latLng.toMapLibre(), 14.0)
            )
            viewModel.userPreferences.addSearchHistoryEntry(result)
            viewModel.onSearchResultClicked()
        },
        onSearchResultHover = { result ->
            highlightedSearchResult = result
            if (result != null) {
                mapInstance?.animateCamera(
                    org.maplibre.android.camera.CameraUpdateFactory.newLatLng(result.latLng.toMapLibre())
                )
            }
        },
        onSearchClose = {
            highlightedSearchResult = null
            viewModel.closeSearch()
        },
        mapContent = {
            MapLibreMapView(
                onMapReady = { mapInstance = it },
                selectedLayer = selectedLayer,
                hasLocationPermission = hasLocationPermission,
                showCountyBorders = showCountyBorders,
                showWaymarkedTrails = showWaymarkedTrails,
                showAvalancheZones = showAvalancheZones,
                showHillshade = showHillshade,
                showSavedPoints = showSavedPoints,
                savedPoints = savedPoints,
                currentTrack = currentTrack,
                viewingTrack = viewingTrack,
                savedCameraLat = savedCameraLat,
                savedCameraLon = savedCameraLon,
                savedCameraZoom = savedCameraZoom,
                rulerState = rulerState,
                searchResults = searchResults,
                highlightedSearchResult = highlightedSearchResult,
                regionsLoadedTrigger = regionsLoadedTrigger,
                onRulerPointAdded = { latLng -> viewModel.addRulerPoint(latLng) },
                onLongPress = { latLng -> viewModel.openSavePointDialog(latLng) },
                onPointClick = { point -> viewModel.openPointInfoDialog(point) }
            )
        }
    )

    if (showTrackingInfoDialog) {
        MapDialogs.TrackingInfoDialog(
            onConfirm = {
                showTrackingInfoDialog = false
                viewModel.userPreferences.confirmTrackingInfoAndEnable()
                LocationTrackingService.enableOnlineTracking(context)
                scope.launch { snackbarHostState.showSnackbar(onlineEnabledMsg) }
            },
            onDismiss = { showTrackingInfoDialog = false }
        )
    }

    if (showStopTrackDialog) {
        MapDialogs.StopTrackDialog(
            trackNameInput = trackNameInput,
            onTrackNameChange = { viewModel.updateTrackNameInput(it) },
            isLoading = isResolvingTrackName,
            onDiscard = {
                viewModel.discardRecording()
                LocationTrackingService.stop(context)
                scope.launch {
                    snackbarHostState.showSnackbar(trackDiscardedMsg)
                }
            },
            onSave = {
                viewModel.saveRecording()
                LocationTrackingService.stop(context)
                scope.launch {
                    snackbarHostState.showSnackbar(trackSavedMsg)
                }
            },
            onDismiss = { viewModel.dismissStopTrackDialog() }
        )
    }

    if (showSavePointDialog && savePointLatLng != null) {
        val latLng = savePointLatLng ?: return
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
                    snackbarHostState.showSnackbar(pointSavedMsg)
                }
            },
            onDismiss = { viewModel.dismissSavePointDialog() }
        )
    }

    if (showPointInfoDialog && clickedPoint != null) {
        val point = clickedPoint ?: return
        var editName by remember { mutableStateOf(point.name) }
        var editDescription by remember { mutableStateOf(point.description ?: "") }
        var editColor by remember { mutableStateOf(point.color ?: "#FF5722") }

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
                point.latLng.latitude.toString().take(10)
            }, ${point.latLng.longitude.toString().take(10)}",
            availableColors = colors,
            onNameChange = { editName = it },
            onDescriptionChange = { editDescription = it },
            onColorChange = { editColor = it },
            onDelete = {
                clickedPoint?.let { viewModel.deletePoint(it.id) }
                scope.launch {
                    snackbarHostState.showSnackbar(pointDeletedMsg)
                }
            },
            onSave = {
                clickedPoint?.let {
                    viewModel.updatePoint(it.id, editName, editDescription, editColor)
                }
                scope.launch {
                    snackbarHostState.showSnackbar(pointUpdatedMsg)
                }
            },
            onDismiss = { viewModel.dismissPointInfoDialog() }
        )
    }

    if (showBackgroundLocationDisclosure) {
        MapDialogs.BackgroundLocationDisclosureDialog(
            onAllow = {
                showBackgroundLocationDisclosure = false
                pendingRecordStart = true
                backgroundLocationLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            },
            onDeny = {
                showBackgroundLocationDisclosure = false
            }
        )
    }

    if (showSaveRulerAsTrackDialog) {
        val savedAsTrackMsg = stringResource(Res.string.saved_as_track_name, rulerTrackName)
        MapDialogs.SaveRulerAsTrackDialog(
            trackName = rulerTrackName,
            rulerState = rulerState,
            onTrackNameChange = { viewModel.updateRulerTrackName(it) },
            isLoading = isResolvingRulerName,
            onSave = {
                viewModel.saveRulerAsTrack()
                scope.launch {
                    snackbarHostState.showSnackbar(savedAsTrackMsg)
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
            showAvalancheZones = false,
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
            onAvalancheZonesToggle = {},
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
