package no.synth.where.ui.map

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitView
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import no.synth.where.BuildInfo
import no.synth.where.data.ClientIdManager
import no.synth.where.data.GeocodingHelper
import no.synth.where.data.MapStyle
import no.synth.where.data.OnlineTrackingClient
import no.synth.where.data.PlatformFile
import no.synth.where.data.PlaceSearchClient
import no.synth.where.data.RegionsRepository
import no.synth.where.data.RulerState
import no.synth.where.data.SavedPoint
import no.synth.where.data.SavedPointUtils
import no.synth.where.data.SavedPointsRepository
import no.synth.where.data.TrackRepository
import no.synth.where.data.UserPreferences
import no.synth.where.data.geo.LatLng
import no.synth.where.location.IosLocationTracker
import no.synth.where.resources.Res
import no.synth.where.resources.location_permission_required
import no.synth.where.resources.online_tracking_disabled
import no.synth.where.resources.online_tracking_enabled
import no.synth.where.resources.point_deleted
import no.synth.where.resources.point_saved
import no.synth.where.resources.point_updated
import no.synth.where.resources.recording_snackbar
import no.synth.where.resources.saved_as_track_name
import no.synth.where.resources.track_discarded
import no.synth.where.resources.track_saved
import no.synth.where.resources.unnamed_point
import no.synth.where.util.NamingUtils
import org.jetbrains.compose.resources.stringResource
import org.koin.mp.KoinPlatform.getKoin
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask

@OptIn(FlowPreview::class)
@Composable
fun IosMapScreen(
    mapViewProvider: MapViewProvider,
    selectedLayer: MapLayer = MapLayer.KARTVERKET,
    showWaymarkedTrails: Boolean = false,
    showCountyBorders: Boolean = false,
    viewingPoint: SavedPoint? = null,
    onClearViewingPoint: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    onOfflineIndicatorClick: () -> Unit = {},
    onOnlineTrackingClick: () -> Unit = {}
) {
    val koin = remember { getKoin() }
    val trackRepository = remember { koin.get<TrackRepository>() }
    val savedPointsRepository = remember { koin.get<SavedPointsRepository>() }
    val userPreferences = remember { koin.get<UserPreferences>() }
    val clientIdManager = remember { koin.get<ClientIdManager>() }
    val locationTracker = remember { IosLocationTracker(trackRepository) }

    var showLayerMenu by remember { mutableStateOf(false) }
    var currentLayer by remember { mutableStateOf(selectedLayer) }
    var waymarkedTrails by remember { mutableStateOf(showWaymarkedTrails) }
    var countyBorders by remember { mutableStateOf(showCountyBorders) }
    var showSavedPoints by remember { mutableStateOf(true) }
    val snackbarHostState = remember { SnackbarHostState() }
    var rulerState by remember { mutableStateOf(RulerState()) }
    val scope = rememberCoroutineScope()

    val isRecording by trackRepository.isRecording.collectAsState()
    val currentTrack by trackRepository.currentTrack.collectAsState()
    val viewingTrack by trackRepository.viewingTrack.collectAsState()
    val savedPoints by savedPointsRepository.savedPoints.collectAsState()
    val onlineTrackingEnabled by userPreferences.onlineTrackingEnabled.collectAsState()
    val offlineModeEnabled by userPreferences.offlineModeEnabled.collectAsState()
    val trackingServerUrl by userPreferences.trackingServerUrl.collectAsState()

    // Hoisted string resources for use in lambdas
    val recordingMsg = stringResource(Res.string.recording_snackbar)
    val trackDiscardedMsg = stringResource(Res.string.track_discarded)
    val trackSavedMsg = stringResource(Res.string.track_saved)
    val pointSavedMsg = stringResource(Res.string.point_saved)
    val pointDeletedMsg = stringResource(Res.string.point_deleted)
    val pointUpdatedMsg = stringResource(Res.string.point_updated)
    val onlineEnabledMsg = stringResource(Res.string.online_tracking_enabled)
    val onlineDisabledMsg = stringResource(Res.string.online_tracking_disabled)
    val locationPermissionMsg = stringResource(Res.string.location_permission_required)
    val unnamedPointStr = stringResource(Res.string.unnamed_point)

    var showStopTrackDialog by remember { mutableStateOf(false) }
    var trackNameInput by remember { mutableStateOf("") }
    var isResolvingTrackName by remember { mutableStateOf(false) }

    // Search state
    var showSearch by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<PlaceSearchClient.SearchResult>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }

    // Save point state (long press)
    var showSavePointDialog by remember { mutableStateOf(false) }
    var savePointLatLng by remember { mutableStateOf<LatLng?>(null) }
    var savePointName by remember { mutableStateOf("") }
    var isResolvingPointName by remember { mutableStateOf(false) }

    // Edit point state (tap)
    var showPointInfoDialog by remember { mutableStateOf(false) }
    var clickedPoint by remember { mutableStateOf<SavedPoint?>(null) }

    // Save ruler as track state
    var showSaveRulerAsTrackDialog by remember { mutableStateOf(false) }
    var rulerTrackName by remember { mutableStateOf("") }
    var isResolvingRulerName by remember { mutableStateOf(false) }

    val cacheDir = remember {
        val paths = NSFileManager.defaultManager.URLsForDirectory(NSCachesDirectory, NSUserDomainMask)
        @Suppress("UNCHECKED_CAST")
        val url = (paths as List<platform.Foundation.NSURL>).first()
        PlatformFile(url.path ?: "")
    }
    val regions = remember { RegionsRepository.getRegions(cacheDir) }

    val styleJson = remember(currentLayer, waymarkedTrails, countyBorders, regions) {
        MapStyle.getStyle(
            selectedLayer = currentLayer,
            showCountyBorders = countyBorders,
            showWaymarkedTrails = waymarkedTrails,
            regions = regions
        )
    }

    LaunchedEffect(Unit) {
        if (!locationTracker.hasPermission) {
            locationTracker.requestPermission()
        }
    }

    LaunchedEffect(offlineModeEnabled) {
        mapViewProvider.setConnected(!offlineModeEnabled)
    }

    // Debounced search
    LaunchedEffect(Unit) {
        snapshotFlow { searchQuery }
            .debounce(300)
            .distinctUntilChanged()
            .collect { query ->
                if (query.length < 2) {
                    searchResults = emptyList()
                    isSearching = false
                    return@collect
                }
                isSearching = true
                searchResults = PlaceSearchClient.search(query)
                isSearching = false
            }
    }

    // Animate camera to viewing point
    LaunchedEffect(viewingPoint) {
        if (viewingPoint != null) {
            mapViewProvider.setCamera(
                latitude = viewingPoint.latLng.latitude,
                longitude = viewingPoint.latLng.longitude,
                zoom = 15.0
            )
        }
    }

    // Fit camera to viewing track bounds and render blue track line
    LaunchedEffect(viewingTrack) {
        val track = viewingTrack
        if (track != null && track.points.size >= 2 && !isRecording) {
            val geoJson = buildTrackGeoJson(track.points)
            mapViewProvider.updateTrackLine(geoJson, "#0000FF")
            val lats = track.points.map { it.latLng.latitude }
            val lngs = track.points.map { it.latLng.longitude }
            mapViewProvider.setCameraBounds(
                south = lats.min(),
                west = lngs.min(),
                north = lats.max(),
                east = lngs.max(),
                padding = 80
            )
        }
    }

    // Set gesture callbacks
    LaunchedEffect(rulerState.isActive, savedPoints.size) {
        mapViewProvider.setOnLongPressCallback(object : MapLongPressCallback {
            override fun onLongPress(latitude: Double, longitude: Double) {
                if (rulerState.isActive) return
                val latLng = LatLng(latitude, longitude)
                savePointLatLng = latLng
                savePointName = ""
                isResolvingPointName = true
                showSavePointDialog = true
                scope.launch {
                    val name = GeocodingHelper.reverseGeocode(latLng)
                    if (name != null) {
                        savePointName = NamingUtils.makeUnique(
                            name, savedPoints.map { it.name }
                        )
                    }
                    isResolvingPointName = false
                }
            }
        })
        mapViewProvider.setOnMapClickCallback(object : MapClickCallback {
            override fun onMapClick(latitude: Double, longitude: Double) {
                if (rulerState.isActive) {
                    rulerState = rulerState.addPoint(LatLng(latitude, longitude))
                    return
                }
                val tapLocation = LatLng(latitude, longitude)
                val nearest = SavedPointUtils.findNearestPoint(tapLocation, savedPoints)
                if (nearest != null) {
                    clickedPoint = nearest
                    showPointInfoDialog = true
                }
            }
        })
    }

    if (showStopTrackDialog) {
        MapDialogs.StopTrackDialog(
            trackNameInput = trackNameInput,
            onTrackNameChange = { trackNameInput = it },
            onDiscard = {
                locationTracker.onlineTrackingClient?.stopTrack()
                locationTracker.onlineTrackingClient = null
                trackRepository.discardRecording()
                locationTracker.stopTracking()
                mapViewProvider.clearTrackLine()
                showStopTrackDialog = false
                trackNameInput = ""
                scope.launch { snackbarHostState.showSnackbar(trackDiscardedMsg) }
            },
            onSave = {
                locationTracker.onlineTrackingClient?.stopTrack()
                locationTracker.onlineTrackingClient = null
                val current = currentTrack
                val name = trackNameInput
                if (current != null && name.isNotBlank()) {
                    trackRepository.renameTrack(current, name)
                }
                trackRepository.stopRecording()
                locationTracker.stopTracking()
                mapViewProvider.clearTrackLine()
                showStopTrackDialog = false
                trackNameInput = ""
                scope.launch { snackbarHostState.showSnackbar(trackSavedMsg) }
            },
            onDismiss = {
                showStopTrackDialog = false
            },
            isLoading = isResolvingTrackName
        )
    }

    if (showSavePointDialog && savePointLatLng != null) {
        val latLng = savePointLatLng!!
        MapDialogs.SavePointDialog(
            pointName = savePointName,
            onPointNameChange = { savePointName = it },
            isLoading = isResolvingPointName,
            coordinates = "${latLng.latitude.toString().take(10)}, ${latLng.longitude.toString().take(10)}",
            onSave = {
                savedPointsRepository.addPoint(
                    name = savePointName.ifBlank { unnamedPointStr },
                    latLng = latLng
                )
                showSavePointDialog = false
                savePointLatLng = null
                savePointName = ""
                scope.launch { snackbarHostState.showSnackbar(pointSavedMsg) }
            },
            onDismiss = {
                showSavePointDialog = false
                savePointLatLng = null
                savePointName = ""
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
            coordinates = "${clickedPoint!!.latLng.latitude.toString().take(10)}, ${clickedPoint!!.latLng.longitude.toString().take(10)}",
            availableColors = colors,
            onNameChange = { editName = it },
            onDescriptionChange = { editDescription = it },
            onColorChange = { editColor = it },
            onDelete = {
                clickedPoint?.let { savedPointsRepository.deletePoint(it.id) }
                showPointInfoDialog = false
                clickedPoint = null
                scope.launch { snackbarHostState.showSnackbar(pointDeletedMsg) }
            },
            onSave = {
                clickedPoint?.let {
                    savedPointsRepository.updatePoint(it.id, editName, editDescription, editColor)
                }
                showPointInfoDialog = false
                clickedPoint = null
                scope.launch { snackbarHostState.showSnackbar(pointUpdatedMsg) }
            },
            onDismiss = {
                showPointInfoDialog = false
                clickedPoint = null
            }
        )
    }

    if (showSaveRulerAsTrackDialog) {
        val savedAsTrackMsg = stringResource(Res.string.saved_as_track_name, rulerTrackName)
        MapDialogs.SaveRulerAsTrackDialog(
            trackName = rulerTrackName,
            rulerState = rulerState,
            onTrackNameChange = { rulerTrackName = it },
            isLoading = isResolvingRulerName,
            onSave = {
                val name = rulerTrackName
                if (name.isNotBlank()) {
                    trackRepository.createTrackFromPoints(name, rulerState.points)
                    rulerState = rulerState.clear()
                    mapViewProvider.clearRuler()
                }
                showSaveRulerAsTrackDialog = false
                rulerTrackName = ""
                scope.launch { snackbarHostState.showSnackbar(savedAsTrackMsg) }
            },
            onDismiss = {
                showSaveRulerAsTrackDialog = false
                rulerTrackName = ""
            }
        )
    }

    MapScreenContent(
        snackbarHostState = snackbarHostState,
        isRecording = isRecording,
        rulerState = rulerState,
        showLayerMenu = showLayerMenu,
        selectedLayer = currentLayer,
        showWaymarkedTrails = waymarkedTrails,
        showCountyBorders = countyBorders,
        showSavedPoints = showSavedPoints,
        offlineModeEnabled = offlineModeEnabled,
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
        onLayerSelected = { currentLayer = it },
        onWaymarkedTrailsToggle = { waymarkedTrails = !waymarkedTrails },
        onCountyBordersToggle = { countyBorders = !countyBorders },
        onSavedPointsToggle = { showSavedPoints = !showSavedPoints },
        onRecordStopClick = {
            if (isRecording) {
                showStopTrackDialog = true
                trackNameInput = ""
                isResolvingTrackName = true
                scope.launch {
                    val track = currentTrack
                    if (track != null && track.points.isNotEmpty()) {
                        val firstPoint = track.points.first()
                        val lastPoint = track.points.last()
                        val startName = GeocodingHelper.reverseGeocode(firstPoint.latLng)
                        val distance = firstPoint.latLng.distanceTo(lastPoint.latLng)
                        val baseName = if (distance > 100 && startName != null) {
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
                            trackNameInput = NamingUtils.makeUnique(
                                baseName,
                                trackRepository.tracks.value.map { it.name }
                            )
                        }
                    }
                    isResolvingTrackName = false
                }
            } else {
                if (!locationTracker.hasPermission) {
                    locationTracker.requestPermission()
                    scope.launch { snackbarHostState.showSnackbar(locationPermissionMsg) }
                    return@MapScreenContent
                }
                trackRepository.startNewTrack()
                locationTracker.startTracking()
                if (onlineTrackingEnabled && !offlineModeEnabled) {
                    scope.launch {
                        val clientId = clientIdManager.getClientId()
                        val client = OnlineTrackingClient(
                            serverUrl = trackingServerUrl,
                            clientId = clientId,
                            hmacSecret = BuildInfo.TRACKING_HMAC_SECRET,
                            canSend = { !userPreferences.offlineModeEnabled.value }
                        )
                        client.startTrack("Track")
                        locationTracker.onlineTrackingClient = client
                    }
                }
                scope.launch { snackbarHostState.showSnackbar(recordingMsg) }
            }
        },
        onMyLocationClick = {
            val location = mapViewProvider.getUserLocation()
            if (location != null && location.size >= 2) {
                mapViewProvider.setCamera(
                    latitude = location[0],
                    longitude = location[1],
                    zoom = 15.0
                )
            }
        },
        onRulerToggle = {
            rulerState = if (rulerState.isActive) {
                mapViewProvider.clearRuler()
                rulerState.clear()
            } else {
                rulerState.copy(isActive = true)
            }
        },
        onSettingsClick = onSettingsClick,
        onOfflineIndicatorClick = onOfflineIndicatorClick,
        onOnlineTrackingClick = onOnlineTrackingClick,
        onZoomIn = { mapViewProvider.zoomIn() },
        onZoomOut = { mapViewProvider.zoomOut() },
        onRulerUndo = {
            rulerState = rulerState.removeLastPoint()
        },
        onRulerClear = {
            mapViewProvider.clearRuler()
            rulerState = rulerState.clear()
        },
        onRulerSaveAsTrack = {
            showSaveRulerAsTrackDialog = true
            rulerTrackName = ""
            isResolvingRulerName = true
            scope.launch {
                val points = rulerState.points
                if (points.isNotEmpty()) {
                    val firstPoint = points.first()
                    val lastPoint = points.last()
                    val startName = GeocodingHelper.reverseGeocode(firstPoint.latLng)
                    val baseName = if (points.size > 1) {
                        val distance = firstPoint.latLng.distanceTo(lastPoint.latLng)
                        if (distance > 100 && startName != null) {
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
                        rulerTrackName = NamingUtils.makeUnique(
                            baseName, trackRepository.tracks.value.map { it.name }
                        )
                    }
                }
                isResolvingRulerName = false
            }
        },
        onOnlineTrackingChange = { enabled ->
            userPreferences.updateOnlineTrackingEnabled(enabled)
            if (isRecording) {
                if (enabled) {
                    scope.launch {
                        val clientId = clientIdManager.getClientId()
                        val client = OnlineTrackingClient(
                            serverUrl = trackingServerUrl,
                            clientId = clientId,
                            hmacSecret = BuildInfo.TRACKING_HMAC_SECRET,
                            canSend = { !userPreferences.offlineModeEnabled.value }
                        )
                        val track = currentTrack
                        if (track != null && track.isRecording) {
                            client.syncExistingTrack(track)
                        } else {
                            client.startTrack(track?.name ?: "Track")
                        }
                        locationTracker.onlineTrackingClient = client
                        snackbarHostState.showSnackbar(onlineEnabledMsg)
                    }
                } else {
                    locationTracker.onlineTrackingClient?.stopTrack()
                    locationTracker.onlineTrackingClient = null
                    scope.launch { snackbarHostState.showSnackbar(onlineDisabledMsg) }
                }
            }
        },
        onCloseViewingTrack = {
            trackRepository.clearViewingTrack()
            mapViewProvider.clearTrackLine()
        },
        onCloseViewingPoint = { onClearViewingPoint() },
        onSearchQueryChange = { searchQuery = it },
        onSearchResultClick = { result ->
            mapViewProvider.setCamera(
                latitude = result.latLng.latitude,
                longitude = result.latLng.longitude,
                zoom = 14.0
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
            UIKitView(
                factory = { mapViewProvider.createMapView() },
                modifier = Modifier.fillMaxSize(),
                update = {
                    mapViewProvider.setStyle(styleJson)
                    mapViewProvider.setShowsUserLocation(true)

                    // Track rendering: recording (red) takes precedence over viewing (blue)
                    val recording = currentTrack
                    if (recording != null && recording.points.size >= 2) {
                        val geoJson = buildTrackGeoJson(recording.points)
                        mapViewProvider.updateTrackLine(geoJson, "#FF0000")
                    } else if (!isRecording) {
                        val viewing = viewingTrack
                        if (viewing != null && viewing.points.size >= 2) {
                            val geoJson = buildTrackGeoJson(viewing.points)
                            mapViewProvider.updateTrackLine(geoJson, "#0000FF")
                        } else {
                            mapViewProvider.clearTrackLine()
                        }
                    }

                    // Saved points rendering
                    if (showSavedPoints && savedPoints.isNotEmpty()) {
                        val geoJson = buildSavedPointsGeoJson(savedPoints)
                        mapViewProvider.updateSavedPoints(geoJson)
                    } else {
                        mapViewProvider.clearSavedPoints()
                    }

                    // Ruler rendering
                    if (rulerState.points.isNotEmpty()) {
                        val pointsGeoJson = buildRulerPointsGeoJson(rulerState.points)
                        val lineGeoJson = if (rulerState.points.size >= 2) {
                            buildRulerLineGeoJson(rulerState.points)
                        } else {
                            """{"type":"Feature","geometry":{"type":"LineString","coordinates":[]}}"""
                        }
                        mapViewProvider.updateRuler(lineGeoJson, pointsGeoJson)
                    } else {
                        mapViewProvider.clearRuler()
                    }
                }
            )
        }
    )
}
