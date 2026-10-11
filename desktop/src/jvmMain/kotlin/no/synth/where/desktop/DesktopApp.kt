package no.synth.where.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import no.synth.where.data.BulkImportOutcome
import no.synth.where.data.BulkImportResult
import no.synth.where.data.DownloadStatus
import no.synth.where.data.HexGrid
import no.synth.where.data.ImportFailure
import no.synth.where.data.LiveTrackingFollower
import no.synth.where.data.OfflineTileReader
import no.synth.where.data.PendingBulkImport
import no.synth.where.data.RouteListResult
import no.synth.where.data.SavedPoint
import no.synth.where.data.StravaRoute
import no.synth.where.data.StravaTokenManager
import no.synth.where.data.Track
import no.synth.where.data.TrackUrlImporter
import no.synth.where.data.isBulkImport
import no.synth.where.data.outcome
import no.synth.where.data.setNickname
import no.synth.where.data.startFollowing
import no.synth.where.data.stopFollowingAll
import no.synth.where.data.suggestedImportFolder
import no.synth.where.data.summary
import no.synth.where.data.unfollow
import no.synth.where.resources.Res
import no.synth.where.resources.import_gpx_corrupted
import no.synth.where.resources.import_no_tracks_found
import no.synth.where.resources.import_url_error
import no.synth.where.ui.AttributionsScreenContent
import no.synth.where.ui.DownloadQueueScreenContent
import no.synth.where.ui.DownloadScreenContent
import no.synth.where.ui.OnlineTrackingScreenContent
import no.synth.where.ui.PointImportState
import no.synth.where.ui.SavedPointsScreenContent
import no.synth.where.ui.SettingsScreen
import no.synth.where.ui.StravaImportHandlers
import no.synth.where.ui.TracksScreenContent
import no.synth.where.ui.map.followedFriends
import no.synth.where.ui.rememberLayerInfos
import no.synth.where.ui.theme.WhereTheme
import no.synth.where.util.CrashReporter
import no.synth.where.util.NamingUtils
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

private const val RELEASES_URL = "https://github.com/henrik242/where/blob/main/RELEASES.md"
private const val GUIDE_URL = "https://where.synth.no/guide"
private const val SPONSOR_URL = "https://buymeacoffee.com/henrik242"

enum class Screen {
    MAP, SETTINGS, TRACKS, SAVED_POINTS, ONLINE_TRACKING, DOWNLOAD, DOWNLOAD_QUEUE, LAYER_REGIONS, ATTRIBUTIONS
}

/** Strava OAuth needs a captured redirect, which the desktop has no scheme handler for, so the
 *  Strava import flow is disabled on desktop (the setup button still opens Strava's API page). */
private fun disabledStrava(onOpenApiSettings: () -> Unit) = StravaImportHandlers(
    credentialsSet = false,
    clientId = null,
    connected = false,
    connecting = false,
    loadingRoutes = false,
    importing = false,
    routes = null,
    onSaveCredentials = { _, _ -> },
    onRemoveCredentials = {},
    onOpenApiSettings = onOpenApiSettings,
    onConnect = {},
    onLoadRoutes = {},
    onDismissRoutes = {},
    onImport = {},
    onDisconnect = {},
)

@Composable
fun DesktopApp() {
    val userPreferences = remember { AppDependencies.userPreferences }
    val trackRepository = remember { AppDependencies.trackRepository }
    val savedPointsRepository = remember { AppDependencies.savedPointsRepository }
    val clientIdManager = remember { AppDependencies.clientIdManager }
    val downloadManager = remember { no.synth.where.data.DesktopMapDownloadManager() }

    val themeMode by userPreferences.themeMode.collectAsState()
    val offlineModeEnabled by userPreferences.offlineModeEnabled.collectAsState()
    val downloadElevationData by userPreferences.downloadElevationData.collectAsState()
    val downloadMaxZoom by userPreferences.downloadMaxZoom.collectAsState()
    val onlineTrackingEnabled by userPreferences.onlineTrackingEnabled.collectAsState()
    val tracks by trackRepository.tracks.collectAsState()
    val isRecording by trackRepository.isRecording.collectAsState()
    val onMapTrackIds by trackRepository.onMapTrackIds.collectAsState()
    val savedPoints by savedPointsRepository.savedPoints.collectAsState()
    val downloadQueue by downloadManager.queue.collectAsState()

    var currentScreen by remember { mutableStateOf(Screen.MAP) }
    var backStack by remember { mutableStateOf(listOf<Screen>()) }
    var viewingPoint by remember { mutableStateOf<SavedPoint?>(null) }
    var selectedLayerId by remember { mutableStateOf("") }
    var highlightOfflineMode by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    var clientId by remember { mutableStateOf("") }

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

    WhereTheme(themeMode = themeMode) {
        when (currentScreen) {
            Screen.MAP -> DesktopMapScreen(
                viewingPoint = viewingPoint,
                onClearViewingPoint = { viewingPoint = null },
                onSettingsClick = { navigateTo(Screen.SETTINGS) },
                onOfflineIndicatorClick = {
                    highlightOfflineMode = true
                    navigateTo(Screen.SETTINGS)
                },
                onOnlineTrackingClick = { navigateTo(Screen.ONLINE_TRACKING) },
            )

            Screen.SETTINGS -> SettingsScreen(
                userPreferences = userPreferences,
                currentLanguageTag = null,
                onLanguageSelected = {},
                onSponsorClick = { DesktopPlatformActions.openUrl(SPONSOR_URL) },
                onBackClick = {
                    highlightOfflineMode = false
                    navigateBack()
                },
                onDownloadClick = { navigateTo(Screen.DOWNLOAD) },
                onTracksClick = { navigateTo(Screen.TRACKS) },
                onSavedPointsClick = { navigateTo(Screen.SAVED_POINTS) },
                onOnlineTrackingClick = { navigateTo(Screen.ONLINE_TRACKING) },
                onAttributionsClick = { navigateTo(Screen.ATTRIBUTIONS) },
                onReleaseNotesClick = { DesktopPlatformActions.openUrl(RELEASES_URL) },
                onGuideClick = { DesktopPlatformActions.openUrl(GUIDE_URL) },
                onCrashReportingChange = {
                    userPreferences.updateCrashReportingEnabled(it)
                    CrashReporter.setEnabled(it)
                },
                highlightOfflineMode = highlightOfflineMode,
            )

            Screen.TRACKS -> {
                var trackToDelete by remember { mutableStateOf<Track?>(null) }
                var trackToRename by remember { mutableStateOf<Track?>(null) }
                var newTrackName by remember { mutableStateOf("") }
                var showImportError by remember { mutableStateOf(false) }
                var importErrorMessage by remember { mutableStateOf("") }
                var isImportingUrl by remember { mutableStateOf(false) }
                var isImporting by remember { mutableStateOf(false) }
                var newlyImportedTrackId by remember { mutableStateOf<String?>(null) }
                var pendingBulkImport by remember { mutableStateOf<PendingBulkImport?>(null) }
                var bulkImportResult by remember { mutableStateOf<BulkImportResult?>(null) }
                val gpxCorruptedMsg = stringResource(Res.string.import_gpx_corrupted)
                val noTracksMsg = stringResource(Res.string.import_no_tracks_found)
                val importUrlErrorMsg = stringResource(Res.string.import_url_error)
                fun sanitizeFileName(name: String): String = name.replace(" ", "_").replace(":", "-")

                TracksScreenContent(
                    tracks = tracks,
                    trackToDelete = trackToDelete,
                    trackToRename = trackToRename,
                    newTrackName = newTrackName,
                    showImportError = showImportError,
                    importErrorMessage = importErrorMessage,
                    isImportingUrl = isImportingUrl,
                    isImporting = isImporting,
                    newlyImportedTrackId = newlyImportedTrackId,
                    onNewlyImportedTrackConsumed = { newlyImportedTrackId = null },
                    onBackClick = { navigateBack() },
                    onImport = {
                        val files = DesktopPlatformActions.pickFiles()
                        when {
                            files.isEmpty() -> Unit
                            isBulkImport(files) ->
                                pendingBulkImport = PendingBulkImport(files.map { it.bytes }, suggestedImportFolder(files))
                            else -> {
                                val onlyBytes = files.first().bytes
                                scope.launch {
                                    isImporting = true
                                    try {
                                        val imported = trackRepository.importTrackFromBytes(onlyBytes)
                                        if (imported == null) {
                                            importErrorMessage = gpxCorruptedMsg
                                            showImportError = true
                                        } else {
                                            newlyImportedTrackId = imported.id
                                        }
                                    } catch (e: Exception) {
                                        importErrorMessage = e.message ?: gpxCorruptedMsg
                                        showImportError = true
                                    } finally {
                                        isImporting = false
                                    }
                                }
                            }
                        }
                    },
                    pendingBulkImport = pendingBulkImport,
                    onBulkImportFolderSelected = { folder ->
                        val items = pendingBulkImport?.items.orEmpty()
                        pendingBulkImport = null
                        scope.launch {
                            isImporting = true
                            try {
                                val result = trackRepository.importTracks(items, folder)
                                when (result.outcome()) {
                                    BulkImportOutcome.IMPORTED -> bulkImportResult = result
                                    BulkImportOutcome.NONE_FOUND -> {
                                        importErrorMessage = noTracksMsg
                                        showImportError = true
                                    }
                                    BulkImportOutcome.ALL_FAILED -> {
                                        importErrorMessage = gpxCorruptedMsg
                                        showImportError = true
                                    }
                                }
                            } catch (e: Exception) {
                                importErrorMessage = e.message ?: gpxCorruptedMsg
                                showImportError = true
                            } finally {
                                isImporting = false
                            }
                        }
                    },
                    onBulkImportDismissed = { pendingBulkImport = null },
                    bulkImportResult = bulkImportResult,
                    onBulkImportResultShown = { bulkImportResult = null },
                    onUrlImport = { input ->
                        scope.launch {
                            isImportingUrl = true
                            try {
                                val track = TrackUrlImporter().importFromUrl(input)
                                val imported = track?.toGPX()?.let { trackRepository.importTrack(it) }
                                if (imported == null) {
                                    importErrorMessage = importUrlErrorMsg
                                    showImportError = true
                                } else {
                                    newlyImportedTrackId = imported.id
                                }
                            } catch (_: Exception) {
                                importErrorMessage = importUrlErrorMsg
                                showImportError = true
                            } finally {
                                isImportingUrl = false
                            }
                        }
                    },
                    onExport = { track ->
                        DesktopPlatformActions.saveFile("${sanitizeFileName(track.name)}.gpx", track.toGPX().encodeToByteArray())
                    },
                    onDeleteRequest = { trackToDelete = it },
                    onConfirmDelete = {
                        trackToDelete?.let { trackRepository.deleteTrack(it) }
                        trackToDelete = null
                    },
                    onDismissDelete = { trackToDelete = null },
                    onRenameRequest = { track ->
                        trackToRename = track
                        newTrackName = track.name
                    },
                    onNewTrackNameChange = { newTrackName = it },
                    onConfirmRename = {
                        trackToRename?.let { trackRepository.renameTrack(it, newTrackName) }
                        trackToRename = null
                    },
                    onDismissRename = { trackToRename = null },
                    onDismissImportError = { showImportError = false },
                    onShowOnMap = { track ->
                        trackRepository.addViewingTrack(track)
                        navigateToMap()
                    },
                    onShowSelectedOnMap = { selected ->
                        trackRepository.setViewingTracks(selected)
                        navigateToMap()
                    },
                    onNavigate = { track ->
                        trackRepository.startNavigation(track, reversed = false)
                        navigateToMap()
                    },
                    onCrop = { track ->
                        trackRepository.addViewingTrack(track)
                        trackRepository.startCrop(track.id)
                        navigateToMap()
                    },
                    onSetTrackColor = { track, color -> trackRepository.setTrackColor(track.id, color) },
                    onMoveToFolder = { moved, folder -> trackRepository.setTracksFolder(moved.map { it.id }, folder) },
                    onRenameFolder = { oldName, newName -> trackRepository.renameFolder(oldName, newName) },
                    onRemoveFolder = { trackRepository.removeFolder(it) },
                    onRestoreFolders = { trackRepository.restoreFolders(it) },
                    isRecording = isRecording,
                    onMapTrackIds = onMapTrackIds,
                    strava = disabledStrava(onOpenApiSettings = { DesktopPlatformActions.openUrl("https://www.strava.com/settings/api") }),
                    stravaMessage = null,
                    onStravaMessageShown = {},
                )
            }

            Screen.SAVED_POINTS -> {
                var showEditDialog by remember { mutableStateOf(false) }
                var editingPoint by remember { mutableStateOf<SavedPoint?>(null) }
                val pointImport = remember { PointImportState(savedPointsRepository, scope) }

                SavedPointsScreenContent(
                    savedPoints = savedPoints,
                    showEditDialog = showEditDialog,
                    editingPoint = editingPoint,
                    onBackClick = { navigateBack() },
                    onEdit = { point ->
                        editingPoint = point
                        showEditDialog = true
                    },
                    onDelete = { savedPointsRepository.deletePoint(it.id) },
                    onShowOnMap = { point ->
                        viewingPoint = point
                        navigateToMap()
                    },
                    onDismissEdit = {
                        showEditDialog = false
                        editingPoint = null
                    },
                    onSaveEdit = { name, desc, color ->
                        editingPoint?.let { savedPointsRepository.updatePoint(it.id, name, desc, color) }
                        showEditDialog = false
                        editingPoint = null
                    },
                    pointImport = pointImport,
                    onPickFiles = {
                        val files = DesktopPlatformActions.pickFiles()
                        if (files.isNotEmpty()) pointImport.read(files.map { it.bytes })
                    },
                )
            }

            Screen.DOWNLOAD -> {
                var refreshTrigger by remember { mutableIntStateOf(0) }
                var cacheSize by remember { mutableLongStateOf(0L) }
                val freeStorageBytes = remember(refreshTrigger) {
                    runCatching { no.synth.where.data.desktopDataDir().usableSpace }.getOrDefault(-1L)
                }
                val layers = rememberLayerInfos()

                LaunchedEffect(downloadQueue.count { it.status == DownloadStatus.COMPLETED }) { refreshTrigger++ }
                LaunchedEffect(refreshTrigger) { cacheSize = downloadManager.getCacheSize() }

                DownloadScreenContent(
                    layers = layers,
                    cacheSize = cacheSize,
                    freeStorageBytes = freeStorageBytes,
                    queueSummary = if (downloadQueue.isEmpty()) null else downloadQueue.summary(),
                    onDownloadsClick = { navigateTo(Screen.DOWNLOAD_QUEUE) },
                    onBackClick = { navigateBack() },
                    onLayerClick = { layerId ->
                        selectedLayerId = layerId
                        navigateTo(Screen.LAYER_REGIONS)
                    },
                    onDeleteLayer = { layerId ->
                        scope.launch {
                            val downloadedHexIds = downloadManager.getDownloadedRegionsForLayer(layerId)
                            downloadManager.deleteAllRegionsForLayer(layerId)
                            for (hexId in downloadedHexIds) {
                                if (!downloadManager.hasOtherLayersForRegion(hexId, layerId)) {
                                    HexGrid.hexFromId(hexId)?.let {
                                        OfflineTileReader.deleteDemTilesForBounds(HexGrid.hexBounds(it))
                                    }
                                }
                            }
                            refreshTrigger++
                        }
                    },
                    onClearAutoCache = {
                        scope.launch {
                            downloadManager.clearAutoCache()
                            refreshTrigger++
                        }
                    },
                    downloadElevationData = downloadElevationData,
                    demCacheSize = remember(refreshTrigger) { OfflineTileReader.getDemCacheSize() },
                    onDownloadElevationDataChange = { enabled ->
                        userPreferences.updateDownloadElevationData(enabled)
                        if (!enabled) scope.launch {
                            OfflineTileReader.clearAllDemTiles()
                            refreshTrigger++
                        }
                    },
                    downloadMaxZoom = downloadMaxZoom,
                    onDownloadMaxZoomChange = { userPreferences.updateDownloadMaxZoom(it) },
                    getLayerStats = { layerName -> downloadManager.getLayerStats(layerName) },
                    refreshTrigger = refreshTrigger,
                )
            }

            Screen.DOWNLOAD_QUEUE -> DownloadQueueScreenContent(
                queue = downloadQueue,
                onCancelDownload = { id -> downloadManager.cancel(id) },
                onClearFinished = { downloadManager.clearFinished() },
                onBackClick = { navigateBack() },
            )

            Screen.LAYER_REGIONS -> DesktopLayerHexMapScreen(
                layerId = selectedLayerId,
                onBackClick = { navigateBack() },
                downloadManager = downloadManager,
                downloadElevationData = downloadElevationData,
                downloadMaxZoom = downloadMaxZoom,
                offlineModeEnabled = offlineModeEnabled,
                onOfflineChipClick = {
                    highlightOfflineMode = true
                    navigateTo(Screen.SETTINGS)
                },
                onQueueChipClick = { navigateTo(Screen.DOWNLOAD_QUEUE) },
            )

            Screen.ATTRIBUTIONS -> AttributionsScreenContent(onBackClick = { navigateBack() })

            Screen.ONLINE_TRACKING -> {
                var showRegenerateDialog by remember { mutableStateOf(false) }
                var showTrackingInfoDialog by remember { mutableStateOf(false) }
                val hasSeenTrackingInfo by userPreferences.hasSeenTrackingInfo.collectAsState()
                val trackingServerUrl by userPreferences.trackingServerUrl.collectAsState()
                val followedClientIdsVal by userPreferences.followedClientIds.collectAsState()
                val followState by AppDependencies.liveTrackingFollower.state.collectAsState()
                val followHistoryVal by userPreferences.followHistory.collectAsState()
                val clientNicknamesVal by userPreferences.clientNicknames.collectAsState()
                var followClientIdInput by remember { mutableStateOf("") }
                val liveTrackingFollower = remember { AppDependencies.liveTrackingFollower }

                LaunchedEffect(Unit) {
                    if (clientId.isEmpty()) clientId = clientIdManager.getClientId()
                }

                val viewerCount by userPreferences.viewerCount.collectAsState()
                val liveShareUntilMillis by userPreferences.liveShareUntilMillis.collectAsState()

                OnlineTrackingScreenContent(
                    isTrackingEnabled = onlineTrackingEnabled,
                    clientId = clientId,
                    viewerCount = viewerCount,
                    showRegenerateDialog = showRegenerateDialog,
                    showTrackingInfoDialog = showTrackingInfoDialog,
                    onBackClick = { navigateBack() },
                    onToggleTracking = { enabled ->
                        if (enabled && !hasSeenTrackingInfo) showTrackingInfoDialog = true
                        else userPreferences.updateOnlineTrackingEnabled(enabled)
                    },
                    onViewOnWeb = { DesktopPlatformActions.openUrl("$trackingServerUrl/$clientId") },
                    onShare = { DesktopPlatformActions.copyToClipboard("Track my location: $trackingServerUrl/$clientId") },
                    onRegenerateClick = { showRegenerateDialog = true },
                    onConfirmRegenerate = {
                        userPreferences.stopLiveShare()
                        scope.launch { clientId = clientIdManager.regenerateClientId() }
                        showRegenerateDialog = false
                    },
                    onDismissRegenerate = { showRegenerateDialog = false },
                    onConfirmTrackingInfo = {
                        showTrackingInfoDialog = false
                        userPreferences.confirmTrackingInfoAndEnable()
                    },
                    onDismissTrackingInfo = { showTrackingInfoDialog = false },
                    liveShareUntilMillis = liveShareUntilMillis,
                    onStartLiveShare = { durationMillis -> userPreferences.startLiveShare(durationMillis) },
                    onStopLiveShare = { userPreferences.stopLiveShare() },
                    offlineModeEnabled = offlineModeEnabled,
                    onDisableOfflineMode = { userPreferences.updateOfflineModeEnabled(false) },
                    // No background-location concept on desktop (no GPS); never show the warning.
                    backgroundLocationMissing = false,
                    onOpenLocationSettings = {},
                    followedFriends = followedFriends(
                        followedClientIdsVal,
                        (followState as? LiveTrackingFollower.FollowState.Following)?.tracks ?: emptyList(),
                        clientNicknamesVal,
                    ),
                    followClientIdInput = followClientIdInput,
                    followHistory = followHistoryVal,
                    onFollowClientIdChange = { followClientIdInput = it },
                    onStartFollowing = {
                        if (startFollowing(userPreferences, liveTrackingFollower, followClientIdInput, clientId)) {
                            followClientIdInput = ""
                            navigateToMap()
                        }
                    },
                    onUnfollow = { id -> unfollow(userPreferences, liveTrackingFollower, id) },
                    onSetNickname = { id, nickname -> setNickname(userPreferences, liveTrackingFollower, id, nickname) },
                    onStopFollowing = { stopFollowingAll(userPreferences, liveTrackingFollower) },
                )
            }
        }
    }
}
