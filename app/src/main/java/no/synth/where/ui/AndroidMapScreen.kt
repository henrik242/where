package no.synth.where.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.view.Surface
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.flow.map
import no.synth.where.WhereApplication
import no.synth.where.data.PlaceSearchClient
import no.synth.where.data.RulerPoint
import no.synth.where.data.RulerState
import no.synth.where.data.Track
import no.synth.where.data.TrackPoint
import no.synth.where.data.geo.LatLng
import no.synth.where.location.fusedLocationFlow
import no.synth.where.resources.Res
import no.synth.where.resources.recording_snackbar
import no.synth.where.service.LocationTrackingService
import no.synth.where.ui.map.LocationFix
import no.synth.where.ui.map.MapDialogs
import no.synth.where.ui.map.MapLayer
import no.synth.where.ui.map.MapPlatform
import no.synth.where.ui.map.MapScreen
import no.synth.where.ui.map.MapScreenContent
import no.synth.where.ui.map.MapScreenState
import no.synth.where.ui.map.PointColors
import no.synth.where.ui.map.RecordingCard
import no.synth.where.ui.map.RulerCard
import no.synth.where.ui.map.SearchOverlay
import no.synth.where.ui.map.ViewingPointBanner
import no.synth.where.ui.map.ViewingTrackBanner
import no.synth.where.util.currentTimeMillis
import no.synth.where.util.formatDateTime
import org.maplibre.compose.location.AndroidHeadingProvider
import org.maplibre.spatialk.units.Bearing
import org.maplibre.spatialk.units.extensions.inDegrees

// MapLibre glyphs URL pointing at the PBF fonts bundled in app/src/main/assets/fonts/, so labels
// render offline without a network round-trip.
private const val ANDROID_ASSET_GLYPHS_URL = "asset://fonts/{fontstack}/{range}.pbf"

private fun Context.isGranted(permission: String) =
    ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

/** Android glue for the shared [MapScreen]: fused location, permissions, compass, Back and the tracking service. */
@Composable
fun AndroidMapScreen(nav: MapScreenNavigation) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val app = context.applicationContext as WhereApplication
    val state = rememberRetainedState { scope ->
        MapScreenState(
            app.trackRepository,
            app.savedPointsRepository,
            app.userPreferences,
            app.onlineTrackingCoordinator,
            app.liveTrackingFollower,
            app.hikingRouteSession,
            scope,
        )
    }

    var hasLocationPermission by remember {
        mutableStateOf(
            context.isGranted(Manifest.permission.ACCESS_FINE_LOCATION) ||
                context.isGranted(Manifest.permission.ACCESS_COARSE_LOCATION)
        )
    }
    var showBackgroundLocationDisclosure by remember { mutableStateOf(false) }
    var fix by remember { mutableStateOf<LocationFix?>(null) }

    fun startRecording() {
        state.trackRepository.startNewTrack(formatDateTime(currentTimeMillis(), "yyyy-MM-dd HH:mm"))
        LocationTrackingService.start(context)
        state.showMessage(Res.string.recording_snackbar)
    }

    // Android may deny this silently (e.g. foreground is "Only this time"). The location
    // foreground service keeps tracking in the background anyway, so record regardless.
    val backgroundLocationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        if (context.isGranted(Manifest.permission.ACCESS_FINE_LOCATION)) startRecording()
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

    // Live location while the screen is visible, so the puck tracks movement during ordinary
    // browsing (the tracking service only runs while recording/navigating/sharing, and
    // getLastKnownLocation never refreshes). Gated to STARTED; removed on stop.
    LaunchedEffect(hasLocationPermission) {
        if (!hasLocationPermission) {
            fix = null
            return@LaunchedEffect
        }
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            fusedLocationFlow(context).collect { loc ->
                fix = LocationFix(
                    latLng = LatLng(loc.latitude, loc.longitude),
                    speed = loc.takeIf { it.hasSpeed() }?.speed?.toDouble(),
                    course = loc.takeIf { it.hasBearing() }?.bearing?.toDouble(),
                )
            }
        }
    }

    val platform = remember {
        val headingProvider = AndroidHeadingProvider(context)
        MapPlatform(
            glyphsUrl = ANDROID_ASSET_GLYPHS_URL,
            headingUpdates = {
                // The library reports azimuth in the device's natural orientation; add the display
                // rotation so the map turns the right way in landscape. Approximate: it does not
                // correct for flat-vs-upright posture.
                headingProvider.updates().map {
                    ((it.bearing - Bearing.North).inDegrees + displayRotationDegrees(context)).mod(360.0)
                }
            },
            lastKnownLocation = { lastKnownLocation(context) },
            onStartRecording = {
                when {
                    // The tracking service needs precise location.
                    !context.isGranted(Manifest.permission.ACCESS_FINE_LOCATION) -> permissionLauncher.launch(
                        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                    )
                    context.isGranted(Manifest.permission.ACCESS_BACKGROUND_LOCATION) -> startRecording()
                    else -> showBackgroundLocationDisclosure = true
                }
            },
            // The foreground service owns the location stream and notification while navigating;
            // it self-stops when navigation ends.
            onNavigationStarted = { LocationTrackingService.start(context) },
            backHandler = { enabled, onBack -> BackHandler(enabled, onBack) },
        )
    }

    MapScreen(state = state, nav = nav, platform = platform, fix = fix, hasLocationPermission = hasLocationPermission)

    if (showBackgroundLocationDisclosure) {
        MapDialogs.BackgroundLocationDisclosureDialog(
            onAllow = {
                showBackgroundLocationDisclosure = false
                backgroundLocationLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            },
            onDeny = { showBackgroundLocationDisclosure = false }
        )
    }
}

private fun lastKnownLocation(context: Context): LatLng? {
    val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    val location = try {
        locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            ?: locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            ?: locationManager.getLastKnownLocation(LocationManager.FUSED_PROVIDER)
    } catch (_: SecurityException) {
        null
    }
    return location?.let { LatLng(it.latitude, it.longitude) }
}

/** Current display rotation as degrees (Surface.ROTATION_* -> 0/90/180/270). */
@Suppress("DEPRECATION")
private fun displayRotationDegrees(context: Context): Int =
    when ((context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay.rotation) {
        Surface.ROTATION_90 -> 90
        Surface.ROTATION_180 -> 180
        Surface.ROTATION_270 -> 270
        else -> 0
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
            onSaveAsTrack = {},
            onCancelRoute = {},
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
            onCancelRoute = {},
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
            },
        )
    }
}
