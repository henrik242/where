package no.synth.where.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import no.synth.where.data.db.TrackDao
import no.synth.where.data.db.TrackEntity
import no.synth.where.data.db.TrackPointEntity
import no.synth.where.util.NamingUtils
import no.synth.where.data.geo.LatLng
import no.synth.where.util.Logger
import no.synth.where.util.currentTimeMillis

data class NavigationSession(val track: Track, val reversed: Boolean)

/** Active crop of a viewing track: keep points[startIndex..endIndex] (inclusive). */
data class TrackCropState(val trackId: String, val startIndex: Int, val endIndex: Int)

class TrackRepository(filesDir: PlatformFile, private val trackDao: TrackDao) {
    private val json = Json { ignoreUnknownKeys = true }
    private val tracksFile = filesDir.resolve("tracks.json")
    private val migratedFile = filesDir.resolve("tracks.json.migrated")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _tracks = MutableStateFlow<List<Track>>(emptyList())
    val tracks: StateFlow<List<Track>> = _tracks.asStateFlow()

    private val _currentTrack = MutableStateFlow<Track?>(null)
    val currentTrack: StateFlow<Track?> = _currentTrack.asStateFlow()

    // Multiple saved tracks can be shown at once. Order = the palette color index, so it is kept
    // stable across add/remove. [focusedTrackId] is the one whose banner + altitude chart show and
    // that is emphasized on the map; null means nothing is focused.
    private val _viewingTracks = MutableStateFlow<List<Track>>(emptyList())
    val viewingTracks: StateFlow<List<Track>> = _viewingTracks.asStateFlow()

    private val _focusedTrackId = MutableStateFlow<String?>(null)
    val focusedTrackId: StateFlow<String?> = _focusedTrackId.asStateFlow()

    private val _navigation = MutableStateFlow<NavigationSession?>(null)
    val navigation: StateFlow<NavigationSession?> = _navigation.asStateFlow()

    // Active crop of the focused viewing track, or null when not cropping.
    private val _cropState = MutableStateFlow<TrackCropState?>(null)
    val cropState: StateFlow<TrackCropState?> = _cropState.asStateFlow()

    // The pre-crop track after a crop is applied, held so the UI can offer a one-tap undo of the
    // (otherwise irreversible) overwrite. Cleared once the undo affordance is consumed or dismissed.
    private val _cropUndo = MutableStateFlow<Track?>(null)
    val cropUndo: StateFlow<Track?> = _cropUndo.asStateFlow()

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    init {
        scope.launch {
            migrateJsonToRoom()
            collectTracks()
        }
    }

    private suspend fun migrateJsonToRoom() {
        if (tracksFile.exists() && !migratedFile.exists()) {
            try {
                val text = tracksFile.readText()
                val loadedTracks: List<Track> = json.decodeFromString(text)
                val tracksToMigrate = loadedTracks.filter { !it.isRecording }
                for (track in tracksToMigrate) {
                    val entity = TrackEntity(
                        id = track.id,
                        name = track.name,
                        startTime = track.startTime,
                        endTime = track.endTime,
                        isRecording = false
                    )
                    val pointEntities = track.points.mapIndexed { index, point ->
                        TrackPointEntity(
                            trackId = track.id,
                            latitude = point.latLng.latitude,
                            longitude = point.latLng.longitude,
                            timestamp = point.timestamp,
                            altitude = point.altitude,
                            accuracy = point.accuracy,
                            orderIndex = index
                        )
                    }
                    trackDao.insertTrackWithPoints(entity, pointEntities)
                }
                tracksFile.renameTo(migratedFile)
                Logger.d("Migrated ${tracksToMigrate.size} tracks from JSON to Room")
            } catch (e: Exception) {
                Logger.e(e, "Track JSON to Room migration error")
            }
        }
    }

    private suspend fun collectTracks() {
        trackDao.getAllTracks().collect { entities ->
            val tracks = entities.map { entity ->
                val points = trackDao.getPointsForTrack(entity.id).map { pointEntity ->
                    TrackPoint(
                        latLng = LatLng(pointEntity.latitude, pointEntity.longitude),
                        timestamp = pointEntity.timestamp,
                        altitude = pointEntity.altitude,
                        accuracy = pointEntity.accuracy
                    )
                }
                Track(
                    id = entity.id,
                    name = entity.name,
                    points = points,
                    startTime = entity.startTime,
                    endTime = entity.endTime,
                    isRecording = entity.isRecording
                )
            }
            _tracks.value = tracks
        }
    }

    private fun saveTrack(track: Track) {
        scope.launch { persistTrack(track) }
    }

    private fun entitiesFor(track: Track): Pair<TrackEntity, List<TrackPointEntity>> {
        val entity = TrackEntity(
            id = track.id,
            name = track.name,
            startTime = track.startTime,
            endTime = track.endTime,
            isRecording = false
        )
        val pointEntities = track.points.mapIndexed { index, point ->
            TrackPointEntity(
                trackId = track.id,
                latitude = point.latLng.latitude,
                longitude = point.latLng.longitude,
                timestamp = point.timestamp,
                altitude = point.altitude,
                accuracy = point.accuracy,
                orderIndex = index
            )
        }
        return entity to pointEntities
    }

    private suspend fun persistTrack(track: Track) {
        try {
            val (entity, pointEntities) = entitiesFor(track)
            trackDao.insertTrackWithPoints(entity, pointEntities)
        } catch (e: Exception) {
            Logger.e(e, "Track repository error")
        }
    }

    private suspend fun overwriteTrack(track: Track) {
        try {
            val (entity, pointEntities) = entitiesFor(track)
            trackDao.replaceTrackWithPoints(entity, pointEntities)
        } catch (e: Exception) {
            Logger.e(e, "Track repository error")
        }
    }

    fun startNewTrack(name: String = "Track ${currentTimeMillis()}") {
        if (_navigation.value != null) return   // recording and navigation are mutually exclusive
        val track = Track(
            name = name,
            points = emptyList(),
            startTime = currentTimeMillis(),
            isRecording = true
        )
        _currentTrack.value = track
        _isRecording.value = true
    }

    fun addTrackPoint(latLng: LatLng, altitude: Double? = null, accuracy: Float? = null) {
        val current = _currentTrack.value ?: return
        val point = TrackPoint(
            latLng = latLng,
            timestamp = currentTimeMillis(),
            altitude = altitude,
            accuracy = accuracy
        )
        _currentTrack.value = current.copy(points = current.points + point)
    }

    fun stopRecording() {
        val current = _currentTrack.value ?: return
        val uniqueName = NamingUtils.makeUnique(current.name, _tracks.value.map { it.name })
        val finishedTrack = current.copy(
            name = uniqueName,
            endTime = currentTimeMillis(),
            isRecording = false
        )
        _currentTrack.value = null
        _isRecording.value = false
        saveTrack(finishedTrack)
    }

    fun discardRecording() {
        _currentTrack.value = null
        _isRecording.value = false
    }

    fun deleteTrack(track: Track) {
        scope.launch {
            trackDao.deleteTrack(track.id)
        }
    }

    fun renameTrack(track: Track, newName: String) {
        if (_currentTrack.value?.id == track.id) {
            _currentTrack.value = track.copy(name = newName)
        } else {
            scope.launch {
                trackDao.renameTrack(track.id, newName)
            }
        }
    }

    /** Add a track to the viewing set (if not already present) and focus it. */
    fun addViewingTrack(track: Track) {
        if (_viewingTracks.value.none { it.id == track.id }) {
            _viewingTracks.value = _viewingTracks.value + track
        }
        _focusedTrackId.value = track.id
    }

    /** Replace the whole viewing set (bulk multi-select), clearing any focus. */
    fun setViewingTracks(tracks: List<Track>) {
        _viewingTracks.value = tracks
        _focusedTrackId.value = null
    }

    fun removeViewingTrack(id: String) {
        _viewingTracks.value = _viewingTracks.value.filterNot { it.id == id }
        if (_focusedTrackId.value == id || _viewingTracks.value.isEmpty()) {
            _focusedTrackId.value = null
        }
        if (_cropState.value?.trackId == id) _cropState.value = null
    }

    fun clearViewingTracks() {
        _viewingTracks.value = emptyList()
        _focusedTrackId.value = null
        _cropState.value = null
    }

    /** Begin cropping the given viewing track, starting with the full range selected. */
    fun startCrop(trackId: String) {
        val track = _viewingTracks.value.firstOrNull { it.id == trackId } ?: return
        if (track.points.size < 2) return
        _focusedTrackId.value = trackId
        _cropState.value = TrackCropState(trackId, 0, track.points.lastIndex)
    }

    fun updateCrop(startIndex: Int, endIndex: Int) {
        val current = _cropState.value ?: return
        val track = _viewingTracks.value.firstOrNull { it.id == current.trackId } ?: return
        val (start, end) = clampCropRange(track.points.size, startIndex, endIndex)
        _cropState.value = current.copy(startIndex = start, endIndex = end)
    }

    fun cancelCrop() {
        _cropState.value = null
    }

    /** Overwrite the cropped track in place with the selected span and exit crop mode. */
    fun applyCrop() {
        val current = _cropState.value ?: return
        val track = _viewingTracks.value.firstOrNull { it.id == current.trackId } ?: return
        _cropState.value = null
        val cropped = track.cropped(current.startIndex, current.endIndex)
        if (cropped === track) return   // full-range selection: nothing trimmed, skip the DB rewrite
        replaceViewingTrack(cropped)
        _cropUndo.value = track          // offer undo of the destructive overwrite
        scope.launch { overwriteTrack(cropped) }
    }

    /** Restore the pre-crop track (reverses the last [applyCrop]). */
    fun undoCrop() {
        val original = _cropUndo.value ?: return
        _cropUndo.value = null
        replaceViewingTrack(original)
        scope.launch { overwriteTrack(original) }
    }

    fun clearCropUndo() {
        _cropUndo.value = null
    }

    private fun replaceViewingTrack(track: Track) {
        _viewingTracks.value = _viewingTracks.value.map { if (it.id == track.id) track else it }
    }

    fun setFocusedTrack(id: String?) {
        _focusedTrackId.value = id
    }

    fun toggleFocusedTrack(id: String) {
        _focusedTrackId.value = if (_focusedTrackId.value == id) null else id
    }

    fun startNavigation(track: Track, reversed: Boolean = false) {
        if (_isRecording.value) return   // recording and navigation are mutually exclusive
        // Navigation takes over the view; hide any passively-viewed tracks so only the
        // grey/blue split line shows.
        _viewingTracks.value = emptyList()
        _focusedTrackId.value = null
        _cropState.value = null
        _navigation.value = NavigationSession(track, reversed)
    }

    fun toggleNavigationReverse() {
        val current = _navigation.value ?: return
        _navigation.value = current.copy(reversed = !current.reversed)
    }

    fun stopNavigation() {
        _navigation.value = null
    }

    suspend fun importTrack(gpxContent: String): Track? = importParsed { Track.fromGPX(gpxContent) }

    suspend fun importTrackFromBytes(data: ByteArray): Track? = importParsed { Track.fromBytes(data) }

    // Parsing large tracks (e.g. FIT files with thousands of points) runs off the main
    // thread so the UI stays responsive; [parse] is invoked on the background dispatcher.
    // The DB write is awaited so the track is persisted (and about to appear in [tracks])
    // by the time this returns.
    private suspend fun importParsed(parse: () -> Track?): Track? = withContext(Dispatchers.Default) {
        val track = parse() ?: return@withContext null
        val uniqueName = NamingUtils.makeUnique(track.name, _tracks.value.map { it.name })
        val trackWithUniqueName = track.copy(name = uniqueName)
        persistTrack(trackWithUniqueName)
        trackWithUniqueName
    }

    fun createTrackFromPoints(name: String, rulerPoints: List<RulerPoint>) {
        val uniqueName = NamingUtils.makeUnique(name, _tracks.value.map { it.name })
        val trackPoints = rulerPoints.map { rulerPoint ->
            TrackPoint(
                latLng = rulerPoint.latLng,
                timestamp = currentTimeMillis(),
                altitude = null,
                accuracy = null
            )
        }
        val track = Track(
            name = uniqueName,
            points = trackPoints,
            startTime = currentTimeMillis(),
            endTime = currentTimeMillis(),
            isRecording = false
        )
        saveTrack(track)
    }
}
