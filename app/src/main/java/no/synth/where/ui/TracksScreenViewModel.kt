package no.synth.where.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import no.synth.where.data.TrackUrlImporter
import no.synth.where.data.Track
import no.synth.where.data.TrackRepository

class TracksScreenViewModel(
    private val trackRepository: TrackRepository
) : ViewModel() {

    val tracks = trackRepository.tracks
    val isRecording = trackRepository.isRecording
    val onMapTrackIds = trackRepository.onMapTrackIds

    private val _isImportingUrl = MutableStateFlow(false)
    val isImportingUrl: StateFlow<Boolean> = _isImportingUrl

    private val _isImporting = MutableStateFlow(false)
    val isImporting: StateFlow<Boolean> = _isImporting

    private val _newlyImportedTrackId = MutableStateFlow<String?>(null)
    val newlyImportedTrackId: StateFlow<String?> = _newlyImportedTrackId

    private val _saveResultMessage = MutableStateFlow<String?>(null)
    val saveResultMessage: StateFlow<String?> = _saveResultMessage

    fun clearNewlyImportedTrackId() {
        _newlyImportedTrackId.value = null
    }

    fun onSaveResultMessageShown() {
        _saveResultMessage.value = null
    }

    /**
     * Runs the platform GPX save (GPX serialization + MediaStore writes) off the main thread and
     * surfaces its result message. [save] returns the user-facing success/failure string.
     */
    fun saveTrack(save: suspend () -> String) {
        viewModelScope.launch {
            _saveResultMessage.value = withContext(Dispatchers.IO) { save() }
        }
    }

    fun deleteTrack(track: Track) {
        trackRepository.deleteTrack(track)
    }

    fun renameTrack(track: Track, newName: String) {
        trackRepository.renameTrack(track, newName)
    }

    fun moveToFolder(tracks: List<Track>, folder: String?) {
        trackRepository.setTracksFolder(tracks.map { it.id }, folder)
    }

    fun renameFolder(oldName: String, newName: String) {
        trackRepository.renameFolder(oldName, newName)
    }

    fun removeFolder(name: String) {
        trackRepository.removeFolder(name)
    }

    fun restoreFolders(previousFolders: Map<String, String?>) {
        trackRepository.restoreFolders(previousFolders)
    }

    /**
     * Reads the file bytes off the main thread and imports them, flipping [isImporting] for the
     * whole operation so the UI can show progress immediately. [readBytes] returns null if the
     * file can't be read; [onResult] receives null on any failure.
     */
    fun importTrackFromBytes(readBytes: suspend () -> ByteArray?, onResult: (Track?) -> Unit) {
        viewModelScope.launch {
            _isImporting.value = true
            try {
                val bytes = withContext(Dispatchers.IO) { readBytes() }
                val imported = bytes?.let {
                    trackRepository.importTrackFromBytes(it)?.also { track ->
                        _newlyImportedTrackId.value = track.id
                    }
                }
                onResult(imported)
            } catch (e: Exception) {
                Timber.e(e, "Failed to import track from bytes")
                onResult(null)
            } finally {
                _isImporting.value = false
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
