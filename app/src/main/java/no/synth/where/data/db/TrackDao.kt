package no.synth.where.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

data class TrackWithPoints(
    val track: TrackEntity,
    val points: List<TrackPointEntity>
)

@Dao
interface TrackDao {
    @Query("SELECT * FROM tracks WHERE isRecording = 0 ORDER BY startTime DESC")
    fun getAllTracks(): Flow<List<TrackEntity>>

    @Query("SELECT * FROM track_points WHERE trackId = :trackId ORDER BY orderIndex")
    suspend fun getPointsForTrack(trackId: String): List<TrackPointEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrack(track: TrackEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrackPoints(points: List<TrackPointEntity>)

    @Transaction
    suspend fun insertTrackWithPoints(track: TrackEntity, points: List<TrackPointEntity>) {
        insertTrack(track)
        insertTrackPoints(points)
    }

    @Query("DELETE FROM tracks WHERE id = :trackId")
    suspend fun deleteTrack(trackId: String)

    @Query("UPDATE tracks SET name = :name WHERE id = :trackId")
    suspend fun renameTrack(trackId: String, name: String)

    @Query("SELECT * FROM tracks ORDER BY startTime DESC")
    suspend fun getAllTracksOnce(): List<TrackEntity>
}
