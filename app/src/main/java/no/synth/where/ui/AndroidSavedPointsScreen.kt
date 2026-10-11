package no.synth.where.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import no.synth.where.data.SavedPoint

/** Android glue for the shared [SavedPointsScreen]: the SAF picker. */
@Composable
fun AndroidSavedPointsScreen(
    onBackClick: () -> Unit,
    onShowOnMap: (SavedPoint) -> Unit,
) {
    val context = LocalContext.current
    val app = context.applicationContext as no.synth.where.WhereApplication
    // Retained so an import survives a rotation.
    val pointImport = rememberRetainedState { scope -> PointImportState(app.savedPointsRepository, scope) }
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
            pointImport.read(files)
        }
    }

    SavedPointsScreen(
        repository = app.savedPointsRepository,
        pointImport = pointImport,
        onPickFiles = { filePickerLauncher.launch("*/*") },
        onBackClick = onBackClick,
        onShowOnMap = onShowOnMap,
    )
}
