package no.synth.where.ui.map

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import kotlin.time.TimeMark
import kotlin.time.TimeSource
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import android.view.MotionEvent
import no.synth.where.data.MapStyle
import no.synth.where.data.MapTapTarget
import no.synth.where.data.PlaceSearchClient
import no.synth.where.data.RulerState
import no.synth.where.data.Track
import no.synth.where.data.resolveMapTap
import no.synth.where.location.GpsKeepAlive
import no.synth.where.data.geo.LatLng
import no.synth.where.data.geo.toCommon
import no.synth.where.data.geo.toMapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import no.synth.where.util.Logger

/**
 * MapLibre `glyphs:` URL pointing at PBF font files bundled in
 * `app/src/main/assets/fonts/`. Keeps label rendering working offline and
 * avoids a network round-trip to protomaps.github.io for every glyph range.
 */
private const val ANDROID_ASSET_GLYPHS_URL = "asset://fonts/{fontstack}/{range}.pbf"

/** How often the current fix is checked for the speed that picks the heading reference. */
private const val HEADING_SOURCE_POLL_MS = 2000L

@Composable
fun MapLibreMapView(
    onMapReady: (MapLibreMap) -> Unit = {},
    selectedLayer: MapLayer = MapLayer.KARTVERKET,
    hasLocationPermission: Boolean = false,
    isRecording: Boolean = false,
    showWaymarkedTrails: Boolean = false,
    showOsmPaths: Boolean = false,
    nveOverlay: NveOverlay? = null,
    showSavedPoints: Boolean = true,
    savedPoints: List<no.synth.where.data.SavedPoint> = emptyList(),
    currentTrack: Track? = null,
    viewingTracks: List<Track> = emptyList(),
    navigationTrack: Track? = null,
    tracksGeoJson: String = "",
    elevationMarkerGeoJson: String = "",
    savedCameraLat: Double = 65.0,
    savedCameraLon: Double = 10.0,
    savedCameraZoom: Double = 5.0,
    rulerState: RulerState = RulerState(),
    searchResults: List<PlaceSearchClient.SearchResult> = emptyList(),
    highlightedSearchResult: PlaceSearchClient.SearchResult? = null,
    friendTrackGeoJson: String? = null,
    mySharedPointsGeoJson: String? = null,
    friendPointsGeoJson: String? = null,
    mySharedPoints: List<no.synth.where.data.SharedPoint> = emptyList(),
    friendSharedPoints: List<no.synth.where.data.SharedPoint> = emptyList(),
    onRulerPointAdded: (LatLng) -> Unit = {},
    onLongPress: (LatLng) -> Unit = {},
    onPointClick: (no.synth.where.data.SavedPoint) -> Unit = {},
    onSharedPointClick: (no.synth.where.data.SharedPoint, Boolean) -> Unit = { _, _ -> },
    onTrackClick: (String) -> Unit = {},
    onMapClickOutsideTrack: () -> Unit = {},
    onTwoFingerMeasure: (TwoFingerMeasurement?) -> Unit = {},
    twoFingerMeasurement: TwoFingerMeasurement? = null,
    coordGridGeoJson: String? = null,
    navigationLayers: NavigationLayers? = null,
    cameraFollowMode: CameraFollowMode = CameraFollowMode.OFF,
    onFollowModeDismissed: () -> Unit = {},
    northLocked: Boolean = false
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    var mapView by remember { mutableStateOf<MapView?>(null) }
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    val context = LocalContext.current
    var isOnline by remember { mutableStateOf(true) }
    val gpsKeepAlive = remember(context) { GpsKeepAlive(context) }
    val hasLocationPermissionState = rememberUpdatedState(hasLocationPermission)
    val isRecordingState = rememberUpdatedState(isRecording)
    // Kept current so the async onStyleLoaded callbacks redraw the latest overlays without the
    // style-reload effects needing to key on them.
    val tracksGeoJsonState = rememberUpdatedState(tracksGeoJson)
    val elevationMarkerGeoJsonState = rememberUpdatedState(elevationMarkerGeoJson)
    val viewingTracksState = rememberUpdatedState(viewingTracks)
    val navigationTrackState = rememberUpdatedState(navigationTrack)
    val rulerStateState = rememberUpdatedState(rulerState)
    val twoFingerMeasurementState = rememberUpdatedState(twoFingerMeasurement)
    val friendTrackGeoJsonState = rememberUpdatedState(friendTrackGeoJson)
    val mySharedPointsGeoJsonState = rememberUpdatedState(mySharedPointsGeoJson)
    val friendPointsGeoJsonState = rememberUpdatedState(friendPointsGeoJson)
    val mySharedPointsState = rememberUpdatedState(mySharedPoints)
    val friendSharedPointsState = rememberUpdatedState(friendSharedPoints)
    val coordGridGeoJsonState = rememberUpdatedState(coordGridGeoJson)
    val savedPointsState = rememberUpdatedState(savedPoints)
    val showSavedPointsState = rememberUpdatedState(showSavedPoints)
    val searchResultsState = rememberUpdatedState(searchResults)
    val highlightedSearchResultState = rememberUpdatedState(highlightedSearchResult)
    val navigationLayersState = rememberUpdatedState(navigationLayers)
    // Read live so reapplyOverlays restores the follow mode after a style reload (activating the
    // location component resets its cameraMode to NONE).
    val cameraFollowModeState = rememberUpdatedState(cameraFollowMode)
    val northLockedState = rememberUpdatedState(northLocked)
    val onFollowModeDismissedState = rememberUpdatedState(onFollowModeDismissed)
    // Compass or course over ground; polled from the fix below, and read live so a style reload
    // restores whichever is in use rather than dropping back to the compass.
    var headingSource by remember { mutableStateOf(HeadingSource.COMPASS) }
    val headingSourceState = rememberUpdatedState(headingSource)
    // When the course was last the reference, so a stop knows how long it may keep pointing that way.
    var followedCourseAt by remember { mutableStateOf<TimeMark?>(null) }
    // The gesture-dismiss listener is registered exactly once, after the component is first enabled.
    var trackingListenerAdded by remember { mutableStateOf(false) }

    // Enable the location component (needs a fix), register the one-shot gesture-dismiss listener,
    // and (re)apply the current follow mode. Safe to call repeatedly; no-ops until a fix exists.
    // snapZoom is left false here so restoring the mode after a style reload never re-zooms.
    fun engageLocationComponent(mapInstance: MapLibreMap, style: Style) {
        MapRenderUtils.enableLocationComponent(mapInstance, style, context, hasLocationPermissionState.value)
        if (!trackingListenerAdded && mapInstance.isLocationComponentEnabledSafe) {
            mapInstance.locationComponent.addOnCameraTrackingChangedListener(
                object : org.maplibre.android.location.OnCameraTrackingChangedListener {
                    // Fired when a pan/rotate gesture breaks the camera away from the puck.
                    override fun onCameraTrackingDismissed() = onFollowModeDismissedState.value()
                    override fun onCameraTrackingChanged(currentMode: Int) {}
                }
            )
            trackingListenerAdded = true
        }
        mapInstance.applyFollowMode(
            cameraFollowModeState.value,
            northLocked = northLockedState.value,
            headingSource = headingSourceState.value,
        )
    }

    // Single owner of the keep-alive policy: run only while resumed with permission, and never
    // alongside the recording service, whose fused subscription already keeps the chip warm
    // (the extra GPS_PROVIDER subscription would burn ~5%/hr for no benefit).
    fun syncGpsKeepAlive() {
        val eligible = lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) &&
            hasLocationPermissionState.value && !isRecordingState.value
        if (eligible) gpsKeepAlive.start() else gpsKeepAlive.stop()
    }

    // setStyle wipes every runtime layer, so each style (re)load must redraw the whole overlay set.
    // Reads live values via the rememberUpdatedState holders so a reload restores the current
    // overlays -- including the navigation route, which otherwise vanishes on a layer switch or
    // reconnect until the next distinct GPS fix (the nav render effect only fires on progress change).
    fun reapplyOverlays(mapInstance: MapLibreMap, style: Style) {
        engageLocationComponent(mapInstance, style)
        MapRenderUtils.updateCoordGridOnMap(style, coordGridGeoJsonState.value)
        MapRenderUtils.updateTracksOnMap(style, tracksGeoJsonState.value)
        MapRenderUtils.updateElevationMarkerOnMap(style, elevationMarkerGeoJsonState.value)
        MapRenderUtils.updateRulerOnMap(style, rulerStateState.value)
        MapRenderUtils.updateMeasurementOnMap(style, twoFingerMeasurementState.value)
        MapRenderUtils.updateFriendTrackOnMap(style, friendTrackGeoJsonState.value)
        MapRenderUtils.updateSharedPointsOnMap(style, friendPointsGeoJsonState.value, "friend-shared-points")
        MapRenderUtils.updateSharedPointsOnMap(style, mySharedPointsGeoJsonState.value, "my-shared-points")
        if (showSavedPointsState.value && savedPointsState.value.isNotEmpty()) {
            MapRenderUtils.updateSavedPointsOnMap(style, savedPointsState.value)
        }
        // Search markers are added by effects that only key on the query results, so without these
        // two a style reload (layer switch, overlay toggle, reconnect) would drop them until the
        // next search.
        MapRenderUtils.updateSearchResultsOnMap(style, searchResultsState.value)
        MapRenderUtils.updateHighlightedSearchResult(style, highlightedSearchResultState.value)
        navigationLayersState.value?.let {
            MapRenderUtils.updateNavigationOnMap(style, it.completed, it.remaining, it.offCourse)
        }
    }

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

    // One owner of the style JSON, so adding an overlay flag cannot leave a stale copy behind.
    val styleJson = remember(selectedLayer, showWaymarkedTrails, showOsmPaths, nveOverlay) {
        MapStyle.getStyle(
            selectedLayer = selectedLayer,
            showWaymarkedTrails = showWaymarkedTrails,
            nveOverlay = nveOverlay,
            showOsmPaths = showOsmPaths,
            glyphsUrl = ANDROID_ASSET_GLYPHS_URL,
        )
    }

    // The only place the style is applied: on the first map, on a layer/overlay change, and on
    // reconnect (a style loaded while offline has empty sources). setStyle wipes every runtime
    // layer, so each load ends in reapplyOverlays. Saved points are deliberately not a key -- they
    // have their own effect, and rebuilding the style for them would re-fetch every tile.
    LaunchedEffect(styleJson, isOnline, map) {
        map?.let { mapInstance ->
            try {
                val current = currentTrack
                val hasNoTracks = viewingTracks.isEmpty() && current == null
                mapInstance.setStyle(
                    Style.Builder().fromJson(styleJson),
                    object : Style.OnStyleLoaded {
                        override fun onStyleLoaded(style: Style) {
                            reapplyOverlays(mapInstance, style)
                            if (hasNoTracks) {
                                mapInstance.cameraPosition = CameraPosition.Builder()
                                    .target(LatLng(savedCameraLat, savedCameraLon).toMapLibre())
                                    .zoom(savedCameraZoom)
                                    .build()
                            }
                            mapInstance.triggerRepaint()
                        }
                    })
            } catch (e: Exception) {
                Logger.e(e, "Map screen error")
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

    // The user cycled the FAB: apply the new mode and snap the zoom in from a far-out view.
    LaunchedEffect(cameraFollowMode, map) {
        map?.applyFollowMode(
            cameraFollowMode,
            snapZoom = true,
            northLocked = northLocked,
            headingSource = headingSource,
        )
    }

    // Follow the fix's own speed to decide whether "heading" means the compass or the course over
    // ground, and re-apply only on a change so a steady drive never touches the camera mode. The
    // location component exposes no update callback, so read its last fix on a timer; it is a
    // field read, and the interval is long enough to be free next to the GPS itself.
    LaunchedEffect(map, hasLocationPermission) {
        val mapInstance = map ?: return@LaunchedEffect
        if (!hasLocationPermission) return@LaunchedEffect
        while (true) {
            // Only a heading-following camera consumes the source. Advancing it in any other mode
            // would let a drive leave the state on HELD, so engaging heading-follow while stopped
            // would preserve whatever bearing is on screen instead of the honest compass.
            if (mapInstance.isLocationComponentEnabledSafe &&
                cameraFollowModeState.value == CameraFollowMode.FOLLOW_HEADING
            ) {
                val fix = mapInstance.locationComponent.lastKnownLocation
                val speedMps = fix?.takeIf { it.hasSpeed() }?.speed?.toDouble()
                val courseDegrees = fix?.takeIf { it.hasBearing() }?.bearing?.toDouble()
                val next = headingSourceFor(
                    current = headingSource,
                    speedMps = speedMps,
                    hasCourse = courseDegrees != null,
                    // Age of the last course actually followed, which is what the hold expires on.
                    sinceCourse = followedCourseAt?.elapsedNow(),
                )
                if (next == HeadingSource.COURSE) followedCourseAt = TimeSource.Monotonic.markNow()
                if (next != headingSource) {
                    headingSource = next
                    // Live holders, not the captured parameters: this loop outlives the
                    // composition it started in, and re-applying a stale mode here would
                    // re-engage a follow the user has since panned away from.
                    mapInstance.applyFollowMode(
                        cameraFollowModeState.value,
                        northLocked = northLockedState.value,
                        headingSource = next,
                    )
                }
            } else {
                // Left heading-follow: forget the held course so the next engage starts fresh.
                headingSource = HeadingSource.COMPASS
                followedCourseAt = null
            }
            delay(HEADING_SOURCE_POLL_MS)
        }
    }

    // Cold start with no cached location: the component only enables once the first fix lands, and
    // no other effect re-runs on that event. Poll until enabled so a follow mode chosen before the
    // fix -- and the gesture-dismiss listener -- engage on arrival.
    LaunchedEffect(map, hasLocationPermission) {
        if (!hasLocationPermission) return@LaunchedEffect
        val mapInstance = map ?: return@LaunchedEffect
        while (!mapInstance.isLocationComponentEnabledSafe) {
            delay(1000)
            mapInstance.style?.let { engageLocationComponent(mapInstance, it) }
        }
    }

    LaunchedEffect(rulerState, map) {
        map?.style?.let { style ->
            MapRenderUtils.updateRulerOnMap(style, rulerState)
        }
    }

    LaunchedEffect(twoFingerMeasurement, map) {
        val measurement = twoFingerMeasurement
        map?.getStyle { style -> MapRenderUtils.updateMeasurementOnMap(style, measurement) }
        if (measurement == null) {
            // updateMeasurementOnMap started the fade; tear the layers down once it finishes.
            delay(TwoFingerTap.FADE_OUT_MS)
            map?.getStyle { style -> MapRenderUtils.removeMeasurementLayers(style) }
        }
    }

    // Update saved points on map when they change (including color changes)
    LaunchedEffect(savedPoints, showSavedPoints) {
        map?.getStyle { style ->
            if (showSavedPoints) {
                MapRenderUtils.updateSavedPointsOnMap(style, savedPoints)
            } else {
                // Remove saved points layer when hidden
                try {
                    style.getLayer("saved-points-layer")?.let { style.removeLayer(it) }
                    style.getSource("saved-points-source")?.let { style.removeSource(it) }
                } catch (e: Exception) {
                    Logger.e(e, "Map screen error")
                }
            }
        }
    }

    LaunchedEffect(friendTrackGeoJson, map) {
        map?.style?.let { style ->
            MapRenderUtils.updateFriendTrackOnMap(style, friendTrackGeoJson)
        }
    }

    LaunchedEffect(friendPointsGeoJson, map) {
        map?.style?.let { style ->
            MapRenderUtils.updateSharedPointsOnMap(style, friendPointsGeoJson, "friend-shared-points")
        }
    }

    LaunchedEffect(mySharedPointsGeoJson, map) {
        map?.style?.let { style ->
            MapRenderUtils.updateSharedPointsOnMap(style, mySharedPointsGeoJson, "my-shared-points")
        }
    }

    LaunchedEffect(coordGridGeoJson, map) {
        map?.style?.let { style ->
            MapRenderUtils.updateCoordGridOnMap(style, coordGridGeoJson)
        }
    }

    LaunchedEffect(searchResults, map) {
        map?.getStyle { style ->
            MapRenderUtils.updateSearchResultsOnMap(style, searchResults)
        }
    }

    LaunchedEffect(highlightedSearchResult, map) {
        map?.getStyle { style ->
            MapRenderUtils.updateHighlightedSearchResult(style, highlightedSearchResult)
        }
    }

    // Registered once per map: every value the handlers read is a live state holder, so nothing here
    // goes stale between registrations. The callback lambdas are the only plain captures, and they
    // only close over the view model.
    LaunchedEffect(map) {
        map?.let { mapInstance ->
            // Remove old listeners if they exist
            clickListener?.let { mapInstance.removeOnMapClickListener(it) }
            longClickListener?.let { mapInstance.removeOnMapLongClickListener(it) }

            // Create and add new click listener
            val newClickListener = MapLibreMap.OnMapClickListener { point ->
                if (twoFingerMeasurementState.value != null) {
                    onTwoFingerMeasure(null)
                }
                val commonPoint = point.toCommon()
                if (rulerStateState.value.isActive) {
                    onRulerPointAdded(commonPoint)
                    true
                } else {
                    val target = resolveMapTap(
                        tap = commonPoint,
                        zoom = mapInstance.cameraPosition.zoom,
                        savedPoints = savedPointsState.value,
                        viewingTracks = viewingTracksState.value,
                        navigationTrack = navigationTrackState.value,
                        mySharedPoints = mySharedPointsState.value,
                        friendSharedPoints = friendSharedPointsState.value
                    )
                    when (target) {
                        is MapTapTarget.Point -> onPointClick(target.point).let { true }
                        is MapTapTarget.SharedPoint -> onSharedPointClick(target.point, target.mine).let { true }
                        is MapTapTarget.TrackLine -> onTrackClick(target.trackId).let { true }
                        MapTapTarget.OutsideTracks -> onMapClickOutsideTrack().let { false }
                        MapTapTarget.Nothing -> false
                    }
                }
            }
            mapInstance.addOnMapClickListener(newClickListener)
            clickListener = newClickListener

            // Create and add new long click listener
            val newLongClickListener = MapLibreMap.OnMapLongClickListener { point ->
                if (!rulerStateState.value.isActive) {
                    onLongPress(point.toCommon())
                    true
                } else {
                    false
                }
            }
            mapInstance.addOnMapLongClickListener(newLongClickListener)
            longClickListener = newLongClickListener
        }
    }

    // North lock: block the rotate gesture and straighten a map already turned off north. While
    // following, the location component owns the bearing, so re-apply the mode instead.
    LaunchedEffect(map, northLocked) {
        val mapInstance = map ?: return@LaunchedEffect
        mapInstance.uiSettings.isRotateGesturesEnabled = !northLocked
        if (cameraFollowMode != CameraFollowMode.OFF) {
            mapInstance.applyFollowMode(
                cameraFollowMode,
                northLocked = northLocked,
                headingSource = headingSource,
            )
        } else if (northLocked && mapInstance.cameraPosition.bearing != 0.0) {
            mapInstance.animateCamera(CameraUpdateFactory.bearingTo(0.0))
        }
    }

    AndroidView(
        factory = { ctx ->
            MapView(ctx).also { mapView = it }.apply {
                // Snapshot of the camera taken when the second finger lands; if
                // MLN nudges the camera (pinch on small jitter, two-finger-tap
                // zoom-out animation) before our recognizer fires, we snap back.
                // iOS prevents the zoom preventively via require(toFail:); the
                // Android gesture stack doesn't expose an equivalent hook.
                var savedCameraPosition: CameraPosition? = null

                val tapDetector = TwoFingerTapDetector(resources.displayMetrics.density) { p1, p2 ->
                    val mapRef = map ?: return@TwoFingerTapDetector
                    try {
                        savedCameraPosition?.let { saved ->
                            mapRef.cancelTransitions()
                            mapRef.cameraPosition = saved
                        }
                        val proj = mapRef.projection
                        val ll1 = proj.fromScreenLocation(p1).toCommon()
                        val ll2 = proj.fromScreenLocation(p2).toCommon()
                        onTwoFingerMeasure(
                            TwoFingerMeasurement(
                                ll1.latitude, ll1.longitude,
                                ll2.latitude, ll2.longitude,
                                ll1.distanceTo(ll2)
                            )
                        )
                    } catch (e: Exception) {
                        Logger.d("Two-finger projection not ready: %s", e.message ?: "")
                    }
                }
                setOnTouchListener { _, event ->
                    when (event.actionMasked) {
                        MotionEvent.ACTION_DOWN -> savedCameraPosition = null
                        MotionEvent.ACTION_POINTER_DOWN -> {
                            if (event.pointerCount == 2 && savedCameraPosition == null) {
                                savedCameraPosition = map?.cameraPosition
                            }
                        }
                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> savedCameraPosition = null
                    }
                    tapDetector.onTouch(event)
                }
                onCreate(null)
                getMapAsync { mapInstance ->
                    map = mapInstance
                    onMapReady(mapInstance)

                    // The compass rose is drawn by MapOverlays (shared with iOS), so the native
                    // ornament would just be a duplicate.
                    mapInstance.uiSettings.isCompassEnabled = false

                    mapInstance.cameraPosition = CameraPosition.Builder()
                        .target(LatLng(savedCameraLat, savedCameraLon).toMapLibre())
                        .zoom(savedCameraZoom)
                        .build()

                    // Click listeners are handled by LaunchedEffect above
                    // Don't add any click listeners here to avoid conflicts
                    // The style is not set here: assigning `map` above re-runs the style effect,
                    // which loads it. Doing both parsed and fetched the whole style twice.
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
                    Lifecycle.Event.ON_RESUME -> {
                        it.onResume()
                        syncGpsKeepAlive()
                    }
                    Lifecycle.Event.ON_PAUSE -> {
                        gpsKeepAlive.stop()
                        it.onPause()
                    }
                    Lifecycle.Event.ON_STOP -> it.onStop()
                    Lifecycle.Event.ON_DESTROY -> it.onDestroy()
                    else -> {}
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            gpsKeepAlive.stop()
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView?.let {
                // LocationComponent only unsubscribes its engine in onStop(). If composition
                // leaves while the lifecycle is still up, onDestroy() alone would leave the
                // location subscription running with no map attached.
                val state = lifecycleOwner.lifecycle.currentState
                if (state.isAtLeast(Lifecycle.State.RESUMED)) it.onPause()
                if (state.isAtLeast(Lifecycle.State.STARTED)) it.onStop()
                it.onDestroy()
            }
        }
    }

    // Nothing else reacts when the user flips location on mid-session: the puck only enables
    // once a fix exists, the non-Play-Services fallback engine binds provider state at
    // subscribe time, and the keep-alive no-ops while GPS is disabled. Re-engage all of it
    // whenever a provider is toggled.
    DisposableEffect(map, context) {
        val mapInstance = map ?: return@DisposableEffect onDispose {}
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context?, intent: Intent?) {
                if (intent?.action != LocationManager.PROVIDERS_CHANGED_ACTION) return
                Logger.d("Location providers changed, re-engaging location component")
                mapInstance.getStyle { style -> engageLocationComponent(mapInstance, style) }
                syncGpsKeepAlive()
            }
        }
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(LocationManager.PROVIDERS_CHANGED_ACTION),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        onDispose { context.unregisterReceiver(receiver) }
    }

    // Re-evaluate keep-alive when permission or recording state changes mid-resume.
    LaunchedEffect(hasLocationPermission, isRecording) {
        syncGpsKeepAlive()
    }
}
