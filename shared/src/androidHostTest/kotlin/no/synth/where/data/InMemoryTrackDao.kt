package no.synth.where.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import no.synth.where.data.db.TrackDao
import no.synth.where.data.db.TrackEntity
import no.synth.where.data.db.TrackPointEntity

/**
 * In-memory [TrackDao] modelling the tracks + points tables, so repository paths (crop rewrite,
 * folder mutations, undo) can be verified without an instrumented Room database. Synchronized
 * because the repository collects [getAllTracks] on a background dispatcher while tests write.
 */
class InMemoryTrackDao : TrackDao {
    private val lock = Any()
    private val tracks = LinkedHashMap<String, TrackEntity>()
    private val rows = ArrayList<TrackPointEntity>()
    private var nextId = 1L
    private val allTracks = MutableStateFlow<List<TrackEntity>>(emptyList())

    var renameFolderCalls = 0
        private set

    fun pointCount(trackId: String): Int = synchronized(lock) { rows.count { it.trackId == trackId } }
    fun folderOf(trackId: String): String? = synchronized(lock) { tracks[trackId]?.folder }
    fun trackCount(): Int = synchronized(lock) { tracks.size }
    fun entity(trackId: String): TrackEntity? = synchronized(lock) { tracks[trackId] }

    override fun getAllTracks(): Flow<List<TrackEntity>> = allTracks

    override suspend fun getPointsForTrack(trackId: String): List<TrackPointEntity> =
        synchronized(lock) { rows.filter { it.trackId == trackId }.sortedBy { it.orderIndex } }

    override suspend fun insertTrack(track: TrackEntity) = synchronized(lock) {
        tracks[track.id] = track
        allTracks.value = tracks.values.toList()
    }

    override suspend fun insertTrackPoints(points: List<TrackPointEntity>) = synchronized(lock) {
        points.forEach { rows.add(it.copy(id = nextId++)) }
    }

    override suspend fun deleteTrack(trackId: String) = synchronized(lock) {
        tracks.remove(trackId)
        rows.removeAll { it.trackId == trackId }
        allTracks.value = tracks.values.toList()
    }

    override suspend fun deletePointsForTrack(trackId: String) = synchronized(lock) {
        rows.removeAll { it.trackId == trackId }
        Unit
    }

    override suspend fun renameTrack(trackId: String, name: String) = synchronized(lock) {
        tracks[trackId]?.let { tracks[trackId] = it.copy(name = name) }
        Unit
    }

    override suspend fun updateFolderForTracks(trackIds: List<String>, folder: String?) = synchronized(lock) {
        trackIds.forEach { id -> tracks[id]?.let { tracks[id] = it.copy(folder = folder) } }
        allTracks.value = tracks.values.toList()
    }

    override suspend fun renameFolder(oldName: String, newName: String) = synchronized(lock) {
        renameFolderCalls++
        tracks.replaceAll { _, track -> if (track.folder == oldName) track.copy(folder = newName) else track }
        allTracks.value = tracks.values.toList()
    }

    override suspend fun clearFolder(folderName: String) = synchronized(lock) {
        tracks.replaceAll { _, track -> if (track.folder == folderName) track.copy(folder = null) else track }
        allTracks.value = tracks.values.toList()
    }

    override suspend fun getAllTracksOnce(): List<TrackEntity> = synchronized(lock) { tracks.values.toList() }
}
