package no.synth.where.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
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
import no.synth.where.ui.map.MapDialogs
import no.synth.where.ui.theme.WhereTheme
import no.synth.where.util.CrashReporter

object AppLinks {
    const val SPONSOR = "https://buymeacoffee.com/henrik242"
    const val RELEASE_NOTES = "https://github.com/henrik242/where/blob/main/RELEASES.md"
    const val GUIDE = "https://where.synth.no/guide"
}

enum class Screen {
    MAP, SETTINGS, TRACKS, SAVED_POINTS, ONLINE_TRACKING, DOWNLOAD, DOWNLOAD_QUEUE, LAYER_REGIONS, ATTRIBUTIONS
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

/** App shell for iOS and desktop: a back stack over the shared screens, with the map screen as a slot. */
@Composable
fun WhereNavHost(
    deps: ShellDependencies,
    platform: ShellPlatform,
    backgroundLocationMissing: Boolean = false,
    userLocation: @Composable () -> LatLng? = { null },
    mapScreen: @Composable (MapScreenNavigation) -> Unit,
) {
    val userPreferences = deps.userPreferences
    val themeMode by userPreferences.themeMode.collectAsState()
    val offlineModeEnabled by userPreferences.offlineModeEnabled.collectAsState()
    val downloadElevationData by userPreferences.downloadElevationData.collectAsState()
    val downloadMaxZoom by userPreferences.downloadMaxZoom.collectAsState()

    var currentScreen by remember { mutableStateOf(Screen.MAP) }
    var backStack by remember { mutableStateOf(listOf<Screen>()) }
    var viewingPoint by remember { mutableStateOf<SavedPoint?>(null) }
    var selectedLayerId by remember { mutableStateOf("") }
    var highlightOfflineMode by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    // Keeps the map's rememberSaveable state (camera) while other screens are shown.
    val saveableStateHolder = rememberSaveableStateHolder()

    fun navigateTo(screen: Screen) {
        backStack = backStack + currentScreen
        currentScreen = screen
    }

    fun navigateBack() {
        if (backStack.isNotEmpty()) {
            currentScreen = backStack.last()
            backStack = backStack.dropLast(1)
        }
    }

    fun navigateToMap() {
        currentScreen = Screen.MAP
        backStack = emptyList()
    }

    fun openOfflineSettings() {
        highlightOfflineMode = true
        navigateTo(Screen.SETTINGS)
    }

    WhereTheme(themeMode = themeMode) {
        when (currentScreen) {
            Screen.MAP -> saveableStateHolder.SaveableStateProvider(Screen.MAP.name) {
                mapScreen(
                    MapScreenNavigation(
                        viewingPoint = viewingPoint,
                        onClearViewingPoint = { viewingPoint = null },
                        onSettingsClick = { navigateTo(Screen.SETTINGS) },
                        onOfflineIndicatorClick = ::openOfflineSettings,
                        onOnlineTrackingClick = { navigateTo(Screen.ONLINE_TRACKING) },
                    )
                )
            }

            Screen.SETTINGS -> SettingsScreen(
                userPreferences = userPreferences,
                currentLanguageTag = platform.currentLanguageTag(),
                onLanguageSelected = platform.onLanguageSelected,
                onSponsorClick = { platform.openUrl(AppLinks.SPONSOR) },
                onBackClick = {
                    highlightOfflineMode = false
                    navigateBack()
                },
                onDownloadClick = { navigateTo(Screen.DOWNLOAD) },
                onTracksClick = { navigateTo(Screen.TRACKS) },
                onSavedPointsClick = { navigateTo(Screen.SAVED_POINTS) },
                onOnlineTrackingClick = { navigateTo(Screen.ONLINE_TRACKING) },
                onAttributionsClick = { navigateTo(Screen.ATTRIBUTIONS) },
                onReleaseNotesClick = { platform.openUrl(AppLinks.RELEASE_NOTES) },
                onGuideClick = { platform.openUrl(AppLinks.GUIDE) },
                onCrashReportingChange = {
                    userPreferences.updateCrashReportingEnabled(it)
                    CrashReporter.setEnabled(it)
                },
                highlightOfflineMode = highlightOfflineMode,
            )

            Screen.TRACKS -> {
                val state = remember {
                    TracksScreenState(
                        deps.trackRepository, userPreferences, deps.stravaTokenManager, deps.stravaRouteImporter, scope,
                    )
                }
                TracksScreen(
                    state = state,
                    onBackClick = ::navigateBack,
                    onShowMap = ::navigateToMap,
                    onImportClick = { platform.pickFiles { if (it.isNotEmpty()) state.importFiles(it) } },
                    onExport = platform.exportTrack,
                    openUrl = platform.openUrl,
                    stravaAuth = platform.stravaAuth,
                )
            }

            Screen.SAVED_POINTS -> {
                val pointImport = remember { PointImportState(deps.savedPointsRepository, scope) }
                SavedPointsScreen(
                    repository = deps.savedPointsRepository,
                    pointImport = pointImport,
                    onPickFiles = { platform.pickFiles { files -> if (files.isNotEmpty()) pointImport.read(files.map { it.bytes }) } },
                    onBackClick = ::navigateBack,
                    onShowOnMap = { point ->
                        viewingPoint = point
                        navigateToMap()
                    },
                )
            }

            Screen.DOWNLOAD -> DownloadScreen(
                downloadManager = deps.downloadManager,
                userPreferences = userPreferences,
                freeStorageBytes = platform.freeStorageBytes,
                onBackClick = ::navigateBack,
                onLayerClick = { layerId ->
                    selectedLayerId = layerId
                    navigateTo(Screen.LAYER_REGIONS)
                },
                onDownloadsClick = { navigateTo(Screen.DOWNLOAD_QUEUE) },
            )

            Screen.DOWNLOAD_QUEUE -> DownloadQueueScreen(deps.downloadManager, onBackClick = ::navigateBack)

            Screen.LAYER_REGIONS -> LayerHexMapScreen(
                layerId = selectedLayerId,
                onBackClick = ::navigateBack,
                downloadManager = deps.downloadManager,
                userLocation = userLocation(),
                downloadElevationData = downloadElevationData,
                downloadMaxZoom = downloadMaxZoom,
                offlineModeEnabled = offlineModeEnabled,
                onOfflineChipClick = ::openOfflineSettings,
                onQueueChipClick = { navigateTo(Screen.DOWNLOAD_QUEUE) },
            )

            Screen.ATTRIBUTIONS -> AttributionsScreenContent(onBackClick = ::navigateBack)

            Screen.ONLINE_TRACKING -> {
                val state = remember {
                    OnlineTrackingState(userPreferences, deps.liveTrackingFollower, deps.clientIdManager, scope)
                }
                OnlineTrackingScreen(
                    state = state,
                    onBackClick = ::navigateBack,
                    onNavigateToMap = ::navigateToMap,
                    openUrl = platform.openUrl,
                    onShare = platform.shareText,
                    backgroundLocationMissing = backgroundLocationMissing,
                    onOpenLocationSettings = platform.openLocationSettings,
                )
            }
        }

        OfflineMigrationNotice(userPreferences)
    }
}

/** One-time notice to upgraders: the engine switch orphaned any old offline downloads. */
@Composable
fun OfflineMigrationNotice(userPreferences: UserPreferences) {
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
