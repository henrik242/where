package no.synth.where.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import org.koin.androidx.compose.koinViewModel
import no.synth.where.R
import no.synth.where.data.Track
import no.synth.where.util.Logger
import java.io.File

@Composable
fun TracksScreen(
    onBackClick: () -> Unit,
    onContinueTrack: (Track) -> Unit,
    onShowTrackOnMap: (Track) -> Unit
) {
    val context = LocalContext.current
    val viewModel: TracksScreenViewModel = koinViewModel()
    val tracks by viewModel.tracks.collectAsState()

    var trackToDelete by remember { mutableStateOf<Track?>(null) }
    var trackToRename by remember { mutableStateOf<Track?>(null) }
    var newTrackName by remember { mutableStateOf("") }
    var showImportError by remember { mutableStateOf(false) }
    var importErrorMessage by remember { mutableStateOf("") }

    val resources = context.resources

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            try {
                val inputStream = context.contentResolver.openInputStream(it)
                val gpxContent = inputStream?.bufferedReader()?.use { reader -> reader.readText() }
                inputStream?.close()

                if (gpxContent != null) {
                    val importedTrack = viewModel.importTrack(gpxContent)
                    if (importedTrack == null) {
                        importErrorMessage = resources.getString(R.string.import_gpx_corrupted)
                        showImportError = true
                    }
                } else {
                    importErrorMessage = resources.getString(R.string.import_gpx_read_failed)
                    showImportError = true
                }
            } catch (e: Exception) {
                importErrorMessage = resources.getString(R.string.import_gpx_error, e.message)
                showImportError = true
            }
        }
    }

    TracksScreenContent(
        tracks = tracks,
        trackToDelete = trackToDelete,
        trackToRename = trackToRename,
        newTrackName = newTrackName,
        showImportError = showImportError,
        importErrorMessage = importErrorMessage,
        onBackClick = onBackClick,
        onImport = { filePickerLauncher.launch("*/*") },
        onExport = { track -> shareTrack(context, track) },
        onSave = { track -> saveTrackToDownloads(context, track) },
        onOpen = { track -> openTrack(context, track) },
        onDeleteRequest = { track -> trackToDelete = track },
        onConfirmDelete = {
            trackToDelete?.let { viewModel.deleteTrack(it) }
            trackToDelete = null
        },
        onDismissDelete = { trackToDelete = null },
        onRenameRequest = { track ->
            trackToRename = track
            newTrackName = track.name
        },
        onNewTrackNameChange = { newTrackName = it },
        onConfirmRename = {
            if (newTrackName.isNotBlank()) {
                trackToRename?.let { viewModel.renameTrack(it, newTrackName) }
            }
            trackToRename = null
        },
        onDismissRename = { trackToRename = null },
        onDismissImportError = { showImportError = false },
        onContinue = onContinueTrack,
        onShowOnMap = onShowTrackOnMap
    )
}

private fun shareTrack(context: android.content.Context, track: Track) {
    try {
        val gpxContent = track.toGPX()
        val fileName = "${track.name.replace(" ", "_").replace(":", "-")}.gpx"
        val file = File(context.cacheDir, fileName)
        file.writeText(gpxContent)

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/gpx+xml"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, track.name)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        context.startActivity(Intent.createChooser(shareIntent, context.getString(R.string.share_track_chooser)))
    } catch (e: Exception) {
        Logger.e(e, "Track operation error")
    }
}

private fun saveTrackToDownloads(context: android.content.Context, track: Track) {
    try {
        val gpxContent = track.toGPX()
        val fileName = "${track.name.replace(" ", "_").replace(":", "-")}.gpx"

        val downloadsDir = File(context.getExternalFilesDir(null), "Tracks")
        if (!downloadsDir.exists()) {
            downloadsDir.mkdirs()
        }

        val file = File(downloadsDir, fileName)
        file.writeText(gpxContent)

        android.widget.Toast.makeText(
            context,
            context.getString(R.string.saved_to_path, file.absolutePath),
            android.widget.Toast.LENGTH_LONG
        ).show()
    } catch (e: Exception) {
        Logger.e(e, "Track operation error")
        android.widget.Toast.makeText(
            context,
            context.getString(R.string.failed_to_save_track, e.message),
            android.widget.Toast.LENGTH_SHORT
        ).show()
    }
}

private fun openTrack(context: android.content.Context, track: Track) {
    try {
        val gpxContent = track.toGPX()
        val fileName = "${track.name.replace(" ", "_").replace(":", "-")}.gpx"
        val file = File(context.cacheDir, fileName)
        file.writeText(gpxContent)

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        val openIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/gpx+xml")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        try {
            context.startActivity(Intent.createChooser(openIntent, context.getString(R.string.open_track_chooser)))
        } catch (_: android.content.ActivityNotFoundException) {
            shareTrack(context, track)
        }
    } catch (e: Exception) {
        Logger.e(e, "Track operation error")
    }
}
