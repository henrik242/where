package no.synth.where.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import no.synth.where.data.db.SavedPointDao
import no.synth.where.data.db.SavedPointEntity
import no.synth.where.util.NamingUtils
import no.synth.where.data.geo.LatLng
import no.synth.where.util.Logger
import no.synth.where.util.currentTimeMillis
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** Waypoints found in the picked files, offered to the user before any of them are stored. */
data class PointImportCandidates(val waypoints: List<ParsedWaypoint>, val failedCount: Int)

class SavedPointsRepository(filesDir: PlatformFile, private val savedPointDao: SavedPointDao) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
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
                    savedPointDao.insertPoint(point.toEntity())
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

    /**
     * Read the `<wpt>` waypoints out of [files] (each a .gpx, or a .zip whose .gpx entries are all
     * read) without storing anything, so the user can pick which ones to keep. A file holding no
     * readable waypoint counts as a failure. Parsing runs off the main thread.
     */
    suspend fun readPoints(files: List<ByteArray>): PointImportCandidates = withContext(Dispatchers.Default) {
        val waypoints = mutableListOf<ParsedWaypoint>()
        var failed = 0
        for (payload in expandArchives(files, ::isPointFileName)) {
            val parsed = GpxWaypoints.parse(payload)
            if (parsed.isEmpty()) failed++ else waypoints.addAll(parsed)
        }
        PointImportCandidates(waypoints, failed)
    }

    /**
     * Store the [waypoints] the user picked, making each name unique against the existing points and
     * within the batch. The DB writes are awaited, so the points are stored by the time this returns.
     */
    @OptIn(ExperimentalUuidApi::class)
    suspend fun importPoints(waypoints: List<ParsedWaypoint>): List<SavedPoint> = withContext(Dispatchers.Default) {
        val existingNames = _savedPoints.value.mapTo(mutableListOf()) { it.name }
        waypoints.map { waypoint ->
            val uniqueName = NamingUtils.makeUnique(waypoint.name, existingNames)
            existingNames.add(uniqueName)
            SavedPoint(
                id = Uuid.random().toString(),
                name = uniqueName,
                latLng = waypoint.latLng,
                description = waypoint.description,
                timestamp = waypoint.timestamp ?: currentTimeMillis()
            ).also { savedPointDao.insertPoint(it.toEntity()) }
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

private fun SavedPoint.toEntity() = SavedPointEntity(
    id = id,
    name = name,
    latitude = latLng.latitude,
    longitude = latLng.longitude,
    description = description,
    timestamp = timestamp,
    color = color
)
