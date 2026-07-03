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
import no.synth.where.data.ClientIdManager
import no.synth.where.data.LiveTrackingFollower
import no.synth.where.data.DownloadLayers
import no.synth.where.data.IosMapDownloadManager
import no.synth.where.data.OfflineMapManager
import no.synth.where.data.SavedPoint
import no.synth.where.data.SavedPointsRepository
import no.synth.where.data.Track
import no.synth.where.data.TrackRepository
import no.synth.where.data.TrackUrlImporter
import no.synth.where.data.UserPreferences
import no.synth.where.ui.AttributionsScreenContent
import no.synth.where.ui.DownloadScreenContent
import no.synth.where.ui.LayerInfo
import no.synth.where.ui.IosLayerHexMapScreen
import no.synth.where.ui.OnlineTrackingScreenContent
import no.synth.where.ui.SavedPointsScreenContent
import no.synth.where.ui.SettingsScreen
import no.synth.where.ui.TracksScreenContent
import no.synth.where.ui.map.IosMapScreen
import no.synth.where.ui.map.MapViewProvider
import no.synth.where.resources.Res
import no.synth.where.resources.*
import no.synth.where.data.HexGrid
import no.synth.where.data.OfflineTileReader
import no.synth.where.di.AppDependencies
import no.synth.where.util.CrashReporter
import org.jetbrains.compose.resources.stringResource
import no.synth.where.ui.theme.WhereTheme
import no.synth.where.util.IosPlatformActions
import platform.Foundation.NSUserDefaults

enum class Screen {
    MAP,
    SETTINGS,
    TRACKS,
    SAVED_POINTS,
    ONLINE_TRACKING,
    DOWNLOAD,
    LAYER_REGIONS,
    ATTRIBUTIONS
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
    val onlineTrackingEnabled by userPreferences.onlineTrackingEnabled.collectAsState()
    val tracks by trackRepository.tracks.collectAsState()
    val isRecording by trackRepository.isRecording.collectAsState()
    val savedPoints by savedPointsRepository.savedPoints.collectAsState()
    val downloadState by downloadManager.downloadState.collectAsState()

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
                val gpxCorruptedMsg = stringResource(Res.string.import_gpx_corrupted)
                val importUrlErrorMsg = stringResource(Res.string.import_url_error)
                fun sanitizeFileName(name: String): String =
                    name.replace(" ", "_").replace(":", "-")

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
                        IosPlatformActions.pickFile(listOf("public.xml", "org.topografix.gpx", "public.data")) { bytes ->
                            if (bytes == null) return@pickFile
                            scope.launch {
                                isImporting = true
                                try {
                                    val imported = trackRepository.importTrackFromBytes(bytes)
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
                    },
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
                    isRecording = isRecording
                )
            }

            Screen.SAVED_POINTS -> {
                var showEditDialog by remember { mutableStateOf(false) }
                var editingPoint by remember { mutableStateOf<SavedPoint?>(null) }

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
                    }
                )
            }

            Screen.DOWNLOAD -> {
                var refreshTrigger by remember { mutableIntStateOf(0) }
                var cacheSize by remember { mutableLongStateOf(0L) }

                val kartverketDesc = stringResource(Res.string.layer_kartverket_desc)
                val toporasterDesc = stringResource(Res.string.layer_toporaster_desc)
                val sjokartrasterDesc = stringResource(Res.string.layer_sjokartraster_desc)
                val mapantDesc = stringResource(Res.string.layer_mapant_desc)
                val osmDesc = stringResource(Res.string.layer_osm_desc)
                val opentopomapDesc = stringResource(Res.string.layer_opentopomap_desc)
                val waymarkedtrailsDesc = stringResource(Res.string.layer_waymarkedtrails_desc)
                val avalanchezonesDesc = stringResource(Res.string.layer_avalanchezones_desc)
                val terrainDesc = stringResource(Res.string.layer_terrain_desc)

                val descriptionMap = remember(kartverketDesc) {
                    mapOf(
                        "kartverket" to kartverketDesc,
                        "toporaster" to toporasterDesc,
                        "sjokartraster" to sjokartrasterDesc,
                        "mapant" to mapantDesc,
                        "osm" to osmDesc,
                        "opentopomap" to opentopomapDesc,
                        "waymarkedtrails" to waymarkedtrailsDesc,
                        "avalanchezones" to avalanchezonesDesc,
                        "terrain" to terrainDesc,
                    )
                }

                val layers = remember(descriptionMap) {
                    DownloadLayers.all.map { layer ->
                        LayerInfo(layer.id, layer.displayName, descriptionMap[layer.id] ?: "")
                    }
                }

                LaunchedEffect(downloadState.isDownloading) {
                    if (!downloadState.isDownloading) refreshTrigger++
                }

                LaunchedEffect(refreshTrigger) {
                    cacheSize = downloadManager.getCacheSize()
                }

                DownloadScreenContent(
                    layers = layers,
                    cacheSize = cacheSize,
                    isDownloading = downloadState.isDownloading,
                    demProgress = downloadState.demProgress,
                    downloadRegionName = downloadState.region?.name,
                    downloadLayerName = downloadState.layerName,
                    downloadProgress = downloadState.progress,
                    onBackClick = { navigateBack() },
                    onLayerClick = { layerId ->
                        selectedLayerId = layerId
                        navigateTo(Screen.LAYER_REGIONS)
                    },
                    onStopDownload = { downloadManager.stopDownload() },
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
                    getLayerStats = { layerName -> downloadManager.getLayerStats(layerName) },
                    refreshTrigger = refreshTrigger
                )
            }

            Screen.LAYER_REGIONS -> {
                IosLayerHexMapScreen(
                    layerId = selectedLayerId,
                    onBackClick = { navigateBack() },
                    hexMapViewProvider = hexMapViewProvider,
                    downloadManager = downloadManager,
                    downloadElevationData = downloadElevationData,
                    offlineModeEnabled = offlineModeEnabled,
                    onOfflineChipClick = {
                        highlightOfflineMode = true
                        navigateTo(Screen.SETTINGS)
                    }
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
                val followedClientIdVal by userPreferences.followedClientId.collectAsState()
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
                    followedClientId = followedClientIdVal,
                    followClientIdInput = followClientIdInput,
                    followHistory = followHistoryVal,
                    onFollowClientIdChange = { followClientIdInput = it },
                    onStartFollowing = {
                        if (LiveTrackingFollower.CLIENT_ID_REGEX.matches(followClientIdInput) && followClientIdInput != clientId) {
                            userPreferences.updateFollowedClientId(followClientIdInput)
                            userPreferences.addFollowHistoryEntry(followClientIdInput)
                            liveTrackingFollower.follow(followClientIdInput)
                            followClientIdInput = ""
                            navigateToMap()
                        }
                    },
                    onStopFollowing = {
                        userPreferences.updateFollowedClientId(null)
                        liveTrackingFollower.stopFollowing()
                    }
                )
            }
        }
    }
}
