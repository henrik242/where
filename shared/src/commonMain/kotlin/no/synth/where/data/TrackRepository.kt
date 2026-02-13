package no.synth.where.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import no.synth.where.data.db.TrackDao
import no.synth.where.data.db.TrackEntity
import no.synth.where.data.db.TrackPointEntity
import no.synth.where.util.NamingUtils
import no.synth.where.data.geo.LatLng
import no.synth.where.util.Logger
import no.synth.where.util.currentTimeMillis

class TrackRepository(filesDir: PlatformFile, private val trackDao: TrackDao) {
    private val json = Json { ignoreUnknownKeys = true }
    private val tracksFile = filesDir.resolve("tracks.json")
    private val migratedFile = filesDir.resolve("tracks.json.migrated")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _tracks = MutableStateFlow<List<Track>>(emptyList())
    val tracks: StateFlow<List<Track>> = _tracks.asStateFlow()

    private val _currentTrack = MutableStateFlow<Track?>(null)
    val currentTrack: StateFlow<Track?> = _currentTrack.asStateFlow()

    private val _viewingTrack = MutableStateFlow<Track?>(null)
    val viewingTrack: StateFlow<Track?> = _viewingTrack.asStateFlow()

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    init {
        scope.launch {
            migrateJsonToRoom()
            collectTracks()
        }
    }

    private suspend fun migrateJsonToRoom() {
        if (tracksFile.exists() && !migratedFile.exists()) {
            try {
                val text = tracksFile.readText()
                val loadedTracks: List<Track> = json.decodeFromString(text)
                val tracksToMigrate = loadedTracks.filter { !it.isRecording }
                for (track in tracksToMigrate) {
                    val entity = TrackEntity(
                        id = track.id,
                        name = track.name,
                        startTime = track.startTime,
                        endTime = track.endTime,
                        isRecording = false
                    )
                    val pointEntities = track.points.mapIndexed { index, point ->
                        TrackPointEntity(
                            trackId = track.id,
                            latitude = point.latLng.latitude,
                            longitude = point.latLng.longitude,
                            timestamp = point.timestamp,
                            altitude = point.altitude,
                            accuracy = point.accuracy,
                            orderIndex = index
                        )
                    }
                    trackDao.insertTrackWithPoints(entity, pointEntities)
                }
                tracksFile.renameTo(migratedFile)
                Logger.d("Migrated ${tracksToMigrate.size} tracks from JSON to Room")
            } catch (e: Exception) {
                Logger.e(e, "Track JSON to Room migration error")
            }
        }
    }

    private suspend fun collectTracks() {
        trackDao.getAllTracks().collect { entities ->
            val tracks = entities.map { entity ->
                val points = trackDao.getPointsForTrack(entity.id).map { pointEntity ->
                    TrackPoint(
                        latLng = LatLng(pointEntity.latitude, pointEntity.longitude),
                        timestamp = pointEntity.timestamp,
                        altitude = pointEntity.altitude,
                        accuracy = pointEntity.accuracy
                    )
                }
                Track(
                    id = entity.id,
                    name = entity.name,
                    points = points,
                    startTime = entity.startTime,
                    endTime = entity.endTime,
                    isRecording = entity.isRecording
                )
            }
            _tracks.value = tracks
        }
    }

    private fun saveTrack(track: Track) {
        scope.launch {
            try {
                val entity = TrackEntity(
                    id = track.id,
                    name = track.name,
                    startTime = track.startTime,
                    endTime = track.endTime,
                    isRecording = false
                )
                val pointEntities = track.points.mapIndexed { index, point ->
                    TrackPointEntity(
                        trackId = track.id,
                        latitude = point.latLng.latitude,
                        longitude = point.latLng.longitude,
                        timestamp = point.timestamp,
                        altitude = point.altitude,
                        accuracy = point.accuracy,
                        orderIndex = index
                    )
                }
                trackDao.insertTrackWithPoints(entity, pointEntities)
            } catch (e: Exception) {
                Logger.e(e, "Track repository error")
            }
        }
    }

    fun startNewTrack(name: String = "Track ${currentTimeMillis()}") {
        val track = Track(
            name = name,
            points = emptyList(),
            startTime = currentTimeMillis(),
            isRecording = true
        )
        _currentTrack.value = track
        _isRecording.value = true
    }

    fun continueTrack(track: Track) {
        _currentTrack.value = track.copy(isRecording = true)
        _isRecording.value = true
        scope.launch {
            trackDao.deleteTrack(track.id)
        }
    }

    fun addTrackPoint(latLng: LatLng, altitude: Double? = null, accuracy: Float? = null) {
        val current = _currentTrack.value ?: return
        val point = TrackPoint(
            latLng = latLng,
            timestamp = currentTimeMillis(),
            altitude = altitude,
            accuracy = accuracy
        )
        _currentTrack.value = current.copy(points = current.points + point)
    }

    fun stopRecording() {
        val current = _currentTrack.value ?: return
        val uniqueName = NamingUtils.makeUnique(current.name, _tracks.value.map { it.name })
        val finishedTrack = current.copy(
            name = uniqueName,
            endTime = currentTimeMillis(),
            isRecording = false
        )
        _currentTrack.value = null
        _isRecording.value = false
        saveTrack(finishedTrack)
    }

    fun discardRecording() {
        _currentTrack.value = null
        _isRecording.value = false
    }

    fun deleteTrack(track: Track) {
        scope.launch {
            trackDao.deleteTrack(track.id)
        }
    }

    fun renameTrack(track: Track, newName: String) {
        if (_currentTrack.value?.id == track.id) {
            _currentTrack.value = track.copy(name = newName)
        } else {
            scope.launch {
                trackDao.renameTrack(track.id, newName)
            }
        }
    }

    fun setViewingTrack(track: Track) {
        _viewingTrack.value = track
    }

    fun clearViewingTrack() {
        _viewingTrack.value = null
    }

    fun importTrack(gpxContent: String): Track? {
        val track = Track.fromGPX(gpxContent) ?: return null
        val uniqueName = NamingUtils.makeUnique(track.name, _tracks.value.map { it.name })
        val trackWithUniqueName = track.copy(name = uniqueName)
        saveTrack(trackWithUniqueName)
        return trackWithUniqueName
    }

    fun createTrackFromPoints(name: String, rulerPoints: List<RulerPoint>) {
        val uniqueName = NamingUtils.makeUnique(name, _tracks.value.map { it.name })
        val trackPoints = rulerPoints.map { rulerPoint ->
            TrackPoint(
                latLng = rulerPoint.latLng,
                timestamp = currentTimeMillis(),
                altitude = null,
                accuracy = null
            )
        }
        val track = Track(
            name = uniqueName,
            points = trackPoints,
            startTime = currentTimeMillis(),
            endTime = currentTimeMillis(),
            isRecording = false
        )
        saveTrack(track)
    }
}
