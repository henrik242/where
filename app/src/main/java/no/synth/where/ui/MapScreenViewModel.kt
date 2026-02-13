package no.synth.where.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import no.synth.where.data.GeocodingHelper
import no.synth.where.data.PlaceSearchClient
import no.synth.where.data.RulerState
import no.synth.where.data.SavedPoint
import no.synth.where.data.SavedPointsRepository
import no.synth.where.data.TrackRepository
import no.synth.where.data.UserPreferences
import no.synth.where.util.NamingUtils
import no.synth.where.data.geo.LatLng

class MapScreenViewModel(
    val trackRepository: TrackRepository,
    val savedPointsRepository: SavedPointsRepository,
    val userPreferences: UserPreferences
) : ViewModel() {

    // Repository state
    val savedPoints = savedPointsRepository.savedPoints
    val isRecording = trackRepository.isRecording
    val currentTrack = trackRepository.currentTrack
    val viewingTrack = trackRepository.viewingTrack
    val tracks = trackRepository.tracks
    val onlineTrackingEnabled = userPreferences.onlineTrackingEnabled

    // UI state
    private val _rulerState = MutableStateFlow(RulerState())
    val rulerState: StateFlow<RulerState> = _rulerState.asStateFlow()

    private val _showSearch = MutableStateFlow(false)
    val showSearch: StateFlow<Boolean> = _showSearch.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchResults = MutableStateFlow<List<PlaceSearchClient.SearchResult>>(emptyList())
    val searchResults: StateFlow<List<PlaceSearchClient.SearchResult>> =
        _searchResults.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    // Dialog state
    private val _showStopTrackDialog = MutableStateFlow(false)
    val showStopTrackDialog: StateFlow<Boolean> = _showStopTrackDialog.asStateFlow()

    private val _trackNameInput = MutableStateFlow("")
    val trackNameInput: StateFlow<String> = _trackNameInput.asStateFlow()

    private val _isResolvingTrackName = MutableStateFlow(false)
    val isResolvingTrackName: StateFlow<Boolean> = _isResolvingTrackName.asStateFlow()

    private val _showSavePointDialog = MutableStateFlow(false)
    val showSavePointDialog: StateFlow<Boolean> = _showSavePointDialog.asStateFlow()

    private val _savePointLatLng = MutableStateFlow<LatLng?>(null)
    val savePointLatLng: StateFlow<LatLng?> = _savePointLatLng.asStateFlow()

    private val _savePointName = MutableStateFlow("")
    val savePointName: StateFlow<String> = _savePointName.asStateFlow()

    private val _isResolvingPointName = MutableStateFlow(false)
    val isResolvingPointName: StateFlow<Boolean> = _isResolvingPointName.asStateFlow()

    private val _clickedPoint = MutableStateFlow<SavedPoint?>(null)
    val clickedPoint: StateFlow<SavedPoint?> = _clickedPoint.asStateFlow()

    private val _showPointInfoDialog = MutableStateFlow(false)
    val showPointInfoDialog: StateFlow<Boolean> = _showPointInfoDialog.asStateFlow()

    private val _showSaveRulerAsTrackDialog = MutableStateFlow(false)
    val showSaveRulerAsTrackDialog: StateFlow<Boolean> = _showSaveRulerAsTrackDialog.asStateFlow()

    private val _rulerTrackName = MutableStateFlow("")
    val rulerTrackName: StateFlow<String> = _rulerTrackName.asStateFlow()

    private val _isResolvingRulerName = MutableStateFlow(false)
    val isResolvingRulerName: StateFlow<Boolean> = _isResolvingRulerName.asStateFlow()

    @OptIn(FlowPreview::class)
    fun initSearch() {
        viewModelScope.launch {
            _searchQuery
                .debounce(300)
                .collect { query ->
                    if (query.length < 2) {
                        _searchResults.value = emptyList()
                        _isSearching.value = false
                        return@collect
                    }
                    _isSearching.value = true
                    _searchResults.value = PlaceSearchClient.search(query)
                    _isSearching.value = false
                }
        }
    }

    init {
        initSearch()
    }

    // Search actions
    fun openSearch() {
        _showSearch.value = true
    }

    fun closeSearch() {
        _showSearch.value = false
        _searchQuery.value = ""
        _searchResults.value = emptyList()
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun onSearchResultClicked() {
        _showSearch.value = false
        _searchQuery.value = ""
        _searchResults.value = emptyList()
    }

    // Recording actions
    fun startRecording(trackName: String) {
        trackRepository.startNewTrack(trackName)
    }

    fun openStopTrackDialog() {
        _trackNameInput.value = ""
        _showStopTrackDialog.value = true
        resolveTrackName()
    }

    fun updateTrackNameInput(name: String) {
        _trackNameInput.value = name
    }

    fun dismissStopTrackDialog() {
        _showStopTrackDialog.value = false
        _trackNameInput.value = ""
    }

    fun discardRecording() {
        trackRepository.discardRecording()
        _showStopTrackDialog.value = false
        _trackNameInput.value = ""
    }

    fun saveRecording() {
        val current = currentTrack.value
        val name = _trackNameInput.value
        if (current != null && name.isNotBlank()) {
            trackRepository.renameTrack(current, name)
        }
        trackRepository.stopRecording()
        _showStopTrackDialog.value = false
        _trackNameInput.value = ""
    }

    private fun resolveTrackName() {
        viewModelScope.launch {
            val track = currentTrack.value
            if (track != null && _trackNameInput.value.isBlank() && track.points.isNotEmpty()) {
                _isResolvingTrackName.value = true
                val firstPoint = track.points.first()
                val lastPoint = track.points.last()
                val startName = GeocodingHelper.reverseGeocode(firstPoint.latLng)
                val distance = firstPoint.latLng.distanceTo(lastPoint.latLng)
                val baseName = if (distance > 100 && startName != null) {
                    val endName = GeocodingHelper.reverseGeocode(lastPoint.latLng)
                    if (endName != null && startName != endName) {
                        "$startName → $endName"
                    } else {
                        startName
                    }
                } else {
                    startName
                }
                if (baseName != null) {
                    _trackNameInput.value =
                        NamingUtils.makeUnique(
                            baseName,
                            trackRepository.tracks.value.map { it.name })
                }
                _isResolvingTrackName.value = false
            }
        }
    }

    // Save point actions
    fun openSavePointDialog(latLng: LatLng) {
        _savePointLatLng.value = latLng
        _savePointName.value = ""
        _showSavePointDialog.value = true
        resolvePointName(latLng)
    }

    fun updateSavePointName(name: String) {
        _savePointName.value = name
    }

    fun dismissSavePointDialog() {
        _showSavePointDialog.value = false
        _savePointName.value = ""
        _savePointLatLng.value = null
    }

    fun savePoint() {
        val name = _savePointName.value
        val latLng = _savePointLatLng.value
        if (name.isNotBlank() && latLng != null) {
            savedPointsRepository.addPoint(name = name, latLng = latLng)
        }
        dismissSavePointDialog()
    }

    private fun resolvePointName(latLng: LatLng) {
        viewModelScope.launch {
            _isResolvingPointName.value = true
            val locationName = GeocodingHelper.reverseGeocode(latLng)
            if (locationName != null) {
                _savePointName.value = NamingUtils.makeUnique(
                    locationName,
                    savedPointsRepository.savedPoints.value.map { it.name }
                )
            }
            _isResolvingPointName.value = false
        }
    }

    // Point info dialog
    fun openPointInfoDialog(point: SavedPoint) {
        _clickedPoint.value = point
        _showPointInfoDialog.value = true
    }

    fun dismissPointInfoDialog() {
        _showPointInfoDialog.value = false
        _clickedPoint.value = null
    }

    fun deletePoint(pointId: String) {
        savedPointsRepository.deletePoint(pointId)
        dismissPointInfoDialog()
    }

    fun updatePoint(pointId: String, name: String, description: String, color: String) {
        if (name.isNotBlank()) {
            savedPointsRepository.updatePoint(pointId, name, description, color)
        }
        dismissPointInfoDialog()
    }

    // Ruler actions
    fun toggleRuler() {
        _rulerState.value =
            if (_rulerState.value.isActive) _rulerState.value.clear() else _rulerState.value.copy(
                isActive = true
            )
    }

    fun addRulerPoint(latLng: LatLng) {
        _rulerState.value = _rulerState.value.addPoint(latLng)
    }

    fun removeLastRulerPoint() {
        _rulerState.value = _rulerState.value.removeLastPoint()
    }

    fun clearRuler() {
        _rulerState.value = _rulerState.value.clear()
    }

    // Save ruler as track
    fun openSaveRulerAsTrackDialog() {
        _showSaveRulerAsTrackDialog.value = true
        resolveRulerName()
    }

    fun updateRulerTrackName(name: String) {
        _rulerTrackName.value = name
    }

    fun dismissSaveRulerAsTrackDialog() {
        _showSaveRulerAsTrackDialog.value = false
        _rulerTrackName.value = ""
    }

    fun saveRulerAsTrack() {
        val name = _rulerTrackName.value
        if (name.isNotBlank()) {
            trackRepository.createTrackFromPoints(name, _rulerState.value.points)
            _rulerState.value = _rulerState.value.clear()
        }
        dismissSaveRulerAsTrackDialog()
    }

    private fun resolveRulerName() {
        viewModelScope.launch {
            val points = _rulerState.value.points
            if (points.isNotEmpty()) {
                _isResolvingRulerName.value = true
                val firstPoint = points.first()
                val lastPoint = points.last()
                val startName = GeocodingHelper.reverseGeocode(firstPoint.latLng)

                val baseName = if (points.size > 1) {
                    val distance = firstPoint.latLng.distanceTo(lastPoint.latLng)
                    if (distance > 100 && startName != null) {
                        val endName = GeocodingHelper.reverseGeocode(lastPoint.latLng)
                        if (endName != null && startName != endName) {
                            "$startName → $endName"
                        } else {
                            startName
                        }
                    } else {
                        startName
                    }
                } else {
                    startName
                }

                if (baseName != null) {
                    _rulerTrackName.value =
                        NamingUtils.makeUnique(
                            baseName,
                            trackRepository.tracks.value.map { it.name })
                }
                _isResolvingRulerName.value = false
            }
        }
    }

    // Viewing track
    fun clearViewingTrack() {
        trackRepository.clearViewingTrack()
    }

    // Online tracking
    fun updateOnlineTracking(enabled: Boolean) {
        userPreferences.updateOnlineTrackingEnabled(enabled)
    }
}
