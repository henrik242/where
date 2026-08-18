package no.synth.where.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import no.synth.where.data.db.SavedPointDao
import no.synth.where.data.db.SavedPointEntity

/**
 * In-memory [SavedPointDao] so repository paths can be verified without an instrumented Room
 * database. Synchronized because the repository collects [getAllPoints] on a background dispatcher
 * while tests write.
 */
class InMemorySavedPointDao : SavedPointDao {
    private val lock = Any()
    private val points = LinkedHashMap<String, SavedPointEntity>()
    private val allPoints = MutableStateFlow<List<SavedPointEntity>>(emptyList())

    fun all(): List<SavedPointEntity> = synchronized(lock) { points.values.toList() }

    override fun getAllPoints(): Flow<List<SavedPointEntity>> = allPoints

    override suspend fun insertPoint(point: SavedPointEntity) = synchronized(lock) {
        points[point.id] = point
        allPoints.value = points.values.toList()
    }

    override suspend fun deletePointById(pointId: String) = synchronized(lock) {
        points.remove(pointId)
        allPoints.value = points.values.toList()
    }

    override suspend fun updatePoint(pointId: String, name: String, description: String, color: String) =
        synchronized(lock) {
            points[pointId]?.let {
                points[pointId] = it.copy(name = name, description = description, color = color)
                allPoints.value = points.values.toList()
            }
            Unit
        }
}
