package no.synth.where.data.db

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/** SQLDelight-backed [SavedPointDao]. */
class SqlDelightSavedPointDao(
    db: WhereDatabase,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : SavedPointDao {
    private val queries = db.savedPointsQueries

    override fun getAllPoints(): Flow<List<SavedPointEntity>> =
        queries.getAllPoints(::mapPoint).asFlow().mapToList(dispatcher)

    override suspend fun insertPoint(point: SavedPointEntity) = withContext(dispatcher) {
        queries.insertPoint(
            id = point.id,
            name = point.name,
            latitude = point.latitude,
            longitude = point.longitude,
            description = point.description,
            timestamp = point.timestamp,
            color = point.color,
        ).let {}
    }

    override suspend fun deletePointById(pointId: String) =
        withContext(dispatcher) { queries.deletePointById(pointId); Unit }

    override suspend fun updatePoint(pointId: String, name: String, description: String, color: String) =
        withContext(dispatcher) { queries.updatePoint(name, description, color, pointId); Unit }

    private fun mapPoint(
        id: String, name: String, latitude: Double, longitude: Double,
        description: String?, timestamp: Long, color: String?,
    ) = SavedPointEntity(id, name, latitude, longitude, description, timestamp, color)
}
