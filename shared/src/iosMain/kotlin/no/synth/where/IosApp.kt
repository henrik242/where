package no.synth.where

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import no.synth.where.data.ClientIdManager
import no.synth.where.data.DownloadLayers
import no.synth.where.data.DownloadState
import no.synth.where.data.FylkeDownloader
import no.synth.where.data.IosMapDownloadManager
import no.synth.where.data.OfflineMapManager
import no.synth.where.data.PlatformFile
import no.synth.where.data.Region
import no.synth.where.data.RegionsRepository
import no.synth.where.data.SavedPoint
import no.synth.where.data.SavedPointsRepository
import no.synth.where.data.Track
import no.synth.where.data.TrackRepository
import no.synth.where.data.UserPreferences
import no.synth.where.ui.DownloadScreenContent
import no.synth.where.ui.LanguageOption
import no.synth.where.ui.LayerInfo
import no.synth.where.ui.LayerRegionsScreenContent
import no.synth.where.ui.OnlineTrackingScreenContent
import no.synth.where.ui.SavedPointsScreenContent
import no.synth.where.ui.SettingsScreenContent
import no.synth.where.ui.TracksScreenContent
import no.synth.where.ui.map.IosMapScreen
import no.synth.where.ui.map.MapViewProvider
import no.synth.where.resources.Res
import no.synth.where.resources.import_gpx_corrupted
import no.synth.where.resources.system_default
import no.synth.where.util.CrashReporter
import no.synth.where.util.Logger
import org.jetbrains.compose.resources.stringResource
import no.synth.where.ui.theme.WhereTheme
import no.synth.where.util.IosPlatformActions
import org.koin.mp.KoinPlatform.getKoin
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask
import platform.Foundation.NSUserDefaults

enum class Screen {
    MAP,
    SETTINGS,
    TRACKS,
    SAVED_POINTS,
    ONLINE_TRACKING,
    DOWNLOAD,
    LAYER_REGIONS
}

@Composable
fun IosApp(mapViewProvider: MapViewProvider, offlineMapManager: OfflineMapManager) {
    val koin = remember { getKoin() }
    val userPreferences = remember { koin.get<UserPreferences>() }
    val trackRepository = remember { koin.get<TrackRepository>() }
    val savedPointsRepository = remember { koin.get<SavedPointsRepository>() }
    val clientIdManager = remember { koin.get<ClientIdManager>() }
    val downloadManager = remember { IosMapDownloadManager(offlineMapManager) }

    val themeMode by userPreferences.themeMode.collectAsState()
    val showCountyBorders by userPreferences.showCountyBorders.collectAsState()
    val crashReportingEnabled by userPreferences.crashReportingEnabled.collectAsState()
    val offlineModeEnabled by userPreferences.offlineModeEnabled.collectAsState()
    val onlineTrackingEnabled by userPreferences.onlineTrackingEnabled.collectAsState()
    val tracks by trackRepository.tracks.collectAsState()
    val savedPoints by savedPointsRepository.savedPoints.collectAsState()
    val downloadState by downloadManager.downloadState.collectAsState()

    val cacheDir = remember {
        val paths = NSFileManager.defaultManager.URLsForDirectory(NSCachesDirectory, NSUserDomainMask)
        @Suppress("UNCHECKED_CAST")
        val url = (paths as List<platform.Foundation.NSURL>).first()
        PlatformFile(url.path ?: "")
    }

    var currentScreen by remember { mutableStateOf(Screen.MAP) }
    var backStack by remember { mutableStateOf(listOf<Screen>()) }
    var viewingPoint by remember { mutableStateOf<SavedPoint?>(null) }
    var selectedLayerId by remember { mutableStateOf("") }
    var regionsLoaded by remember { mutableStateOf(false) }
    var highlightOfflineMode by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    var clientId by remember { mutableStateOf("") }

    LaunchedEffect(offlineModeEnabled) {
        if (offlineModeEnabled) return@LaunchedEffect
        for (attempt in 1..3) {
            val hasCached = FylkeDownloader.hasCachedData(cacheDir)
            Logger.d("FylkeDownloader: attempt=%s, hasCachedData=%s", attempt.toString(), hasCached.toString())
            if (hasCached) break
            val success = FylkeDownloader.downloadAndCacheFylker(cacheDir)
            Logger.d("FylkeDownloader: download result=%s", success.toString())
            if (success) break
            kotlinx.coroutines.delay(2000L)
        }
        regionsLoaded = true
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
                    showCountyBorders = showCountyBorders,
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
                val languages = listOf(
                    LanguageOption(null, stringResource(Res.string.system_default)),
                    LanguageOption("en", "English"),
                    LanguageOption("nb", "Norsk bokmål")
                )

                val appleLanguages = NSUserDefaults.standardUserDefaults.arrayForKey("AppleLanguages")
                val currentTag = (appleLanguages?.firstOrNull() as? String)?.let { lang ->
                    languages.find { it.tag == lang }?.tag
                }
                val currentLanguageLabel = languages.find { it.tag == currentTag }?.displayName
                    ?: languages.first().displayName

                val themeOptions = listOf(
                    LanguageOption("system", "System"),
                    LanguageOption("light", "Light"),
                    LanguageOption("dark", "Dark")
                )
                val currentThemeLabel = themeOptions.find { it.tag == themeMode }?.displayName ?: "System"

                SettingsScreenContent(
                    versionInfo = BuildInfo.VERSION_INFO,
                    highlightOfflineMode = highlightOfflineMode,
                    onBackClick = {
                        highlightOfflineMode = false
                        navigateBack()
                    },
                    onDownloadClick = { navigateTo(Screen.DOWNLOAD) },
                    onTracksClick = { navigateTo(Screen.TRACKS) },
                    onSavedPointsClick = { navigateTo(Screen.SAVED_POINTS) },
                    onOnlineTrackingClick = { navigateTo(Screen.ONLINE_TRACKING) },
                    offlineModeEnabled = offlineModeEnabled,
                    onOfflineModeChange = { userPreferences.updateOfflineModeEnabled(it) },
                    crashReportingEnabled = crashReportingEnabled,
                    onCrashReportingChange = {
                        userPreferences.updateCrashReportingEnabled(it)
                        CrashReporter.setEnabled(it)
                    },
                    currentLanguageLabel = currentLanguageLabel,
                    languages = languages,
                    onLanguageSelected = { tag ->
                        if (tag == null) {
                            NSUserDefaults.standardUserDefaults.removeObjectForKey("AppleLanguages")
                        } else {
                            NSUserDefaults.standardUserDefaults.setObject(listOf(tag), "AppleLanguages")
                        }
                    },
                    themeOptions = themeOptions,
                    currentThemeLabel = currentThemeLabel,
                    onThemeSelected = { userPreferences.updateThemeMode(it) }
                )
            }

            Screen.TRACKS -> {
                var trackToDelete by remember { mutableStateOf<Track?>(null) }
                var trackToRename by remember { mutableStateOf<Track?>(null) }
                var newTrackName by remember { mutableStateOf("") }
                var showImportError by remember { mutableStateOf(false) }
                var importErrorMessage by remember { mutableStateOf("") }
                val gpxCorruptedMsg = stringResource(Res.string.import_gpx_corrupted)
                fun sanitizeFileName(name: String): String =
                    name.replace(" ", "_").replace(":", "-")

                TracksScreenContent(
                    tracks = tracks,
                    trackToDelete = trackToDelete,
                    trackToRename = trackToRename,
                    newTrackName = newTrackName,
                    showImportError = showImportError,
                    importErrorMessage = importErrorMessage,
                    onBackClick = { navigateBack() },
                    onImport = {
                        IosPlatformActions.pickFile(listOf("public.xml", "org.topografix.gpx")) { content ->
                            if (content == null) return@pickFile
                            try {
                                val imported = trackRepository.importTrack(content)
                                if (imported == null) {
                                    importErrorMessage = gpxCorruptedMsg
                                    showImportError = true
                                }
                            } catch (e: Exception) {
                                importErrorMessage = e.message ?: gpxCorruptedMsg
                                showImportError = true
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
                    onContinue = {},
                    onShowOnMap = { track ->
                        trackRepository.setViewingTrack(track)
                        navigateToMap()
                    }
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

                val layers = remember {
                    DownloadLayers.all.map { layer ->
                        LayerInfo(layer.id, layer.displayName, layerDescription(layer.id))
                    }
                }

                LaunchedEffect(downloadState.isDownloading) {
                    if (!downloadState.isDownloading) refreshTrigger++
                }

                DownloadScreenContent(
                    layers = layers,
                    cacheSize = 0L,
                    isDownloading = downloadState.isDownloading,
                    downloadRegionName = downloadState.region?.name,
                    downloadLayerName = downloadState.layerName,
                    downloadProgress = downloadState.progress,
                    onBackClick = { navigateBack() },
                    onLayerClick = { layerId ->
                        selectedLayerId = layerId
                        navigateTo(Screen.LAYER_REGIONS)
                    },
                    onStopDownload = { downloadManager.stopDownload() },
                    getLayerStats = { layerName -> downloadManager.getLayerStats(layerName) },
                    refreshTrigger = refreshTrigger
                )
            }

            Screen.LAYER_REGIONS -> {
                val regions = remember(regionsLoaded) {
                    val r = RegionsRepository.getRegions(cacheDir)
                    Logger.d("LAYER_REGIONS: regionsLoaded=%s, regions count=%s", regionsLoaded.toString(), r.size.toString())
                    r
                }
                val layerDisplayName = remember(selectedLayerId) {
                    DownloadLayers.all.find { it.id == selectedLayerId }?.displayName ?: selectedLayerId
                }
                var refreshTrigger by remember { mutableIntStateOf(0) }
                var showDeleteDialog by remember { mutableStateOf<Region?>(null) }

                var wasDownloading by remember { mutableStateOf(false) }
                LaunchedEffect(downloadState.isDownloading) {
                    if (wasDownloading && !downloadState.isDownloading) {
                        refreshTrigger++
                    }
                    wasDownloading = downloadState.isDownloading
                }

                LayerRegionsScreenContent(
                    layerDisplayName = layerDisplayName,
                    layerId = selectedLayerId,
                    regions = regions,
                    isDownloading = downloadState.isDownloading,
                    downloadRegionName = downloadState.region?.name,
                    downloadLayerName = downloadState.layerName,
                    downloadProgress = downloadState.progress,
                    showDeleteDialog = showDeleteDialog,
                    onBackClick = { navigateBack() },
                    onStopDownload = { downloadManager.stopDownload() },
                    onStartDownload = { region ->
                        downloadManager.startDownload(region, selectedLayerId)
                    },
                    onDeleteRequest = { region -> showDeleteDialog = region },
                    onConfirmDelete = { region ->
                        scope.launch {
                            downloadManager.deleteRegionTiles(region, selectedLayerId)
                            showDeleteDialog = null
                            refreshTrigger++
                        }
                    },
                    onDismissDelete = { showDeleteDialog = null },
                    getRegionTileInfo = { region ->
                        downloadManager.getRegionTileInfo(region, selectedLayerId)
                    },
                    refreshTrigger = refreshTrigger,
                    offlineModeEnabled = offlineModeEnabled
                )
            }

            Screen.ONLINE_TRACKING -> {
                var showRegenerateDialog by remember { mutableStateOf(false) }
                val trackingServerUrl by userPreferences.trackingServerUrl.collectAsState()

                LaunchedEffect(Unit) {
                    if (clientId.isEmpty()) {
                        clientId = clientIdManager.getClientId()
                    }
                }

                OnlineTrackingScreenContent(
                    isTrackingEnabled = onlineTrackingEnabled,
                    clientId = clientId,
                    showRegenerateDialog = showRegenerateDialog,
                    onBackClick = { navigateBack() },
                    onToggleTracking = { userPreferences.updateOnlineTrackingEnabled(it) },
                    onViewOnWeb = {
                        IosPlatformActions.openUrl("${trackingServerUrl}?clients=$clientId")
                    },
                    onShare = {
                        val url = "${trackingServerUrl}?clients=$clientId"
                        IosPlatformActions.shareText("Track my location: $url")
                    },
                    onRegenerateClick = { showRegenerateDialog = true },
                    onConfirmRegenerate = {
                        scope.launch { clientId = clientIdManager.regenerateClientId() }
                        showRegenerateDialog = false
                    },
                    onDismissRegenerate = { showRegenerateDialog = false }
                )
            }
        }
    }
}

private fun layerDescription(layerId: String): String = when (layerId) {
    "kartverket" -> "Topographic maps from Kartverket"
    "toporaster" -> "Topographic raster maps"
    "sjokartraster" -> "Nautical charts"
    "osm" -> "Community-sourced street maps"
    "opentopomap" -> "Topographic maps with hiking trails"
    "waymarkedtrails" -> "Hiking trail overlay"
    else -> ""
}
