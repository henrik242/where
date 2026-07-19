package no.synth.where.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import no.synth.where.data.db.TrackDao
import no.synth.where.data.db.TrackEntity
import no.synth.where.data.db.TrackPointEntity
import no.synth.where.util.NamingUtils
import no.synth.where.data.geo.LatLng
import no.synth.where.data.navigation.NavigationProgress
import no.synth.where.util.Logger
import no.synth.where.util.currentTimeMillis

data class NavigationSession(val track: Track, val reversed: Boolean)

/** Outcome of a bulk import: the tracks that landed, and how many files failed to parse. */
data class BulkImportResult(val imported: List<Track>, val failedCount: Int) {
    val importedCount: Int get() = imported.size
    val totalCount: Int get() = importedCount + failedCount
}

/** Active crop of a viewing track: keep points[startIndex..endIndex] (inclusive). */
data class TrackCropState(val trackId: String, val startIndex: Int, val endIndex: Int)

/** Trims a folder name; a blank name means "no folder" (null). Shared by the repo and the picker. */
fun normalizeFolderName(folder: String?): String? = folder?.trim()?.takeIf { it.isNotEmpty() }

/** Ids of the saved tracks currently drawn on the map: the viewing set plus any navigated track. */
internal fun onMapTrackIdsOf(viewingTracks: List<Track>, navigation: NavigationSession?): Set<String> =
    buildSet {
        viewingTracks.forEach { add(it.id) }
        navigation?.track?.id?.let { add(it) }
    }

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

    // Latest progress along the navigated track. Produced outside the UI (the Android foreground
    // service, or the iOS foreground poller) so navigation keeps running in the background; the UI
    // only observes. Reset to null whenever the session starts, ends, or changes direction so
    // observers fall back to a "locating" state instead of a stale snapshot.
    private val _navigationProgress = MutableStateFlow<NavigationProgress?>(null)
    val navigationProgress: StateFlow<NavigationProgress?> = _navigationProgress.asStateFlow()

    // So the saved-tracks list can flag which rows are already on the map.
    val onMapTrackIds: StateFlow<Set<String>> =
        combine(viewingTracks, navigation) { viewing, nav -> onMapTrackIdsOf(viewing, nav) }
            .stateIn(scope, SharingStarted.Eagerly, emptySet())

    // Index into the focused track's points marked by scrubbing its altitude chart, or null when
    // none. Transient: cleared whenever the focused track / view changes.
    private val _elevationMarker = MutableStateFlow<Int?>(null)
    val elevationMarker: StateFlow<Int?> = _elevationMarker.asStateFlow()

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
                    isRecording = entity.isRecording,
                    folder = entity.folder
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
            isRecording = false,
            folder = track.folder
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

    /** Move tracks into [folder]; null (or blank) removes them from any folder. */
    fun setTracksFolder(trackIds: List<String>, folder: String?) {
        if (trackIds.isEmpty()) return
        scope.launch { trackDao.updateFolderForTracks(trackIds, normalizeFolderName(folder)) }
    }

    /**
     * Move every track in [oldName] to [newName]; blank new names are ignored. [oldName] must be an
     * exact stored folder value (not normalized). If [newName] matches an existing folder the two
     * merge, since folders are identified by their exact name.
     */
    fun renameFolder(oldName: String, newName: String) {
        val normalized = normalizeFolderName(newName) ?: return
        scope.launch { trackDao.renameFolder(oldName, normalized) }
    }

    /** Dissolve the folder: its tracks are kept but moved out of any folder. [name] must be exact. */
    fun removeFolder(name: String) {
        scope.launch { trackDao.clearFolder(name) }
    }

    /**
     * Undo a folder change by putting each track back in the folder it had before. [previousFolders]
     * maps track id to its prior folder (null = no folder); already-normalized, so it is applied as-is
     * in one bulk update per distinct destination. Unlike crop undo (held in the repo), folder undo is
     * captured in the UI and replayed here, since it must cover a bulk move statelessly.
     */
    fun restoreFolders(previousFolders: Map<String, String?>) {
        if (previousFolders.isEmpty()) return
        scope.launch {
            previousFolders.entries
                .groupBy({ it.value }, { it.key })
                .forEach { (folder, ids) -> trackDao.updateFolderForTracks(ids, folder) }
        }
    }

    /** Add a track to the viewing set (if not already present) and focus it. */
    fun addViewingTrack(track: Track) {
        // The navigated track is already drawn as the split line; don't add it as a plain line too.
        if (_navigation.value?.track?.id == track.id) return
        if (_viewingTracks.value.none { it.id == track.id }) {
            _viewingTracks.value = _viewingTracks.value + track
        }
        // Focusing shows the banner + altitude chart, which the navigation card owns while active,
        // so add the track without focusing it when navigating.
        if (_navigation.value == null) _focusedTrackId.value = track.id
        _elevationMarker.value = null
    }

    /** Replace the whole viewing set (bulk multi-select), clearing any focus. */
    fun setViewingTracks(tracks: List<Track>) {
        // Keep the navigated track out of the viewing set so it isn't drawn twice; it stays on the
        // map as the split line while any other selected tracks show alongside it.
        _viewingTracks.value = tracks.filterNot { it.id == _navigation.value?.track?.id }
        _focusedTrackId.value = null
        _elevationMarker.value = null
    }

    fun removeViewingTrack(id: String) {
        _viewingTracks.value = _viewingTracks.value.filterNot { it.id == id }
        if (_focusedTrackId.value == id || _viewingTracks.value.isEmpty()) {
            _focusedTrackId.value = null
        }
        if (_cropState.value?.trackId == id) _cropState.value = null
        _elevationMarker.value = null
    }

    fun clearViewingTracks() {
        _viewingTracks.value = emptyList()
        _focusedTrackId.value = null
        _cropState.value = null
        _elevationMarker.value = null
    }

    /** Begin cropping the given viewing track, starting with the full range selected. */
    fun startCrop(trackId: String) {
        val track = _viewingTracks.value.firstOrNull { it.id == trackId } ?: return
        if (track.points.size < 2) return
        _focusedTrackId.value = trackId
        _cropState.value = TrackCropState(trackId, 0, track.points.lastIndex)
        _elevationMarker.value = null
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

    /** Set (or clear, with null) the focused track's point index marked by scrubbing its chart. */
    fun setElevationMarker(index: Int?) {
        _elevationMarker.value = index
    }

    fun setFocusedTrack(id: String?) {
        // Focus (banner + altitude chart) is a track-view affordance; the navigation card owns the
        // top band while active, so tapping an "other" track on the map must not focus it.
        if (_navigation.value != null) return
        // A stray map tap must not unfocus (and so hide) the track being cropped; Cancel/Back are
        // the explicit exits. Clearing focus mid-crop would leave the crop session dangling.
        if (id == null && isCroppingFocused()) return
        _focusedTrackId.value = id
        _elevationMarker.value = null
    }

    fun toggleFocusedTrack(id: String) {
        if (_navigation.value != null) return   // see setFocusedTrack: no focus while navigating
        if (_focusedTrackId.value == id && isCroppingFocused()) return
        _focusedTrackId.value = if (_focusedTrackId.value == id) null else id
        _elevationMarker.value = null
    }

    private fun isCroppingFocused(): Boolean =
        _cropState.value != null && _cropState.value?.trackId == _focusedTrackId.value

    fun startNavigation(track: Track, reversed: Boolean = false) {
        if (_isRecording.value) return   // recording and navigation are mutually exclusive
        // Other viewed tracks stay on the map alongside the grey/blue split line; only drop the
        // navigated track from the set so it isn't drawn twice. Focus-only state (banner, altitude
        // chart, crop) is cleared since the navigation card owns the top band while active.
        _viewingTracks.value = _viewingTracks.value.filterNot { it.id == track.id }
        _focusedTrackId.value = null
        _cropState.value = null
        _elevationMarker.value = null
        _navigationProgress.value = null
        _navigation.value = NavigationSession(track, reversed)
    }

    fun toggleNavigationReverse() {
        val current = _navigation.value ?: return
        _navigationProgress.value = null   // computed against the old direction
        _navigation.value = current.copy(reversed = !current.reversed)
    }

    fun stopNavigation() {
        _navigation.value = null
        _navigationProgress.value = null
    }

    fun updateNavigationProgress(progress: NavigationProgress) {
        if (_navigation.value == null) return   // a late fix must not resurrect an ended session
        _navigationProgress.value = progress
    }

    suspend fun importTrack(gpxContent: String): Track? = importParsed { Track.fromGPX(gpxContent) }

    suspend fun importTrackFromBytes(data: ByteArray): Track? = importParsed { Track.fromBytes(data) }

    /**
     * Import many [files] into [folder] (null = unfiled). Each file is a .gpx/.fit, or a .zip whose
     * .gpx/.fit entries are each imported. Names are made unique across existing tracks and within
     * the batch. Shares [importParsed]'s threading/persistence contract.
     */
    suspend fun importTracks(files: List<ByteArray>, folder: String? = null): BulkImportResult =
        withContext(Dispatchers.Default) {
            val destination = normalizeFolderName(folder)
            val payloads = files.flatMap { bytes ->
                if (ArchiveExtractor.isZip(bytes)) {
                    ArchiveExtractor.extract(bytes) { isTrackFileName(it) }.map { it.bytes }
                } else {
                    listOf(bytes)
                }
            }
            val existingNames = _tracks.value.mapTo(mutableListOf()) { it.name }
            val imported = mutableListOf<Track>()
            var failed = 0
            for (payload in payloads) {
                val parsed = Track.fromBytes(payload)
                if (parsed == null) {
                    failed++
                    continue
                }
                val uniqueName = NamingUtils.makeUnique(parsed.name, existingNames)
                existingNames.add(uniqueName)
                val track = parsed.copy(name = uniqueName, folder = destination)
                persistTrack(track)
                imported.add(track)
            }
            BulkImportResult(imported, failed)
        }

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
