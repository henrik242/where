package no.synth.where.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import no.synth.where.data.SavedPoint
import no.synth.where.data.SavedPointsRepository

/** The saved points screen on every platform; [onPickFiles] opens the picker, which feeds [pointImport]. */
@Composable
fun SavedPointsScreen(
    repository: SavedPointsRepository,
    pointImport: PointImportState,
    onPickFiles: () -> Unit,
    onBackClick: () -> Unit,
    onShowOnMap: (SavedPoint) -> Unit,
) {
    val savedPoints by repository.savedPoints.collectAsState()
    var editingPoint by remember { mutableStateOf<SavedPoint?>(null) }

    SavedPointsScreenContent(
        savedPoints = savedPoints,
        showEditDialog = editingPoint != null,
        editingPoint = editingPoint,
        onBackClick = onBackClick,
        onEdit = { editingPoint = it },
        onDelete = { repository.deletePoint(it.id) },
        onShowOnMap = onShowOnMap,
        onDismissEdit = { editingPoint = null },
        onSaveEdit = { name, description, color ->
            editingPoint?.let { repository.updatePoint(it.id, name, description, color) }
            editingPoint = null
        },
        pointImport = pointImport,
        onPickFiles = onPickFiles,
    )
}
