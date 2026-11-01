package no.synth.where.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import no.synth.where.data.ClientIdManager
import no.synth.where.data.LegacyOfflineCache
import no.synth.where.data.LiveTrackingFollower
import no.synth.where.data.MapDownloadManager
import no.synth.where.data.PickedFile
import no.synth.where.data.SavedPoint
import no.synth.where.data.SavedPointsRepository
import no.synth.where.data.StravaRouteImporter
import no.synth.where.data.StravaTokenManager
import no.synth.where.data.Track
import no.synth.where.data.TrackRepository
import no.synth.where.data.UserPreferences
import no.synth.where.data.geo.LatLng
import no.synth.where.navigation.AttributionsRoute
import no.synth.where.navigation.DownloadQueueRoute
import no.synth.where.navigation.DownloadRoute
import no.synth.where.navigation.LayerRegionsRoute
import no.synth.where.navigation.MapRoute
import no.synth.where.navigation.OnlineTrackingRoute
import no.synth.where.navigation.SavedPointsRoute
import no.synth.where.navigation.SettingsRoute
import no.synth.where.navigation.TracksRoute
import no.synth.where.ui.map.MapDialogs
import no.synth.where.util.CrashReporter

object AppLinks {
    const val SPONSOR = "https://buymeacoffee.com/henrik242"
    const val RELEASE_NOTES = "https://github.com/henrik242/where/blob/main/RELEASES.md"
    const val GUIDE = "https://where.synth.no/guide"
}

class ShellDependencies(
    val userPreferences: UserPreferences,
    val trackRepository: TrackRepository,
    val savedPointsRepository: SavedPointsRepository,
    val clientIdManager: ClientIdManager,
    val liveTrackingFollower: LiveTrackingFollower,
    val stravaTokenManager: StravaTokenManager,
    val stravaRouteImporter: StravaRouteImporter,
    val downloadManager: MapDownloadManager,
)

/** Platform actions behind the shared shell. A null [stravaAuth] disables Strava import. */
class ShellPlatform(
    val openUrl: (String) -> Unit,
    /** Opens the file picker; the callback gets the picked files, or is not called on cancel. */
    val pickFiles: ((List<PickedFile>) -> Unit) -> Unit,
    val exportTrack: (Track) -> Unit,
    val shareText: (String) -> Unit,
    val freeStorageBytes: () -> Long,
    val stravaAuth: StravaAuthLauncher? = null,
    val currentLanguageTag: () -> String? = { null },
    val onLanguageSelected: (String?) -> Unit = {},
    val openLocationSettings: () -> Unit = {},
)

/** What the shell hands the platform map screen. */
class MapScreenNavigation(
    val viewingPoint: SavedPoint?,
    val onClearViewingPoint: () -> Unit,
    val onSettingsClick: () -> Unit,
    val onOfflineIndicatorClick: () -> Unit,
    val onOnlineTrackingClick: () -> Unit,
)

/**
 * Platform screens that need more glue than [ShellPlatform] offers (activity result launchers,
 * services). A null slot uses the shared screen wired through [ShellPlatform].
 */
class ShellScreens(
    val map: @Composable (MapScreenNavigation) -> Unit,
    val tracks: (@Composable (route: TracksRoute, onBack: () -> Unit, onShowMap: () -> Unit) -> Unit)? = null,
    val savedPoints: (@Composable (onBack: () -> Unit, onShowOnMap: (SavedPoint) -> Unit) -> Unit)? = null,
    val onlineTracking: (@Composable (onBack: () -> Unit, onNavigateToMap: () -> Unit) -> Unit)? = null,
)

/** The app's navigation graph, shared by all platforms. */
@Composable
fun WhereNavHost(
    deps: ShellDependencies,
    platform: ShellPlatform,
    screens: ShellScreens,
    navController: NavHostController = rememberNavController(),
    backgroundLocationMissing: Boolean = false,
    userLocation: @Composable () -> LatLng? = { null },
) {
    val userPreferences = deps.userPreferences
    val offlineModeEnabled by userPreferences.offlineModeEnabled.collectAsState()
    var viewingPoint by remember { mutableStateOf<SavedPoint?>(null) }

    DisposableEffect(navController) {
        val listener = NavController.OnDestinationChangedListener { _, destination, _ ->
            CrashReporter.log("nav: ${destination.route}")
        }
        navController.addOnDestinationChangedListener(listener)
        onDispose { navController.removeOnDestinationChangedListener(listener) }
    }

    fun back() {
        navController.popBackStack()
    }

    fun toMap() {
        navController.popBackStack<MapRoute>(inclusive = false)
    }

    NavHost(navController = navController, startDestination = MapRoute) {
        composable<MapRoute> {
            screens.map(
                MapScreenNavigation(
                    viewingPoint = viewingPoint,
                    onClearViewingPoint = { viewingPoint = null },
                    onSettingsClick = { navController.navigate(SettingsRoute()) },
                    onOfflineIndicatorClick = { navController.navigate(SettingsRoute(highlightOfflineMode = true)) },
                    onOnlineTrackingClick = { navController.navigate(OnlineTrackingRoute) },
                )
            )
        }

        composable<SettingsRoute> { entry ->
            SettingsScreen(
                userPreferences = userPreferences,
                currentLanguageTag = platform.currentLanguageTag(),
                onLanguageSelected = platform.onLanguageSelected,
                onSponsorClick = { platform.openUrl(AppLinks.SPONSOR) },
                onBackClick = ::back,
                onDownloadClick = { navController.navigate(DownloadRoute) },
                onTracksClick = { navController.navigate(TracksRoute()) },
                onSavedPointsClick = { navController.navigate(SavedPointsRoute) },
                onOnlineTrackingClick = { navController.navigate(OnlineTrackingRoute) },
                onAttributionsClick = { navController.navigate(AttributionsRoute) },
                onReleaseNotesClick = { platform.openUrl(AppLinks.RELEASE_NOTES) },
                onGuideClick = { platform.openUrl(AppLinks.GUIDE) },
                onCrashReportingChange = {
                    userPreferences.updateCrashReportingEnabled(it)
                    CrashReporter.setEnabled(it)
                },
                highlightOfflineMode = entry.toRoute<SettingsRoute>().highlightOfflineMode,
            )
        }

        composable<TracksRoute> { entry ->
            val route = entry.toRoute<TracksRoute>()
            screens.tracks?.invoke(route, ::back, ::toMap) ?: run {
                val state = rememberRetainedState { scope ->
                    TracksScreenState(
                        deps.trackRepository, userPreferences, deps.stravaTokenManager, deps.stravaRouteImporter, scope,
                    )
                }
                TracksScreen(
                    state = state,
                    onBackClick = ::back,
                    onShowMap = ::toMap,
                    onImportClick = { platform.pickFiles { if (it.isNotEmpty()) state.importFiles(it) } },
                    onExport = platform.exportTrack,
                    openUrl = platform.openUrl,
                    stravaAuth = platform.stravaAuth,
                )
            }
        }

        composable<SavedPointsRoute> {
            val showOnMap: (SavedPoint) -> Unit = { point ->
                viewingPoint = point
                toMap()
            }
            screens.savedPoints?.invoke(::back, showOnMap) ?: run {
                val pointImport = rememberRetainedState { scope -> PointImportState(deps.savedPointsRepository, scope) }
                SavedPointsScreen(
                    repository = deps.savedPointsRepository,
                    pointImport = pointImport,
                    onPickFiles = { platform.pickFiles { files -> if (files.isNotEmpty()) pointImport.read(files.map { it.bytes }) } },
                    onBackClick = ::back,
                    onShowOnMap = showOnMap,
                )
            }
        }

        composable<DownloadRoute> {
            DownloadScreen(
                downloadManager = deps.downloadManager,
                userPreferences = userPreferences,
                freeStorageBytes = platform.freeStorageBytes,
                onBackClick = ::back,
                onLayerClick = { layerId -> navController.navigate(LayerRegionsRoute(layerId)) },
                onDownloadsClick = { navController.navigate(DownloadQueueRoute) },
            )
        }

        composable<DownloadQueueRoute> {
            DownloadQueueScreen(deps.downloadManager, onBackClick = ::back)
        }

        composable<LayerRegionsRoute> { entry ->
            val downloadElevationData by userPreferences.downloadElevationData.collectAsState()
            val downloadMaxZoom by userPreferences.downloadMaxZoom.collectAsState()
            LayerHexMapScreen(
                layerId = entry.toRoute<LayerRegionsRoute>().layerId,
                onBackClick = ::back,
                downloadManager = deps.downloadManager,
                userLocation = userLocation(),
                downloadElevationData = downloadElevationData,
                downloadMaxZoom = downloadMaxZoom,
                offlineModeEnabled = offlineModeEnabled,
                onOfflineChipClick = { navController.navigate(SettingsRoute(highlightOfflineMode = true)) },
                onQueueChipClick = { navController.navigate(DownloadQueueRoute) },
            )
        }

        composable<AttributionsRoute> {
            AttributionsScreenContent(onBackClick = ::back)
        }

        composable<OnlineTrackingRoute> {
            screens.onlineTracking?.invoke(::back, ::toMap) ?: run {
                val state = rememberRetainedState { scope ->
                    OnlineTrackingState(userPreferences, deps.liveTrackingFollower, deps.clientIdManager, scope)
                }
                OnlineTrackingScreen(
                    state = state,
                    onBackClick = ::back,
                    onNavigateToMap = ::toMap,
                    openUrl = platform.openUrl,
                    onShare = platform.shareText,
                    backgroundLocationMissing = backgroundLocationMissing,
                    onOpenLocationSettings = platform.openLocationSettings,
                )
            }
        }
    }

    OfflineMigrationNotice(userPreferences)
}

/** One-time notice to upgraders: the engine switch orphaned any old offline downloads. */
@Composable
private fun OfflineMigrationNotice(userPreferences: UserPreferences) {
    val migrationNoticeShown by userPreferences.offlineMigrationNoticeShown.collectAsState()
    var showMigrationNotice by remember { mutableStateOf(false) }
    LaunchedEffect(migrationNoticeShown) {
        if (!migrationNoticeShown && LegacyOfflineCache.exists()) showMigrationNotice = true
    }
    if (showMigrationNotice) {
        MapDialogs.OfflineMigrationDialog(onDismiss = {
            showMigrationNotice = false
            userPreferences.markOfflineMigrationNoticeShown()
            LegacyOfflineCache.deleteAll()
        })
    }
}
