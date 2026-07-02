package no.synth.where.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import no.synth.where.data.TrackUrlImporter
import no.synth.where.data.Track
import no.synth.where.data.TrackRepository

class TracksScreenViewModel(
    private val trackRepository: TrackRepository
) : ViewModel() {

    val tracks = trackRepository.tracks
    val isRecording = trackRepository.isRecording

    private val _isImportingUrl = MutableStateFlow(false)
    val isImportingUrl: StateFlow<Boolean> = _isImportingUrl

    private val _newlyImportedTrackId = MutableStateFlow<String?>(null)
    val newlyImportedTrackId: StateFlow<String?> = _newlyImportedTrackId

    fun clearNewlyImportedTrackId() {
        _newlyImportedTrackId.value = null
    }

    fun deleteTrack(track: Track) {
        trackRepository.deleteTrack(track)
    }

    fun renameTrack(track: Track, newName: String) {
        trackRepository.renameTrack(track, newName)
    }

    fun importTrackFromBytes(data: ByteArray, onResult: (Track?) -> Unit) {
        viewModelScope.launch {
            try {
                val imported = trackRepository.importTrackFromBytes(data)?.also {
                    _newlyImportedTrackId.value = it.id
                }
                onResult(imported)
            } catch (e: Exception) {
                Timber.e(e, "Failed to import track from bytes")
                onResult(null)
            }
        }
    }

    fun importFromUrl(input: String, onResult: (Track?) -> Unit) {
        viewModelScope.launch {
            _isImportingUrl.value = true
            try {
                val importer = TrackUrlImporter()
                val track = importer.importFromUrl(input)
                if (track != null) {
                    val gpx = track.toGPX()
                    val imported = trackRepository.importTrack(gpx)
                    if (imported != null) _newlyImportedTrackId.value = imported.id
                    onResult(imported)
                } else {
                    onResult(null)
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to import track from URL")
                onResult(null)
            } finally {
                _isImportingUrl.value = false
            }
        }
    }
}
