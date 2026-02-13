package no.synth.where

import androidx.lifecycle.ViewModel
import no.synth.where.data.TrackRepository
import no.synth.where.data.UserPreferences

class WhereAppViewModel(
    val trackRepository: TrackRepository,
    val userPreferences: UserPreferences
) : ViewModel() {
    val showCountyBorders = userPreferences.showCountyBorders
}
