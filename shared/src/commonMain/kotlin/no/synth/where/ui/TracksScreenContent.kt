package no.synth.where.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import no.synth.where.data.Track
import no.synth.where.util.formatDateTime
import no.synth.where.util.formatKm
import no.synth.where.resources.Res
import no.synth.where.resources.*
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TracksScreenContent(
    tracks: List<Track>,
    trackToDelete: Track?,
    trackToRename: Track?,
    newTrackName: String,
    showImportError: Boolean,
    importErrorMessage: String,
    isImportingUrl: Boolean = false,
    onBackClick: () -> Unit,
    onImport: () -> Unit,
    onUrlImport: (String) -> Unit = {},
    onExport: (Track) -> Unit,
    onSave: ((Track) -> Unit)? = null,
    onOpen: ((Track) -> Unit)? = null,
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
                title = { Text(stringResource(Res.string.saved_tracks)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(painterResource(Res.drawable.ic_arrow_back), contentDescription = stringResource(Res.string.back))
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            item {
                ImportSection(
                    isImportingUrl = isImportingUrl,
                    onImportFile = onImport,
                    onUrlImport = onUrlImport
                )
            }
            if (tracks.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillParentMaxHeight(0.5f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(Res.string.no_saved_tracks),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                items(tracks, key = { it.id }) { track ->
                    TrackItem(
                        track = track,
                        onExport = { onExport(track) },
                        onSave = onSave?.let { { it(track) } },
                        onOpen = onOpen?.let { { it(track) } },
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

    trackToDelete?.let { track ->
        AlertDialog(
            onDismissRequest = onDismissDelete,
            title = { Text(stringResource(Res.string.delete_track_title)) },
            text = { Text(stringResource(Res.string.delete_track_confirm, track.name)) },
            confirmButton = {
                TextButton(onClick = onConfirmDelete) {
                    Text(stringResource(Res.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissDelete) {
                    Text(stringResource(Res.string.cancel))
                }
            }
        )
    }

    if (showImportError) {
        AlertDialog(
            onDismissRequest = onDismissImportError,
            title = { Text(stringResource(Res.string.import_failed)) },
            text = { Text(importErrorMessage) },
            confirmButton = {
                TextButton(onClick = onDismissImportError) {
                    Text(stringResource(Res.string.ok))
                }
            }
        )
    }

    trackToRename?.let {
        AlertDialog(
            onDismissRequest = onDismissRename,
            title = { Text(stringResource(Res.string.rename_track)) },
            text = {
                OutlinedTextField(
                    value = newTrackName,
                    onValueChange = onNewTrackNameChange,
                    label = { Text(stringResource(Res.string.track_name)) },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = onConfirmRename) {
                    Text(stringResource(Res.string.rename))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissRename) {
                    Text(stringResource(Res.string.cancel))
                }
            }
        )
    }
}

@Composable
fun TrackItem(
    track: Track,
    onExport: () -> Unit,
    onSave: (() -> Unit)? = null,
    onOpen: (() -> Unit)? = null,
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
                painter = if (expanded) painterResource(Res.drawable.ic_expand_less) else painterResource(Res.drawable.ic_expand_more),
                contentDescription = if (expanded) stringResource(Res.string.collapse) else stringResource(Res.string.expand)
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
                        painterResource(Res.drawable.ic_map),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(Res.string.show_on_map))
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
                        painterResource(Res.drawable.ic_play_arrow),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(Res.string.continue_label))
                }
                if (onSave != null) {
                    OutlinedButton(
                        onClick = onSave,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            painterResource(Res.drawable.ic_save),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(Res.string.save))
                    }
                } else {
                    OutlinedButton(
                        onClick = onExport,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            painterResource(Res.drawable.ic_share),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(Res.string.share))
                    }
                }
            }
            if (onOpen != null || onSave != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (onOpen != null) {
                        OutlinedButton(
                            onClick = onOpen,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                painterResource(Res.drawable.ic_open_in_new),
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(stringResource(Res.string.open))
                        }
                    }
                    OutlinedButton(
                        onClick = onExport,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            painterResource(Res.drawable.ic_share),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(Res.string.share))
                    }
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
                        painterResource(Res.drawable.ic_edit),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(Res.string.rename))
                }
                OutlinedButton(
                    onClick = onDelete,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(
                        painterResource(Res.drawable.ic_delete),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(Res.string.delete))
                }
            }
        }
    }
}

@Composable
fun formatTrackInfo(track: Track): String {
    val date = formatDateTime(track.startTime, "MMM d, yyyy HH:mm")
    val distance = track.getDistanceMeters().formatKm()
    val duration = track.getDurationMillis()
    val hours = duration / (1000 * 60 * 60)
    val minutes = (duration / (1000 * 60)) % 60

    val durationStr = when {
        hours > 0 -> stringResource(Res.string.duration_hours_minutes, hours, minutes)
        else -> stringResource(Res.string.duration_minutes, minutes)
    }

    return stringResource(Res.string.track_info_format, date, distance, durationStr, track.points.size)
}

@Composable
private fun ImportSection(
    isImportingUrl: Boolean,
    onImportFile: () -> Unit,
    onUrlImport: (String) -> Unit
) {
    var importUrl by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onImportFile,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        painterResource(Res.drawable.ic_file_upload),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(Res.string.import_gpx))
                }
                OutlinedButton(
                    onClick = { expanded = !expanded },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        painterResource(Res.drawable.ic_open_in_browser),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(Res.string.import_url))
                }
            }

            if (expanded) {
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = importUrl,
                    onValueChange = { importUrl = it },
                    label = { Text(stringResource(Res.string.import_url_hint)) },
                    singleLine = true,
                    enabled = !isImportingUrl,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        if (importUrl.isNotBlank()) {
                            onUrlImport(importUrl)
                            importUrl = ""
                            expanded = false
                        }
                    },
                    enabled = importUrl.isNotBlank() && !isImportingUrl,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isImportingUrl) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(Res.string.importing))
                    } else {
                        Text(stringResource(Res.string.import_label))
                    }
                }
            }
        }
    }
}
