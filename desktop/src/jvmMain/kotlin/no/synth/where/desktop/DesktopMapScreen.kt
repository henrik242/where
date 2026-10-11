package no.synth.where.desktop

import no.synth.where.ui.map.HikingRouteSnackbars
import no.synth.where.ui.map.crossingsGeoJsonFor
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
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
import no.synth.where.data.MapTapTarget
import no.synth.where.data.PlaceSearchClient
import no.synth.where.data.RulerState
import no.synth.where.data.SavedPoint
import no.synth.where.data.SharedPoint
import no.synth.where.data.TerrainClient
import no.synth.where.data.Track
import no.synth.where.data.geo.LatLng
import no.synth.where.data.resolveMapTap
import no.synth.where.data.stopFollowingAll
import no.synth.where.resources.Res
import no.synth.where.resources.point_deleted
import no.synth.where.resources.point_saved
import no.synth.where.resources.point_saved_locally
import no.synth.where.resources.point_shared_snackbar
import no.synth.where.resources.point_updated
import no.synth.where.resources.saved_as_track_name
import no.synth.where.resources.track_cropped
import no.synth.where.resources.undo
import no.synth.where.resources.zoom_in_for_paths
import no.synth.where.ui.map.CameraFollowMode
import no.synth.where.ui.map.CoordGrid
import no.synth.where.ui.map.MapDialogs
import no.synth.where.ui.map.MapScreenContent
import no.synth.where.ui.map.MapZoomLevels
import no.synth.where.ui.map.NavigationLayers
import no.synth.where.ui.map.NavigationUiState
import no.synth.where.ui.map.PointColors
import no.synth.where.ui.map.TwoFingerMeasurement
import no.synth.where.ui.map.buildElevationMarkerGeoJson
import no.synth.where.ui.map.buildMeasurementLineGeoJson
import no.synth.where.ui.map.buildMeasurementPointsGeoJson
import no.synth.where.ui.map.buildRulerLineGeoJson
import no.synth.where.ui.map.buildRulerPointsGeoJson
import no.synth.where.ui.map.buildSavedPointsGeoJson
import no.synth.where.ui.map.buildSearchResultsGeoJson
import no.synth.where.ui.map.buildSharedPointsGeoJson
import no.synth.where.ui.map.buildTracksGeoJson
import no.synth.where.ui.map.followedFriends
import no.synth.where.ui.map.friendBounds
import no.synth.where.ui.map.rememberAutoDismissingTwoFingerMeasurement
import no.synth.where.ui.map.rememberNavigationProgress
import no.synth.where.ui.map.renderableTracks
import no.synth.where.ui.map.compose.WhereComposeMap
import no.synth.where.ui.map.compose.WhereMapCallbacks
import no.synth.where.ui.map.compose.WhereMapController
import no.synth.where.ui.map.compose.WhereMapRenderData
import no.synth.where.util.NamingUtils
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Duration.Companion.milliseconds

/**
 * The desktop map screen: a viewer over the shared maplibre-compose map. It shows tracks, saved
 * points, search, the ruler/two-finger measure, offline/online state and followed friends, but has
 * no live GPS (no puck, no follow, no recording): the desktop has no location source.
 */
@OptIn(FlowPreview::class)
@Composable
fun DesktopMapScreen(
    viewingPoint: SavedPoint? = null,
    onClearViewingPoint: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    onOfflineIndicatorClick: () -> Unit = {},
    onOnlineTrackingClick: () -> Unit = {},
) {
    val trackRepository = remember { AppDependencies.trackRepository }
    val savedPointsRepository = remember { AppDependencies.savedPointsRepository }
    val userPreferences = remember { AppDependencies.userPreferences }
    val coordinator = remember { AppDependencies.onlineTrackingCoordinator }
    val controller = remember { WhereMapController() }

    var showLayerMenu by remember { mutableStateOf(false) }
    val currentLayer by userPreferences.selectedMapLayer.collectAsState()
    val waymarkedTrails by userPreferences.showWaymarkedTrails.collectAsState()
    val osmPaths by userPreferences.showOsmPaths.collectAsState()
    val nveOverlay by userPreferences.nveOverlay.collectAsState()
    val showSavedPoints by userPreferences.showSavedPoints.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var rulerState by remember { mutableStateOf(RulerState()) }
    val scope = rememberCoroutineScope()
    val routeSession = remember { AppDependencies.hikingRouteSession }
    val isPlanningRoute by routeSession.isPlanning.collectAsState()
    val plannedCrossings by routeSession.crossings.collectAsState()
    HikingRouteSnackbars(routeSession, snackbarHostState)

    val isRecording by trackRepository.isRecording.collectAsState()
    val currentTrack by trackRepository.currentTrack.collectAsState()
    val viewingTracks by trackRepository.viewingTracks.collectAsState()
    val focusedTrackId by trackRepository.focusedTrackId.collectAsState()
    val navigation by trackRepository.navigation.collectAsState()
    val navigationChartVisible by trackRepository.navigationChartVisible.collectAsState()
    val navChartTrack = remember(navigation?.track?.id, navigation?.reversed) {
        navigation?.let { if (it.reversed) it.track.copy(points = it.track.points.reversed()) else it.track }
    }
    val navChartTrackState = rememberUpdatedState(navChartTrack)
    val cropState by trackRepository.cropState.collectAsState()
    val cropUndo by trackRepository.cropUndo.collectAsState()
    val elevationMarker by trackRepository.elevationMarker.collectAsState()

    var navLayers by remember { mutableStateOf<NavigationLayers?>(null) }
    // No live position on desktop, so navigation progress is not advanced; the stored progress still
    // renders the completed/remaining split for a track opened for navigation.
    val navigationProgress = rememberNavigationProgress(
        session = navigation,
        progress = trackRepository.navigationProgress,
        onRenderLayers = { layers -> navLayers = layers },
        onClearLayers = { navLayers = null },
    )

    val savedPoints by savedPointsRepository.savedPoints.collectAsState()
    val onlineTrackingEnabled by userPreferences.onlineTrackingEnabled.collectAsState()
    val viewerCount by userPreferences.viewerCount.collectAsState()
    val offlineModeEnabled by userPreferences.offlineModeEnabled.collectAsState()
    val liveShareUntilMillis by userPreferences.liveShareUntilMillis.collectAsState()

    val liveTrackingFollower = remember { AppDependencies.liveTrackingFollower }
    val followState by liveTrackingFollower.state.collectAsState()
    val friendTrackGeoJson by liveTrackingFollower.friendTrackGeoJson.collectAsState()
    val mySharedPoints by coordinator.mySharedPoints.collectAsState()
    val canShare by coordinator.canShare.collectAsState()
    val friendSharedPoints by liveTrackingFollower.friendPoints.collectAsState()
    val friendPointsGeoJson by liveTrackingFollower.friendPointsGeoJson.collectAsState()
    val followedClientIds by userPreferences.followedClientIds.collectAsState()
    val clientNicknames by userPreferences.clientNicknames.collectAsState()
    val followedFriends = followedFriends(
        followedClientIds,
        (followState as? LiveTrackingFollower.FollowState.Following)?.tracks ?: emptyList(),
        clientNicknames,
    )

    LaunchedEffect(followedClientIds, clientNicknames) {
        liveTrackingFollower.follow(followedClientIds, clientNicknames)
    }
    var hasZoomedToFriend by remember { mutableStateOf(false) }
    LaunchedEffect(followedClientIds) { hasZoomedToFriend = false }
    LaunchedEffect(followState) {
        val following = followState as? LiveTrackingFollower.FollowState.Following ?: return@LaunchedEffect
        if (hasZoomedToFriend) return@LaunchedEffect
        val bounds = following.tracks.friendBounds() ?: return@LaunchedEffect
        hasZoomedToFriend = true
        controller.animateToBounds(bounds, maxZoom = MapZoomLevels.FRIEND_MAX)
    }

    val trackSavedMsg = stringResource(Res.string.point_saved)
    val pointSavedMsg = stringResource(Res.string.point_saved)
    val pointDeletedMsg = stringResource(Res.string.point_deleted)
    val pointUpdatedMsg = stringResource(Res.string.point_updated)
    val pointSharedMsg = stringResource(Res.string.point_shared_snackbar)
    val pointSavedLocallyMsg = stringResource(Res.string.point_saved_locally)
    val trackCroppedMsg = stringResource(Res.string.track_cropped)
    val zoomInForPathsMsg = stringResource(Res.string.zoom_in_for_paths)
    val undoLabel = stringResource(Res.string.undo)

    LaunchedEffect(cropUndo) {
        if (cropUndo == null) return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(trackCroppedMsg, actionLabel = undoLabel, duration = SnackbarDuration.Long)
        if (result == SnackbarResult.ActionPerformed) trackRepository.undoCrop() else trackRepository.clearCropUndo()
    }

    // Search state
    var showSearch by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<PlaceSearchClient.SearchResult>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    val searchHistory by userPreferences.searchHistory.collectAsState()
    var highlightedSearchResult by remember { mutableStateOf<PlaceSearchClient.SearchResult?>(null) }

    // Save point (long press / right drag) + edit (tap) dialogs
    var showSavePointDialog by remember { mutableStateOf(false) }
    var savePointLatLng by remember { mutableStateOf<LatLng?>(null) }
    var savePointName by remember { mutableStateOf("") }
    var savePointDescription by remember { mutableStateOf("") }
    var isResolvingPointName by remember { mutableStateOf(false) }

    fun closeSavePointDialog() {
        showSavePointDialog = false
        savePointLatLng = null
        savePointName = ""
        savePointDescription = ""
    }

    var movingSharedPointId by remember { mutableStateOf<String?>(null) }
    var managingSharedPoint by remember { mutableStateOf<SharedPoint?>(null) }
    var friendPointDialog by remember { mutableStateOf<SharedPoint?>(null) }
    var showPointInfoDialog by remember { mutableStateOf(false) }
    var clickedPoint by remember { mutableStateOf<SavedPoint?>(null) }

    val tracksGeoJson = remember(viewingTracks, focusedTrackId, currentTrack, cropState, navigation != null) {
        buildTracksGeoJson(renderableTracks(viewingTracks, focusedTrackId, currentTrack, cropState, navigating = navigation != null))
    }

    val showCoordGrid by userPreferences.showCoordGrid.collectAsState()
    val crosshairActive by userPreferences.crosshairActive.collectAsState()
    var crosshairInfo by remember { mutableStateOf(CrosshairInfo()) }
    val coordFormat by userPreferences.coordFormat.collectAsState()
    var centerLatLng by remember { mutableStateOf<LatLng?>(null) }
    var cameraZoom by remember { mutableStateOf(5.0) }
    var cameraBearing by remember { mutableStateOf(0.0) }
    val northLocked by userPreferences.northLocked.collectAsState()
    var twoFingerMeasurement by rememberAutoDismissingTwoFingerMeasurement()

    var showSaveRulerAsTrackDialog by remember { mutableStateOf(false) }
    var rulerTrackName by remember { mutableStateOf("") }
    var isResolvingRulerName by remember { mutableStateOf(false) }

    val styleJson = remember(currentLayer, waymarkedTrails, osmPaths, nveOverlay) {
        MapStyle.getStyle(
            selectedLayer = currentLayer,
            showWaymarkedTrails = waymarkedTrails,
            nveOverlay = nveOverlay,
            showOsmPaths = osmPaths,
        )
    }

    var coordGridGeoJson by remember { mutableStateOf<String?>(null) }

    // Debounced place search
    LaunchedEffect(Unit) {
        snapshotFlow { searchQuery }
            .debounce(300.milliseconds)
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

    LaunchedEffect(showCoordGrid, coordFormat) {
        if (!showCoordGrid) {
            coordGridGeoJson = null
            return@LaunchedEffect
        }
        snapshotFlow { Pair(centerLatLng, cameraZoom) }
            .debounce(200.milliseconds)
            .collect { (center, zoom) ->
                val lat = center?.latitude ?: return@collect
                coordGridGeoJson = withContext(Dispatchers.Default) {
                    CoordGrid.buildGeoJson(lat, center.longitude, zoom, coordFormat)
                }
            }
    }

    LaunchedEffect(viewingPoint, controller.isAttached) {
        if (viewingPoint != null) {
            controller.setCamera(viewingPoint.latLng.latitude, viewingPoint.latLng.longitude, zoom = 15.0)
        }
    }

    val elevationMarkerGeoJson = remember(elevationMarker, focusedTrackId, viewingTracks, navChartTrack) {
        buildElevationMarkerGeoJson(viewingTracks, focusedTrackId, navChartTrack, elevationMarker)
    }

    LaunchedEffect(viewingTracks) {
        if (navigation != null) return@LaunchedEffect
        val bounds = Track.focusOrCombinedBounds(viewingTracks, focusedTrackId) ?: return@LaunchedEffect
        controller.animateToBounds(bounds)
    }

    val isLiveSharing by coordinator.isLiveSharing.collectAsState()

    val mapCallbacks = remember {
        WhereMapCallbacks(
            onUserGesture = {},
            onTwoFingerTap = { ll1, ll2 ->
                twoFingerMeasurement = TwoFingerMeasurement(
                    ll1.latitude, ll1.longitude, ll2.latitude, ll2.longitude, ll1.distanceTo(ll2)
                )
            },
            onCameraMove = { center, zoom, bearing ->
                centerLatLng = center
                cameraZoom = zoom
                cameraBearing = bearing
            },
            onLongPress = { latLng ->
                if (rulerState.isActive) return@WhereMapCallbacks
                val movingId = movingSharedPointId
                if (movingId != null) {
                    coordinator.moveSharedPoint(movingId, latLng)
                    movingSharedPointId = null
                    return@WhereMapCallbacks
                }
                savePointLatLng = latLng
                savePointName = ""
                savePointDescription = ""
                isResolvingPointName = true
                showSavePointDialog = true
                scope.launch {
                    val name = GeocodingHelper.reverseGeocode(latLng)
                    if (name != null && savePointName.isBlank()) {
                        savePointName = NamingUtils.makeUnique(name, savedPoints.map { it.name })
                    }
                    isResolvingPointName = false
                }
            },
            onMapClick = { latLng ->
                if (twoFingerMeasurement != null) twoFingerMeasurement = null
                if (rulerState.isActive) {
                    rulerState = rulerState.addPoint(latLng)
                    return@WhereMapCallbacks
                }
                when (val target = resolveMapTap(
                    tap = latLng,
                    zoom = cameraZoom,
                    savedPoints = savedPoints,
                    viewingTracks = viewingTracks,
                    navigationTrack = navChartTrackState.value,
                    mySharedPoints = mySharedPoints,
                    friendSharedPoints = friendSharedPoints,
                )) {
                    is MapTapTarget.Point -> {
                        clickedPoint = target.point
                        showPointInfoDialog = true
                    }
                    is MapTapTarget.SharedPoint -> {
                        if (target.mine) managingSharedPoint = target.point else friendPointDialog = target.point
                    }
                    is MapTapTarget.TrackLine -> trackRepository.onTrackTapped(target.trackId)
                    MapTapTarget.OutsideTracks -> trackRepository.onMapTapOutsideTracks()
                    MapTapTarget.Nothing -> {}
                }
            },
        )
    }

    val savedPointsJson = remember(showSavedPoints, savedPoints) {
        if (showSavedPoints && savedPoints.isNotEmpty()) buildSavedPointsGeoJson(savedPoints) else null
    }
    val mySharedPointsJson = remember(mySharedPoints) {
        if (mySharedPoints.isNotEmpty()) buildSharedPointsGeoJson(mySharedPoints) else null
    }
    val searchResultsJson = remember(searchResults) {
        if (searchResults.isNotEmpty()) buildSearchResultsGeoJson(searchResults) else null
    }
    val searchHighlightJson = remember(highlightedSearchResult) {
        highlightedSearchResult?.let { buildSearchResultsGeoJson(listOf(it)) }
    }
    val rulerLineJson = remember(rulerState) {
        if (rulerState.points.size >= 2) buildRulerLineGeoJson(rulerState.points) else null
    }
    val rulerPointsJson = remember(rulerState) {
        if (rulerState.points.isNotEmpty()) buildRulerPointsGeoJson(rulerState.points) else null
    }
    val measurementLineJson = remember(twoFingerMeasurement) {
        twoFingerMeasurement?.let { buildMeasurementLineGeoJson(it) }
    }
    val measurementPointsJson = remember(twoFingerMeasurement) {
        twoFingerMeasurement?.let { buildMeasurementPointsGeoJson(it) }
    }

    val renderData = WhereMapRenderData(
        styleJson = styleJson,
        initialZoom = 5.0,
        tracksGeoJson = tracksGeoJson,
        elevationMarkerGeoJson = elevationMarkerGeoJson,
        savedPointsGeoJson = savedPointsJson,
        friendTrackGeoJson = friendTrackGeoJson,
        mySharedPointsGeoJson = mySharedPointsJson,
        friendSharedPointsGeoJson = friendPointsGeoJson,
        searchResultsGeoJson = searchResultsJson,
        searchHighlightGeoJson = searchHighlightJson,
        rulerLineGeoJson = rulerLineJson,
        rulerPointsGeoJson = rulerPointsJson,
        crossingsGeoJson = remember(plannedCrossings, viewingTracks) { crossingsGeoJsonFor(plannedCrossings, viewingTracks) },
        measurementLineGeoJson = measurementLineJson,
        measurementPointsGeoJson = measurementPointsJson,
        coordGridGeoJson = coordGridGeoJson,
        navCompletedGeoJson = navLayers?.completed,
        navRemainingGeoJson = navLayers?.remaining,
        navOffCourseGeoJson = navLayers?.offCourse,
        userLocation = null,
    )

    if (showSavePointDialog && savePointLatLng != null) {
        val latLng = savePointLatLng ?: return
        MapDialogs.SavePointDialog(
            pointName = savePointName,
            onPointNameChange = { savePointName = it },
            pointDescription = savePointDescription,
            onPointDescriptionChange = { savePointDescription = it },
            isLoading = isResolvingPointName,
            coordinates = "${latLng.latitude.toString().take(10)}, ${latLng.longitude.toString().take(10)}",
            onSave = {
                savedPointsRepository.addPoint(name = savePointName, latLng = latLng, description = savePointDescription)
                closeSavePointDialog()
                scope.launch { snackbarHostState.showSnackbar(pointSavedMsg) }
            },
            canShare = canShare,
            onShareOnly = {
                coordinator.addSharedPoint(savePointName, savePointDescription, latLng, PointColors.DEFAULT)
                closeSavePointDialog()
                scope.launch { snackbarHostState.showSnackbar(pointSharedMsg) }
            },
            onSaveAndShare = {
                savedPointsRepository.addPoint(name = savePointName, latLng = latLng, description = savePointDescription)
                coordinator.addSharedPoint(savePointName, savePointDescription, latLng, PointColors.DEFAULT)
                closeSavePointDialog()
                scope.launch { snackbarHostState.showSnackbar(pointSharedMsg) }
            },
            onDismiss = { closeSavePointDialog() },
        )
    }

    managingSharedPoint?.let { point ->
        var editName by remember(point.id) { mutableStateOf(point.name) }
        var editDescription by remember(point.id) { mutableStateOf(point.description) }
        var editColor by remember(point.id) { mutableStateOf(point.color) }
        MapDialogs.ManageSharedPointDialog(
            pointName = editName,
            onNameChange = { editName = it },
            pointDescription = editDescription,
            onDescriptionChange = { editDescription = it },
            pointColor = editColor,
            onColorChange = { editColor = it },
            availableColors = PointColors.withSelected(point.color),
            coordinates = "${point.latLng.latitude.toString().take(10)}, ${point.latLng.longitude.toString().take(10)}",
            onMove = {
                movingSharedPointId = point.id
                managingSharedPoint = null
            },
            onDelete = {
                coordinator.removeSharedPoint(point.id)
                managingSharedPoint = null
                scope.launch { snackbarHostState.showSnackbar(pointDeletedMsg) }
            },
            onSave = {
                coordinator.updateSharedPoint(point.id, editName, editDescription, editColor)
                managingSharedPoint = null
                scope.launch { snackbarHostState.showSnackbar(pointUpdatedMsg) }
            },
            onDismiss = { managingSharedPoint = null },
        )
    }

    friendPointDialog?.let { point ->
        MapDialogs.FriendPointDialog(
            pointName = point.name,
            pointDescription = point.description,
            coordinates = "${point.latLng.latitude.toString().take(10)}, ${point.latLng.longitude.toString().take(10)}",
            onSaveLocally = {
                savedPointsRepository.addPoint(
                    name = point.name, latLng = point.latLng, description = point.description, color = point.color
                )
                friendPointDialog = null
                scope.launch { snackbarHostState.showSnackbar(pointSavedLocallyMsg) }
            },
            onDismiss = { friendPointDialog = null },
        )
    }

    clickedPoint?.let { point ->
        if (!showPointInfoDialog) return@let
        var editName by remember(point.id) { mutableStateOf(point.name) }
        var editDescription by remember(point.id) { mutableStateOf(point.description ?: "") }
        var editColor by remember(point.id) { mutableStateOf(point.color ?: PointColors.DEFAULT) }
        MapDialogs.PointInfoDialog(
            pointName = editName,
            pointDescription = editDescription,
            pointColor = editColor,
            coordinates = "${point.latLng.latitude.toString().take(10)}, ${point.latLng.longitude.toString().take(10)}",
            availableColors = PointColors.withSelected(point.color),
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
                clickedPoint?.let { savedPointsRepository.updatePoint(it.id, editName, editDescription, editColor) }
                showPointInfoDialog = false
                clickedPoint = null
                scope.launch { snackbarHostState.showSnackbar(pointUpdatedMsg) }
            },
            onDismiss = {
                showPointInfoDialog = false
                clickedPoint = null
            },
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
                if (rulerTrackName.isNotBlank()) {
                    trackRepository.createTrackFromPoints(rulerTrackName, rulerState.points)
                    rulerState = rulerState.clear()
                }
                showSaveRulerAsTrackDialog = false
                rulerTrackName = ""
                scope.launch { snackbarHostState.showSnackbar(savedAsTrackMsg) }
            },
            onDismiss = {
                showSaveRulerAsTrackDialog = false
                rulerTrackName = ""
            },
        )
    }

    LaunchedEffect(crosshairActive, centerLatLng) {
        val latLng = centerLatLng ?: return@LaunchedEffect
        if (!crosshairActive) return@LaunchedEffect
        crosshairInfo = CrosshairInfo(isLoading = true)
        delay(500.milliseconds)
        val info = TerrainClient.getTerrainInfo(latLng)
        crosshairInfo = if (info != null) CrosshairInfo(elevation = info.elevation, slopeDegrees = info.slopeDegrees) else CrosshairInfo()
    }

    MapScreenContent(
        snackbarHostState = snackbarHostState,
        isRecording = isRecording,
        rulerState = rulerState,
        showLayerMenu = showLayerMenu,
        selectedLayer = currentLayer,
        showWaymarkedTrails = waymarkedTrails,
        showOsmPaths = osmPaths,
        showSavedPoints = showSavedPoints,
        nveOverlay = nveOverlay,
        showCoordGrid = showCoordGrid,
        crosshairActive = crosshairActive,
        crosshairInfo = crosshairInfo,
        centerLatLng = centerLatLng,
        userLocation = null,
        coordFormat = coordFormat,
        onToggleCoordFormat = { userPreferences.updateCoordFormat(coordFormat.next()) },
        onCrosshairToggle = { userPreferences.updateCrosshairActive(!crosshairActive) },
        offlineModeEnabled = offlineModeEnabled,
        isLocating = false,
        onlineTrackingEnabled = onlineTrackingEnabled,
        liveShareUntilMillis = liveShareUntilMillis,
        isLiveSharing = isLiveSharing,
        viewerCount = viewerCount,
        recordingTrack = currentTrack,
        viewingTracks = viewingTracks,
        focusedTrackId = focusedTrackId,
        cropState = cropState,
        onCropChange = { start, end -> trackRepository.updateCrop(start, end) },
        onCancelCrop = { trackRepository.cancelCrop() },
        onApplyCrop = { trackRepository.applyCrop() },
        elevationMarker = elevationMarker,
        onElevationScrub = { trackRepository.setElevationMarker(it) },
        mapBearing = cameraBearing,
        northLocked = northLocked,
        onResetNorth = { controller.resetNorth() },
        onToggleNorthLock = {
            if (!northLocked) controller.resetNorth()
            userPreferences.updateNorthLocked(!northLocked)
        },
        navigation = NavigationUiState(
            progress = navigationProgress,
            track = navChartTrack,
            chartVisible = navigationChartVisible,
            onToggleReverse = { trackRepository.toggleNavigationReverse() },
            onStop = { trackRepository.stopNavigation() },
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
        onOsmPathsToggle = {
            userPreferences.updateShowOsmPaths(!osmPaths)
            if (!osmPaths && cameraZoom < MapStyle.OSM_PATHS_MIN_ZOOM) {
                scope.launch { snackbarHostState.showSnackbar(zoomInForPathsMsg) }
            }
        },
        onNveOverlayToggle = { userPreferences.toggleNveOverlay(it) },
        onCoordGridToggle = { userPreferences.updateShowCoordGrid(!showCoordGrid) },
        onSavedPointsToggle = { userPreferences.updateShowSavedPoints(!showSavedPoints) },
        // No GPS on desktop: recording and my-location follow are not available.
        onRecordStopClick = {},
        cameraFollowMode = CameraFollowMode.OFF,
        onMyLocationClick = {},
        onRulerToggle = {
            val measurement = twoFingerMeasurement
            rulerState = if (!rulerState.isActive && measurement != null) {
                twoFingerMeasurement = null
                rulerState.activatedWith(measurement.endpoints)
            } else if (rulerState.isActive) rulerState.clear() else rulerState.copy(isActive = true)
        },
        onSettingsClick = onSettingsClick,
        onOfflineIndicatorClick = onOfflineIndicatorClick,
        onOnlineTrackingClick = onOnlineTrackingClick,
        onZoomIn = { controller.zoomIn() },
        onZoomOut = { controller.zoomOut() },
        onRulerUndo = { rulerState = rulerState.removeLastPoint() },
        onRulerClear = { rulerState = rulerState.clear() },
        isPlanningRoute = isPlanningRoute,
        onFindRoute = {
            val planned = rulerState.points
            routeSession.plan(planned.map { it.latLng }) { if (rulerState.points == planned) rulerState = rulerState.clear() }
        },
        onRulerSaveAsTrack = {
            showSaveRulerAsTrackDialog = true
            rulerTrackName = ""
            isResolvingRulerName = true
            scope.launch {
                val points = rulerState.points
                if (points.isNotEmpty()) {
                    val startName = GeocodingHelper.reverseGeocode(points.first().latLng)
                    if (startName != null) {
                        rulerTrackName = NamingUtils.makeUnique(startName, trackRepository.tracks.value.map { it.name })
                    }
                }
                isResolvingRulerName = false
            }
        },
        onCloseTrack = { focusedTrackId?.let { trackRepository.removeViewingTrack(it) } },
        onCollapseTrack = { trackRepository.setFocusedTrack(null) },
        onSetTrackColor = { id, color -> trackRepository.setTrackColor(id, color) },
        onStartNavigation = { focusedTrackId?.let { id -> trackRepository.startNavigationById(id) } },
        onCloseViewingPoint = { onClearViewingPoint() },
        onSearchQueryChange = { searchQuery = it },
        onSearchResultClick = { result ->
            highlightedSearchResult = null
            controller.setCamera(result.latLng.latitude, result.latLng.longitude, zoom = 14.0)
            userPreferences.addSearchHistoryEntry(result)
            showSearch = false
            searchQuery = ""
            searchResults = emptyList()
        },
        onSearchResultHover = { result ->
            highlightedSearchResult = result
            if (result != null) controller.panTo(result.latLng.latitude, result.latLng.longitude)
        },
        onSearchClose = {
            highlightedSearchResult = null
            showSearch = false
            searchQuery = ""
            searchResults = emptyList()
        },
        followedFriends = followedFriends,
        isFollowConnecting = followState is LiveTrackingFollower.FollowState.Connecting,
        onFollowBannerClick = { clientId ->
            val following = followState as? LiveTrackingFollower.FollowState.Following ?: return@MapScreenContent
            val bounds = following.tracks.friendBounds(clientId) ?: return@MapScreenContent
            controller.animateToBounds(bounds, maxZoom = MapZoomLevels.FRIEND_MAX)
        },
        onStopFollowing = { stopFollowingAll(userPreferences, liveTrackingFollower) },
        isMovingSharedPoint = movingSharedPointId != null,
        onCancelMovePoint = { movingSharedPointId = null },
        mapContent = {
            WhereComposeMap(
                data = renderData,
                modifier = Modifier.fillMaxSize(),
                controller = controller,
                callbacks = mapCallbacks,
                rotateEnabled = !northLocked,
                attributionVisible = !crosshairActive,
            )
        },
    )
}
