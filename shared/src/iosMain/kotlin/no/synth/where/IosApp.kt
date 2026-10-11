package no.synth.where

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import no.synth.where.data.DownloadQueueManager
import no.synth.where.data.MapDownloadManager
import no.synth.where.data.MaplibreComposeDownloadEngine
import no.synth.where.data.PlatformFile
import no.synth.where.data.geo.LatLng
import no.synth.where.di.AppDependencies
import no.synth.where.ui.ShellDependencies
import no.synth.where.ui.ShellPlatform
import no.synth.where.ui.WhereNavHost
import no.synth.where.ui.gpxFileName
import no.synth.where.ui.map.IosMapScreen
import no.synth.where.util.IosPlatformActions
import no.synth.where.util.IosWebAuth
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileSystemFreeSize
import platform.Foundation.NSHomeDirectory
import platform.Foundation.NSNumber
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUserDefaults
import platform.UIKit.UIApplicationOpenSettingsURLString

/** Types the import pickers accept. public.data is the catch-all, so this matches Android's any-file pick. */
private val IMPORT_FILE_TYPES = listOf("public.xml", "org.topografix.gpx", "public.data", "public.zip-archive")

/** Free space on the volume holding the app's data, or -1 if it can't be determined. */
@OptIn(ExperimentalForeignApi::class)
private fun iosFreeStorageBytes(): Long =
    try {
        val attrs = NSFileManager.defaultManager.attributesOfFileSystemForPath(NSHomeDirectory(), null)
        (attrs?.get(NSFileSystemFreeSize) as? NSNumber)?.longLongValue ?: -1L
    } catch (_: Exception) {
        -1L
    }

private val iosPlatform = ShellPlatform(
    openUrl = IosPlatformActions::openUrl,
    pickFiles = { onResult -> IosPlatformActions.pickFiles(IMPORT_FILE_TYPES, onResult) },
    exportTrack = { track -> IosPlatformActions.shareFile(track.gpxFileName(), track.toGPX()) },
    shareText = IosPlatformActions::shareText,
    freeStorageBytes = ::iosFreeStorageBytes,
    // null = user cancelled: stay silent. Otherwise complete the exchange (the callback may carry
    // ?error=..., which handleCallbackUrl rejects).
    stravaAuth = { url, onCallback -> IosWebAuth.start(url, "where") { it?.let(onCallback) } },
    currentLanguageTag = {
        NSUserDefaults.standardUserDefaults.arrayForKey("AppleLanguages")?.firstOrNull() as? String
    },
    onLanguageSelected = { tag ->
        if (tag == null) {
            NSUserDefaults.standardUserDefaults.removeObjectForKey("AppleLanguages")
        } else {
            NSUserDefaults.standardUserDefaults.setObject(listOf(tag), "AppleLanguages")
        }
    },
    // Always permission can only be granted once from a prompt; after that the Settings app is the
    // only way back, so link straight to this app's page.
    openLocationSettings = { IosPlatformActions.openUrl(UIApplicationOpenSettingsURLString) },
)

@Composable
fun IosApp() {
    val deps = remember {
        ShellDependencies(
            userPreferences = AppDependencies.userPreferences,
            trackRepository = AppDependencies.trackRepository,
            savedPointsRepository = AppDependencies.savedPointsRepository,
            clientIdManager = AppDependencies.clientIdManager,
            liveTrackingFollower = AppDependencies.liveTrackingFollower,
            stravaTokenManager = AppDependencies.stravaTokenManager,
            stravaRouteImporter = AppDependencies.stravaRouteImporter,
            downloadManager = MapDownloadManager(
                DownloadQueueManager(
                    engine = MaplibreComposeDownloadEngine(PlatformFile(NSTemporaryDirectory())),
                    scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
                )
            ),
        )
    }

    // App-scoped tracker (see AppSetup): a live share must survive leaving the map screen.
    val coordinator = remember { AppDependencies.onlineTrackingCoordinator }
    val locationTracker = remember { AppDependencies.locationTracker }
    val shouldTrackLocation by coordinator.shouldTrackLocation.collectAsState()
    val alwaysPermissionGranted by locationTracker.alwaysPermissionGranted.collectAsState()
    LaunchedEffect(shouldTrackLocation) {
        if (shouldTrackLocation) {
            // Start unconditionally, even while the prompt is still up: CoreLocation holds a start
            // issued in `notDetermined` and resumes it from the authorization callback. Waiting for
            // permission first would leave trackingActive false, and nothing would ever restart it.
            if (!locationTracker.hasAlwaysPermission) locationTracker.requestAlwaysPermission()
            locationTracker.startTracking()
        } else {
            locationTracker.stopTracking()
        }
    }

    WhereNavHost(
        deps = deps,
        platform = iosPlatform,
        backgroundLocationMissing = !alwaysPermissionGranted,
        userLocation = { rememberTrackerLocation() },
    ) { nav -> IosMapScreen(nav) }
}

/**
 * The tracker's last fix, polled while shown. IosMapScreen stops the tracker keep-alive on dispose,
 * so drive it here too or the fix stays frozen.
 */
@OptIn(ExperimentalForeignApi::class)
@Composable
private fun rememberTrackerLocation(): LatLng? {
    val locationTracker = remember { AppDependencies.locationTracker }
    var location by remember { mutableStateOf<LatLng?>(null) }
    DisposableEffect(Unit) {
        locationTracker.startKeepAlive()
        onDispose { locationTracker.stopKeepAlive() }
    }
    LaunchedEffect(Unit) {
        while (true) {
            location = locationTracker.lastLocation?.coordinate?.useContents { LatLng(latitude, longitude) }
            delay(2000)
        }
    }
    return location
}
