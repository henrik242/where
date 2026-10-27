package no.synth.where.ui.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import kotlinx.coroutines.delay
import no.synth.where.data.geo.LatLng
import no.synth.where.di.AppDependencies
import no.synth.where.location.iosCompassReadings
import no.synth.where.resources.Res
import no.synth.where.resources.location_permission_required
import no.synth.where.resources.recording_snackbar
import no.synth.where.ui.MapScreenNavigation
import platform.CoreLocation.CLLocation
import platform.Foundation.NSBundle
import platform.Foundation.NSURL
import kotlin.time.Duration.Companion.milliseconds

/**
 * URL template for MapLibre `glyphs:` pointing at PBF files inside the iOS app bundle's Fonts/
 * folder. Built from `NSURL` so spaces in the bundle path are percent-encoded correctly.
 */
private fun iosBundleGlyphsUrl(): String {
    val fontsRoot = NSURL.fileURLWithPath("${NSBundle.mainBundle.bundlePath}/Fonts").absoluteString
        ?: "file://${NSBundle.mainBundle.bundlePath}/Fonts"
    val trimmed = fontsRoot.trimEnd('/')
    return "$trimmed/{fontstack}/{range}.pbf"
}

@OptIn(ExperimentalForeignApi::class)
private fun CLLocation.toLatLng(): LatLng = coordinate.useContents { LatLng(latitude, longitude) }

/** iOS glue for the shared [MapScreen]: CoreLocation fixes, compass, permissions and navigation progress. */
@Composable
fun IosMapScreen(nav: MapScreenNavigation) {
    val trackRepository = remember { AppDependencies.trackRepository }
    val locationTracker = remember { AppDependencies.locationTracker }
    val scope = rememberCoroutineScope()
    val state = remember {
        MapScreenState(
            trackRepository,
            AppDependencies.savedPointsRepository,
            AppDependencies.userPreferences,
            AppDependencies.onlineTrackingCoordinator,
            AppDependencies.liveTrackingFollower,
            AppDependencies.hikingRouteSession,
            scope,
        )
    }
    val platform = remember {
        MapPlatform(
            glyphsUrl = iosBundleGlyphsUrl(),
            headingUpdates = ::iosCompassReadings,
            onStartRecording = {
                if (!locationTracker.hasPermission) {
                    locationTracker.requestAlwaysPermission()
                    state.showMessage(Res.string.location_permission_required)
                } else {
                    if (!locationTracker.hasAlwaysPermission) locationTracker.requestAlwaysPermission()
                    trackRepository.startNewTrack()
                    state.showMessage(Res.string.recording_snackbar)
                }
            },
        )
    }

    var fix by remember { mutableStateOf<LocationFix?>(null) }
    var hasPermission by remember { mutableStateOf(locationTracker.hasPermission) }

    LaunchedEffect(Unit) {
        if (!locationTracker.hasPermission) locationTracker.requestPermission()
    }
    // Recording and live sharing drive the tracker from IosApp, which outlives this screen; this
    // keep-alive only feeds the puck while the map is shown.
    DisposableEffect(locationTracker) {
        locationTracker.startKeepAlive()
        onDispose { locationTracker.stopKeepAlive() }
    }
    LaunchedEffect(locationTracker) {
        while (true) {
            hasPermission = locationTracker.hasPermission
            locationTracker.lastLocation?.let { loc ->
                fix = LocationFix(
                    latLng = loc.toLatLng(),
                    speed = loc.speed.takeIf { it >= 0.0 },
                    course = loc.course.takeIf { it >= 0.0 },
                )
            }
            delay(500.milliseconds)
        }
    }

    val navigation by trackRepository.navigation.collectAsState()
    NavigationProgressPoller(
        session = navigation,
        location = { locationTracker.lastLocation?.toLatLng() },
        updateProgress = trackRepository::updateNavigationProgress,
    )

    MapScreen(state = state, nav = nav, platform = platform, fix = fix, hasLocationPermission = hasPermission)
}
