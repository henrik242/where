package no.synth.where

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
import no.synth.where.data.PendingBulkImport
import no.synth.where.data.isBulkImport
import no.synth.where.data.outcome
import no.synth.where.data.suggestedImportFolder
import no.synth.where.data.LiveTrackingFollower
import no.synth.where.data.startFollowing
import no.synth.where.data.stopFollowingAll
import no.synth.where.data.unfollow
import no.synth.where.data.DownloadStatus
import no.synth.where.data.summary
import no.synth.where.data.IosMapDownloadManager
import no.synth.where.data.OfflineMapManager
import no.synth.where.data.RouteListResult
import no.synth.where.data.SavedPoint
import no.synth.where.data.StravaRoute
import no.synth.where.data.StravaTokenManager
import no.synth.where.data.Track
import no.synth.where.data.TrackUrlImporter
import no.synth.where.ui.AttributionsScreenContent
import no.synth.where.ui.DownloadQueueScreenContent
import no.synth.where.ui.DownloadScreenContent
import no.synth.where.ui.IosLayerHexMapScreen
import no.synth.where.ui.rememberLayerInfos
import no.synth.where.ui.OnlineTrackingScreenContent
import no.synth.where.ui.PointImportState
import no.synth.where.ui.SavedPointsScreenContent
import no.synth.where.ui.SettingsScreen
import no.synth.where.ui.StravaImportHandlers
import no.synth.where.ui.TracksScreenContent
import no.synth.where.ui.map.IosMapScreen
import no.synth.where.ui.map.followedFriends
import no.synth.where.ui.map.MapViewProvider
import no.synth.where.resources.Res
import no.synth.where.resources.*
import no.synth.where.data.HexGrid
import no.synth.where.data.OfflineTileReader
import no.synth.where.di.AppDependencies
import platform.UIKit.UIApplicationOpenSettingsURLString
import no.synth.where.util.CrashReporter
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import no.synth.where.ui.theme.WhereTheme
import no.synth.where.util.IosPlatformActions
import no.synth.where.util.IosWebAuth
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileSystemFreeSize
import platform.Foundation.NSHomeDirectory
import platform.Foundation.NSNumber
import platform.Foundation.NSUserDefaults

/** Types the import pickers accept. public.data is the catch-all, so this matches Android's any-file pick. */
private val IMPORT_FILE_TYPES = listOf("public.xml", "org.topografix.gpx", "public.data", "public.zip-archive")

enum class Screen {
    MAP,
    SETTINGS,
    TRACKS,
    SAVED_POINTS,
    ONLINE_TRACKING,
    DOWNLOAD,
    DOWNLOAD_QUEUE,
    LAYER_REGIONS,
    ATTRIBUTIONS
}

/** Free space on the volume holding the app's data, or -1 if it can't be determined. */
@OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
private fun iosFreeStorageBytes(): Long =
    try {
        val attrs = NSFileManager.defaultManager.attributesOfFileSystemForPath(NSHomeDirectory(), null)
        (attrs?.get(NSFileSystemFreeSize) as? NSNumber)?.longLongValue ?: -1L
    } catch (_: Exception) {
        -1L
    }

@Composable
fun IosApp(mapViewProvider: MapViewProvider, offlineMapManager: OfflineMapManager, hexMapViewProvider: MapViewProvider) {
    val userPreferences = remember { AppDependencies.userPreferences }
    val trackRepository = remember { AppDependencies.trackRepository }
    val savedPointsRepository = remember { AppDependencies.savedPointsRepository }
    val clientIdManager = remember { AppDependencies.clientIdManager }
    val downloadManager = remember { IosMapDownloadManager(offlineMapManager) }

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
            Screen.MAP -> {
                IosMapScreen(
                    mapViewProvider = mapViewProvider,
                    viewingPoint = viewingPoint,
                    onClearViewingPoint = { viewingPoint = null },
                    onSettingsClick = { navigateTo(Screen.SETTINGS) },
                    onOfflineIndicatorClick = {
                        highlightOfflineMode = true
                        navigateTo(Screen.SETTINGS)
                    },
                    onOnlineTrackingClick = { navigateTo(Screen.ONLINE_TRACKING) }
                )
            }

            Screen.SETTINGS -> {
                val appleLanguages = NSUserDefaults.standardUserDefaults.arrayForKey("AppleLanguages")
                val currentLanguageTag = appleLanguages?.firstOrNull() as? String

                SettingsScreen(
                    userPreferences = userPreferences,
                    currentLanguageTag = currentLanguageTag,
                    onLanguageSelected = { tag ->
                        if (tag == null) {
                            NSUserDefaults.standardUserDefaults.removeObjectForKey("AppleLanguages")
                        } else {
                            NSUserDefaults.standardUserDefaults.setObject(listOf(tag), "AppleLanguages")
                        }
                    },
                    onSponsorClick = { IosPlatformActions.openUrl("https://buymeacoffee.com/henrik242") },
                    onBackClick = {
                        highlightOfflineMode = false
                        navigateBack()
                    },
                    onDownloadClick = { navigateTo(Screen.DOWNLOAD) },
                    onTracksClick = { navigateTo(Screen.TRACKS) },
                    onSavedPointsClick = { navigateTo(Screen.SAVED_POINTS) },
                    onOnlineTrackingClick = { navigateTo(Screen.ONLINE_TRACKING) },
                    onAttributionsClick = { navigateTo(Screen.ATTRIBUTIONS) },
                    onReleaseNotesClick = { IosPlatformActions.openUrl("https://github.com/henrik242/where/blob/main/RELEASES.md") },
                    onGuideClick = { IosPlatformActions.openUrl("https://where.synth.no/guide") },
                    onCrashReportingChange = {
                        userPreferences.updateCrashReportingEnabled(it)
                        CrashReporter.setEnabled(it)
                    },
                    highlightOfflineMode = highlightOfflineMode
                )
            }

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
                val stravaConnectedMsg = stringResource(Res.string.strava_connected)
                val stravaConnectFailedMsg = stringResource(Res.string.strava_connect_failed, StravaTokenManager.CALLBACK_DOMAIN)
                val stravaLoadFailedMsg = stringResource(Res.string.strava_load_failed)
                val stravaSessionExpiredMsg = stringResource(Res.string.strava_session_expired)
                val stravaRateLimitedMsg = stringResource(Res.string.strava_rate_limited)
                fun sanitizeFileName(name: String): String =
                    name.replace(" ", "_").replace(":", "-")

                val stravaTokenManager = remember { AppDependencies.stravaTokenManager }
                val stravaRouteImporter = remember { AppDependencies.stravaRouteImporter }
                val stravaConnected by userPreferences.stravaConnected.collectAsState()
                val stravaClientId by userPreferences.stravaClientId.collectAsState()
                val stravaConnecting by stravaTokenManager.exchanging.collectAsState()
                var stravaRoutes by remember { mutableStateOf<List<StravaRoute>?>(null) }
                var stravaLoading by remember { mutableStateOf(false) }
                var stravaImporting by remember { mutableStateOf(false) }
                var stravaMessage by remember { mutableStateOf<String?>(null) }

                LaunchedEffect(Unit) {
                    stravaTokenManager.authOutcome.collect { ok ->
                        stravaMessage = if (ok) stravaConnectedMsg else stravaConnectFailedMsg
                    }
                }

                val stravaHandlers = StravaImportHandlers(
                    credentialsSet = !stravaClientId.isNullOrBlank(),
                    clientId = stravaClientId,
                    connected = stravaConnected,
                    connecting = stravaConnecting,
                    loadingRoutes = stravaLoading,
                    importing = stravaImporting,
                    routes = stravaRoutes,
                    onSaveCredentials = { id, secret -> stravaTokenManager.saveCredentials(id, secret) },
                    onRemoveCredentials = { scope.launch { stravaTokenManager.forgetCredentials() } },
                    onOpenApiSettings = { IosPlatformActions.openUrl("https://www.strava.com/settings/api") },
                    onConnect = {
                        val url = stravaTokenManager.buildAuthorizeUrl()
                        if (url != null) {
                            IosWebAuth.start(url, "where") { callbackUrl ->
                                // null = user cancelled: stay silent. Otherwise complete the exchange
                                // (the callback may carry ?error=..., which handleCallbackUrl rejects).
                                if (callbackUrl != null) {
                                    scope.launch { stravaTokenManager.handleCallbackUrl(callbackUrl) }
                                }
                            }
                        } else {
                            stravaMessage = stravaConnectFailedMsg
                        }
                    },
                    onLoadRoutes = {
                        scope.launch {
                            stravaLoading = true
                            try {
                                when (val result = stravaRouteImporter.listRoutes()) {
                                    is RouteListResult.Success -> stravaRoutes = result.routes
                                    RouteListResult.NotAuthorized -> {
                                        stravaTokenManager.clearSession()
                                        stravaMessage = stravaSessionExpiredMsg
                                    }
                                    RouteListResult.RateLimited -> stravaMessage = stravaRateLimitedMsg
                                    else -> stravaMessage = stravaLoadFailedMsg
                                }
                            } finally {
                                stravaLoading = false
                            }
                        }
                    },
                    onDismissRoutes = { stravaRoutes = null },
                    onImport = { routes ->
                        scope.launch {
                            stravaImporting = true
                            try {
                                val result = stravaRouteImporter.importRoutes(routes)
                                stravaRoutes = null
                                stravaMessage = if (result.rateLimited) stravaRateLimitedMsg
                                    else getString(Res.string.strava_imported_count, result.imported, result.total)
                            } finally {
                                stravaImporting = false
                            }
                        }
                    },
                    onDisconnect = {
                        scope.launch {
                            stravaTokenManager.disconnect()
                            stravaRoutes = null
                        }
                    },
                )

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
                        IosPlatformActions.pickFiles(IMPORT_FILE_TYPES) { files ->
                            when {
                                files.isEmpty() -> Unit
                                isBulkImport(files) -> {
                                    pendingBulkImport = PendingBulkImport(files.map { it.bytes }, suggestedImportFolder(files))
                                }
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
                        IosPlatformActions.shareFile("${sanitizeFileName(track.name)}.gpx", track.toGPX())
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
                    onShowSelectedOnMap = { tracks ->
                        trackRepository.setViewingTracks(tracks)
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
                    onMoveToFolder = { moved, folder ->
                        trackRepository.setTracksFolder(moved.map { it.id }, folder)
                    },
                    onRenameFolder = { oldName, newName ->
                        trackRepository.renameFolder(oldName, newName)
                    },
                    onRemoveFolder = { trackRepository.removeFolder(it) },
                    onRestoreFolders = { trackRepository.restoreFolders(it) },
                    isRecording = isRecording,
                    onMapTrackIds = onMapTrackIds,
                    strava = stravaHandlers,
                    stravaMessage = stravaMessage,
                    onStravaMessageShown = { stravaMessage = null }
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
                        editingPoint?.let {
                            savedPointsRepository.updatePoint(it.id, name, desc, color)
                        }
                        showEditDialog = false
                        editingPoint = null
                    },
                    pointImport = pointImport,
                    onPickFiles = {
                        IosPlatformActions.pickFiles(IMPORT_FILE_TYPES) { files ->
                            if (files.isNotEmpty()) pointImport.read(files.map { it.bytes })
                        }
                    }
                )
            }

            Screen.DOWNLOAD -> {
                var refreshTrigger by remember { mutableIntStateOf(0) }
                var cacheSize by remember { mutableLongStateOf(0L) }
                val freeStorageBytes = remember(refreshTrigger) { iosFreeStorageBytes() }

                val layers = rememberLayerInfos()

                // Refresh cache size + layer stats whenever the queue drains an item.
                LaunchedEffect(downloadQueue.count { it.status == DownloadStatus.COMPLETED }) {
                    refreshTrigger++
                }

                LaunchedEffect(refreshTrigger) {
                    cacheSize = downloadManager.getCacheSize()
                }

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
                                val hasOther = downloadManager.hasOtherLayersForRegion(hexId, layerId)
                                if (!hasOther) {
                                    val hex = HexGrid.hexFromId(hexId)
                                    if (hex != null) {
                                        OfflineTileReader.deleteDemTilesForBounds(HexGrid.hexBounds(hex))
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
                        if (!enabled) {
                            scope.launch {
                                OfflineTileReader.clearAllDemTiles()
                                refreshTrigger++
                            }
                        }
                    },
                    downloadMaxZoom = downloadMaxZoom,
                    onDownloadMaxZoomChange = { userPreferences.updateDownloadMaxZoom(it) },
                    getLayerStats = { layerName -> downloadManager.getLayerStats(layerName) },
                    refreshTrigger = refreshTrigger
                )
            }

            Screen.DOWNLOAD_QUEUE -> {
                DownloadQueueScreenContent(
                    queue = downloadQueue,
                    onCancelDownload = { id -> downloadManager.cancel(id) },
                    onClearFinished = { downloadManager.clearFinished() },
                    onBackClick = { navigateBack() },
                )
            }

            Screen.LAYER_REGIONS -> {
                IosLayerHexMapScreen(
                    layerId = selectedLayerId,
                    onBackClick = { navigateBack() },
                    hexMapViewProvider = hexMapViewProvider,
                    downloadManager = downloadManager,
                    downloadElevationData = downloadElevationData,
                    downloadMaxZoom = downloadMaxZoom,
                    offlineModeEnabled = offlineModeEnabled,
                    onOfflineChipClick = {
                        highlightOfflineMode = true
                        navigateTo(Screen.SETTINGS)
                    },
                    onQueueChipClick = { navigateTo(Screen.DOWNLOAD_QUEUE) }
                )
            }

            Screen.ATTRIBUTIONS -> {
                AttributionsScreenContent(
                    onBackClick = { navigateBack() }
                )
            }

            Screen.ONLINE_TRACKING -> {
                var showRegenerateDialog by remember { mutableStateOf(false) }
                var showTrackingInfoDialog by remember { mutableStateOf(false) }
                val hasSeenTrackingInfo by userPreferences.hasSeenTrackingInfo.collectAsState()
                val trackingServerUrl by userPreferences.trackingServerUrl.collectAsState()
                val followedClientIdsVal by userPreferences.followedClientIds.collectAsState()
                val followState by AppDependencies.liveTrackingFollower.state.collectAsState()
                val followHistoryVal by userPreferences.followHistory.collectAsState()
                var followClientIdInput by remember { mutableStateOf("") }
                val liveTrackingFollower = remember { AppDependencies.liveTrackingFollower }

                LaunchedEffect(Unit) {
                    if (clientId.isEmpty()) {
                        clientId = clientIdManager.getClientId()
                    }
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
                        if (enabled && !hasSeenTrackingInfo) {
                            showTrackingInfoDialog = true
                        } else {
                            userPreferences.updateOnlineTrackingEnabled(enabled)
                        }
                    },
                    onViewOnWeb = {
                        IosPlatformActions.openUrl("${trackingServerUrl}/$clientId")
                    },
                    onShare = {
                        val url = "${trackingServerUrl}/$clientId"
                        IosPlatformActions.shareText("Track my location: $url")
                    },
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
                    onStartLiveShare = { durationMillis ->
                        userPreferences.startLiveShare(durationMillis)
                    },
                    onStopLiveShare = { userPreferences.stopLiveShare() },
                    offlineModeEnabled = offlineModeEnabled,
                    onDisableOfflineMode = { userPreferences.updateOfflineModeEnabled(false) },
                    // Always permission can only be granted once from a prompt; after that the
                    // Settings app is the only way back, so link straight to this app's page.
                    backgroundLocationMissing = !alwaysPermissionGranted,
                    onOpenLocationSettings = {
                        IosPlatformActions.openUrl(UIApplicationOpenSettingsURLString)
                    },
                    followedFriends = followedFriends(
                        followedClientIdsVal,
                        (followState as? LiveTrackingFollower.FollowState.Following)?.tracks ?: emptyList()
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
                    onStopFollowing = { stopFollowingAll(userPreferences, liveTrackingFollower) }
                )
            }
        }
    }
}
