package no.synth.where

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import no.synth.where.data.ClientIdManager
import no.synth.where.data.SavedPoint
import no.synth.where.data.SavedPointsRepository
import no.synth.where.data.Track
import no.synth.where.data.TrackRepository
import no.synth.where.data.UserPreferences
import no.synth.where.ui.LanguageOption
import no.synth.where.ui.OnlineTrackingScreenContent
import no.synth.where.ui.SavedPointsScreenContent
import no.synth.where.ui.SettingsScreenContent
import no.synth.where.ui.TracksScreenContent
import no.synth.where.ui.map.IosMapScreen
import no.synth.where.ui.map.MapViewProvider
import no.synth.where.ui.theme.WhereTheme
import no.synth.where.util.IosPlatformActions
import org.koin.mp.KoinPlatform.getKoin

enum class Screen {
    MAP,
    SETTINGS,
    TRACKS,
    SAVED_POINTS,
    ONLINE_TRACKING
}

@Composable
fun IosApp(mapViewProvider: MapViewProvider) {
    val koin = remember { getKoin() }
    val userPreferences = remember { koin.get<UserPreferences>() }
    val trackRepository = remember { koin.get<TrackRepository>() }
    val savedPointsRepository = remember { koin.get<SavedPointsRepository>() }
    val clientIdManager = remember { koin.get<ClientIdManager>() }

    val themeMode by userPreferences.themeMode.collectAsState()
    val showCountyBorders by userPreferences.showCountyBorders.collectAsState()
    val crashReportingEnabled by userPreferences.crashReportingEnabled.collectAsState()
    val onlineTrackingEnabled by userPreferences.onlineTrackingEnabled.collectAsState()
    val tracks by trackRepository.tracks.collectAsState()
    val savedPoints by savedPointsRepository.savedPoints.collectAsState()

    var currentScreen by remember { mutableStateOf(Screen.MAP) }
    var backStack by remember { mutableStateOf(listOf<Screen>()) }
    var viewingPoint by remember { mutableStateOf<SavedPoint?>(null) }

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
                    showCountyBorders = showCountyBorders,
                    viewingPoint = viewingPoint,
                    onClearViewingPoint = { viewingPoint = null },
                    onSettingsClick = { navigateTo(Screen.SETTINGS) }
                )
            }

            Screen.SETTINGS -> {
                val themeOptions = listOf(
                    LanguageOption("system", "System"),
                    LanguageOption("light", "Light"),
                    LanguageOption("dark", "Dark")
                )
                val currentThemeLabel = themeOptions.find { it.tag == themeMode }?.displayName ?: "System"

                SettingsScreenContent(
                    versionInfo = "Where iOS MVP",
                    onBackClick = { navigateBack() },
                    onDownloadClick = {},
                    onTracksClick = { navigateTo(Screen.TRACKS) },
                    onSavedPointsClick = { navigateTo(Screen.SAVED_POINTS) },
                    onOnlineTrackingClick = { navigateTo(Screen.ONLINE_TRACKING) },
                    crashReportingEnabled = crashReportingEnabled,
                    onCrashReportingChange = { userPreferences.updateCrashReportingEnabled(it) },
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
                                    importErrorMessage = "Failed to import GPX file. The file may be corrupted or in an unsupported format."
                                    showImportError = true
                                }
                            } catch (e: Exception) {
                                importErrorMessage = "Error importing GPX file: ${e.message}"
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
