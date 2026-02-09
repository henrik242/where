package no.synth.where.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SavedPointDao {
    @Query("SELECT * FROM saved_points ORDER BY timestamp DESC")
    fun getAllPoints(): Flow<List<SavedPointEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPoint(point: SavedPointEntity)

    @Query("DELETE FROM saved_points WHERE id = :pointId")
    suspend fun deletePointById(pointId: String)

    @Query("UPDATE saved_points SET name = :name, description = :description, color = :color WHERE id = :pointId")
    suspend fun updatePoint(pointId: String, name: String, description: String, color: String)
}
