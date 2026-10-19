package no.synth.where.ui.map

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import no.synth.where.data.GeocodingHelper
import no.synth.where.data.LiveTrackingFollower
import no.synth.where.data.OnlineTrackingCoordinator
import no.synth.where.data.PlaceSearchClient
import no.synth.where.data.RulerState
import no.synth.where.data.SavedPoint
import no.synth.where.data.SavedPointsRepository
import no.synth.where.data.SharedPoint
import no.synth.where.data.TrackRepository
import no.synth.where.data.UserPreferences
import no.synth.where.data.geo.LatLng
import no.synth.where.data.routing.HikingRouteSession
import no.synth.where.resources.Res
import no.synth.where.resources.point_deleted
import no.synth.where.resources.point_saved
import no.synth.where.resources.point_saved_locally
import no.synth.where.resources.point_shared_snackbar
import no.synth.where.resources.point_updated
import no.synth.where.resources.saved_as_track_name
import no.synth.where.resources.track_discarded
import no.synth.where.resources.track_saved
import no.synth.where.util.NamingUtils
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import kotlin.time.Duration.Companion.milliseconds

/**
 * State and actions of the map screen that are not tied to the map view itself: search, ruler,
 * dialogs and snackbars. Android holds it in a ViewModel so it survives rotation.
 */
@OptIn(FlowPreview::class)
class MapScreenState(
    val trackRepository: TrackRepository,
    val savedPointsRepository: SavedPointsRepository,
    val userPreferences: UserPreferences,
    val coordinator: OnlineTrackingCoordinator,
    val follower: LiveTrackingFollower,
    val routeSession: HikingRouteSession,
    private val scope: CoroutineScope,
) {
    val snackbarHostState = SnackbarHostState()
    val stopNavConfirm = StopNavigationConfirmState()

    private var _rulerState by mutableStateOf(RulerState())

    /** Changing the points drops the found or pending route, which belonged to the old ones. */
    var rulerState: RulerState
        get() = _rulerState
        set(value) {
            if (value.points != _rulerState.points) routeSession.clear()
            _rulerState = value
        }

    var showSearch by mutableStateOf(false)
    var searchQuery by mutableStateOf("")
    var searchResults by mutableStateOf<List<PlaceSearchClient.SearchResult>>(emptyList())
        private set
    var isSearching by mutableStateOf(false)
        private set
    var highlightedSearchResult by mutableStateOf<PlaceSearchClient.SearchResult?>(null)

    var showStopTrackDialog by mutableStateOf(false)
        private set
    var trackNameInput by mutableStateOf("")
    var isResolvingTrackName by mutableStateOf(false)
        private set

    var savePointLatLng by mutableStateOf<LatLng?>(null)
        private set
    var savePointName by mutableStateOf("")
    var savePointDescription by mutableStateOf("")
    var isResolvingPointName by mutableStateOf(false)
        private set

    var clickedPoint by mutableStateOf<SavedPoint?>(null)

    var showSaveRulerAsTrackDialog by mutableStateOf(false)
        private set
    var rulerTrackName by mutableStateOf("")
    var isResolvingRulerName by mutableStateOf(false)
        private set

    /** A shared point this client is relocating: the next long-press sets its new spot. */
    var movingSharedPointId by mutableStateOf<String?>(null)
    var managingSharedPoint by mutableStateOf<SharedPoint?>(null)
    var friendPointDialog by mutableStateOf<SharedPoint?>(null)

    init {
        // A route left from an earlier map screen has no ruler points to belong to.
        routeSession.clear()
        scope.launch {
            snapshotFlow { searchQuery }
                .debounce(300.milliseconds)
                .distinctUntilChanged()
                .collect { query ->
                    if (query.length < 2) {
                        searchResults = emptyList()
                        isSearching = false
                        return@collect
                    }
                    isSearching = true
                    searchResults = PlaceSearchClient.search(query)
                    isSearching = false
                }
        }
    }

    fun showMessage(res: StringResource, vararg args: Any) {
        scope.launch { snackbarHostState.showSnackbar(getString(res, *args)) }
    }

    fun closeSearch() {
        highlightedSearchResult = null
        showSearch = false
        searchQuery = ""
        searchResults = emptyList()
    }

    fun onSearchResultClicked(result: PlaceSearchClient.SearchResult) {
        userPreferences.addSearchHistoryEntry(result)
        closeSearch()
    }

    // Recording

    fun openStopTrackDialog() {
        trackNameInput = ""
        showStopTrackDialog = true
        val track = trackRepository.currentTrack.value ?: return
        if (track.points.isEmpty()) return
        scope.launch {
            isResolvingTrackName = true
            val baseName = GeocodingHelper.routeName(track.points.first().latLng, track.points.last().latLng)
            if (baseName != null && trackNameInput.isBlank()) {
                trackNameInput = NamingUtils.makeUnique(baseName, trackRepository.tracks.value.map { it.name })
            }
            isResolvingTrackName = false
        }
    }

    fun dismissStopTrackDialog() {
        showStopTrackDialog = false
        trackNameInput = ""
    }

    fun discardRecording() {
        trackRepository.discardRecording()
        dismissStopTrackDialog()
        showMessage(Res.string.track_discarded)
    }

    fun saveRecording() {
        val current = trackRepository.currentTrack.value
        if (current != null && trackNameInput.isNotBlank()) trackRepository.renameTrack(current, trackNameInput)
        trackRepository.stopRecording()
        dismissStopTrackDialog()
        showMessage(Res.string.track_saved)
    }

    // Save point (long press)

    fun openSavePointDialog(latLng: LatLng) {
        savePointLatLng = latLng
        savePointName = ""
        savePointDescription = ""
        scope.launch {
            isResolvingPointName = true
            val name = GeocodingHelper.reverseGeocode(latLng)
            if (name != null && savePointName.isBlank()) {
                savePointName = NamingUtils.makeUnique(name, savedPointsRepository.savedPoints.value.map { it.name })
            }
            isResolvingPointName = false
        }
    }

    fun dismissSavePointDialog() {
        savePointLatLng = null
        savePointName = ""
        savePointDescription = ""
    }

    fun savePoint(save: Boolean, share: Boolean) {
        val latLng = savePointLatLng ?: return
        if (save && savePointName.isNotBlank()) {
            savedPointsRepository.addPoint(name = savePointName, latLng = latLng, description = savePointDescription)
            userPreferences.updateShowSavedPoints(true)
        }
        if (share) coordinator.addSharedPoint(savePointName, savePointDescription, latLng, PointColors.DEFAULT)
        dismissSavePointDialog()
        showMessage(if (share) Res.string.point_shared_snackbar else Res.string.point_saved)
    }

    fun onLongPress(latLng: LatLng) {
        if (rulerState.isActive) return
        val movingId = movingSharedPointId
        if (movingId != null) {
            coordinator.moveSharedPoint(movingId, latLng)
            movingSharedPointId = null
        } else {
            openSavePointDialog(latLng)
        }
    }

    // Saved and shared point dialogs

    fun deleteClickedPoint() {
        clickedPoint?.let { savedPointsRepository.deletePoint(it.id) }
        clickedPoint = null
        showMessage(Res.string.point_deleted)
    }

    fun updateClickedPoint(name: String, description: String, color: String) {
        clickedPoint?.let { if (name.isNotBlank()) savedPointsRepository.updatePoint(it.id, name, description, color) }
        clickedPoint = null
        showMessage(Res.string.point_updated)
    }

    fun deleteSharedPoint(point: SharedPoint) {
        coordinator.removeSharedPoint(point.id)
        managingSharedPoint = null
        showMessage(Res.string.point_deleted)
    }

    fun updateSharedPoint(point: SharedPoint, name: String, description: String, color: String) {
        coordinator.updateSharedPoint(point.id, name, description, color)
        managingSharedPoint = null
        showMessage(Res.string.point_updated)
    }

    fun saveFriendPointLocally(point: SharedPoint) {
        savedPointsRepository.addPoint(
            name = point.name, latLng = point.latLng, description = point.description, color = point.color
        )
        userPreferences.updateShowSavedPoints(true)
        friendPointDialog = null
        showMessage(Res.string.point_saved_locally)
    }

    // Ruler

    /** Turns the ruler on (seeded from a live two-finger [measurement], if any) or off. */
    fun toggleRuler(measurement: TwoFingerMeasurement?) {
        rulerState = when {
            !rulerState.isActive && measurement != null -> rulerState.activatedWith(measurement.endpoints)
            rulerState.isActive -> rulerState.clear()
            else -> rulerState.copy(isActive = true)
        }
    }

    /** Back from a found route to the straight lines, else remove the last point. */
    fun undoRuler() {
        if (routeSession.preview.value != null) {
            routeSession.clear()
        } else {
            rulerState = rulerState.removeLastPoint()
        }
    }

    fun findHikingRoute() {
        routeSession.plan(rulerState.points.map { it.latLng })
    }

    fun openSaveRulerAsTrackDialog() {
        showSaveRulerAsTrackDialog = true
        val route = routeSession.preview.value
        if (route != null) {
            rulerTrackName = NamingUtils.makeUnique(route.name, trackRepository.tracks.value.map { it.name })
            return
        }
        rulerTrackName = ""
        val points = rulerState.points
        if (points.isEmpty()) return
        scope.launch {
            isResolvingRulerName = true
            val baseName = GeocodingHelper.routeName(points.first().latLng, points.last().latLng)
            if (baseName != null && rulerTrackName.isBlank()) {
                rulerTrackName = NamingUtils.makeUnique(baseName, trackRepository.tracks.value.map { it.name })
            }
            isResolvingRulerName = false
        }
    }

    fun dismissSaveRulerAsTrackDialog() {
        showSaveRulerAsTrackDialog = false
        rulerTrackName = ""
    }

    fun saveRulerAsTrack() {
        val name = rulerTrackName
        if (name.isNotBlank()) {
            val route = routeSession.preview.value
            if (route != null) {
                routeSession.save(route, name) { showMessage(Res.string.saved_as_track_name, name) }
            } else {
                trackRepository.createTrackFromPoints(name, rulerState.points)
                showMessage(Res.string.saved_as_track_name, name)
            }
            rulerState = rulerState.clear()
        }
        dismissSaveRulerAsTrackDialog()
    }
}
