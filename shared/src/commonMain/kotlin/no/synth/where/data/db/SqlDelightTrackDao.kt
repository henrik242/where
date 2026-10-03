package no.synth.where.data.db

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/** SQLDelight-backed [TrackDao]. Queries run on [dispatcher]; multi-row writes are transactional. */
class SqlDelightTrackDao(
    private val db: WhereDatabase,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : TrackDao {
    private val tracks = db.tracksQueries
    private val points = db.trackPointsQueries

    override fun getAllTracks(): Flow<List<TrackEntity>> =
        tracks.getAllTracks(::mapTrack).asFlow().mapToList(dispatcher)

    override suspend fun getPointsForTrack(trackId: String): List<TrackPointEntity> =
        withContext(dispatcher) { points.getPointsForTrack(trackId, ::mapPoint).executeAsList() }

    override suspend fun insertTrack(track: TrackEntity) = withContext(dispatcher) { insertTrackRow(track) }

    override suspend fun insertTrackPoints(points: List<TrackPointEntity>) = withContext(dispatcher) {
        db.transaction { points.forEach(::insertPointRow) }
    }

    override suspend fun insertTrackWithPoints(track: TrackEntity, points: List<TrackPointEntity>) =
        withContext(dispatcher) {
            db.transaction {
                insertTrackRow(track)
                points.forEach(::insertPointRow)
            }
        }

    override suspend fun replaceTrackWithPoints(track: TrackEntity, points: List<TrackPointEntity>) =
        withContext(dispatcher) {
            db.transaction {
                this@SqlDelightTrackDao.points.deletePointsForTrack(track.id)
                insertTrackRow(track)
                points.forEach(::insertPointRow)
            }
        }

    override suspend fun deleteTrack(trackId: String) = withContext(dispatcher) {
        // Delete child points explicitly so correctness doesn't depend on PRAGMA foreign_keys.
        db.transaction {
            points.deletePointsForTrack(trackId)
            tracks.deleteTrack(trackId)
        }
    }

    override suspend fun deletePointsForTrack(trackId: String) =
        withContext(dispatcher) { points.deletePointsForTrack(trackId); Unit }

    override suspend fun renameTrack(trackId: String, name: String) =
        withContext(dispatcher) { tracks.renameTrack(name, trackId); Unit }

    override suspend fun updateColor(trackId: String, color: String?) =
        withContext(dispatcher) { tracks.updateColor(color, trackId); Unit }

    override suspend fun updateFolderForTracks(trackIds: List<String>, folder: String?) =
        withContext(dispatcher) { tracks.updateFolderForTracks(folder, trackIds); Unit }

    override suspend fun renameFolder(oldName: String, newName: String) =
        withContext(dispatcher) { tracks.renameFolder(newName, oldName); Unit }

    override suspend fun clearFolder(folderName: String) =
        withContext(dispatcher) { tracks.clearFolder(folderName); Unit }

    override suspend fun getAllTracksOnce(): List<TrackEntity> =
        withContext(dispatcher) { tracks.getAllTracksOnce(::mapTrack).executeAsList() }

    override suspend fun findTrackIdBySourceId(sourceId: String): String? =
        withContext(dispatcher) { tracks.findTrackIdBySourceId(sourceId).executeAsOneOrNull() }

    private fun insertTrackRow(track: TrackEntity) = tracks.insertTrack(
        id = track.id,
        name = track.name,
        startTime = track.startTime,
        endTime = track.endTime,
        isRecording = if (track.isRecording) 1L else 0L,
        folder = track.folder,
        sourceId = track.sourceId,
        color = track.color,
    ).let {}

    private fun insertPointRow(point: TrackPointEntity) = points.insertTrackPoint(
        trackId = point.trackId,
        latitude = point.latitude,
        longitude = point.longitude,
        timestamp = point.timestamp,
        altitude = point.altitude,
        accuracy = point.accuracy?.toDouble(),
        orderIndex = point.orderIndex.toLong(),
    ).let {}

    private fun mapTrack(
        id: String, name: String, startTime: Long, endTime: Long?,
        isRecording: Long, folder: String?, sourceId: String?, color: String?,
    ) = TrackEntity(id, name, startTime, endTime, isRecording != 0L, folder, sourceId, color)

    private fun mapPoint(
        id: Long, trackId: String, latitude: Double, longitude: Double,
        timestamp: Long, altitude: Double?, accuracy: Double?, orderIndex: Long,
    ) = TrackPointEntity(id, trackId, latitude, longitude, timestamp, altitude, accuracy?.toFloat(), orderIndex.toInt())
}
