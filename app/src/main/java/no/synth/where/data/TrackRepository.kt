package no.synth.where.data

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.maplibre.android.geometry.LatLng
import java.io.File

class TrackRepository private constructor(private val context: Context) {
    private val gson = Gson()
    private val tracksFile = File(context.filesDir, "tracks.json")

    private val _tracks = mutableStateListOf<Track>()
    val tracks: List<Track> get() = _tracks

    private val _currentTrack = MutableStateFlow<Track?>(null)
    val currentTrack: StateFlow<Track?> = _currentTrack.asStateFlow()

    private val _viewingTrack = MutableStateFlow<Track?>(null)
    val viewingTrack: StateFlow<Track?> = _viewingTrack.asStateFlow()

    val isRecording = mutableStateOf(false)

    init {
        loadTracks()
    }

    private fun loadTracks() {
        if (tracksFile.exists()) {
            try {
                val json = tracksFile.readText()
                val type = object : TypeToken<List<Track>>() {}.type
                val loadedTracks: List<Track> = gson.fromJson(json, type)
                _tracks.clear()
                _tracks.addAll(loadedTracks.filter { !it.isRecording })
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun saveTracks() {
        try {
            val tracksToSave = _tracks.filter { !it.isRecording }
            val json = gson.toJson(tracksToSave)
            tracksFile.writeText(json)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun startNewTrack(name: String = "Track ${System.currentTimeMillis()}") {
        val track = Track(
            name = name,
            points = emptyList(),
            startTime = System.currentTimeMillis(),
            isRecording = true
        )
        _currentTrack.value = track
        isRecording.value = true
    }

    fun continueTrack(track: Track) {
        _currentTrack.value = track.copy(isRecording = true)
        isRecording.value = true
        _tracks.remove(track)
    }

    fun addTrackPoint(latLng: LatLng, altitude: Double? = null, accuracy: Float? = null) {
        val current = _currentTrack.value ?: return
        val point = TrackPoint(
            latLng = latLng,
            timestamp = System.currentTimeMillis(),
            altitude = altitude,
            accuracy = accuracy
        )
        _currentTrack.value = current.copy(points = current.points + point)
    }

    fun stopRecording() {
        val current = _currentTrack.value ?: return
        val finishedTrack = current.copy(
            endTime = System.currentTimeMillis(),
            isRecording = false
        )
        _tracks.add(0, finishedTrack)
        _currentTrack.value = null
        isRecording.value = false
        saveTracks()
    }

    fun discardRecording() {
        _currentTrack.value = null
        isRecording.value = false
    }

    fun deleteTrack(track: Track) {
        _tracks.remove(track)
        saveTracks()
    }

    fun renameTrack(track: Track, newName: String) {
        // Check if it's the current recording track
        if (_currentTrack.value?.id == track.id) {
            _currentTrack.value = track.copy(name = newName)
        } else {
            // It's a saved track
            val index = _tracks.indexOf(track)
            if (index >= 0) {
                _tracks[index] = track.copy(name = newName)
                saveTracks()
            }
        }
    }

    fun setViewingTrack(track: Track) {
        _viewingTrack.value = track
    }

    fun clearViewingTrack() {
        _viewingTrack.value = null
    }

    companion object {
        @Volatile
        private var instance: TrackRepository? = null

        fun getInstance(context: Context): TrackRepository {
            return instance ?: synchronized(this) {
                instance ?: TrackRepository(context.applicationContext).also { instance = it }
            }
        }
    }
}

