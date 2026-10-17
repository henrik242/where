package no.synth.where.ui.map

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.withContext
import no.synth.where.data.CoverageLookup
import no.synth.where.data.CoverageTilesClient
import no.synth.where.data.CrosshairInfo
import no.synth.where.data.LiveTrackingFollower
import no.synth.where.data.MapStyle
import no.synth.where.data.MapTapTarget
import no.synth.where.data.TerrainClient
import no.synth.where.data.Track
import no.synth.where.data.geo.LatLng
import no.synth.where.data.geo.bounds
import no.synth.where.data.resolveMapTap
import no.synth.where.data.stopFollowingAll
import no.synth.where.resources.Res
import no.synth.where.resources.track_cropped
import no.synth.where.resources.undo
import no.synth.where.resources.zoom_in_for_paths
import no.synth.where.ui.MapScreenNavigation
import no.synth.where.ui.map.compose.WhereComposeMap
import no.synth.where.ui.map.compose.WhereMapCallbacks
import no.synth.where.ui.map.compose.WhereMapController
import no.synth.where.ui.map.compose.WhereMapRenderData
import org.jetbrains.compose.resources.getString
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/** A location fix: position, plus speed (m/s) and course over ground (degrees) when known. */
data class LocationFix(val latLng: LatLng, val speed: Double? = null, val course: Double? = null)

/** Platform side of the map screen. The defaults are a GPS-less viewer (desktop). */
class MapPlatform(
    val glyphsUrl: String? = null,
    /** Compass bearings in degrees, corrected for display rotation; null without a compass. */
    val headingUpdates: (() -> Flow<Double>)? = null,
    /** Last known position, to open the map there; null to keep the default camera. */
    val lastKnownLocation: (() -> LatLng?)? = null,
    /** Starts recording, prompting for permissions as needed; null when the platform cannot record. */
    val onStartRecording: (() -> Unit)? = null,
    /** Called after navigation starts from the track banner, e.g. to start a location service. */
    val onNavigationStarted: () -> Unit = {},
    /** Hooks system back; Android only. */
    val backHandler: @Composable (enabled: Boolean, onBack: () -> Unit) -> Unit = { _, _ -> },
)

/**
 * The map screen on every platform, over the shared maplibre-compose map. The platform feeds the
 * live [fix] and permission state; recording, follow and heading need them, the rest does not.
 */
@OptIn(FlowPreview::class)
@Composable
fun MapScreen(
    state: MapScreenState,
    nav: MapScreenNavigation,
    platform: MapPlatform,
    fix: LocationFix? = null,
    hasLocationPermission: Boolean = false,
) {
    val trackRepository = state.trackRepository
    val userPreferences = state.userPreferences
    val coordinator = state.coordinator
    val follower = state.follower
    val controller = remember { WhereMapController() }
    val viewingPoint = nav.viewingPoint

    val savedPoints by state.savedPointsRepository.savedPoints.collectAsState()
    val isRecording by trackRepository.isRecording.collectAsState()
    val currentTrack by trackRepository.currentTrack.collectAsState()
    val viewingTracks by trackRepository.viewingTracks.collectAsState()
    val focusedTrackId by trackRepository.focusedTrackId.collectAsState()
    val cropState by trackRepository.cropState.collectAsState()
    val cropUndo by trackRepository.cropUndo.collectAsState()
    val elevationMarker by trackRepository.elevationMarker.collectAsState()
    val navigation by trackRepository.navigation.collectAsState()
    val navigationChartVisible by trackRepository.navigationChartVisible.collectAsState()
    val isPlanningRoute by state.routeSession.isPlanning.collectAsState()
    val plannedCrossings by state.routeSession.crossings.collectAsState()
    val routePreview = state.routeSession.preview.collectAsState().value

    val onlineTrackingEnabled by userPreferences.onlineTrackingEnabled.collectAsState()
    val viewerCount by userPreferences.viewerCount.collectAsState()
    val liveShareUntilMillis by userPreferences.liveShareUntilMillis.collectAsState()
    val offlineModeEnabled by userPreferences.offlineModeEnabled.collectAsState()
    val showSavedPoints by userPreferences.showSavedPoints.collectAsState()
    val searchHistory by userPreferences.searchHistory.collectAsState()
    val northLocked by userPreferences.northLocked.collectAsState()
    val largeCompass by userPreferences.largeCompass.collectAsState()
    val selectedLayer by userPreferences.selectedMapLayer.collectAsState()
    val showWaymarkedTrails by userPreferences.showWaymarkedTrails.collectAsState()
    val showOsmPaths by userPreferences.showOsmPaths.collectAsState()
    val nveOverlay by userPreferences.nveOverlay.collectAsState()
    val showCoverage by userPreferences.showCoverage.collectAsState()
    val coverageTilesVersion by userPreferences.coverageTilesVersion.collectAsState()
    val showCoordGrid by userPreferences.showCoordGrid.collectAsState()
    val crosshairActive by userPreferences.crosshairActive.collectAsState()
    val coordFormat by userPreferences.coordFormat.collectAsState()
    val followedClientIds by userPreferences.followedClientIds.collectAsState()
    val clientNicknames by userPreferences.clientNicknames.collectAsState()

    val isLiveSharing by coordinator.isLiveSharing.collectAsState()
    val mySharedPoints by coordinator.mySharedPoints.collectAsState()
    val canShare by coordinator.canShare.collectAsState()
    val followState by follower.state.collectAsState()
    val friendTrackGeoJson by follower.friendTrackGeoJson.collectAsState()
    val friendSharedPoints by follower.friendPoints.collectAsState()
    val friendPointsGeoJson by follower.friendPointsGeoJson.collectAsState()
    val followedFriends = remember(followedClientIds, followState, clientNicknames) {
        followedFriends(
            followedClientIds,
            (followState as? LiveTrackingFollower.FollowState.Following)?.tracks ?: emptyList(),
            clientNicknames,
        )
    }

    var showLayerMenu by remember { mutableStateOf(false) }
    var cameraFollowMode by remember { mutableStateOf(CameraFollowMode.OFF) }
    var crosshairInfo by remember { mutableStateOf(CrosshairInfo()) }
    var centerLatLng by remember { mutableStateOf<LatLng?>(null) }
    var mapBearing by remember { mutableDoubleStateOf(0.0) }
    var twoFingerMeasurement by rememberAutoDismissingTwoFingerMeasurement()
    var coordGridGeoJson by remember { mutableStateOf<String?>(null) }
    var hasZoomedToFriend by rememberSaveable { mutableStateOf(false) }
    var hasZoomedToLocation by rememberSaveable { mutableStateOf(false) }
    // Camera kept across navigation away from the map and back.
    var cameraLat by rememberSaveable { mutableDoubleStateOf(65.0) }
    var cameraLon by rememberSaveable { mutableDoubleStateOf(10.0) }
    var cameraZoom by rememberSaveable { mutableDoubleStateOf(5.0) }
    val isLocating = hasLocationPermission && fix == null

    // The navigated route in travel order (reversed when navigating in reverse): feeds the altitude
    // chart, its scrub marker and the route tap target. Keeps the session track's id, so tap routing
    // still matches; only the point order flips so the chart reads left-to-right as "ahead".
    val navChartTrack = remember(navigation?.track?.id, navigation?.reversed) {
        navigation?.let { if (it.reversed) it.track.copy(points = it.track.points.reversed()) else it.track }
    }
    var navigationLayers by remember { mutableStateOf<NavigationLayers?>(null) }
    val navigationProgress = rememberNavigationProgress(
        session = navigation,
        progress = trackRepository.navigationProgress,
        onRenderLayers = { layers -> navigationLayers = layers },
        onClearLayers = { navigationLayers = null },
    )

    // Back exits the active mode in order: cancel a crop, close the navigation chart, stop
    // navigation, unfocus a track (the line stays), then clear the tracks.
    platform.backHandler(navigation != null || viewingTracks.isNotEmpty() || cropState != null) {
        when {
            cropState != null -> trackRepository.cancelCrop()
            navigation != null && navigationChartVisible -> trackRepository.hideNavigationChart()
            navigation != null -> state.stopNavConfirm.request()
            focusedTrackId != null -> trackRepository.setFocusedTrack(null)
            else -> trackRepository.clearViewingTracks()
        }
    }

    // Prefs are the source of truth for who is followed; follow() is a no-op for an unchanged set.
    LaunchedEffect(followedClientIds, clientNicknames) {
        follower.follow(followedClientIds, clientNicknames)
    }

    // Zoom to the friends' tracks when their first data arrives.
    LaunchedEffect(followedClientIds) { hasZoomedToFriend = false }
    LaunchedEffect(followState) {
        val following = followState as? LiveTrackingFollower.FollowState.Following ?: return@LaunchedEffect
        if (hasZoomedToFriend) return@LaunchedEffect
        val bounds = following.tracks.friendBounds() ?: return@LaunchedEffect
        hasZoomedToFriend = true
        controller.animateToBounds(bounds, maxZoom = MapZoomLevels.FRIEND_MAX)
    }

    // After a crop overwrites the track, offer a one-tap undo of the otherwise irreversible change.
    LaunchedEffect(cropUndo) {
        if (cropUndo == null) return@LaunchedEffect
        val result = state.snackbarHostState.showSnackbar(
            getString(Res.string.track_cropped),
            actionLabel = getString(Res.string.undo),
            duration = SnackbarDuration.Long,
        )
        if (result == SnackbarResult.ActionPerformed) trackRepository.undoCrop() else trackRepository.clearCropUndo()
    }

    LaunchedEffect(showCoordGrid, coordFormat) {
        if (!showCoordGrid) {
            coordGridGeoJson = null
            return@LaunchedEffect
        }
        // Sampled, not debounced: a following camera never stops moving long enough to debounce.
        snapshotFlow { Triple(cameraLat, cameraLon, cameraZoom) }
            .sample(200.milliseconds)
            .collect { (lat, lng, zoom) ->
                coordGridGeoJson = withContext(Dispatchers.Default) { CoordGrid.buildGeoJson(lat, lng, zoom, coordFormat) }
            }
    }

    // Terrain info for the crosshair, at most every 500 ms; the last value stays up while refreshing.
    // Each effect only touches its own fields of crosshairInfo.
    LaunchedEffect(crosshairActive) {
        if (!crosshairActive) return@LaunchedEffect
        crosshairInfo = crosshairInfo.copy(isLoading = true, elevation = null, slopeDegrees = null)
        snapshotFlow { centerLatLng }.filterNotNull().sample(500.milliseconds).collect { latLng ->
            val info = TerrainClient.getTerrainInfo(latLng)
            crosshairInfo = crosshairInfo.copy(isLoading = false, elevation = info?.elevation, slopeDegrees = info?.slopeDegrees)
        }
    }

    // Coverage needs a connection; the overlay and readout are off offline. Re-keyed when the version updates.
    val withCoverage = showCoverage && !offlineModeEnabled
    LaunchedEffect(crosshairActive, withCoverage, coverageTilesVersion) {
        crosshairInfo = crosshairInfo.copy(coverage = if (withCoverage) CoverageLookup.Loading else null)
        if (!crosshairActive || !withCoverage) return@LaunchedEffect
        snapshotFlow { centerLatLng }.filterNotNull().sample(500.milliseconds).collect { latLng ->
            crosshairInfo = crosshairInfo.copy(coverage = CoverageTilesClient.getCoverage(latLng, coverageTilesVersion))
        }
    }

    // Open on the last known position, unless there is something else to show.
    LaunchedEffect(hasLocationPermission, viewingTracks, currentTrack) {
        val lastKnown = platform.lastKnownLocation ?: return@LaunchedEffect
        if (!hasLocationPermission || hasZoomedToLocation || viewingTracks.isNotEmpty() || currentTrack != null) return@LaunchedEffect
        val location = lastKnown() ?: return@LaunchedEffect
        delay(500.milliseconds)
        controller.setCamera(location.latitude, location.longitude, zoom = 12.0)
        hasZoomedToLocation = true
    }

    // Re-keyed on attach so a point opened before the map binds still gets centered once it does.
    LaunchedEffect(viewingPoint, controller.isAttached) {
        val point = viewingPoint ?: return@LaunchedEffect
        controller.setCamera(point.latLng.latitude, point.latLng.longitude, zoom = 15.0)
    }

    // Frame a found route with room around it to see the terrain it avoids.
    LaunchedEffect(routePreview) {
        val bounds = routePreview?.points?.map { it.latLng }?.bounds() ?: return@LaunchedEffect
        controller.animateToBounds(bounds, zoomOut = MapZoomLevels.ROUTE_ZOOM_OUT)
    }

    // Fit the camera whenever the viewing set changes, but not when merely tap-focusing a track.
    LaunchedEffect(viewingTracks) {
        if (navigation != null) return@LaunchedEffect // the camera follows the user while navigating
        val bounds = Track.focusOrCombinedBounds(viewingTracks, focusedTrackId) ?: return@LaunchedEffect
        // A just-saved route keeps its preview's framing.
        val justSaved = viewingTracks.lastOrNull()?.id == state.routeSession.crossings.value?.trackId
        controller.animateToBounds(bounds, zoomOut = if (justSaved) MapZoomLevels.ROUTE_ZOOM_OUT else 0.0)
    }

    // Zoom in when first engaging follow (e.g. from the country overview), but not on every fix, so
    // the user can still zoom out while following.
    LaunchedEffect(cameraFollowMode) {
        if (cameraFollowMode != CameraFollowMode.OFF) controller.zoomToAtLeast(MapZoomLevels.FOLLOW_MIN)
    }

    // FOLLOW_HEADING: center on the puck and rotate. Compass when on foot or stationary, course over
    // ground while travelling (a vehicle's magnetization throws the compass off); a brief stop HELDs
    // the last course. See headingSourceFor / HeadingSource.
    val latestFix = rememberUpdatedState(fix)
    var compassBearing by remember { mutableStateOf<Double?>(null) }
    var headingSource by remember { mutableStateOf(HeadingSource.COMPASS) }
    var followedCourseAt by remember { mutableStateOf<TimeMark?>(null) }
    LaunchedEffect(cameraFollowMode) {
        if (cameraFollowMode != CameraFollowMode.FOLLOW_HEADING) return@LaunchedEffect
        platform.headingUpdates?.invoke()?.collect { compassBearing = it }
    }
    LaunchedEffect(cameraFollowMode) {
        if (cameraFollowMode != CameraFollowMode.FOLLOW_HEADING) {
            headingSource = HeadingSource.COMPASS
            followedCourseAt = null
            return@LaunchedEffect
        }
        while (true) {
            val current = latestFix.value
            val source = headingSourceFor(headingSource, current?.speed, current?.course != null, followedCourseAt?.elapsedNow())
            if (source == HeadingSource.COURSE) followedCourseAt = TimeSource.Monotonic.markNow()
            headingSource = source
            delay(500.milliseconds)
        }
    }

    // Both follow modes animate the camera per frame; FOLLOW keeps north up.
    LaunchedEffect(cameraFollowMode) {
        if (cameraFollowMode == CameraFollowMode.OFF) return@LaunchedEffect
        val animator = FollowCameraAnimator()
        while (true) {
            val frame = withFrameNanos { it }
            val current = latestFix.value
            // HELD (or a missing reading) keeps the current bearing.
            val bearing = if (cameraFollowMode == CameraFollowMode.FOLLOW) 0.0 else when (headingSource) {
                HeadingSource.COURSE -> current?.course
                HeadingSource.COMPASS -> compassBearing
                HeadingSource.HELD -> null
            }
            val moving = current != null && controller.followStep { pose -> animator.step(frame, pose, current.latLng, bearing) }
            // Settled: stop asking for frames until there is something new to follow.
            if (!moving) snapshotFlow { Triple(latestFix.value, compassBearing, headingSource) }.drop(1).first()
        }
    }

    // The coverage tile URLs contain the dataset version, so look it up whenever the overlay is shown.
    LaunchedEffect(withCoverage) {
        if (withCoverage) {
            CoverageTilesClient.fetchVersion()?.let { userPreferences.updateCoverageTilesVersion(it) }
        }
    }

    val styleJson = remember(selectedLayer, showWaymarkedTrails, showOsmPaths, nveOverlay, withCoverage, coverageTilesVersion) {
        MapStyle.getStyle(
            selectedLayer = selectedLayer,
            showWaymarkedTrails = showWaymarkedTrails,
            nveOverlay = nveOverlay,
            showOsmPaths = showOsmPaths,
            coverageVersion = coverageTilesVersion.takeIf { withCoverage },
            glyphsUrl = platform.glyphsUrl ?: MapStyle.DEFAULT_GLYPHS_URL,
        )
    }

    // All visible track lines (viewing set + recording) as one data-driven FeatureCollection. The
    // navigated track is excluded from the viewing set (it shows as the split line), so tracks here
    // are the other ones kept visible alongside it while navigating.
    val tracksGeoJson = remember(viewingTracks, focusedTrackId, currentTrack, cropState, navigation != null) {
        buildTracksGeoJson(renderableTracks(viewingTracks, focusedTrackId, currentTrack, cropState, navigating = navigation != null))
    }
    val rulerState = state.rulerState
    val renderData = WhereMapRenderData(
        styleJson = styleJson,
        initialTarget = LatLng(cameraLat, cameraLon),
        initialZoom = cameraZoom,
        tracksGeoJson = tracksGeoJson,
        elevationMarkerGeoJson = remember(elevationMarker, focusedTrackId, viewingTracks, navChartTrack) {
            buildElevationMarkerGeoJson(viewingTracks, focusedTrackId, navChartTrack, elevationMarker)
        },
        savedPointsGeoJson = remember(showSavedPoints, savedPoints) {
            if (showSavedPoints && savedPoints.isNotEmpty()) buildSavedPointsGeoJson(savedPoints) else null
        },
        friendTrackGeoJson = friendTrackGeoJson,
        mySharedPointsGeoJson = remember(mySharedPoints) {
            if (mySharedPoints.isNotEmpty()) buildSharedPointsGeoJson(mySharedPoints) else null
        },
        friendSharedPointsGeoJson = friendPointsGeoJson,
        searchResultsGeoJson = remember(state.searchResults) {
            if (state.searchResults.isNotEmpty()) buildSearchResultsGeoJson(state.searchResults) else null
        },
        searchHighlightGeoJson = remember(state.highlightedSearchResult) {
            state.highlightedSearchResult?.let { buildSearchResultsGeoJson(listOf(it)) }
        },
        // null (not an empty-coordinates LineString, which maplibre rejects and so keeps the old
        // line) so the source clears when the ruler is cleared.
        rulerLineGeoJson = remember(rulerState, routePreview) {
            when {
                routePreview != null -> buildLineGeoJson(routePreview.points.map { it.latLng })
                rulerState.points.size >= 2 -> buildRulerLineGeoJson(rulerState.points)
                else -> null
            }
        },
        rulerPointsGeoJson = remember(rulerState) {
            if (rulerState.points.isNotEmpty()) buildRulerPointsGeoJson(rulerState.points) else null
        },
        crossingsGeoJson = remember(routePreview, plannedCrossings, viewingTracks) {
            if (routePreview != null) {
                routePreview.crossings.takeIf { it.isNotEmpty() }?.let(::buildCrossingsGeoJson)
            } else {
                crossingsGeoJsonFor(plannedCrossings, viewingTracks)
            }
        },
        measurementLineGeoJson = remember(twoFingerMeasurement) { twoFingerMeasurement?.let { buildMeasurementLineGeoJson(it) } },
        measurementPointsGeoJson = remember(twoFingerMeasurement) { twoFingerMeasurement?.let { buildMeasurementPointsGeoJson(it) } },
        coordGridGeoJson = coordGridGeoJson,
        navCompletedGeoJson = navigationLayers?.completed,
        navRemainingGeoJson = navigationLayers?.remaining,
        navOffCourseGeoJson = navigationLayers?.offCourse,
        userLocation = fix?.latLng,
    )

    val mapCallbacks = WhereMapCallbacks(
        onUserGesture = { if (cameraFollowMode != CameraFollowMode.OFF) cameraFollowMode = CameraFollowMode.OFF },
        onTwoFingerTap = { ll1, ll2 ->
            twoFingerMeasurement = TwoFingerMeasurement(ll1.latitude, ll1.longitude, ll2.latitude, ll2.longitude, ll1.distanceTo(ll2))
        },
        onCameraMove = { center, zoom, bearing ->
            mapBearing = bearing
            cameraLat = center.latitude
            cameraLon = center.longitude
            cameraZoom = zoom
            centerLatLng = center
        },
        onLongPress = state::onLongPress,
        onMapClick = { latLng ->
            if (twoFingerMeasurement != null) twoFingerMeasurement = null
            if (state.rulerState.isActive) {
                state.rulerState = state.rulerState.addPoint(latLng)
                return@WhereMapCallbacks
            }
            val target = resolveMapTap(
                tap = latLng,
                zoom = cameraZoom,
                savedPoints = savedPoints,
                viewingTracks = viewingTracks,
                navigationTrack = navChartTrack,
                mySharedPoints = mySharedPoints,
                friendSharedPoints = friendSharedPoints,
            )
            when (target) {
                is MapTapTarget.Point -> state.clickedPoint = target.point
                is MapTapTarget.SharedPoint ->
                    if (target.mine) state.managingSharedPoint = target.point else state.friendPointDialog = target.point
                is MapTapTarget.TrackLine -> trackRepository.onTrackTapped(target.trackId)
                MapTapTarget.OutsideTracks -> trackRepository.onMapTapOutsideTracks()
                MapTapTarget.Nothing -> {}
            }
        },
    )

    HikingRouteSnackbars(state.routeSession, state.snackbarHostState)

    MapScreenContent(
        snackbarHostState = state.snackbarHostState,
        isRecording = isRecording,
        rulerState = rulerState,
        showLayerMenu = showLayerMenu,
        selectedLayer = selectedLayer,
        showWaymarkedTrails = showWaymarkedTrails,
        showOsmPaths = showOsmPaths,
        showSavedPoints = showSavedPoints,
        nveOverlay = nveOverlay,
        showCoverage = showCoverage,
        showCoordGrid = showCoordGrid,
        crosshairActive = crosshairActive,
        crosshairInfo = crosshairInfo,
        centerLatLng = centerLatLng,
        userLocation = fix?.latLng,
        coordFormat = coordFormat,
        onToggleCoordFormat = { userPreferences.updateCoordFormat(coordFormat.next()) },
        onCrosshairToggle = { userPreferences.updateCrosshairActive(!crosshairActive) },
        offlineModeEnabled = offlineModeEnabled,
        isLocating = isLocating,
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
        mapBearing = mapBearing,
        northLocked = northLocked,
        // Only reachable with the camera free (see compassTapAction), so there is no follow mode
        // for the animation to cancel.
        onResetNorth = { controller.resetNorth() },
        onToggleNorthLock = {
            if (!northLocked) {
                cameraFollowMode = cameraFollowMode.withoutHeading()
                controller.resetNorth() // straighten a map that is already turned off north
            }
            userPreferences.updateNorthLocked(!northLocked)
        },
        largeCompass = largeCompass,
        onToggleLargeCompass = { userPreferences.updateLargeCompass(!largeCompass) },
        navigation = NavigationUiState(
            progress = navigationProgress,
            track = navChartTrack,
            chartVisible = navigationChartVisible,
            onToggleReverse = { trackRepository.toggleNavigationReverse() },
            onStop = { state.stopNavConfirm.request() },
        ),
        viewingPointName = viewingPoint?.name,
        viewingPointColor = viewingPoint?.color ?: PointColors.DEFAULT,
        showViewingPoint = viewingPoint != null,
        showSearch = state.showSearch,
        searchQuery = state.searchQuery,
        searchResults = state.searchResults,
        searchHistory = searchHistory,
        isSearching = state.isSearching,
        onSearchClick = { state.showSearch = true },
        onLayerMenuToggle = { showLayerMenu = it },
        onLayerSelected = {
            userPreferences.updateSelectedMapLayer(it)
            showLayerMenu = false
        },
        // Overlay toggles leave the menu open so their effect on the other items (the two NVE
        // overlays replace each other) stays visible.
        onWaymarkedTrailsToggle = { userPreferences.updateShowWaymarkedTrails(!showWaymarkedTrails) },
        onOsmPathsToggle = {
            userPreferences.updateShowOsmPaths(!showOsmPaths)
            // The source has no complete path data below OSM_PATHS_MIN_ZOOM, so say so rather than
            // leave a checked menu item that draws nothing.
            if (!showOsmPaths && cameraZoom < MapStyle.OSM_PATHS_MIN_ZOOM) state.showMessage(Res.string.zoom_in_for_paths)
        },
        onNveOverlayToggle = { userPreferences.toggleNveOverlay(it) },
        onCoverageToggle = { userPreferences.updateShowCoverage(!showCoverage) },
        onCoordGridToggle = { userPreferences.updateShowCoordGrid(!showCoordGrid) },
        onSavedPointsToggle = { userPreferences.updateShowSavedPoints(!showSavedPoints) },
        onRecordStopClick = {
            if (isRecording) state.openStopTrackDialog() else platform.onStartRecording?.invoke()
        },
        cameraFollowMode = cameraFollowMode,
        onMyLocationClick = { if (hasLocationPermission) cameraFollowMode = cameraFollowMode.next(northLocked) },
        onRulerToggle = {
            val measurement = twoFingerMeasurement
            if (!state.rulerState.isActive) twoFingerMeasurement = null
            state.toggleRuler(measurement)
        },
        onSettingsClick = nav.onSettingsClick,
        onOfflineIndicatorClick = nav.onOfflineIndicatorClick,
        onOnlineTrackingClick = nav.onOnlineTrackingClick,
        onZoomIn = { controller.zoomIn() },
        onZoomOut = { controller.zoomOut() },
        onRulerUndo = state::undoRuler,
        onRulerClear = { state.rulerState = state.rulerState.clear() },
        isPlanningRoute = isPlanningRoute,
        routePreview = routePreview,
        onFindRoute = state::findHikingRoute,
        onCancelRoute = state.routeSession::clear,
        onRulerSaveAsTrack = state::openSaveRulerAsTrackDialog,
        onCloseTrack = { focusedTrackId?.let { trackRepository.removeViewingTrack(it) } },
        onCollapseTrack = { trackRepository.setFocusedTrack(null) },
        onSetTrackColor = { id, color -> trackRepository.setTrackColor(id, color) },
        onStartNavigation = {
            focusedTrackId?.let { id -> if (trackRepository.startNavigationById(id)) platform.onNavigationStarted() }
        },
        onCloseViewingPoint = nav.onClearViewingPoint,
        onSearchQueryChange = { state.searchQuery = it },
        onSearchResultClick = { result ->
            controller.setCamera(result.latLng.latitude, result.latLng.longitude, zoom = 14.0)
            state.onSearchResultClicked(result)
        },
        onSearchResultHover = { result ->
            state.highlightedSearchResult = result
            if (result != null) controller.panTo(result.latLng.latitude, result.latLng.longitude)
        },
        onSearchClose = state::closeSearch,
        followedFriends = followedFriends,
        isFollowConnecting = followState is LiveTrackingFollower.FollowState.Connecting,
        onFollowBannerClick = { clientId ->
            val following = followState as? LiveTrackingFollower.FollowState.Following ?: return@MapScreenContent
            val bounds = following.tracks.friendBounds(clientId) ?: return@MapScreenContent
            controller.animateToBounds(bounds, maxZoom = MapZoomLevels.FRIEND_MAX)
        },
        onStopFollowing = { stopFollowingAll(userPreferences, follower) },
        isMovingSharedPoint = state.movingSharedPointId != null,
        onCancelMovePoint = { state.movingSharedPointId = null },
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

    MapScreenDialogs(state, canShare, isNavigating = navigation != null)
}

private fun LatLng.coordinateText() = "${latitude.toString().take(10)}, ${longitude.toString().take(10)}"

@Composable
private fun MapScreenDialogs(state: MapScreenState, canShare: Boolean, isNavigating: Boolean) {
    val routePreview = state.routeSession.preview.collectAsState().value
    if (state.showStopTrackDialog) {
        MapDialogs.StopTrackDialog(
            trackNameInput = state.trackNameInput,
            onTrackNameChange = { state.trackNameInput = it },
            isLoading = state.isResolvingTrackName,
            onDiscard = state::discardRecording,
            onSave = state::saveRecording,
            onDismiss = state::dismissStopTrackDialog,
        )
    }

    StopNavigationConfirmDialog(
        state = state.stopNavConfirm,
        isNavigating = isNavigating,
        onConfirm = { state.trackRepository.stopNavigation() },
    )

    state.savePointLatLng?.let { latLng ->
        MapDialogs.SavePointDialog(
            pointName = state.savePointName,
            onPointNameChange = { state.savePointName = it },
            pointDescription = state.savePointDescription,
            onPointDescriptionChange = { state.savePointDescription = it },
            isLoading = state.isResolvingPointName,
            coordinates = latLng.coordinateText(),
            onSave = { state.savePoint(save = true, share = false) },
            canShare = canShare,
            onShareOnly = { state.savePoint(save = false, share = true) },
            onSaveAndShare = { state.savePoint(save = true, share = true) },
            onDismiss = state::dismissSavePointDialog,
        )
    }

    state.managingSharedPoint?.let { point ->
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
            coordinates = point.latLng.coordinateText(),
            onMove = {
                state.movingSharedPointId = point.id
                state.managingSharedPoint = null
            },
            onDelete = { state.deleteSharedPoint(point) },
            onSave = { state.updateSharedPoint(point, editName, editDescription, editColor) },
            onDismiss = { state.managingSharedPoint = null },
        )
    }

    state.friendPointDialog?.let { point ->
        MapDialogs.FriendPointDialog(
            pointName = point.name,
            pointDescription = point.description,
            coordinates = point.latLng.coordinateText(),
            onSaveLocally = { state.saveFriendPointLocally(point) },
            onDismiss = { state.friendPointDialog = null },
        )
    }

    state.clickedPoint?.let { point ->
        var editName by remember(point.id) { mutableStateOf(point.name) }
        var editDescription by remember(point.id) { mutableStateOf(point.description ?: "") }
        var editColor by remember(point.id) { mutableStateOf(point.color ?: PointColors.DEFAULT) }
        MapDialogs.PointInfoDialog(
            pointName = editName,
            pointDescription = editDescription,
            pointColor = editColor,
            coordinates = point.latLng.coordinateText(),
            availableColors = PointColors.withSelected(point.color),
            onNameChange = { editName = it },
            onDescriptionChange = { editDescription = it },
            onColorChange = { editColor = it },
            onDelete = state::deleteClickedPoint,
            onSave = { state.updateClickedPoint(editName, editDescription, editColor) },
            onDismiss = { state.clickedPoint = null },
        )
    }

    if (state.showSaveRulerAsTrackDialog) {
        MapDialogs.SaveRulerAsTrackDialog(
            trackName = state.rulerTrackName,
            pointCount = state.rulerState.points.size.takeIf { routePreview == null },
            distanceMeters = routePreview?.distanceMeters ?: state.rulerState.getTotalDistanceMeters(),
            onTrackNameChange = { state.rulerTrackName = it },
            isLoading = state.isResolvingRulerName,
            onSave = state::saveRulerAsTrack,
            onDismiss = state::dismissSaveRulerAsTrackDialog,
        )
    }
}
