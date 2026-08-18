package no.synth.where.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import no.synth.where.data.SavedPoint

@Composable
fun SavedPointsScreen(
    onBackClick: () -> Unit,
    onShowOnMap: (SavedPoint) -> Unit = {}
) {
    val context = LocalContext.current
    val app = context.applicationContext as no.synth.where.WhereApplication
    val viewModel: SavedPointsScreenViewModel = viewModel { SavedPointsScreenViewModel(app.savedPointsRepository) }
    val savedPoints by viewModel.savedPoints.collectAsState()

    var showEditDialog by remember { mutableStateOf(false) }
    var editingPoint by remember { mutableStateOf<SavedPoint?>(null) }
    val scope = rememberCoroutineScope()

    // An unreadable pick reaches read() as an empty list, reported as "no points found" like any
    // other selection we couldn't get waypoints out of.
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            val files = withContext(Dispatchers.IO) {
                uris.mapNotNull { uri ->
                    runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
                }
            }
            viewModel.pointImport.read(files)
        }
    }

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
            editingPoint?.let { viewModel.updatePoint(it.id, name, description, color) }
            showEditDialog = false
            editingPoint = null
        },
        pointImport = viewModel.pointImport,
        onPickFiles = { filePickerLauncher.launch("*/*") }
    )
}
