package no.synth.where.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import no.synth.where.data.BulkImportOutcome
import no.synth.where.data.BulkImportResult
import no.synth.where.data.ImportFailure
import no.synth.where.data.PendingBulkImport
import no.synth.where.data.PickedFile
import no.synth.where.data.RouteListResult
import no.synth.where.data.StravaRoute
import no.synth.where.data.StravaRouteImporter
import no.synth.where.data.StravaTokenManager
import no.synth.where.data.Track
import no.synth.where.data.TrackRepository
import no.synth.where.data.TrackUrlImporter
import no.synth.where.data.UserPreferences
import no.synth.where.data.isBulkImport
import no.synth.where.data.outcome
import no.synth.where.data.suggestedImportFolder
import no.synth.where.resources.Res
import no.synth.where.resources.import_gpx_corrupted
import no.synth.where.resources.import_no_tracks_found
import no.synth.where.resources.import_url_error
import no.synth.where.resources.strava_connect_failed
import no.synth.where.resources.strava_connected
import no.synth.where.resources.strava_imported_count
import no.synth.where.resources.strava_load_failed
import no.synth.where.resources.strava_rate_limited
import no.synth.where.resources.strava_session_expired
import no.synth.where.util.Logger
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString

/** Opens the Strava authorize URL; platforms that capture the redirect themselves pass it to the callback. */
typealias StravaAuthLauncher = (url: String, onCallback: (String) -> Unit) -> Unit

/** State and actions of the tracks screen. Android holds it in a ViewModel so it survives rotation. */
class TracksScreenState(
    val trackRepository: TrackRepository,
    val userPreferences: UserPreferences,
    private val stravaTokenManager: StravaTokenManager,
    private val stravaRouteImporter: StravaRouteImporter,
    private val scope: CoroutineScope,
) {
    var trackToDelete by mutableStateOf<Track?>(null)
    var trackToRename by mutableStateOf<Track?>(null)
    var newTrackName by mutableStateOf("")
    var importError by mutableStateOf<String?>(null)
    var isImporting by mutableStateOf(false)
        private set
    var isImportingUrl by mutableStateOf(false)
        private set
    var newlyImportedTrackId by mutableStateOf<String?>(null)
    var pendingBulkImport by mutableStateOf<PendingBulkImport?>(null)
    var bulkImportResult by mutableStateOf<BulkImportResult?>(null)
    var saveResultMessage by mutableStateOf<String?>(null)

    var stravaRoutes by mutableStateOf<List<StravaRoute>?>(null)
    var stravaLoading by mutableStateOf(false)
        private set
    var stravaImporting by mutableStateOf(false)
        private set
    var stravaMessage by mutableStateOf<String?>(null)
    val stravaConnecting = stravaTokenManager.exchanging

    private fun fail(res: StringResource, detail: String? = null) {
        scope.launch { importError = detail ?: getString(res) }
    }

    /** One picker for everything: one track file imports directly; several, or a zip, ask for a folder first. */
    fun importFiles(files: List<PickedFile>) {
        when {
            files.isEmpty() -> fail(Res.string.import_gpx_corrupted)
            isBulkImport(files) -> pendingBulkImport = PendingBulkImport(files.map { it.bytes }, suggestedImportFolder(files))
            else -> importBytes(files.first().bytes)
        }
    }

    fun importBytes(bytes: ByteArray) {
        scope.launch {
            isImporting = true
            try {
                val imported = trackRepository.importTrackFromBytes(bytes)
                if (imported == null) fail(Res.string.import_gpx_corrupted) else newlyImportedTrackId = imported.id
            } catch (e: Exception) {
                Logger.e(e, "Failed to import track from bytes")
                fail(Res.string.import_gpx_corrupted, e.message)
            } finally {
                isImporting = false
            }
        }
    }

    fun importBulk(folder: String?) {
        val items = pendingBulkImport?.items.orEmpty()
        pendingBulkImport = null
        scope.launch {
            isImporting = true
            try {
                val result = trackRepository.importTracks(items, folder)
                when (result.outcome()) {
                    BulkImportOutcome.IMPORTED -> bulkImportResult = result
                    BulkImportOutcome.NONE_FOUND -> fail(Res.string.import_no_tracks_found)
                    BulkImportOutcome.ALL_FAILED -> fail(Res.string.import_gpx_corrupted)
                }
            } catch (e: Exception) {
                Logger.e(e, "Failed to import tracks")
                fail(Res.string.import_gpx_corrupted, e.message)
            } finally {
                isImporting = false
            }
        }
    }

    fun importFromUrl(input: String) {
        scope.launch {
            isImportingUrl = true
            try {
                val track = TrackUrlImporter().importFromUrl(input)
                val imported = track?.toGPX()?.let { trackRepository.importTrack(it) }
                if (imported == null) fail(Res.string.import_url_error) else newlyImportedTrackId = imported.id
            } catch (e: Exception) {
                Logger.e(e, "Failed to import track from URL")
                fail(Res.string.import_url_error)
            } finally {
                isImportingUrl = false
            }
        }
    }

    /** Runs a platform save and shows the message it returns. */
    fun save(track: Track, save: suspend (Track) -> String) {
        scope.launch { saveResultMessage = save(track) }
    }

    fun confirmDelete() {
        trackToDelete?.let { trackRepository.deleteTrack(it) }
        trackToDelete = null
    }

    fun requestRename(track: Track) {
        trackToRename = track
        newTrackName = track.name
    }

    fun confirmRename() {
        if (newTrackName.isNotBlank()) trackToRename?.let { trackRepository.renameTrack(it, newTrackName) }
        trackToRename = null
    }

    private suspend fun showStravaMessage(res: StringResource) {
        stravaMessage = if (res == Res.string.strava_connect_failed) {
            getString(res, StravaTokenManager.CALLBACK_DOMAIN)
        } else {
            getString(res)
        }
    }

    /** Reports each OAuth round-trip outcome; the redirect may complete outside this screen. */
    suspend fun collectStravaAuthOutcomes() {
        stravaTokenManager.authOutcome.collect { ok ->
            showStravaMessage(if (ok) Res.string.strava_connected else Res.string.strava_connect_failed)
        }
    }

    fun connectStrava(launch: StravaAuthLauncher) {
        val url = stravaTokenManager.buildAuthorizeUrl()
        if (url == null) {
            scope.launch { showStravaMessage(Res.string.strava_connect_failed) }
            return
        }
        launch(url) { callbackUrl -> scope.launch { stravaTokenManager.handleCallbackUrl(callbackUrl) } }
    }

    fun loadStravaRoutes() {
        scope.launch {
            stravaLoading = true
            try {
                when (val result = stravaRouteImporter.listRoutes()) {
                    is RouteListResult.Success -> stravaRoutes = result.routes
                    RouteListResult.NotAuthorized -> {
                        stravaTokenManager.clearSession()
                        showStravaMessage(Res.string.strava_session_expired)
                    }
                    RouteListResult.RateLimited -> showStravaMessage(Res.string.strava_rate_limited)
                    else -> showStravaMessage(Res.string.strava_load_failed)
                }
            } finally {
                stravaLoading = false
            }
        }
    }

    fun importStravaRoutes(routes: List<StravaRoute>) {
        scope.launch {
            stravaImporting = true
            try {
                val result = stravaRouteImporter.importRoutes(routes)
                stravaRoutes = null
                if (result.failure == ImportFailure.NOT_AUTHORIZED) stravaTokenManager.clearSession()
                stravaMessage = when {
                    result.failure == ImportFailure.NOT_AUTHORIZED -> getString(Res.string.strava_session_expired)
                    result.failure == ImportFailure.FAILED -> getString(Res.string.strava_load_failed)
                    result.rateLimited -> getString(Res.string.strava_rate_limited)
                    else -> getString(Res.string.strava_imported_count, result.imported, result.total)
                }
            } finally {
                stravaImporting = false
            }
        }
    }

    fun saveStravaCredentials(clientId: String, secret: String) = stravaTokenManager.saveCredentials(clientId, secret)

    fun forgetStravaCredentials() {
        scope.launch { stravaTokenManager.forgetCredentials() }
    }

    fun disconnectStrava() {
        scope.launch {
            stravaTokenManager.disconnect()
            stravaRoutes = null
        }
    }
}

/**
 * The tracks screen on every platform. [onImportClick] opens the platform picker, which hands its
 * files to [TracksScreenState.importFiles]; a null [stravaAuth] disables Strava import.
 */
@Composable
fun TracksScreen(
    state: TracksScreenState,
    onBackClick: () -> Unit,
    onShowMap: () -> Unit,
    onImportClick: () -> Unit,
    onExport: (Track) -> Unit,
    openUrl: (String) -> Unit,
    stravaAuth: StravaAuthLauncher?,
    onSave: (suspend (Track) -> String)? = null,
    onOpen: ((Track) -> Unit)? = null,
    onNavigationStarted: () -> Unit = {},
) {
    val repo = state.trackRepository
    val tracks by repo.tracks.collectAsState()
    val isRecording by repo.isRecording.collectAsState()
    val onMapTrackIds by repo.onMapTrackIds.collectAsState()
    val stravaConnected by state.userPreferences.stravaConnected.collectAsState()
    val stravaClientId by state.userPreferences.stravaClientId.collectAsState()
    val stravaConnecting by state.stravaConnecting.collectAsState()

    if (stravaAuth != null) {
        LaunchedEffect(state) { state.collectStravaAuthOutcomes() }
    }

    val openApiSettings = { openUrl("https://www.strava.com/settings/api") }
    val strava = if (stravaAuth != null) {
        StravaImportHandlers(
            credentialsSet = !stravaClientId.isNullOrBlank(),
            clientId = stravaClientId,
            connected = stravaConnected,
            connecting = stravaConnecting,
            loadingRoutes = state.stravaLoading,
            importing = state.stravaImporting,
            routes = state.stravaRoutes,
            onSaveCredentials = state::saveStravaCredentials,
            onRemoveCredentials = state::forgetStravaCredentials,
            onOpenApiSettings = openApiSettings,
            onConnect = { state.connectStrava(stravaAuth) },
            onLoadRoutes = state::loadStravaRoutes,
            onDismissRoutes = { state.stravaRoutes = null },
            onImport = state::importStravaRoutes,
            onDisconnect = state::disconnectStrava,
        )
    } else {
        disabledStravaHandlers(openApiSettings)
    }

    TracksScreenContent(
        tracks = tracks,
        trackToDelete = state.trackToDelete,
        trackToRename = state.trackToRename,
        newTrackName = state.newTrackName,
        showImportError = state.importError != null,
        importErrorMessage = state.importError.orEmpty(),
        isImportingUrl = state.isImportingUrl,
        isImporting = state.isImporting,
        newlyImportedTrackId = state.newlyImportedTrackId,
        onNewlyImportedTrackConsumed = { state.newlyImportedTrackId = null },
        onBackClick = onBackClick,
        onImport = onImportClick,
        pendingBulkImport = state.pendingBulkImport,
        onBulkImportFolderSelected = state::importBulk,
        onBulkImportDismissed = { state.pendingBulkImport = null },
        bulkImportResult = state.bulkImportResult,
        onBulkImportResultShown = { state.bulkImportResult = null },
        onUrlImport = state::importFromUrl,
        onExport = onExport,
        onSave = onSave?.let { save -> { track -> state.save(track, save) } },
        saveResultMessage = state.saveResultMessage,
        onSaveResultMessageShown = { state.saveResultMessage = null },
        onOpen = onOpen,
        onDeleteRequest = { state.trackToDelete = it },
        onConfirmDelete = state::confirmDelete,
        onDismissDelete = { state.trackToDelete = null },
        onRenameRequest = state::requestRename,
        onNewTrackNameChange = { state.newTrackName = it },
        onConfirmRename = state::confirmRename,
        onDismissRename = { state.trackToRename = null },
        onDismissImportError = { state.importError = null },
        onShowOnMap = { track ->
            repo.addViewingTrack(track)
            onShowMap()
        },
        onShowSelectedOnMap = { selected ->
            repo.setViewingTracks(selected)
            onShowMap()
        },
        onNavigate = { track ->
            repo.startNavigation(track, reversed = false)
            onNavigationStarted()
            onShowMap()
        },
        onCrop = { track ->
            repo.addViewingTrack(track)
            repo.startCrop(track.id)
            onShowMap()
        },
        onSetTrackColor = { track, color -> repo.setTrackColor(track.id, color) },
        onMoveToFolder = { moved, folder -> repo.setTracksFolder(moved.map { it.id }, folder) },
        onRenameFolder = { oldName, newName -> repo.renameFolder(oldName, newName) },
        onRemoveFolder = { repo.removeFolder(it) },
        onRestoreFolders = { repo.restoreFolders(it) },
        isRecording = isRecording,
        onMapTrackIds = onMapTrackIds,
        strava = strava,
        stravaMessage = state.stravaMessage,
        onStravaMessageShown = { state.stravaMessage = null },
    )
}

/** Strava import with no way to capture the OAuth redirect: only the API settings link works. */
private fun disabledStravaHandlers(onOpenApiSettings: () -> Unit) = StravaImportHandlers(
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

fun Track.gpxFileName() = "${name.replace(" ", "_").replace(":", "-")}.gpx"
