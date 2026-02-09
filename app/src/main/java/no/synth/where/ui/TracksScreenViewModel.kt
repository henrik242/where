package no.synth.where.ui

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import no.synth.where.data.Track
import no.synth.where.data.TrackRepository
import javax.inject.Inject

@HiltViewModel
class TracksScreenViewModel @Inject constructor(
    private val trackRepository: TrackRepository
) : ViewModel() {

    val tracks = trackRepository.tracks

    fun deleteTrack(track: Track) {
        trackRepository.deleteTrack(track)
    }

    fun renameTrack(track: Track, newName: String) {
        trackRepository.renameTrack(track, newName)
    }

    fun importTrack(gpxContent: String): Track? {
        return trackRepository.importTrack(gpxContent)
    }

    fun continueTrack(track: Track) {
        trackRepository.continueTrack(track)
    }

    fun setViewingTrack(track: Track) {
        trackRepository.setViewingTrack(track)
    }
}
