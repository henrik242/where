package no.synth.where

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import no.synth.where.data.PlatformFile
import no.synth.where.data.SkiTrailDownloader
import no.synth.where.data.SkiTrailRepository
import no.synth.where.data.TrackRepository
import no.synth.where.data.UserPreferences

class WhereAppViewModel(
    val trackRepository: TrackRepository,
    val userPreferences: UserPreferences
) : ViewModel() {
    val showCountyBorders = userPreferences.showCountyBorders

    private val _skiTrailsReady = MutableStateFlow(false)
    val skiTrailsReady = _skiTrailsReady.asStateFlow()

    private var skiTrailDownloadStarted = false

    fun downloadSkiTrailsIfNeeded(cacheDir: PlatformFile) {
        if (skiTrailDownloadStarted) return
        skiTrailDownloadStarted = true
        viewModelScope.launch {
            val success = SkiTrailDownloader.downloadAndCacheSkiTrails(cacheDir)
            if (success) {
                SkiTrailRepository.reloadSkiTrails(cacheDir)
                _skiTrailsReady.value = true
            } else {
                skiTrailDownloadStarted = false
            }
        }
    }
}
