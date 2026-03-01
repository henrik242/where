package no.synth.where.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import no.synth.where.data.TrackUrlImporter
import no.synth.where.data.Track
import no.synth.where.data.TrackRepository

class TracksScreenViewModel(
    private val trackRepository: TrackRepository
) : ViewModel() {

    val tracks = trackRepository.tracks

    private val _isImportingUrl = MutableStateFlow(false)
    val isImportingUrl: StateFlow<Boolean> = _isImportingUrl

    fun deleteTrack(track: Track) {
        trackRepository.deleteTrack(track)
    }

    fun renameTrack(track: Track, newName: String) {
        trackRepository.renameTrack(track, newName)
    }

    fun importTrack(gpxContent: String): Track? {
        return trackRepository.importTrack(gpxContent)
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
                    onResult(imported)
                } else {
                    onResult(null)
                }
            } catch (_: Exception) {
                onResult(null)
            } finally {
                _isImportingUrl.value = false
            }
        }
    }
}
