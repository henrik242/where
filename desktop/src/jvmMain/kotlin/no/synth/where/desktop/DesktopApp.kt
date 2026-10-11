package no.synth.where.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import no.synth.where.data.DownloadQueueManager
import no.synth.where.data.MapDownloadManager
import no.synth.where.data.MaplibreComposeDownloadEngine
import no.synth.where.data.PlatformFile
import no.synth.where.data.desktopDataDir
import no.synth.where.ui.ShellDependencies
import no.synth.where.ui.ShellPlatform
import no.synth.where.ui.WhereNavHost
import no.synth.where.ui.gpxFileName

// Strava OAuth needs a captured redirect, which the desktop has no scheme handler for, so Strava
// import stays disabled (stravaAuth = null; the setup button still opens Strava's API page). There is
// no language picker backend or background location either.
private val desktopPlatform = ShellPlatform(
    openUrl = DesktopPlatformActions::openUrl,
    pickFiles = { onResult -> onResult(DesktopPlatformActions.pickFiles()) },
    exportTrack = { track -> DesktopPlatformActions.saveFile(track.gpxFileName(), track.toGPX().encodeToByteArray()) },
    shareText = DesktopPlatformActions::copyToClipboard,
    freeStorageBytes = { runCatching { desktopDataDir().usableSpace }.getOrDefault(-1L) },
)

@Composable
fun DesktopApp() {
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
                    engine = MaplibreComposeDownloadEngine(PlatformFile(System.getProperty("java.io.tmpdir"))),
                    scope = CoroutineScope(SupervisorJob() + Dispatchers.Main),
                )
            ),
        )
    }

    WhereNavHost(deps = deps, platform = desktopPlatform) { nav -> DesktopMapScreen(nav) }
}
