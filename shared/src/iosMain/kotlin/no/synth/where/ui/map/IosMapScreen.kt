package no.synth.where.ui.map

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import no.synth.where.data.CrosshairInfo
import no.synth.where.data.GeocodingHelper
import no.synth.where.data.LiveTrackingFollower
import no.synth.where.data.MapStyle
import no.synth.where.data.PlaceSearchClient
import no.synth.where.data.TerrainClient
import no.synth.where.data.RulerState
import no.synth.where.data.SavedPoint
import no.synth.where.data.SavedPointUtils
import no.synth.where.data.Track
import no.synth.where.data.TrackUtils
import no.synth.where.data.geo.CoordFormat
import no.synth.where.data.geo.LatLng
import no.synth.where.data.geo.bounds
import no.synth.where.di.AppDependencies
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
import no.synth.where.resources.track_cropped
import no.synth.where.resources.track_discarded
import no.synth.where.resources.track_saved
import no.synth.where.resources.undo
import no.synth.where.resources.unnamed_point
import no.synth.where.util.NamingUtils
import org.jetbrains.compose.resources.stringResource
import platform.Foundation.NSBundle
import platform.Foundation.NSURL

/**
 * URL template for MapLibre `glyphs:` pointing at PBF files inside the iOS
 * app bundle's Fonts/ folder. The font stack name (no spaces) substitutes
 * directly into the path so no percent-decoding is needed by MapLibre's
 * file source. Built from `NSURL` so any spaces in the bundle path are
 * percent-encoded correctly.
 */
private fun iosBundleGlyphsUrl(): String {
    val fontsRoot = NSURL.fileURLWithPath("${NSBundle.mainBundle.bundlePath}/Fonts").absoluteString
        ?: "file://${NSBundle.mainBundle.bundlePath}/Fonts"
    val trimmed = fontsRoot.trimEnd('/')
    return "$trimmed/{fontstack}/{range}.pbf"
}

@OptIn(FlowPreview::class)
@Composable
fun IosMapScreen(
    mapViewProvider: MapViewProvider,
    viewingPoint: SavedPoint? = null,
    onClearViewingPoint: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    onOfflineIndicatorClick: () -> Unit = {},
    onOnlineTrackingClick: () -> Unit = {}
) {
    val trackRepository = remember { AppDependencies.trackRepository }
    val savedPointsRepository = remember { AppDependencies.savedPointsRepository }
    val userPreferences = remember { AppDependencies.userPreferences }
    val coordinator = remember { AppDependencies.onlineTrackingCoordinator }
    val locationTracker = remember { IosLocationTracker(trackRepository, coordinator) }

    var showLayerMenu by remember { mutableStateOf(false) }
    val currentLayer by userPreferences.selectedMapLayer.collectAsState()
    val waymarkedTrails by userPreferences.showWaymarkedTrails.collectAsState()
    val avalancheZones by userPreferences.showAvalancheZones.collectAsState()
    val hillshade by userPreferences.showHillshade.collectAsState()
    val showSavedPoints by userPreferences.showSavedPoints.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var rulerState by remember { mutableStateOf(RulerState()) }
    val scope = rememberCoroutineScope()

    val isRecording by trackRepository.isRecording.collectAsState()
    val currentTrack by trackRepository.currentTrack.collectAsState()
    val viewingTracks by trackRepository.viewingTracks.collectAsState()
    val focusedTrackId by trackRepository.focusedTrackId.collectAsState()
    val navigation by trackRepository.navigation.collectAsState()
    val navigationChartVisible by trackRepository.navigationChartVisible.collectAsState()
    // The navigated route in travel order (reversed when navigating in reverse) — feeds the altitude
    // chart, its scrub marker, and the route tap-target. Keeps the session track's id, so tap
    // routing still matches; only the point order flips so the chart reads left-to-right as "ahead".
    val navChartTrack = remember(navigation?.track?.id, navigation?.reversed) {
        navigation?.let { if (it.reversed) it.track.copy(points = it.track.points.reversed()) else it.track }
    }
    val cropState by trackRepository.cropState.collectAsState()
    val cropUndo by trackRepository.cropUndo.collectAsState()
    val elevationMarker by trackRepository.elevationMarker.collectAsState()
    // iOS has no background producer yet (see the Live Activity follow-up), so a foreground
    // poller feeds the shared progress flow that the observer below and the banner read.
    NavigationProgressPoller(
        session = navigation,
        location = {
            val loc = mapViewProvider.getUserLocation()
            if (loc != null && loc.size >= 2) LatLng(loc[0], loc[1]) else null
        },
        updateProgress = trackRepository::updateNavigationProgress,
    )
    val navigationProgress = rememberNavigationProgress(
        session = navigation,
        progress = trackRepository.navigationProgress,
        onRenderLayers = { layers ->
            mapViewProvider.updateNavigation(layers.completed, layers.remaining, layers.offCourse)
        },
        onClearLayers = { mapViewProvider.clearNavigation() },
    )
    val savedPoints by savedPointsRepository.savedPoints.collectAsState()
    val onlineTrackingEnabled by userPreferences.onlineTrackingEnabled.collectAsState()
    val viewerCount by userPreferences.viewerCount.collectAsState()
    val offlineModeEnabled by userPreferences.offlineModeEnabled.collectAsState()
    val liveShareUntilMillis by userPreferences.liveShareUntilMillis.collectAsState()

    val liveTrackingFollower = remember { AppDependencies.liveTrackingFollower }
    val followState by liveTrackingFollower.state.collectAsState()
    val friendTrackGeoJson by liveTrackingFollower.friendTrackGeoJson.collectAsState()
    val followedClientId by userPreferences.followedClientId.collectAsState()

    // Auto-follow on startup
    LaunchedEffect(followedClientId) {
        val id = followedClientId
        if (id != null && followState is LiveTrackingFollower.FollowState.Idle) {
            liveTrackingFollower.follow(id)
        }
    }

    // Zoom to friend track when first data arrives
    var hasZoomedToFriend by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(followedClientId) {
        hasZoomedToFriend = false
    }
    LaunchedEffect(followState) {
        val following = followState as? LiveTrackingFollower.FollowState.Following ?: return@LaunchedEffect
        if (hasZoomedToFriend) return@LaunchedEffect
        val bounds = following.tracks.flatMap { it.points }.bounds() ?: return@LaunchedEffect
        hasZoomedToFriend = true
        mapViewProvider.animateToBounds(bounds, maxZoom = MapZoomLevels.FRIEND_MAX)
    }

    // Hoisted string resources for use in lambdas
    val recordingMsg = stringResource(Res.string.recording_snackbar)
    val trackDiscardedMsg = stringResource(Res.string.track_discarded)
    val trackSavedMsg = stringResource(Res.string.track_saved)
    val pointSavedMsg = stringResource(Res.string.point_saved)
    val pointDeletedMsg = stringResource(Res.string.point_deleted)
    val pointUpdatedMsg = stringResource(Res.string.point_updated)
    val locationPermissionMsg = stringResource(Res.string.location_permission_required)
    val trackCroppedMsg = stringResource(Res.string.track_cropped)
    val undoLabel = stringResource(Res.string.undo)

    // After a crop overwrites the track, offer a one-tap undo of the (otherwise irreversible) change.
    LaunchedEffect(cropUndo) {
        if (cropUndo == null) return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(
            trackCroppedMsg,
            actionLabel = undoLabel,
            duration = SnackbarDuration.Long
        )
        if (result == SnackbarResult.ActionPerformed) trackRepository.undoCrop() else trackRepository.clearCropUndo()
    }
    val unnamedPointStr = stringResource(Res.string.unnamed_point)

    var showStopTrackDialog by remember { mutableStateOf(false) }
    var trackNameInput by remember { mutableStateOf("") }
    var isResolvingTrackName by remember { mutableStateOf(false) }

    // Stopping navigation is confirmed first so an active route isn't ended by an accidental tap.
    val stopNavConfirm = rememberStopNavigationConfirmState()

    // Search state
    var showSearch by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<PlaceSearchClient.SearchResult>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    val searchHistory by userPreferences.searchHistory.collectAsState()
    var highlightedSearchResult by remember { mutableStateOf<PlaceSearchClient.SearchResult?>(null) }

    // Save point state (long press)
    var showSavePointDialog by remember { mutableStateOf(false) }
    var savePointLatLng by remember { mutableStateOf<LatLng?>(null) }
    var savePointName by remember { mutableStateOf("") }
    var isResolvingPointName by remember { mutableStateOf(false) }

    // Edit point state (tap)
    var showPointInfoDialog by remember { mutableStateOf(false) }
    var clickedPoint by remember { mutableStateOf<SavedPoint?>(null) }

    // Track view state
    // All visible track lines (viewing set + recording) as one data-driven FeatureCollection.
    // The navigated track is excluded from the viewing set (it shows as the grey/blue split line),
    // so any tracks here are the "other" tracks kept visible alongside it while navigating.
    val tracksGeoJson = remember(viewingTracks, focusedTrackId, currentTrack, cropState, navigation != null) {
        buildTracksGeoJson(
            renderableTracks(viewingTracks, focusedTrackId, currentTrack, cropState, navigating = navigation != null)
        )
    }

    val showCoordGrid by userPreferences.showCoordGrid.collectAsState()
    val crosshairActive by userPreferences.crosshairActive.collectAsState()
    var crosshairInfo by remember { mutableStateOf(CrosshairInfo()) }
    val coordFormat by userPreferences.coordFormat.collectAsState()
    var centerLatLng by remember { mutableStateOf<LatLng?>(null) }
    var cameraZoom by remember { mutableStateOf(5.0) }
    var cameraFollowMode by remember { mutableStateOf(CameraFollowMode.OFF) }
    var userLocation by remember { mutableStateOf<LatLng?>(null) }
    var hasFix by remember { mutableStateOf(false) }
    val isLocating = locationTracker.hasPermission && !hasFix
    var twoFingerMeasurement by rememberAutoDismissingTwoFingerMeasurement()

    // Save ruler as track state
    var showSaveRulerAsTrackDialog by remember { mutableStateOf(false) }
    var rulerTrackName by remember { mutableStateOf("") }
    var isResolvingRulerName by remember { mutableStateOf(false) }

    val glyphsUrl = remember { iosBundleGlyphsUrl() }
    val styleJson = remember(currentLayer, waymarkedTrails, avalancheZones, hillshade) {
        MapStyle.getStyle(
            selectedLayer = currentLayer,
            showWaymarkedTrails = waymarkedTrails,
            showAvalancheZones = avalancheZones,
            showHillshade = hillshade,
            glyphsUrl = glyphsUrl,
        )
    }

    LaunchedEffect(Unit) {
        if (!locationTracker.hasPermission) {
            locationTracker.requestPermission()
        }
    }

    DisposableEffect(locationTracker) {
        locationTracker.startKeepAlive()
        onDispose { locationTracker.stopKeepAlive() }
    }

    LaunchedEffect(locationTracker) {
        while (!hasFix) {
            if (locationTracker.lastLocation != null) hasFix = true
            else kotlinx.coroutines.delay(1000)
        }
    }

    LaunchedEffect(offlineModeEnabled) {
        mapViewProvider.setConnected(!offlineModeEnabled)
    }

    val shouldTrackLocation by coordinator.shouldTrackLocation.collectAsState()
    val isLiveSharing by coordinator.isLiveSharing.collectAsState()

    LaunchedEffect(shouldTrackLocation) {
        if (shouldTrackLocation) {
            when {
                !locationTracker.hasPermission -> locationTracker.requestAlwaysPermission()
                !locationTracker.hasAlwaysPermission -> {
                    locationTracker.requestAlwaysPermission()
                    locationTracker.startTracking()
                }
                else -> locationTracker.startTracking()
            }
        } else {
            locationTracker.stopTracking()
        }
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

    // Camera move callback for crosshair and grid
    DisposableEffect(Unit) {
        mapViewProvider.setOnCameraMoveCallback(object : MapCameraMoveCallback {
            override fun onCameraMove(latitude: Double, longitude: Double, zoom: Double, bearing: Double) {
                centerLatLng = LatLng(latitude, longitude)
                cameraZoom = zoom
            }
        })
        onDispose { mapViewProvider.setOnCameraMoveCallback(null) }
    }

    // Reflect gesture-driven follow changes (e.g. panning drops back to OFF) in the FAB state.
    DisposableEffect(Unit) {
        mapViewProvider.setOnTrackingModeCallback(object : MapTrackingModeCallback {
            override fun onTrackingModeChanged(mode: CameraFollowMode) {
                cameraFollowMode = mode
            }
        })
        onDispose { mapViewProvider.setOnTrackingModeCallback(null) }
    }

    // Two-finger tap callback for distance measurement
    DisposableEffect(Unit) {
        mapViewProvider.setOnTwoFingerTapCallback(MapTwoFingerTapCallback { lat1, lng1, lat2, lng2 ->
            val ll1 = LatLng(lat1, lng1)
            val ll2 = LatLng(lat2, lng2)
            twoFingerMeasurement = TwoFingerMeasurement(lat1, lng1, lat2, lng2, ll1.distanceTo(ll2))
        })
        onDispose { mapViewProvider.setOnTwoFingerTapCallback(null) }
    }

    // Initialize center position when crosshair is activated
    LaunchedEffect(crosshairActive) {
        if (crosshairActive && centerLatLng == null) {
            val center = mapViewProvider.getCameraCenter()
            if (center != null && center.size >= 2) {
                centerLatLng = LatLng(center[0], center[1])
            }
        }
    }

    // Update user location periodically while crosshair is active
    LaunchedEffect(crosshairActive) {
        if (!crosshairActive) {
            userLocation = null
            return@LaunchedEffect
        }
        while (true) {
            val loc = mapViewProvider.getUserLocation()
            if (loc != null && loc.size >= 2) {
                userLocation = LatLng(loc[0], loc[1])
            }
            kotlinx.coroutines.delay(3000)
        }
    }

    // Debounced terrain info fetch
    LaunchedEffect(crosshairActive, centerLatLng) {
        val latLng = centerLatLng ?: return@LaunchedEffect
        if (!crosshairActive) return@LaunchedEffect
        crosshairInfo = CrosshairInfo(isLoading = true)
        kotlinx.coroutines.delay(500)
        val info = TerrainClient.getTerrainInfo(latLng)
        crosshairInfo = if (info != null) {
            CrosshairInfo(elevation = info.elevation, slopeDegrees = info.slopeDegrees)
        } else {
            CrosshairInfo()
        }
    }

    // Coordinate grid overlay
    LaunchedEffect(showCoordGrid, coordFormat) {
        if (!showCoordGrid) {
            mapViewProvider.clearCoordGrid()
            return@LaunchedEffect
        }
        snapshotFlow { Pair(centerLatLng, cameraZoom) }
            .debounce(200)
            .collect { (center, zoom) ->
                val lat = center?.latitude ?: return@collect
                val lng = center.longitude
                val geoJson = withContext(Dispatchers.Default) {
                    CoordGrid.buildGeoJson(lat, lng, zoom, coordFormat)
                }
                mapViewProvider.updateCoordGrid(geoJson)
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

    // Render every visible track line from the shared data-driven FeatureCollection.
    LaunchedEffect(tracksGeoJson) {
        mapViewProvider.updateTracks(tracksGeoJson)
    }

    val elevationMarkerGeoJson = remember(elevationMarker, focusedTrackId, viewingTracks, navChartTrack) {
        buildElevationMarkerGeoJson(viewingTracks, focusedTrackId, navChartTrack, elevationMarker)
    }
    LaunchedEffect(elevationMarkerGeoJson) {
        mapViewProvider.updateElevationMarker(elevationMarkerGeoJson)
    }

    // Fit the camera whenever the viewing set changes, but not on tap-focus (focusedTrackId is
    // deliberately not a key). Opening a single track focuses it, so zoom to that track; a bulk
    // multi-select clears focus, so fit the union of the whole set.
    LaunchedEffect(viewingTracks) {
        if (navigation != null) return@LaunchedEffect   // the camera follows the user while navigating
        val bounds = Track.focusOrCombinedBounds(viewingTracks, focusedTrackId) ?: return@LaunchedEffect
        mapViewProvider.animateToBounds(bounds)
    }

    LaunchedEffect(twoFingerMeasurement) {
        val m = twoFingerMeasurement
        if (m != null) {
            mapViewProvider.updateMeasurement(buildMeasurementLineGeoJson(m), buildMeasurementPointsGeoJson(m))
        } else {
            mapViewProvider.fadeMeasurement(TwoFingerTap.FADE_OUT_MS.toDouble())
            delay(TwoFingerTap.FADE_OUT_MS)
            mapViewProvider.clearMeasurement()
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
                if (twoFingerMeasurement != null) {
                    twoFingerMeasurement = null
                }
                if (rulerState.isActive) {
                    rulerState = rulerState.addPoint(LatLng(latitude, longitude))
                    return
                }
                val tapLocation = LatLng(latitude, longitude)
                val nearest = SavedPointUtils.findNearestPoint(tapLocation, savedPoints)
                if (nearest != null) {
                    clickedPoint = nearest
                    showPointInfoDialog = true
                } else {
                    val tolerance = TrackUtils.metersPerPixel(latitude, cameraZoom) *
                        TrackUtils.TAP_RADIUS_PX
                    val candidates = TrackUtils.tappableTracks(viewingTracks, navChartTrack)
                    val tapped = TrackUtils.findTappedTrack(tapLocation, candidates, tolerance)
                    if (tapped != null) trackRepository.onTrackTapped(tapped.id)
                    else if (candidates.isNotEmpty()) trackRepository.onMapTapOutsideTracks()
                }
            }
        })
    }

    if (showStopTrackDialog) {
        MapDialogs.StopTrackDialog(
            trackNameInput = trackNameInput,
            onTrackNameChange = { trackNameInput = it },
            onDiscard = {
                trackRepository.discardRecording()
                mapViewProvider.clearTrackLine()
                showStopTrackDialog = false
                trackNameInput = ""
                scope.launch { snackbarHostState.showSnackbar(trackDiscardedMsg) }
            },
            onSave = {
                val current = currentTrack
                val name = trackNameInput
                if (current != null && name.isNotBlank()) {
                    trackRepository.renameTrack(current, name)
                }
                trackRepository.stopRecording()
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

    StopNavigationConfirmDialog(
        state = stopNavConfirm,
        isNavigating = navigation != null,
        onConfirm = {
            // The shared render effect clears the nav layers when the session ends. Don't clear the
            // track line: stopNavigation() puts the navigated track back in the viewing set (focused),
            // so the reactive updateTracks() render redraws it in detail mode.
            trackRepository.stopNavigation()
        }
    )

    if (showSavePointDialog && savePointLatLng != null) {
        val latLng = savePointLatLng ?: return
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
        val point = clickedPoint ?: return
        var editName by remember { mutableStateOf(point.name) }
        var editDescription by remember { mutableStateOf(point.description ?: "") }
        var editColor by remember { mutableStateOf(point.color ?: PointColors.DEFAULT) }

        val colors = PointColors.withSelected(point.color)

        MapDialogs.PointInfoDialog(
            pointName = editName,
            pointDescription = editDescription,
            pointColor = editColor,
            coordinates = "${point.latLng.latitude.toString().take(10)}, ${point.latLng.longitude.toString().take(10)}",
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
        showSavedPoints = showSavedPoints,
        showAvalancheZones = avalancheZones,
        showHillshade = hillshade,
        showCoordGrid = showCoordGrid,
        crosshairActive = crosshairActive,
        crosshairInfo = crosshairInfo,
        centerLatLng = centerLatLng,
        userLocation = userLocation,
        coordFormat = coordFormat,
        onToggleCoordFormat = { userPreferences.updateCoordFormat(coordFormat.next()) },
        onCrosshairToggle = { userPreferences.updateCrosshairActive(!crosshairActive) },
        offlineModeEnabled = offlineModeEnabled,
        isLocating = isLocating,
        onlineTrackingEnabled = onlineTrackingEnabled,
        liveShareUntilMillis = liveShareUntilMillis,
        isLiveSharing = isLiveSharing,
        viewerCount = viewerCount,
        recordingDistance = currentTrack?.getDistanceMeters(),
        viewingTracks = viewingTracks,
        focusedTrackId = focusedTrackId,
        cropState = cropState,
        onCropChange = { start, end -> trackRepository.updateCrop(start, end) },
        onCancelCrop = { trackRepository.cancelCrop() },
        onApplyCrop = { trackRepository.applyCrop() },
        elevationMarker = elevationMarker,
        onElevationScrub = { trackRepository.setElevationMarker(it) },
        navigation = NavigationUiState(
            progress = navigationProgress,
            track = navChartTrack,
            chartVisible = navigationChartVisible,
            onToggleReverse = { trackRepository.toggleNavigationReverse() },
            onStop = { stopNavConfirm.request() },
        ),
        viewingPointName = viewingPoint?.name,
        viewingPointColor = viewingPoint?.color ?: PointColors.DEFAULT,
        showViewingPoint = viewingPoint != null,
        showSearch = showSearch,
        searchQuery = searchQuery,
        searchResults = searchResults,
        searchHistory = searchHistory,
        isSearching = isSearching,
        onSearchClick = { showSearch = true },
        onLayerMenuToggle = { showLayerMenu = it },
        onLayerSelected = { userPreferences.updateSelectedMapLayer(it) },
        onWaymarkedTrailsToggle = { userPreferences.updateShowWaymarkedTrails(!waymarkedTrails) },
        onAvalancheZonesToggle = { userPreferences.updateShowAvalancheZones(!avalancheZones) },
        onHillshadeToggle = { userPreferences.updateShowHillshade(!hillshade) },
        onCoordGridToggle = { userPreferences.updateShowCoordGrid(!showCoordGrid) },
        onSavedPointsToggle = { userPreferences.updateShowSavedPoints(!showSavedPoints) },
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
                    locationTracker.requestAlwaysPermission()
                    scope.launch { snackbarHostState.showSnackbar(locationPermissionMsg) }
                    return@MapScreenContent
                }
                if (!locationTracker.hasAlwaysPermission) {
                    locationTracker.requestAlwaysPermission()
                }
                trackRepository.startNewTrack()
                scope.launch { snackbarHostState.showSnackbar(recordingMsg) }
            }
        },
        cameraFollowMode = cameraFollowMode,
        onMyLocationClick = {
            // Cycle OFF -> FOLLOW -> FOLLOW_HEADING; the provider centers/rotates via the map's
            // user tracking mode. Panning by hand reports back through the tracking-mode callback.
            val next = cameraFollowMode.next()
            cameraFollowMode = next
            mapViewProvider.setCameraFollowMode(next)
        },
        onRulerToggle = {
            val measurement = twoFingerMeasurement
            rulerState = if (!rulerState.isActive && measurement != null) {
                twoFingerMeasurement = null
                rulerState.activatedWith(measurement.endpoints)
            } else if (rulerState.isActive) {
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
        onCloseTrack = { focusedTrackId?.let { trackRepository.removeViewingTrack(it) } },
        onCollapseTrack = { trackRepository.setFocusedTrack(null) },
        onStartNavigation = {
            focusedTrackId?.let { id -> trackRepository.startNavigationById(id) }
        },
        onCloseViewingPoint = { onClearViewingPoint() },
        onSearchQueryChange = { searchQuery = it },
        onSearchResultClick = { result ->
            highlightedSearchResult = null
            mapViewProvider.setCamera(
                latitude = result.latLng.latitude,
                longitude = result.latLng.longitude,
                zoom = 14.0
            )
            userPreferences.addSearchHistoryEntry(result)
            showSearch = false
            searchQuery = ""
            searchResults = emptyList()
        },
        onSearchResultHover = { result ->
            highlightedSearchResult = result
            if (result != null) {
                mapViewProvider.panTo(
                    latitude = result.latLng.latitude,
                    longitude = result.latLng.longitude
                )
            }
        },
        onSearchClose = {
            highlightedSearchResult = null
            showSearch = false
            searchQuery = ""
            searchResults = emptyList()
        },
        followedClientId = followedClientId,
        isFollowConnecting = followState is LiveTrackingFollower.FollowState.Connecting,
        isFollowedTrackActive = (followState as? LiveTrackingFollower.FollowState.Following)?.tracks?.any { it.isActive } == true,
        onFollowBannerClick = {
            val following = followState as? LiveTrackingFollower.FollowState.Following ?: return@MapScreenContent
            val bounds = following.tracks.flatMap { it.points }.bounds() ?: return@MapScreenContent
            mapViewProvider.animateToBounds(bounds, maxZoom = MapZoomLevels.FRIEND_MAX)
        },
        onStopFollowing = {
            userPreferences.updateFollowedClientId(null)
            liveTrackingFollower.stopFollowing()
        },
        mapContent = {
            UIKitView(
                factory = { mapViewProvider.createMapView() },
                modifier = Modifier.fillMaxSize(),
                update = {
                    mapViewProvider.setStyle(styleJson)
                    mapViewProvider.setShowsUserLocation(true)

                    // All track lines (viewing set + recording) from the shared data-driven
                    // FeatureCollection; the navigated route is excluded (drawn as the split line),
                    // while other viewed tracks stay visible (dimmed) alongside it.
                    mapViewProvider.updateTracks(tracksGeoJson)

                    // Friend track rendering
                    val friendGeoJson = friendTrackGeoJson
                    if (friendGeoJson != null) {
                        mapViewProvider.updateFriendTrackLine(friendGeoJson, "#8D6E63")
                    } else {
                        mapViewProvider.clearFriendTrackLine()
                    }

                    // Saved points rendering
                    if (showSavedPoints && savedPoints.isNotEmpty()) {
                        val geoJson = buildSavedPointsGeoJson(savedPoints)
                        mapViewProvider.updateSavedPoints(geoJson)
                    } else {
                        mapViewProvider.clearSavedPoints()
                    }

                    // Search results rendering
                    if (searchResults.isNotEmpty()) {
                        val geoJson = buildSearchResultsGeoJson(searchResults)
                        mapViewProvider.updateSearchResults(geoJson)
                    } else {
                        mapViewProvider.clearSearchResults()
                    }

                    // Search result highlight
                    val highlighted = highlightedSearchResult
                    if (highlighted != null) {
                        val highlightGeoJson = buildSearchResultsGeoJson(listOf(highlighted))
                        mapViewProvider.highlightSearchResult(highlightGeoJson)
                    } else {
                        mapViewProvider.clearHighlightedSearchResult()
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
