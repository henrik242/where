package no.synth.where

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import no.synth.where.data.TrackRepository
import no.synth.where.data.UserPreferences
import javax.inject.Inject

@HiltViewModel
class WhereAppViewModel @Inject constructor(
    val trackRepository: TrackRepository,
    val userPreferences: UserPreferences
) : ViewModel() {
    val showCountyBorders = userPreferences.showCountyBorders
}
