package no.synth.where.ui

import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import no.synth.where.data.Track
import no.synth.where.resources.Res
import no.synth.where.resources.*
import no.synth.where.util.Logger
import org.jetbrains.compose.resources.stringResource
import java.io.File

@Composable
fun TracksScreen(
    pendingImportUrl: String? = null,
    pendingImportFileUri: String? = null,
    onBackClick: () -> Unit,
    onShowTrackOnMap: (Track) -> Unit,
    onShowTracksOnMap: (List<Track>) -> Unit = {},
    onNavigateTrack: (Track) -> Unit,
    onCropTrack: (Track) -> Unit = {}
) {
    val context = LocalContext.current
    val app = context.applicationContext as no.synth.where.WhereApplication
    val viewModel: TracksScreenViewModel = viewModel { TracksScreenViewModel(app.trackRepository) }
    val tracks by viewModel.tracks.collectAsState()
    val isRecording by viewModel.isRecording.collectAsState()
    val isImportingUrl by viewModel.isImportingUrl.collectAsState()
    val isImporting by viewModel.isImporting.collectAsState()
    val newlyImportedTrackId by viewModel.newlyImportedTrackId.collectAsState()
    val saveResultMessage by viewModel.saveResultMessage.collectAsState()
    val onMapTrackIds by viewModel.onMapTrackIds.collectAsState()

    var trackToDelete by remember { mutableStateOf<Track?>(null) }
    var trackToRename by remember { mutableStateOf<Track?>(null) }
    var newTrackName by remember { mutableStateOf("") }
    var showImportError by remember { mutableStateOf(false) }
    var importErrorMessage by remember { mutableStateOf("") }

    var urlToConfirm by remember { mutableStateOf<String?>(null) }
    var fileUriToConfirm by remember { mutableStateOf<String?>(null) }

    // Pre-resolve strings for use in non-composable lambdas
    val importUrlErrorStr = stringResource(Res.string.import_url_error)
    val importGpxCorruptedStr = stringResource(Res.string.import_gpx_corrupted)
    val shareTrackChooserStr = stringResource(Res.string.share_track_chooser)
    val openTrackChooserStr = stringResource(Res.string.open_track_chooser)
    val savedToPathFmt = stringResource(Res.string.saved_to_path)
    val failedToSaveTrackFmt = stringResource(Res.string.failed_to_save_track)

    LaunchedEffect(pendingImportUrl) {
        if (pendingImportUrl != null) urlToConfirm = pendingImportUrl
    }

    LaunchedEffect(pendingImportFileUri) {
        if (pendingImportFileUri != null) fileUriToConfirm = pendingImportFileUri
    }

    urlToConfirm?.let { url ->
        AlertDialog(
            onDismissRequest = { urlToConfirm = null },
            title = { Text(stringResource(Res.string.import_from_title)) },
            text = { Text(stringResource(Res.string.import_from_message, friendlySourceName(url))) },
            confirmButton = {
                TextButton(onClick = {
                    urlToConfirm = null
                    viewModel.importFromUrl(url) { track ->
                        if (track == null) {
                            importErrorMessage = importUrlErrorStr
                            showImportError = true
                        }
                    }
                }) { Text(stringResource(Res.string.import_label)) }
            },
            dismissButton = {
                TextButton(onClick = { urlToConfirm = null }) {
                    Text(stringResource(Res.string.cancel))
                }
            }
        )
    }

    fileUriToConfirm?.let { uriString ->
        // Resolve the display name off the main thread (a cross-process ContentResolver query),
        // showing the cheap last-path-segment fallback until it returns, so it never runs in
        // composition on every recomposition.
        val fileName by produceState(uriString.toUri().lastPathSegment ?: uriString, uriString) {
            value = withContext(Dispatchers.IO) { displayNameForUri(context, uriString.toUri()) }
        }
        AlertDialog(
            onDismissRequest = { fileUriToConfirm = null },
            title = { Text(stringResource(Res.string.import_from_title)) },
            text = { Text(stringResource(Res.string.import_file_message, fileName)) },
            confirmButton = {
                TextButton(onClick = {
                    val uri = uriString.toUri()
                    fileUriToConfirm = null
                    viewModel.importTrackFromBytes(
                        readBytes = { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } }
                    ) { importedTrack ->
                        if (importedTrack == null) {
                            importErrorMessage = importGpxCorruptedStr
                            showImportError = true
                        }
                    }
                }) { Text(stringResource(Res.string.import_label)) }
            },
            dismissButton = {
                TextButton(onClick = { fileUriToConfirm = null }) {
                    Text(stringResource(Res.string.cancel))
                }
            }
        )
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { picked ->
            viewModel.importTrackFromBytes(
                readBytes = { context.contentResolver.openInputStream(picked)?.use { it.readBytes() } }
            ) { importedTrack ->
                if (importedTrack == null) {
                    importErrorMessage = importGpxCorruptedStr
                    showImportError = true
                }
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
        isImportingUrl = isImportingUrl,
        isImporting = isImporting,
        newlyImportedTrackId = newlyImportedTrackId,
        onNewlyImportedTrackConsumed = { viewModel.clearNewlyImportedTrackId() },
        onBackClick = onBackClick,
        onImport = { filePickerLauncher.launch("*/*") },
        onUrlImport = { url ->
            viewModel.importFromUrl(url) { track ->
                if (track == null) {
                    importErrorMessage = importUrlErrorStr
                    showImportError = true
                }
            }
        },
        onExport = { track -> shareTrack(context, track, shareTrackChooserStr) },
        onSave = { track ->
            viewModel.saveTrack { saveTrackToDownloads(context, track, savedToPathFmt, failedToSaveTrackFmt) }
        },
        saveResultMessage = saveResultMessage,
        onSaveResultMessageShown = { viewModel.onSaveResultMessageShown() },
        onOpen = { track -> openTrack(context, track, openTrackChooserStr, shareTrackChooserStr) },
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
        onShowOnMap = onShowTrackOnMap,
        onShowSelectedOnMap = onShowTracksOnMap,
        onNavigate = onNavigateTrack,
        onCrop = onCropTrack,
        onMoveToFolder = { moved, folder -> viewModel.moveToFolder(moved, folder) },
        onRenameFolder = { oldName, newName -> viewModel.renameFolder(oldName, newName) },
        onRemoveFolder = { viewModel.removeFolder(it) },
        onRestoreFolders = { viewModel.restoreFolders(it) },
        isRecording = isRecording,
        onMapTrackIds = onMapTrackIds
    )
}

private fun Track.gpxFileName() = "${name.replace(" ", "_").replace(":", "-")}.gpx"

private fun displayNameForUri(context: android.content.Context, uri: Uri): String {
    if (uri.scheme == "content") {
        runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (index >= 0) cursor.getString(index)?.let { return it }
                    }
                }
        }
    }
    return uri.lastPathSegment ?: uri.toString()
}

private fun friendlySourceName(url: String): String {
    val host = url.toUri().host ?: return url
    return when {
        "strava.com" in host || "strava.app.link" in host -> "Strava"
        "garmin.com" in host -> "Garmin Connect"
        "komoot.com" in host || "komoot.de" in host -> "Komoot"
        "ut.no" in host -> "UT.no"
        else -> host
    }
}

private fun shareTrack(context: android.content.Context, track: Track, chooserTitle: String) {
    try {
        val gpxContent = track.toGPX()
        val file = File(context.cacheDir, track.gpxFileName())
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

        context.startActivity(Intent.createChooser(shareIntent, chooserTitle))
    } catch (e: Exception) {
        Logger.e(e, "Track operation error")
    }
}

private fun saveTrackToDownloads(context: android.content.Context, track: Track, savedFmt: String, failedFmt: String): String {
    return try {
        val gpxContent = track.toGPX()
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, track.gpxFileName())
            put(MediaStore.Downloads.MIME_TYPE, "application/gpx+xml")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }

        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: throw java.io.IOException("Could not create Downloads entry")

        try {
            resolver.openOutputStream(uri)?.use { it.write(gpxContent.toByteArray()) }
                ?: throw java.io.IOException("Could not open output stream")
            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw e
        }

        String.format(savedFmt, android.os.Environment.DIRECTORY_DOWNLOADS)
    } catch (e: Exception) {
        Logger.e(e, "Track operation error")
        String.format(failedFmt, e.message ?: e.toString())
    }
}

private fun openTrack(context: android.content.Context, track: Track, chooserTitle: String, shareChooserTitle: String) {
    try {
        val gpxContent = track.toGPX()
        val file = File(context.cacheDir, track.gpxFileName())
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
            context.startActivity(Intent.createChooser(openIntent, chooserTitle))
        } catch (_: android.content.ActivityNotFoundException) {
            shareTrack(context, track, shareChooserTitle)
        }
    } catch (e: Exception) {
        Logger.e(e, "Track operation error")
    }
}
