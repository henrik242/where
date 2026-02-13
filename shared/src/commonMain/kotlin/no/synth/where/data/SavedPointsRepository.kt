package no.synth.where.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import no.synth.where.data.db.SavedPointDao
import no.synth.where.data.db.SavedPointEntity
import no.synth.where.util.NamingUtils
import no.synth.where.data.geo.LatLng
import no.synth.where.util.Logger
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class SavedPointsRepository(filesDir: PlatformFile, private val savedPointDao: SavedPointDao) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json { ignoreUnknownKeys = true }
    private val pointsFile: PlatformFile = filesDir.resolve("saved_points.json")
    private val migratedFile: PlatformFile = filesDir.resolve("saved_points.json.migrated")

    private val _savedPoints = MutableStateFlow<List<SavedPoint>>(emptyList())
    val savedPoints: StateFlow<List<SavedPoint>> = _savedPoints.asStateFlow()

    init {
        scope.launch {
            migrateJsonToRoom()
            collectPoints()
        }
    }

    private suspend fun migrateJsonToRoom() {
        if (pointsFile.exists() && !migratedFile.exists()) {
            try {
                val text = pointsFile.readText()
                val points: List<SavedPoint> = json.decodeFromString(text)
                for (point in points) {
                    val entity = SavedPointEntity(
                        id = point.id,
                        name = point.name,
                        latitude = point.latLng.latitude,
                        longitude = point.latLng.longitude,
                        description = point.description,
                        timestamp = point.timestamp,
                        color = point.color
                    )
                    savedPointDao.insertPoint(entity)
                }
                pointsFile.renameTo(migratedFile)
                Logger.d("Migrated ${points.size} saved points from JSON to Room")
            } catch (e: Exception) {
                Logger.e(e, "Saved points JSON to Room migration error")
            }
        }
    }

    private suspend fun collectPoints() {
        savedPointDao.getAllPoints().collect { entities ->
            _savedPoints.value = entities.map { entity ->
                SavedPoint(
                    id = entity.id,
                    name = entity.name,
                    latLng = LatLng(entity.latitude, entity.longitude),
                    description = entity.description,
                    timestamp = entity.timestamp,
                    color = entity.color
                )
            }
        }
    }

    @OptIn(ExperimentalUuidApi::class)
    fun addPoint(name: String, latLng: LatLng, description: String = "", color: String = "#FF5722") {
        val uniqueName = NamingUtils.makeUnique(name, _savedPoints.value.map { it.name })
        val point = SavedPointEntity(
            id = Uuid.random().toString(),
            name = uniqueName,
            latitude = latLng.latitude,
            longitude = latLng.longitude,
            description = description,
            color = color
        )
        scope.launch {
            try {
                savedPointDao.insertPoint(point)
            } catch (e: Exception) {
                Logger.e(e, "Saved points repository error")
            }
        }
    }

    fun deletePoint(pointId: String) {
        scope.launch {
            try {
                savedPointDao.deletePointById(pointId)
            } catch (e: Exception) {
                Logger.e(e, "Saved points repository error")
            }
        }
    }

    fun updatePoint(pointId: String, name: String, description: String, color: String) {
        val otherNames = _savedPoints.value.filter { it.id != pointId }.map { it.name }
        val uniqueName = NamingUtils.makeUnique(name, otherNames)

        scope.launch {
            try {
                savedPointDao.updatePoint(pointId, uniqueName, description, color)
            } catch (e: Exception) {
                Logger.e(e, "Saved points repository error")
            }
        }
    }
}
