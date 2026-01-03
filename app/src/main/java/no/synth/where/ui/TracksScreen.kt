package no.synth.where.ui

import android.content.Intent
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import no.synth.where.data.Track
import no.synth.where.data.TrackRepository
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TracksScreen(
    onBackClick: () -> Unit,
    onContinueTrack: (Track) -> Unit,
    onShowTrackOnMap: (Track) -> Unit
) {
    val context = LocalContext.current
    val trackRepository = remember { TrackRepository.getInstance(context) }
    val tracks = trackRepository.tracks

    var trackToDelete by remember { mutableStateOf<Track?>(null) }
    var trackToRename by remember { mutableStateOf<Track?>(null) }
    var newTrackName by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Saved Tracks") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
                    text = "No saved tracks yet",
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
                        onExport = { shareTrack(context, track) },
                        onSave = { saveTrackToDownloads(context, track) },
                        onOpen = { openTrack(context, track) },
                        onDelete = { trackToDelete = track },
                        onRename = {
                            trackToRename = track
                            newTrackName = track.name
                        },
                        onContinue = { onContinueTrack(track) },
                        onShowOnMap = { onShowTrackOnMap(track) }
                    )
                    HorizontalDivider()
                }
            }
        }
    }

    // Delete confirmation dialog
    trackToDelete?.let { track ->
        AlertDialog(
            onDismissRequest = { trackToDelete = null },
            title = { Text("Delete Track?") },
            text = { Text("Are you sure you want to delete '${track.name}'?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        trackRepository.deleteTrack(track)
                        trackToDelete = null
                    }
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { trackToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Rename dialog
    trackToRename?.let { track ->
        AlertDialog(
            onDismissRequest = { trackToRename = null },
            title = { Text("Rename Track") },
            text = {
                OutlinedTextField(
                    value = newTrackName,
                    onValueChange = { newTrackName = it },
                    label = { Text("Track Name") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newTrackName.isNotBlank()) {
                            trackRepository.renameTrack(track, newTrackName)
                        }
                        trackToRename = null
                    }
                ) {
                    Text("Rename")
                }
            },
            dismissButton = {
                TextButton(onClick = { trackToRename = null }) {
                    Text("Cancel")
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
                contentDescription = if (expanded) "Collapse" else "Expand"
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
                    Text("Show on Map")
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
                    Text("Continue")
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
                    Text("Save")
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
                    Text("Open")
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
                    Text("Share")
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
                    Text("Rename")
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
                    Text("Delete")
                }
            }
        }
    }
}

private fun formatTrackInfo(track: Track): String {
    val dateFormat = SimpleDateFormat("MMM d, yyyy HH:mm", Locale.getDefault())
    val date = dateFormat.format(Date(track.startTime))
    val distance = track.getDistanceMeters() / 1000.0
    val duration = track.getDurationMillis()
    val hours = duration / (1000 * 60 * 60)
    val minutes = (duration / (1000 * 60)) % 60

    val durationStr = when {
        hours > 0 -> "${hours}h ${minutes}m"
        else -> "${minutes}m"
    }

    return "$date • %.2f km • $durationStr • ${track.points.size} points".format(distance)
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

        context.startActivity(Intent.createChooser(shareIntent, "Share Track"))
    } catch (e: Exception) {
        e.printStackTrace()
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

        // Show a toast or snackbar
        android.widget.Toast.makeText(
            context,
            "Saved to ${file.absolutePath}",
            android.widget.Toast.LENGTH_LONG
        ).show()
    } catch (e: Exception) {
        e.printStackTrace()
        android.widget.Toast.makeText(
            context,
            "Failed to save track: ${e.message}",
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
            context.startActivity(Intent.createChooser(openIntent, "Open Track With"))
        } catch (e: android.content.ActivityNotFoundException) {
            // If no app can open GPX, fall back to share
            shareTrack(context, track)
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

