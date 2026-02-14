package no.synth.where.ui

import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import org.koin.androidx.compose.koinViewModel
import no.synth.where.data.SavedPoint

@Composable
fun SavedPointsScreen(
    onBackClick: () -> Unit,
    onShowOnMap: (SavedPoint) -> Unit = {}
) {
    val viewModel: SavedPointsScreenViewModel = koinViewModel()
    val savedPoints by viewModel.savedPoints.collectAsState()

    var showEditDialog by remember { mutableStateOf(false) }
    var editingPoint by remember { mutableStateOf<SavedPoint?>(null) }

    SavedPointsScreenContent(
        savedPoints = savedPoints,
        showEditDialog = showEditDialog,
        editingPoint = editingPoint,
        onBackClick = onBackClick,
        onEdit = { point ->
            editingPoint = point
            showEditDialog = true
        },
        onDelete = { point -> viewModel.deletePoint(point.id) },
        onShowOnMap = onShowOnMap,
        onDismissEdit = {
            showEditDialog = false
            editingPoint = null
        },
        onSaveEdit = { name, description, color ->
            viewModel.updatePoint(editingPoint!!.id, name, description, color)
            showEditDialog = false
            editingPoint = null
        }
    )
}
