package no.synth.where.ui

import no.synth.where.location.fusedLocationFlow

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import no.synth.where.data.CrosshairInfo
import no.synth.where.data.LiveTrackingFollower
import no.synth.where.data.stopFollowingAll
import no.synth.where.data.MapStyle
import no.synth.where.data.PlaceSearchClient
import no.synth.where.data.TerrainClient
import no.synth.where.resources.Res
import no.synth.where.resources.*
import org.jetbrains.compose.resources.stringResource
import no.synth.where.data.RulerPoint
import no.synth.where.data.RulerState
import no.synth.where.data.SavedPoint
import no.synth.where.data.Track
import no.synth.where.data.TrackPoint
import no.synth.where.ui.map.NavigationUiState
import no.synth.where.ui.map.rememberNavigationProgress
import no.synth.where.WhereApplication
import no.synth.where.service.LocationTrackingService
import no.synth.where.ui.map.CameraFollowMode
import no.synth.where.ui.map.HeadingSource
import no.synth.where.ui.map.headingSourceFor
import no.synth.where.ui.map.CoordGrid
import no.synth.where.ui.map.MapDialogs
import no.synth.where.ui.map.MapLayer
import no.synth.where.ui.map.NavigationLayers
import no.synth.where.ui.map.PointColors
import no.synth.where.ui.map.MapZoomLevels
import no.synth.where.ui.map.buildElevationMarkerGeoJson
import no.synth.where.ui.map.buildSharedPointsGeoJson
import no.synth.where.data.SharedPoint
import no.synth.where.ui.map.followedFriends
import no.synth.where.ui.map.friendBounds
import no.synth.where.ui.map.buildTracksGeoJson
import no.synth.where.ui.map.renderableTracks
import no.synth.where.ui.map.TwoFingerMeasurement
import no.synth.where.ui.map.MapScreenContent
import no.synth.where.ui.map.buildSavedPointsGeoJson
import no.synth.where.ui.map.buildSearchResultsGeoJson
import no.synth.where.ui.map.buildRulerLineGeoJson
import no.synth.where.ui.map.buildRulerPointsGeoJson
import no.synth.where.ui.map.buildMeasurementLineGeoJson
import no.synth.where.ui.map.buildMeasurementPointsGeoJson
import no.synth.where.ui.map.compose.WhereComposeMap
import no.synth.where.ui.map.compose.WhereMapCallbacks
import no.synth.where.ui.map.compose.WhereMapController
import org.maplibre.compose.location.AndroidHeadingProvider
import org.maplibre.spatialk.units.Bearing
import org.maplibre.spatialk.units.extensions.inDegrees
import no.synth.where.ui.map.compose.WhereMapRenderData
import no.synth.where.data.MapTapTarget
import no.synth.where.data.resolveMapTap
import no.synth.where.ui.map.StopNavigationConfirmDialog
import no.synth.where.ui.map.rememberStopNavigationConfirmState
import no.synth.where.ui.map.rememberAutoDismissingTwoFingerMeasurement
import no.synth.where.ui.map.RecordingCard
import no.synth.where.ui.map.RulerCard
import no.synth.where.ui.map.SearchOverlay
import no.synth.where.ui.map.ViewingPointBanner
import no.synth.where.ui.map.ViewingTrackBanner
import no.synth.where.data.geo.LatLng
import no.synth.where.data.geo.bounds
import no.synth.where.util.Logger
import no.synth.where.util.formatDateTime
import no.synth.where.util.currentTimeMillis
import kotlin.time.Duration.Companion.milliseconds

// MapLibre glyphs URL pointing at the PBF fonts bundled in app/src/main/assets/fonts/, so labels
// render offline without a network round-trip.
private const val ANDROID_ASSET_GLYPHS_URL = "asset://fonts/{fontstack}/{range}.pbf"

@Composable
fun MapScreen(
    onSettingsClick: () -> Unit,
    onOfflineSettingsClick: () -> Unit = {},
    onOnlineTrackingSettingsClick: () -> Unit = {},
    viewingPoint: SavedPoint? = null,
    onClearViewingPoint: () -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val app = context.applicationContext as WhereApplication
    val viewModel: MapScreenViewModel = viewModel { MapScreenViewModel(app.trackRepository, app.savedPointsRepository, app.userPreferences) }
    val savedPoints by viewModel.savedPoints.collectAsState()
    val isRecording by viewModel.isRecording.collectAsState()
    val currentTrack by viewModel.currentTrack.collectAsState()
    val viewingTracks by viewModel.viewingTracks.collectAsState()
    val focusedTrackId by viewModel.focusedTrackId.collectAsState()
    val cropState by viewModel.cropState.collectAsState()
    val cropUndo by viewModel.cropUndo.collectAsState()
    val onlineTrackingEnabled by viewModel.onlineTrackingEnabled.collectAsState()
    val viewerCount by viewModel.userPreferences.viewerCount.collectAsState()
    val liveShareUntilMillis by viewModel.userPreferences.liveShareUntilMillis.collectAsState()
    val coordinator = remember(context) { (context.applicationContext as WhereApplication).onlineTrackingCoordinator }
    val isLiveSharing by coordinator.isLiveSharing.collectAsState()
    val offlineModeEnabled by viewModel.userPreferences.offlineModeEnabled.collectAsState()
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
    val savePointDescription by viewModel.savePointDescription.collectAsState()
    val isResolvingPointName by viewModel.isResolvingPointName.collectAsState()
    val clickedPoint by viewModel.clickedPoint.collectAsState()
    val showPointInfoDialog by viewModel.showPointInfoDialog.collectAsState()
    val showSaveRulerAsTrackDialog by viewModel.showSaveRulerAsTrackDialog.collectAsState()
    val rulerTrackName by viewModel.rulerTrackName.collectAsState()
    val isResolvingRulerName by viewModel.isResolvingRulerName.collectAsState()

    val liveTrackingFollower = app.liveTrackingFollower
    val followState by liveTrackingFollower.state.collectAsState()
    val friendTrackGeoJson by liveTrackingFollower.friendTrackGeoJson.collectAsState()
    val followedClientIds by viewModel.userPreferences.followedClientIds.collectAsState()
    val clientNicknames by viewModel.userPreferences.clientNicknames.collectAsState()
    val followedFriends = remember(followedClientIds, followState, clientNicknames) {
        followedFriends(
            followedClientIds,
            (followState as? LiveTrackingFollower.FollowState.Following)?.tracks ?: emptyList(),
            clientNicknames
        )
    }

    // Prefs are the source of truth for who is followed; follow() is a no-op for an unchanged set
    // and set of labels.
    LaunchedEffect(followedClientIds, clientNicknames) {
        liveTrackingFollower.follow(followedClientIds, clientNicknames)
    }

    // Shared points (issue #99): the ones this client owns come from the coordinator, followed
    // friends' from the WebSocket follower.
    val mySharedPoints by coordinator.mySharedPoints.collectAsState()
    val canShare by coordinator.canShare.collectAsState()
    val friendSharedPoints by liveTrackingFollower.friendPoints.collectAsState()
    val friendPointsGeoJson by liveTrackingFollower.friendPointsGeoJson.collectAsState()
    val mySharedPointsGeoJson = remember(mySharedPoints) { buildSharedPointsGeoJson(mySharedPoints) }

    val controller = remember { WhereMapController() }
    val headingProvider = remember { AndroidHeadingProvider(context) }
    // A shared point this client is currently relocating (next long-press sets its new spot), and
    // the point dialogs (own point management / a friend's point).
    var movingSharedPointId by remember { mutableStateOf<String?>(null) }
    var managingSharedPoint by remember { mutableStateOf<SharedPoint?>(null) }
    var friendPointDialog by remember { mutableStateOf<SharedPoint?>(null) }

    val navigation by viewModel.navigation.collectAsState()
    val navigationChartVisible by viewModel.navigationChartVisible.collectAsState()
    // The navigated route in travel order (reversed when navigating in reverse) — feeds the altitude
    // chart, its scrub marker, and the route tap-target. Keeps the session track's id, so tap
    // routing still matches; only the point order flips so the chart reads left-to-right as "ahead".
    val navChartTrack = remember(navigation?.track?.id, navigation?.reversed) {
        navigation?.let { if (it.reversed) it.track.copy(points = it.track.points.reversed()) else it.track }
    }
    // The latest navigation route layers, held so MapLibreMapView can redraw them after a style
    // reload (layer switch / reconnect); null when not navigating.
    var navigationLayers by remember { mutableStateOf<NavigationLayers?>(null) }
    val navigationProgress = rememberNavigationProgress(
        session = navigation,
        progress = viewModel.navigationProgress,
        onRenderLayers = { layers -> navigationLayers = layers },
        onClearLayers = { navigationLayers = null },
    )

    // Stopping navigation is confirmed first so an active route isn't ended by an accidental tap.
    val stopNavConfirm = rememberStopNavigationConfirmState()

    // Back exits the active mode in order: stop navigation, then unfocus a track (chrome returns,
    // line stays), then clear the track. Handling navigation first avoids a half-exited state where
    // the route is hidden but the session keeps polling.
    BackHandler(enabled = navigation != null || viewingTracks.isNotEmpty() || cropState != null) {
        when {
            // While cropping, Back means "cancel crop" — otherwise unfocusing would hide the crop
            // UI while leaving the crop session dangling.
            cropState != null -> viewModel.cancelCrop()
            // Back closes the tapped-open altitude chart before ending the session.
            navigation != null && navigationChartVisible -> viewModel.hideNavigationChart()
            navigation != null -> stopNavConfirm.request()
            focusedTrackId != null -> viewModel.unfocusTrack()
            else -> viewModel.clearViewingTracks()
        }
    }

    // Zoom to friend track when first data arrives
    var hasZoomedToFriend by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(followedClientIds) {
        hasZoomedToFriend = false
    }
    LaunchedEffect(followState) {
        val following = followState as? LiveTrackingFollower.FollowState.Following ?: return@LaunchedEffect
        if (hasZoomedToFriend) return@LaunchedEffect
        val bounds = following.tracks.friendBounds() ?: return@LaunchedEffect
        hasZoomedToFriend = true
        controller.animateToBounds(bounds, maxZoom = MapZoomLevels.FRIEND_MAX.toDouble())
    }
    var cameraFollowMode by remember { mutableStateOf(CameraFollowMode.OFF) }
    var highlightedSearchResult by remember { mutableStateOf<PlaceSearchClient.SearchResult?>(null) }
    var mapBearing by remember { mutableDoubleStateOf(0.0) }
    val northLocked by viewModel.userPreferences.northLocked.collectAsState()
    val selectedLayer by viewModel.userPreferences.selectedMapLayer.collectAsState()
    var showLayerMenu by remember { mutableStateOf(false) }
    val showWaymarkedTrails by viewModel.userPreferences.showWaymarkedTrails.collectAsState()
    val showOsmPaths by viewModel.userPreferences.showOsmPaths.collectAsState()
    val nveOverlay by viewModel.userPreferences.nveOverlay.collectAsState()
    val showCoordGrid by viewModel.userPreferences.showCoordGrid.collectAsState()
    val styleJson = remember(selectedLayer, showWaymarkedTrails, showOsmPaths, nveOverlay) {
        MapStyle.getStyle(
            selectedLayer = selectedLayer,
            showWaymarkedTrails = showWaymarkedTrails,
            nveOverlay = nveOverlay,
            showOsmPaths = showOsmPaths,
            glyphsUrl = ANDROID_ASSET_GLYPHS_URL,
        )
    }
    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(
                    context, Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
        )
    }
    var hasBackgroundLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    var showBackgroundLocationDisclosure by remember { mutableStateOf(false) }
    var pendingRecordStart by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val crosshairActive by viewModel.userPreferences.crosshairActive.collectAsState()
    var crosshairInfo by remember { mutableStateOf(CrosshairInfo()) }
    val coordFormat by viewModel.userPreferences.coordFormat.collectAsState()
    var centerLatLng by remember { mutableStateOf<LatLng?>(null) }
    var userLocation by remember { mutableStateOf<LatLng?>(null) }
    var twoFingerMeasurement by rememberAutoDismissingTwoFingerMeasurement()

    var hasZoomedToLocation by rememberSaveable { mutableStateOf(false) }
    var hasFix by remember { mutableStateOf(false) }
    val isLocating = hasLocationPermission && !hasFix

    // Save camera position across navigation
    var savedCameraLat by rememberSaveable { mutableDoubleStateOf(65.0) }
    var savedCameraLon by rememberSaveable { mutableDoubleStateOf(10.0) }
    var savedCameraZoom by rememberSaveable { mutableDoubleStateOf(5.0) }

    var coordGridGeoJson by remember { mutableStateOf<String?>(null) }

    @OptIn(FlowPreview::class)
    LaunchedEffect(showCoordGrid, coordFormat) {
        if (!showCoordGrid) {
            coordGridGeoJson = null
            return@LaunchedEffect
        }
        snapshotFlow { Triple(savedCameraLat, savedCameraLon, savedCameraZoom) }
            .debounce(200.milliseconds)
            .collect { (lat, lng, zoom) ->
                coordGridGeoJson = withContext(Dispatchers.Default) {
                    CoordGrid.buildGeoJson(lat, lng, zoom, coordFormat)
                }
            }
    }

    // Pre-resolve string resources for use in lambdas
    val recordingMsg = stringResource(Res.string.recording_snackbar)
    val trackDiscardedMsg = stringResource(Res.string.track_discarded)
    val trackSavedMsg = stringResource(Res.string.track_saved)
    val pointSavedMsg = stringResource(Res.string.point_saved)
    val pointDeletedMsg = stringResource(Res.string.point_deleted)
    val pointUpdatedMsg = stringResource(Res.string.point_updated)
    val pointSharedMsg = stringResource(Res.string.point_shared_snackbar)
    val pointSavedLocallyMsg = stringResource(Res.string.point_saved_locally)
    val trackCroppedMsg = stringResource(Res.string.track_cropped)
    val zoomInForPathsMsg = stringResource(Res.string.zoom_in_for_paths)
    val undoLabel = stringResource(Res.string.undo)

    // After a crop overwrites the track, offer a one-tap undo of the (otherwise irreversible) change.
    LaunchedEffect(cropUndo) {
        if (cropUndo == null) return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(
            trackCroppedMsg,
            actionLabel = undoLabel,
            duration = SnackbarDuration.Long
        )
        if (result == SnackbarResult.ActionPerformed) viewModel.undoCrop() else viewModel.clearCropUndo()
    }

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
        // POST_NOTIFICATIONS must be requested at runtime on targetSdk 33+, or the
        // recording/navigating/sharing foreground-service notifications are silently dropped.
        permissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.POST_NOTIFICATIONS
            )
        )
    }

    // All visible track lines (viewing set + recording) as one data-driven FeatureCollection.
    // The navigated track is excluded from the viewing set (it shows as the grey/blue split line),
    // so any tracks here are the "other" tracks kept visible alongside it while navigating.
    val tracksGeoJson = remember(viewingTracks, focusedTrackId, currentTrack, cropState, navigation != null) {
        buildTracksGeoJson(
            renderableTracks(viewingTracks, focusedTrackId, currentTrack, cropState, navigating = navigation != null)
        )
    }

    val elevationMarker by viewModel.elevationMarker.collectAsState()
    val elevationMarkerGeoJson = remember(elevationMarker, focusedTrackId, viewingTracks, navChartTrack) {
        buildElevationMarkerGeoJson(viewingTracks, focusedTrackId, navChartTrack, elevationMarker)
    }

    // Fit the camera whenever the viewing set changes (adding/removing a track), but not when
    // merely tap-focusing one (focusedTrackId is deliberately not a key).
    LaunchedEffect(viewingTracks) {
        if (navigation != null) return@LaunchedEffect   // the camera follows the user while navigating
        val bounds = Track.focusOrCombinedBounds(viewingTracks, focusedTrackId) ?: return@LaunchedEffect
        controller.animateToBounds(bounds)
    }

    var lastFix by remember { mutableStateOf<android.location.Location?>(null) }

    // Subscribe to live location updates while the screen is visible, so the puck tracks movement
    // during ordinary browsing (the tracking service only runs while recording/navigating/sharing,
    // and getLastKnownLocation never refreshes). Gated to STARTED; removed on stop.
    LaunchedEffect(hasLocationPermission) {
        if (!hasLocationPermission) {
            hasFix = false
            return@LaunchedEffect
        }
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            fusedLocationFlow(context).collect { loc ->
                hasFix = true
                userLocation = LatLng(loc.latitude, loc.longitude)
                lastFix = loc
            }
        }
    }

    // Debounced terrain info fetch when crosshair is active
    LaunchedEffect(crosshairActive, centerLatLng) {
        val latLng = centerLatLng ?: return@LaunchedEffect
        if (!crosshairActive) return@LaunchedEffect
        crosshairInfo = CrosshairInfo(isLoading = true)
        delay(500.milliseconds)
        val info = TerrainClient.getTerrainInfo(latLng)
        crosshairInfo = if (info != null) {
            CrosshairInfo(elevation = info.elevation, slopeDegrees = info.slopeDegrees)
        } else {
            CrosshairInfo()
        }
    }

    LaunchedEffect(hasLocationPermission, viewingTracks, currentTrack) {
        if (hasLocationPermission && !hasZoomedToLocation && viewingTracks.isEmpty() && currentTrack == null) {
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
                    delay(500.milliseconds)
                    controller.setCamera(location.latitude, location.longitude, zoom = 12.0)
                    hasZoomedToLocation = true
                }
            } catch (e: Exception) {
                Logger.e(e, "Map screen error")
            }
        }
    }

    LaunchedEffect(viewingPoint) {
        val point = viewingPoint ?: return@LaunchedEffect
        controller.setCamera(point.latLng.latitude, point.latLng.longitude, zoom = 15.0)
    }

    // FOLLOW: keep the puck centered, north up.
    LaunchedEffect(cameraFollowMode, userLocation) {
        if (cameraFollowMode != CameraFollowMode.FOLLOW) return@LaunchedEffect
        val loc = userLocation ?: return@LaunchedEffect
        controller.follow(loc.latitude, loc.longitude, 0.0)
    }

    // FOLLOW_HEADING: center on the puck and rotate. Compass when on foot/stationary, course over
    // ground while travelling (a vehicle's magnetization throws the compass off); a brief stop HELDs
    // the last course. See headingSourceFor / HeadingSource.
    val latestLoc = rememberUpdatedState(userLocation)
    val latestFix = rememberUpdatedState(lastFix)
    var compassBearing by remember { mutableStateOf<Double?>(null) }
    var headingSource by remember { mutableStateOf(HeadingSource.COMPASS) }
    var followedCourseAt by remember { mutableStateOf<kotlin.time.TimeMark?>(null) }
    LaunchedEffect(cameraFollowMode) {
        if (cameraFollowMode != CameraFollowMode.FOLLOW_HEADING) return@LaunchedEffect
        headingProvider.updates().collect { compassBearing = (it.bearing - Bearing.North).inDegrees }
    }
    LaunchedEffect(cameraFollowMode) {
        if (cameraFollowMode != CameraFollowMode.FOLLOW_HEADING) {
            headingSource = HeadingSource.COMPASS
            followedCourseAt = null
            return@LaunchedEffect
        }
        while (true) {
            val fix = latestFix.value
            val speed = fix?.takeIf { it.hasSpeed() }?.speed?.toDouble()
            val course = fix?.takeIf { it.hasBearing() }?.bearing?.toDouble()
            val source = headingSourceFor(headingSource, speed, course != null, followedCourseAt?.elapsedNow())
            if (source == HeadingSource.COURSE) followedCourseAt = kotlin.time.TimeSource.Monotonic.markNow()
            headingSource = source
            // Keep centering on every fix; HELD (or a missing reading) keeps the current bearing.
            val bearing = when (source) {
                HeadingSource.COURSE -> course
                HeadingSource.COMPASS -> compassBearing
                HeadingSource.HELD -> null
            } ?: controller.cameraBearing
            val loc = latestLoc.value
            if (loc != null) controller.follow(loc.latitude, loc.longitude, bearing)
            delay(500.milliseconds)
        }
    }

    // Overlay GeoJSON for the reactive shared renderer.
    val savedPointsJson = remember(showSavedPoints, savedPoints) {
        if (showSavedPoints && savedPoints.isNotEmpty()) buildSavedPointsGeoJson(savedPoints) else null
    }
    val mySharedPointsJson = remember(mySharedPoints) {
        if (mySharedPoints.isNotEmpty()) mySharedPointsGeoJson else null
    }
    val searchResultsJson = remember(searchResults) {
        if (searchResults.isNotEmpty()) buildSearchResultsGeoJson(searchResults) else null
    }
    val searchHighlightJson = remember(highlightedSearchResult) {
        highlightedSearchResult?.let { buildSearchResultsGeoJson(listOf(it)) }
    }
    // null (not an empty-coordinates LineString, which maplibre rejects and so keeps the old line)
    // so the reactive source clears to an empty FeatureCollection when the ruler is cleared.
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
        initialTarget = LatLng(savedCameraLat, savedCameraLon),
        initialZoom = savedCameraZoom,
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
        measurementLineGeoJson = measurementLineJson,
        measurementPointsGeoJson = measurementPointsJson,
        coordGridGeoJson = coordGridGeoJson,
        navCompletedGeoJson = navigationLayers?.completed,
        navRemainingGeoJson = navigationLayers?.remaining,
        navOffCourseGeoJson = navigationLayers?.offCourse,
        userLocation = userLocation,
    )

    val mapCallbacks =
        WhereMapCallbacks(
            onUserGesture = { if (cameraFollowMode != CameraFollowMode.OFF) cameraFollowMode = CameraFollowMode.OFF },
            onTwoFingerTap = { ll1, ll2 ->
                twoFingerMeasurement = TwoFingerMeasurement(
                    ll1.latitude, ll1.longitude, ll2.latitude, ll2.longitude, ll1.distanceTo(ll2)
                )
            },
            onCameraMove = { center, zoom, bearing ->
                mapBearing = bearing
                savedCameraLat = center.latitude
                savedCameraLon = center.longitude
                savedCameraZoom = zoom
                centerLatLng = center
            },
            onLongPress = { latLng ->
                if (rulerState.isActive) return@WhereMapCallbacks
                val movingId = movingSharedPointId
                if (movingId != null) {
                    coordinator.moveSharedPoint(movingId, latLng)
                    movingSharedPointId = null
                } else {
                    viewModel.openSavePointDialog(latLng)
                }
            },
            onMapClick = { latLng ->
                if (twoFingerMeasurement != null) twoFingerMeasurement = null
                if (rulerState.isActive) {
                    viewModel.addRulerPoint(latLng)
                    return@WhereMapCallbacks
                }
                val target = resolveMapTap(
                    tap = latLng,
                    zoom = savedCameraZoom,
                    savedPoints = savedPoints,
                    viewingTracks = viewingTracks,
                    navigationTrack = navChartTrack,
                    mySharedPoints = mySharedPoints,
                    friendSharedPoints = friendSharedPoints,
                )
                when (target) {
                    is MapTapTarget.Point -> viewModel.openPointInfoDialog(target.point)
                    is MapTapTarget.SharedPoint ->
                        if (target.mine) managingSharedPoint = target.point else friendPointDialog = target.point
                    is MapTapTarget.TrackLine -> viewModel.onTrackTapped(target.trackId)
                    MapTapTarget.OutsideTracks -> viewModel.onMapTapOutsideTracks()
                    MapTapTarget.Nothing -> {}
                }
            },
        )

    MapScreenContent(
        snackbarHostState = snackbarHostState,
        isRecording = isRecording,
        rulerState = rulerState,
        showLayerMenu = showLayerMenu,
        selectedLayer = selectedLayer,
        showWaymarkedTrails = showWaymarkedTrails,
        showOsmPaths = showOsmPaths,
        showSavedPoints = showSavedPoints,
        nveOverlay = nveOverlay,
        showCoordGrid = showCoordGrid,
        crosshairActive = crosshairActive,
        crosshairInfo = crosshairInfo,
        centerLatLng = centerLatLng,
        userLocation = userLocation,
        coordFormat = coordFormat,
        onToggleCoordFormat = { viewModel.userPreferences.updateCoordFormat(coordFormat.next()) },
        onCrosshairToggle = { viewModel.userPreferences.updateCrosshairActive(!crosshairActive) },
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
        onCropChange = { start, end -> viewModel.updateCrop(start, end) },
        onCancelCrop = { viewModel.cancelCrop() },
        onApplyCrop = { viewModel.applyCrop() },
        elevationMarker = elevationMarker,
        onElevationScrub = { viewModel.setElevationMarker(it) },
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
            viewModel.userPreferences.updateNorthLocked(!northLocked)
        },
        navigation = NavigationUiState(
            progress = navigationProgress,
            track = navChartTrack,
            chartVisible = navigationChartVisible,
            onToggleReverse = { viewModel.toggleNavigationReverse() },
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
        onSearchClick = { viewModel.openSearch() },
        onLayerMenuToggle = { showLayerMenu = it },
        onLayerSelected = { viewModel.userPreferences.updateSelectedMapLayer(it); showLayerMenu = false },
        // Overlay toggles leave the menu open (as on iOS) so their effect on the other
        // items -- the two NVE overlays replace each other -- stays visible.
        onWaymarkedTrailsToggle = { viewModel.userPreferences.updateShowWaymarkedTrails(!showWaymarkedTrails) },
        onOsmPathsToggle = {
            viewModel.userPreferences.updateShowOsmPaths(!showOsmPaths)
            // The source carries no complete path data below OSM_PATHS_MIN_ZOOM, so say so rather
            // than leaving a checked menu item that draws nothing.
            if (!showOsmPaths && savedCameraZoom < MapStyle.OSM_PATHS_MIN_ZOOM) {
                scope.launch { snackbarHostState.showSnackbar(zoomInForPathsMsg) }
            }
        },
        onNveOverlayToggle = { viewModel.userPreferences.toggleNveOverlay(it) },
        onCoordGridToggle = { viewModel.userPreferences.updateShowCoordGrid(!showCoordGrid) },
        onSavedPointsToggle = { viewModel.userPreferences.updateShowSavedPoints(!showSavedPoints) },
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
        cameraFollowMode = cameraFollowMode,
        onMyLocationClick = {
            // Cycle OFF -> FOLLOW -> FOLLOW_HEADING; the LaunchedEffect on cameraFollowMode applies
            // it to the location component (centering, and compass rotation for heading mode).
            if (hasLocationPermission) {
                cameraFollowMode = cameraFollowMode.next(northLocked)
            }
        },
        onRulerToggle = {
            val measurement = twoFingerMeasurement
            if (!rulerState.isActive && measurement != null) {
                viewModel.startRulerFrom(measurement.endpoints)
                twoFingerMeasurement = null
            } else {
                viewModel.toggleRuler()
            }
        },
        onSettingsClick = onSettingsClick,
        onZoomIn = { controller.zoomIn() },
        onZoomOut = { controller.zoomOut() },
        onRulerUndo = { viewModel.removeLastRulerPoint() },
        onRulerClear = { viewModel.clearRuler() },
        onRulerSaveAsTrack = { viewModel.openSaveRulerAsTrackDialog() },
        onOfflineIndicatorClick = onOfflineSettingsClick,
        onOnlineTrackingClick = onOnlineTrackingSettingsClick,
        onCloseTrack = { focusedTrackId?.let { viewModel.removeViewingTrack(it) } },
        onCollapseTrack = { viewModel.unfocusTrack() },
        onSetTrackColor = { id, color -> viewModel.setTrackColor(id, color) },
        onStartNavigation = {
            // The foreground service owns the location stream and notification while navigating; it
            // self-stops when navigation ends. Only start it if navigation actually began.
            focusedTrackId?.let { id ->
                if (viewModel.startNavigation(id)) LocationTrackingService.start(context)
            }
        },
        onCloseViewingPoint = onClearViewingPoint,
        onSearchQueryChange = { viewModel.updateSearchQuery(it) },
        onSearchResultClick = { result ->
            highlightedSearchResult = null
            controller.setCamera(result.latLng.latitude, result.latLng.longitude, zoom = 14.0)
            viewModel.userPreferences.addSearchHistoryEntry(result)
            viewModel.onSearchResultClicked()
        },
        onSearchResultHover = { result ->
            highlightedSearchResult = result
            if (result != null) {
                controller.panTo(result.latLng.latitude, result.latLng.longitude)
            }
        },
        onSearchClose = {
            highlightedSearchResult = null
            viewModel.closeSearch()
        },
        followedFriends = followedFriends,
        isFollowConnecting = followState is LiveTrackingFollower.FollowState.Connecting,
        onFollowBannerClick = { clientId ->
            val following = followState as? LiveTrackingFollower.FollowState.Following ?: return@MapScreenContent
            val bounds = following.tracks.friendBounds(clientId) ?: return@MapScreenContent
            controller.animateToBounds(bounds, maxZoom = MapZoomLevels.FRIEND_MAX.toDouble())
        },
        onStopFollowing = { stopFollowingAll(viewModel.userPreferences, liveTrackingFollower) },
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
        }
    )

    if (showStopTrackDialog) {
        MapDialogs.StopTrackDialog(
            trackNameInput = trackNameInput,
            onTrackNameChange = { viewModel.updateTrackNameInput(it) },
            isLoading = isResolvingTrackName,
            onDiscard = {
                viewModel.discardRecording()
                scope.launch {
                    snackbarHostState.showSnackbar(trackDiscardedMsg)
                }
            },
            onSave = {
                viewModel.saveRecording()
                scope.launch {
                    snackbarHostState.showSnackbar(trackSavedMsg)
                }
            },
            onDismiss = { viewModel.dismissStopTrackDialog() }
        )
    }

    StopNavigationConfirmDialog(
        state = stopNavConfirm,
        isNavigating = navigation != null,
        onConfirm = { viewModel.stopNavigation() }
    )

    if (showSavePointDialog && savePointLatLng != null) {
        val latLng = savePointLatLng ?: return
        MapDialogs.SavePointDialog(
            pointName = savePointName,
            onPointNameChange = { viewModel.updateSavePointName(it) },
            pointDescription = savePointDescription,
            onPointDescriptionChange = { viewModel.updateSavePointDescription(it) },
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
            canShare = canShare,
            onShareOnly = {
                coordinator.addSharedPoint(savePointName, savePointDescription, latLng, PointColors.DEFAULT)
                viewModel.dismissSavePointDialog()
                scope.launch { snackbarHostState.showSnackbar(pointSharedMsg) }
            },
            onSaveAndShare = {
                viewModel.savePoint()
                coordinator.addSharedPoint(savePointName, savePointDescription, latLng, PointColors.DEFAULT)
                scope.launch { snackbarHostState.showSnackbar(pointSharedMsg) }
            },
            onDismiss = { viewModel.dismissSavePointDialog() }
        )
    }

    managingSharedPoint?.let { point ->
        var editName by remember(point.id) { mutableStateOf(point.name) }
        var editDescription by remember(point.id) { mutableStateOf(point.description) }
        var editColor by remember(point.id) { mutableStateOf(point.color) }
        val colors = PointColors.withSelected(point.color)
        MapDialogs.ManageSharedPointDialog(
            pointName = editName,
            onNameChange = { editName = it },
            pointDescription = editDescription,
            onDescriptionChange = { editDescription = it },
            pointColor = editColor,
            onColorChange = { editColor = it },
            availableColors = colors,
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
            onDismiss = { managingSharedPoint = null }
        )
    }

    friendPointDialog?.let { point ->
        MapDialogs.FriendPointDialog(
            pointName = point.name,
            pointDescription = point.description,
            coordinates = "${point.latLng.latitude.toString().take(10)}, ${point.latLng.longitude.toString().take(10)}",
            onSaveLocally = {
                viewModel.savedPointsRepository.addPoint(
                    name = point.name,
                    latLng = point.latLng,
                    description = point.description,
                    color = point.color
                )
                friendPointDialog = null
                scope.launch { snackbarHostState.showSnackbar(pointSavedLocallyMsg) }
            },
            onDismiss = { friendPointDialog = null }
        )
    }

    clickedPoint?.let { point ->
        if (!showPointInfoDialog) return@let
        var editName by remember(point.id) { mutableStateOf(point.name) }
        var editDescription by remember(point.id) { mutableStateOf(point.description ?: "") }
        var editColor by remember(point.id) { mutableStateOf(point.color ?: PointColors.DEFAULT) }

        val colors = PointColors.withSelected(point.color)

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

private val sampleTrack = Track(
    name = "Bymarka → Lian",
    points = listOf(
        TrackPoint(LatLng(63.43, 10.39), timestamp = 0L, altitude = 120.0),
        TrackPoint(LatLng(63.435, 10.40), timestamp = 1L, altitude = 180.0),
        TrackPoint(LatLng(63.44, 10.41), timestamp = 2L, altitude = 150.0),
    ),
    startTime = System.currentTimeMillis() - 42 * 60 * 1000,
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
            track = sampleTrack,
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
            onStartNavigation = {},
            onCloseTrack = {},
            onCollapse = {}
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
            showOsmPaths = false,
            showSavedPoints = true,
            nveOverlay = null,
            onlineTrackingEnabled = false,
            recordingTrack = sampleTrack,
            viewingTracks = listOf(sampleTrack),
            focusedTrackId = sampleTrack.id,
            viewingPointName = null,
            viewingPointColor = PointColors.DEFAULT,
            showViewingPoint = false,
            showSearch = false,
            searchQuery = "",
            searchResults = emptyList(),
            isSearching = false,
            onSearchClick = {},
            onLayerMenuToggle = {},
            onLayerSelected = {},
            onWaymarkedTrailsToggle = {},
            onOsmPathsToggle = {},
            onNveOverlayToggle = {},
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
            onCloseTrack = {},
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
