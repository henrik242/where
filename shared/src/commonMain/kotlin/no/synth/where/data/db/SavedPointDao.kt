package no.synth.where.data.db

import kotlinx.coroutines.flow.Flow

interface SavedPointDao {
    fun getAllPoints(): Flow<List<SavedPointEntity>>

    suspend fun insertPoint(point: SavedPointEntity)

    suspend fun deletePointById(pointId: String)

    suspend fun updatePoint(pointId: String, name: String, description: String, color: String)
}
