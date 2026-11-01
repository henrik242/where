package no.synth.where.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import no.synth.where.data.db.TrackDao
import no.synth.where.data.routing.RoutePoint
import no.synth.where.data.db.TrackEntity
import no.synth.where.data.db.TrackPointEntity
import no.synth.where.util.NamingUtils
import no.synth.where.data.geo.LatLng
import no.synth.where.data.navigation.NavigationProgress
import no.synth.kmpzip.zip.isZip
import no.synth.where.data.navigation.TrackNavigator
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
    private val json = AppJson
    private val tracksFile = filesDir.resolve("tracks.json")
    private val migratedFile = filesDir.resolve("tracks.json.migrated")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val jumpFilter = GpsJumpFilter()

    val tracks: StateFlow<List<Track>>
        field = MutableStateFlow<List<Track>>(emptyList())

    val currentTrack: StateFlow<Track?>
        field = MutableStateFlow<Track?>(null)

    // Multiple saved tracks can be shown at once. Order = the palette color index, so it is kept
    // stable across add/remove. [focusedTrackId] is the one whose banner + altitude chart show and
    // that is emphasized on the map; null means nothing is focused.
    val viewingTracks: StateFlow<List<Track>>
        field = MutableStateFlow<List<Track>>(emptyList())

    val focusedTrackId: StateFlow<String?>
        field = MutableStateFlow<String?>(null)

    val navigation: StateFlow<NavigationSession?>
        field = MutableStateFlow<NavigationSession?>(null)

    // Latest progress along the navigated track. Produced outside the UI (the Android foreground
    // service, or the iOS foreground poller) so navigation keeps running in the background; the UI
    // only observes. Reset to null when the session starts or ends so observers fall back to a
    // "locating" state instead of a stale snapshot; on a direction change it is recomputed in place
    // (see [toggleNavigationReverse]) rather than nulled, so the card doesn't flash "locating".
    val navigationProgress: StateFlow<NavigationProgress?>
        field = MutableStateFlow<NavigationProgress?>(null)

    // So the saved-tracks list can flag which rows are already on the map.
    val onMapTrackIds: StateFlow<Set<String>> =
        combine(viewingTracks, navigation) { viewing, nav -> onMapTrackIdsOf(viewing, nav) }
            .stateIn(scope, SharingStarted.Eagerly, emptySet())

    // Index into the focused track's points marked by scrubbing its altitude chart, or null when
    // none. Transient: cleared whenever the focused track / view changes. During navigation the
    // index refers to the navigated track's points instead.
    val elevationMarker: StateFlow<Int?>
        field = MutableStateFlow<Int?>(null)

    // Whether the navigated track's altitude chart is shown, toggled by tapping the route (mirrors
    // tap-to-focus for viewing tracks). Only meaningful while navigating.
    val navigationChartVisible: StateFlow<Boolean>
        field = MutableStateFlow(false)

    // Active crop of the focused viewing track, or null when not cropping.
    val cropState: StateFlow<TrackCropState?>
        field = MutableStateFlow<TrackCropState?>(null)

    // The pre-crop track after a crop is applied, held so the UI can offer a one-tap undo of the
    // (otherwise irreversible) overwrite. Cleared once the undo affordance is consumed or dismissed.
    val cropUndo: StateFlow<Track?>
        field = MutableStateFlow<Track?>(null)

    val isRecording: StateFlow<Boolean>
        field = MutableStateFlow(false)

    init {
        scope.launch {
            migrateJsonToDb()
            collectTracks()
        }
    }

    private suspend fun migrateJsonToDb() {
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
                Logger.d("Migrated ${tracksToMigrate.size} tracks from JSON to database")
            } catch (e: Exception) {
                Logger.e(e, "Track JSON to database migration error")
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
                    folder = entity.folder,
                    sourceId = entity.sourceId,
                    color = entity.color
                )
            }
            this@TrackRepository.tracks.value = tracks
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
            folder = track.folder,
            sourceId = track.sourceId,
            color = track.color
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
        if (navigation.value != null) return   // recording and navigation are mutually exclusive
        val track = Track(
            name = name,
            points = emptyList(),
            startTime = currentTimeMillis(),
            isRecording = true
        )
        currentTrack.value = track
        isRecording.value = true
    }

    /**
     * Gate a raw fix against the shared [GpsJumpFilter]. Platform callbacks call this once per fix,
     * before both [addTrackPoint] and the live-share coordinator, so a teleport is dropped from both.
     */
    fun acceptFix(latLng: LatLng): Boolean = jumpFilter.accept(latLng, currentTimeMillis())

    fun addTrackPoint(latLng: LatLng, altitude: Double? = null, accuracy: Float? = null) {
        val current = currentTrack.value ?: return
        val point = TrackPoint(
            latLng = latLng,
            timestamp = currentTimeMillis(),
            altitude = altitude,
            accuracy = accuracy
        )
        currentTrack.value = current.copy(points = current.points + point)
    }

    fun stopRecording() {
        val current = currentTrack.value ?: return
        val uniqueName = NamingUtils.makeUnique(current.name, tracks.value.map { it.name })
        val finishedTrack = current.copy(
            name = uniqueName,
            endTime = currentTimeMillis(),
            isRecording = false
        )
        currentTrack.value = null
        isRecording.value = false
        saveTrack(finishedTrack)
    }

    fun discardRecording() {
        currentTrack.value = null
        isRecording.value = false
    }

    fun deleteTrack(track: Track) {
        scope.launch {
            trackDao.deleteTrack(track.id)
        }
    }

    fun renameTrack(track: Track, newName: String) {
        if (currentTrack.value?.id == track.id) {
            currentTrack.value = track.copy(name = newName)
        } else {
            scope.launch {
                trackDao.renameTrack(track.id, newName)
            }
        }
    }

    /** Set a track's line color; null resets it to the automatic palette color. */
    fun setTrackColor(trackId: String, color: String?) {
        // Update the on-map snapshot immediately so the line/banner recolor without a re-add; the
        // DB write feeds the persisted [tracks] list (and survives restarts).
        viewingTracks.value = viewingTracks.value.map { if (it.id == trackId) it.copy(color = color) else it }
        scope.launch { trackDao.updateColor(trackId, color) }
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
        if (navigation.value?.track?.id == track.id) return
        if (viewingTracks.value.none { it.id == track.id }) {
            viewingTracks.value = viewingTracks.value + track
        }
        // Focusing shows the banner + altitude chart, which the navigation card owns while active,
        // so add the track without focusing it when navigating.
        if (navigation.value == null) focusedTrackId.value = track.id
        elevationMarker.value = null
    }

    /** Replace the whole viewing set (bulk multi-select), clearing any focus. */
    fun setViewingTracks(tracks: List<Track>) {
        // Keep the navigated track out of the viewing set so it isn't drawn twice; it stays on the
        // map as the split line while any other selected tracks show alongside it.
        viewingTracks.value = tracks.filterNot { it.id == navigation.value?.track?.id }
        focusedTrackId.value = null
        elevationMarker.value = null
    }

    fun removeViewingTrack(id: String) {
        viewingTracks.value = viewingTracks.value.filterNot { it.id == id }
        if (focusedTrackId.value == id || viewingTracks.value.isEmpty()) {
            focusedTrackId.value = null
        }
        if (cropState.value?.trackId == id) cropState.value = null
        elevationMarker.value = null
    }

    fun clearViewingTracks() {
        viewingTracks.value = emptyList()
        focusedTrackId.value = null
        cropState.value = null
        elevationMarker.value = null
    }

    /** Begin cropping the given viewing track, starting with the full range selected. */
    fun startCrop(trackId: String) {
        val track = viewingTracks.value.firstOrNull { it.id == trackId } ?: return
        if (track.points.size < 2) return
        focusedTrackId.value = trackId
        cropState.value = TrackCropState(trackId, 0, track.points.lastIndex)
        elevationMarker.value = null
    }

    fun updateCrop(startIndex: Int, endIndex: Int) {
        val current = cropState.value ?: return
        val track = viewingTracks.value.firstOrNull { it.id == current.trackId } ?: return
        val (start, end) = clampCropRange(track.points.size, startIndex, endIndex)
        cropState.value = current.copy(startIndex = start, endIndex = end)
    }

    fun cancelCrop() {
        cropState.value = null
    }

    /** Overwrite the cropped track in place with the selected span and exit crop mode. */
    fun applyCrop() {
        val current = cropState.value ?: return
        val track = viewingTracks.value.firstOrNull { it.id == current.trackId } ?: return
        cropState.value = null
        val cropped = track.cropped(current.startIndex, current.endIndex)
        if (cropped === track) return   // full-range selection: nothing trimmed, skip the DB rewrite
        replaceViewingTrack(cropped)
        cropUndo.value = track          // offer undo of the destructive overwrite
        scope.launch { overwriteTrack(cropped) }
    }

    /** Restore the pre-crop track (reverses the last [applyCrop]). */
    fun undoCrop() {
        val original = cropUndo.value ?: return
        cropUndo.value = null
        replaceViewingTrack(original)
        scope.launch { overwriteTrack(original) }
    }

    fun clearCropUndo() {
        cropUndo.value = null
    }

    private fun replaceViewingTrack(track: Track) {
        viewingTracks.value = viewingTracks.value.map { if (it.id == track.id) track else it }
    }

    /** Set (or clear, with null) the focused track's point index marked by scrubbing its chart. */
    fun setElevationMarker(index: Int?) {
        elevationMarker.value = index
    }

    fun setFocusedTrack(id: String?) {
        // Focus (banner + altitude chart) is a track-view affordance; the navigation card owns the
        // top band while active, so tapping an "other" track on the map must not focus it.
        if (navigation.value != null) return
        // A stray map tap must not unfocus (and so hide) the track being cropped; Cancel/Back are
        // the explicit exits. Clearing focus mid-crop would leave the crop session dangling.
        if (id == null && isCroppingFocused()) return
        focusedTrackId.value = id
        elevationMarker.value = null
    }

    fun toggleFocusedTrack(id: String) {
        if (navigation.value != null) return   // see setFocusedTrack: no focus while navigating
        if (focusedTrackId.value == id && isCroppingFocused()) return
        focusedTrackId.value = if (focusedTrackId.value == id) null else id
        elevationMarker.value = null
    }

    private fun isCroppingFocused(): Boolean =
        cropState.value != null && cropState.value?.trackId == focusedTrackId.value

    /** Returns false (and does nothing) if recording, which is mutually exclusive with navigation. */
    fun startNavigation(track: Track, reversed: Boolean = false): Boolean {
        if (isRecording.value) return false
        // Other viewed tracks stay on the map alongside the grey/blue split line; only drop the
        // navigated track from the set so it isn't drawn twice. Focus-only state (banner, altitude
        // chart, crop) is cleared since the navigation card owns the top band while active.
        viewingTracks.value = viewingTracks.value.filterNot { it.id == track.id }
        focusedTrackId.value = null
        cropState.value = null
        clearChartState()
        navigationProgress.value = null
        navigation.value = NavigationSession(track, reversed)
        return true
    }

    /** Navigate a viewing-set track by [id]. Returns false if it isn't in the set (or recording). */
    fun startNavigationById(id: String, reversed: Boolean = false): Boolean =
        viewingTracks.value.firstOrNull { it.id == id }?.let { startNavigation(it, reversed) } ?: false

    fun toggleNavigationReverse() {
        val current = navigation.value ?: return
        val reversed = !current.reversed
        val lastLocation = navigationProgress.value?.location
        elevationMarker.value = null      // point indices flip when reversed; keep the chart open
        navigation.value = current.copy(reversed = reversed)
        // Recompute progress against the last known fix right away. The producers (Android service,
        // iOS poller) only emit on a fresh fix, and the fused provider goes quiet while the user is
        // stationary - exactly the u-turn case - so simply nulling here would strand the card on
        // "locating" until the user moved. Fall back to null only when we never had a fix to reverse.
        navigationProgress.value = lastLocation?.let {
            TrackNavigator(current.track, reversed).progressAt(it)
        }
    }

    /**
     * End navigation and show the just-navigated track in detail mode: put it back on the map (it
     * was dropped from the viewing set by [startNavigation]) and focus it (banner + altitude chart).
     */
    fun stopNavigation() {
        val track = navigation.value?.track
        navigation.value = null
        navigationProgress.value = null
        clearChartState()
        if (track != null) {
            if (viewingTracks.value.none { it.id == track.id }) {
                viewingTracks.value = viewingTracks.value + track
            }
            focusedTrackId.value = track.id
        }
    }

    /**
     * Toggle the navigated track's altitude chart (from tapping the route). No-op when idle or when
     * the route has no elevation data, since the chart would render nothing.
     */
    fun toggleNavigationChart() {
        val track = navigation.value?.track ?: return
        if (!track.hasElevationData()) return
        navigationChartVisible.value = !navigationChartVisible.value
        elevationMarker.value = null
    }

    /** Hide the navigation chart (a tap off the route), mirroring unfocus for viewing tracks. */
    fun hideNavigationChart() {
        if (!navigationChartVisible.value) return
        clearChartState()
    }

    /** Route a track tap: the navigated route toggles its chart; any other track focuses (see below). */
    fun onTrackTapped(id: String) {
        if (id == navigation.value?.track?.id) toggleNavigationChart() else toggleFocusedTrack(id)
    }

    /** Route a tap that missed every track: dismiss the nav chart while navigating, else clear focus. */
    fun onMapTapOutsideTracks() {
        if (navigation.value != null) hideNavigationChart() else setFocusedTrack(null)
    }

    /** Reset the transient altitude-chart state: the scrub marker and the nav-chart visibility. */
    private fun clearChartState() {
        elevationMarker.value = null
        navigationChartVisible.value = false
    }

    fun updateNavigationProgress(progress: NavigationProgress) {
        if (navigation.value == null) return   // a late fix must not resurrect an ended session
        navigationProgress.value = progress
    }

    suspend fun importTrack(gpxContent: String): Track? = importParsed { Track.fromGPX(gpxContent) }

    suspend fun importTrackFromBytes(data: ByteArray): Track? = importParsed { Track.fromBytes(data) }

    /**
     * Import a Strava route's GPX into [folder], tagged with [sourceId] so a later re-import
     * replaces the existing track in place instead of creating a duplicate.
     */
    suspend fun importStravaRoute(gpx: String, sourceId: String, folder: String?): Track? =
        withContext(Dispatchers.Default) {
            val parsed = Track.fromGPX(gpx) ?: return@withContext null
            val existingId = trackDao.findTrackIdBySourceId(sourceId)
            val name = if (existingId != null) parsed.name
                else NamingUtils.makeUnique(parsed.name, tracks.value.map { it.name })
            val track = parsed.copy(
                id = existingId ?: parsed.id,
                name = name,
                folder = normalizeFolderName(folder),
                sourceId = sourceId,
                // Color is a user preference with no GPX source; keep it across re-imports.
                color = existingId?.let { id -> tracks.value.firstOrNull { it.id == id }?.color }
            )
            if (existingId != null) overwriteTrack(track) else persistTrack(track)
            track
        }

    /**
     * Import many [files] into [folder] (null = unfiled). Each file is a .gpx/.fit/.geojson, or a
     * .zip whose track entries are each imported. Names are made unique across existing tracks and
     * within the batch. Shares [importParsed]'s threading/persistence contract.
     */
    suspend fun importTracks(files: List<ByteArray>, folder: String? = null): BulkImportResult =
        withContext(Dispatchers.Default) {
            val destination = normalizeFolderName(folder)
            val payloads = expandArchives(files, ::isTrackFileName)
            val existingNames = tracks.value.mapTo(mutableListOf()) { it.name }
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
        val uniqueName = NamingUtils.makeUnique(track.name, tracks.value.map { it.name })
        val trackWithUniqueName = track.copy(name = uniqueName)
        persistTrack(trackWithUniqueName)
        trackWithUniqueName
    }

    /** Saves a planned route, keeping its DEM elevations, and shows it focused on the map. */
    suspend fun savePlannedRoute(name: String, points: List<RoutePoint>): Track {
        val now = currentTimeMillis()
        val track = Track(
            name = NamingUtils.makeUnique(name, tracks.value.map { it.name }),
            points = points.map { TrackPoint(latLng = it.latLng, timestamp = now, altitude = it.elevation) },
            startTime = now,
            endTime = now,
        )
        persistTrack(track)
        addViewingTrack(track)
        return track
    }

    fun createTrackFromPoints(name: String, rulerPoints: List<RulerPoint>) {
        val uniqueName = NamingUtils.makeUnique(name, tracks.value.map { it.name })
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
