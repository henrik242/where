package no.synth.where.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import no.synth.where.R
import no.synth.where.data.Track
import no.synth.where.data.TrackPoint
import org.maplibre.android.geometry.LatLng
import timber.log.Timber
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun TracksScreen(
    onBackClick: () -> Unit,
    onContinueTrack: (Track) -> Unit,
    onShowTrackOnMap: (Track) -> Unit
) {
    val context = LocalContext.current
    val viewModel: TracksScreenViewModel = hiltViewModel()
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TracksScreenContent(
    tracks: List<Track>,
    trackToDelete: Track?,
    trackToRename: Track?,
    newTrackName: String,
    showImportError: Boolean,
    importErrorMessage: String,
    onBackClick: () -> Unit,
    onImport: () -> Unit,
    onExport: (Track) -> Unit,
    onSave: (Track) -> Unit,
    onOpen: (Track) -> Unit,
    onDeleteRequest: (Track) -> Unit,
    onConfirmDelete: () -> Unit,
    onDismissDelete: () -> Unit,
    onRenameRequest: (Track) -> Unit,
    onNewTrackNameChange: (String) -> Unit,
    onConfirmRename: () -> Unit,
    onDismissRename: () -> Unit,
    onDismissImportError: () -> Unit,
    onContinue: (Track) -> Unit,
    onShowOnMap: (Track) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.saved_tracks)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(onClick = onImport) {
                        Icon(Icons.Filled.FileUpload, contentDescription = stringResource(R.string.import_gpx))
                    }
                }
            )
        }
    ) { padding ->
        if (tracks.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.no_saved_tracks),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                items(tracks, key = { it.id }) { track ->
                    TrackItem(
                        track = track,
                        onExport = { onExport(track) },
                        onSave = { onSave(track) },
                        onOpen = { onOpen(track) },
                        onDelete = { onDeleteRequest(track) },
                        onRename = { onRenameRequest(track) },
                        onContinue = { onContinue(track) },
                        onShowOnMap = { onShowOnMap(track) }
                    )
                    HorizontalDivider()
                }
            }
        }
    }

    // Delete confirmation dialog
    trackToDelete?.let { track ->
        AlertDialog(
            onDismissRequest = onDismissDelete,
            title = { Text(stringResource(R.string.delete_track_title)) },
            text = { Text(stringResource(R.string.delete_track_confirm, track.name)) },
            confirmButton = {
                TextButton(onClick = onConfirmDelete) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissDelete) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (showImportError) {
        AlertDialog(
            onDismissRequest = onDismissImportError,
            title = { Text(stringResource(R.string.import_failed)) },
            text = { Text(importErrorMessage) },
            confirmButton = {
                TextButton(onClick = onDismissImportError) {
                    Text(stringResource(R.string.ok))
                }
            }
        )
    }

    trackToRename?.let {
        AlertDialog(
            onDismissRequest = onDismissRename,
            title = { Text(stringResource(R.string.rename_track)) },
            text = {
                OutlinedTextField(
                    value = newTrackName,
                    onValueChange = onNewTrackNameChange,
                    label = { Text(stringResource(R.string.track_name)) },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = onConfirmRename) {
                    Text(stringResource(R.string.rename))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissRename) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
fun TrackItem(
    track: Track,
    onExport: () -> Unit,
    onSave: () -> Unit,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
    onRename: () -> Unit,
    onContinue: () -> Unit,
    onShowOnMap: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.name,
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = formatTrackInfo(track),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = if (expanded) stringResource(R.string.collapse) else stringResource(R.string.expand)
            )
        }

        if (expanded) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onShowOnMap,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        Icons.Filled.Map,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.show_on_map))
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onContinue,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        Icons.Filled.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.continue_label))
                }
                OutlinedButton(
                    onClick = onSave,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        Icons.Filled.Save,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.save))
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onOpen,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.open))
                }
                OutlinedButton(
                    onClick = onExport,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        Icons.Filled.Share,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.share))
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onRename,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        Icons.Filled.Edit,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.rename))
                }
                OutlinedButton(
                    onClick = onDelete,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.delete))
                }
            }
        }
    }
}

@Composable
private fun formatTrackInfo(track: Track): String {
    val dateFormat = SimpleDateFormat("MMM d, yyyy HH:mm", Locale.getDefault())
    val date = dateFormat.format(Date(track.startTime))
    val distance = "%.2f".format(track.getDistanceMeters() / 1000.0)
    val duration = track.getDurationMillis()
    val hours = duration / (1000 * 60 * 60)
    val minutes = (duration / (1000 * 60)) % 60

    val durationStr = when {
        hours > 0 -> stringResource(R.string.duration_hours_minutes, hours, minutes)
        else -> stringResource(R.string.duration_minutes, minutes)
    }

    return stringResource(R.string.track_info_format, date, distance, durationStr, track.points.size)
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
        Timber.e(e, "Track operation error")
    }
}

private fun saveTrackToDownloads(context: android.content.Context, track: Track) {
    try {
        val gpxContent = track.toGPX()
        val fileName = "${track.name.replace(" ", "_").replace(":", "-")}.gpx"

        // Save to app's external files directory (accessible via Files app)
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
        Timber.e(e, "Track operation error")
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
            // If no app can open GPX, fall back to share
            shareTrack(context, track)
        }
    } catch (e: Exception) {
        Timber.e(e, "Track operation error")
    }
}

@Preview(showSystemUi = true)
@Composable
private fun TracksScreenPreview() {
    val now = System.currentTimeMillis()
    val sampleTracks = listOf(
        Track(
            id = "1",
            name = "Morning Hike",
            points = listOf(
                TrackPoint(LatLng(59.9139, 10.7522), now - 3600000),
                TrackPoint(LatLng(59.9200, 10.7600), now - 1800000),
                TrackPoint(LatLng(59.9250, 10.7700), now)
            ),
            startTime = now - 3600000,
            endTime = now
        ),
        Track(
            id = "2",
            name = "Evening Run",
            points = listOf(
                TrackPoint(LatLng(60.3913, 5.3221), now - 7200000),
                TrackPoint(LatLng(60.3950, 5.3300), now - 5400000)
            ),
            startTime = now - 7200000,
            endTime = now - 5400000
        )
    )
    TracksScreenContent(
        tracks = sampleTracks,
        trackToDelete = null,
        trackToRename = null,
        newTrackName = "",
        showImportError = false,
        importErrorMessage = "",
        onBackClick = {},
        onImport = {},
        onExport = {},
        onSave = {},
        onOpen = {},
        onDeleteRequest = {},
        onConfirmDelete = {},
        onDismissDelete = {},
        onRenameRequest = {},
        onNewTrackNameChange = {},
        onConfirmRename = {},
        onDismissRename = {},
        onDismissImportError = {},
        onContinue = {},
        onShowOnMap = {}
    )
}
