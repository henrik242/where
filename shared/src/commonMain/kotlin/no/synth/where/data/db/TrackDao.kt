package no.synth.where.data.db

import kotlinx.coroutines.flow.Flow

interface TrackDao {
    fun getAllTracks(): Flow<List<TrackEntity>>

    suspend fun getPointsForTrack(trackId: String): List<TrackPointEntity>

    suspend fun insertTrack(track: TrackEntity)

    suspend fun insertTrackPoints(points: List<TrackPointEntity>)

    // Insert a track and its points atomically. Implementations must back this with a transaction.
    suspend fun insertTrackWithPoints(track: TrackEntity, points: List<TrackPointEntity>)

    suspend fun deleteTrack(trackId: String)

    suspend fun deletePointsForTrack(trackId: String)

    // Overwrite a track in place, atomically. Implementations must back this with a transaction.
    suspend fun replaceTrackWithPoints(track: TrackEntity, points: List<TrackPointEntity>)

    suspend fun renameTrack(trackId: String, name: String)

    suspend fun updateColor(trackId: String, color: String?)

    suspend fun updateFolderForTracks(trackIds: List<String>, folder: String?)

    suspend fun renameFolder(oldName: String, newName: String)

    suspend fun clearFolder(folderName: String)

    suspend fun getAllTracksOnce(): List<TrackEntity>

    suspend fun findTrackIdBySourceId(sourceId: String): String?
}
