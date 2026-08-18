package no.synth.where.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import no.synth.where.data.SavedPointsRepository

class SavedPointsScreenViewModel(
    private val savedPointsRepository: SavedPointsRepository
) : ViewModel() {

    val savedPoints = savedPointsRepository.savedPoints

    // Held here, not remembered in the composable, so an import survives a rotation.
    val pointImport = PointImportState(savedPointsRepository, viewModelScope)

    fun deletePoint(pointId: String) {
        savedPointsRepository.deletePoint(pointId)
    }

    fun updatePoint(pointId: String, name: String, description: String, color: String) {
        savedPointsRepository.updatePoint(pointId, name, description, color)
    }
}
