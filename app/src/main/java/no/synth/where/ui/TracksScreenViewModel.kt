package no.synth.where.ui

import androidx.lifecycle.ViewModel
import no.synth.where.data.Track
import no.synth.where.data.TrackRepository

class TracksScreenViewModel(
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
}
