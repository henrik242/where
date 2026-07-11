package no.synth.where.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.gestures.animateScrollBy
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import no.synth.where.data.Track
import no.synth.where.util.formatDateTime
import no.synth.where.util.formatKm
import no.synth.where.resources.Res
import no.synth.where.resources.*
import org.jetbrains.compose.resources.getString
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
    isImportingUrl: Boolean,
    isImporting: Boolean = false,
    newlyImportedTrackId: String?,
    onNewlyImportedTrackConsumed: () -> Unit,
    onBackClick: () -> Unit,
    onImport: () -> Unit,
    onUrlImport: (String) -> Unit,
    onExport: (Track) -> Unit,
    onSave: ((Track) -> Unit)? = null,
    saveResultMessage: String? = null,
    onSaveResultMessageShown: () -> Unit = {},
    onOpen: ((Track) -> Unit)? = null,
    onDeleteRequest: (Track) -> Unit,
    onConfirmDelete: () -> Unit,
    onDismissDelete: () -> Unit,
    onRenameRequest: (Track) -> Unit,
    onNewTrackNameChange: (String) -> Unit,
    onConfirmRename: () -> Unit,
    onDismissRename: () -> Unit,
    onDismissImportError: () -> Unit,
    onShowOnMap: (Track) -> Unit,
    onShowSelectedOnMap: (List<Track>) -> Unit = {},
    onNavigate: (Track) -> Unit = {},
    onCrop: (Track) -> Unit = {},
    isRecording: Boolean = false,
    onMapTrackIds: Set<String> = emptySet(),
) {
    // Multi-select mode: long-press a track to enter it, then tap rows to build a set and show them
    // all on the map at once.
    var selectionMode by remember { mutableStateOf(false) }
    val selectedIds = remember { mutableStateListOf<String>() }

    fun exitSelection() {
        selectionMode = false
        selectedIds.clear()
    }

    val snackbarHostState = remember { SnackbarHostState() }

    // Show the message once, then let it display fully before consuming it (consuming changes the
    // effect key and would otherwise cancel showSnackbar mid-display).
    LaunchedEffect(saveResultMessage) {
        val message = saveResultMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        onSaveResultMessageShown()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (selectionMode) {
                TopAppBar(
                    title = { Text(stringResource(Res.string.n_selected, selectedIds.size)) },
                    navigationIcon = {
                        IconButton(onClick = { exitSelection() }) {
                            Icon(painterResource(Res.drawable.ic_close), contentDescription = stringResource(Res.string.cancel))
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = {
                                val selected = tracks.filter { it.id in selectedIds }
                                if (selected.isNotEmpty()) onShowSelectedOnMap(selected)
                                exitSelection()
                            },
                            enabled = selectedIds.isNotEmpty()
                        ) {
                            Icon(painterResource(Res.drawable.ic_map), contentDescription = stringResource(Res.string.show_on_map))
                        }
                    }
                )
            } else {
                TopAppBar(
                    title = { Text(stringResource(Res.string.saved_tracks)) },
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(painterResource(Res.drawable.ic_arrow_back), contentDescription = stringResource(Res.string.back))
                        }
                    }
                )
            }
        }
    ) { padding ->
        val listState = rememberLazyListState()
        val uiScope = rememberCoroutineScope()
        var expandedTrackId by remember { mutableStateOf<String?>(null) }
        var highlightedTrackId by remember { mutableStateOf<String?>(null) }

        // Re-runs whenever a new import id arrives or the track list updates. It waits (returns
        // early) until the imported track is actually loaded, then confirms it exactly once.
        // The scroll/highlight/snackbar run in uiScope so consuming the id (which cancels this
        // effect) doesn't abort them mid-animation.
        LaunchedEffect(newlyImportedTrackId, tracks) {
            val id = newlyImportedTrackId ?: return@LaunchedEffect
            val index = tracks.indexOfFirst { it.id == id }
            if (index < 0) return@LaunchedEffect
            val name = tracks[index].name
            onNewlyImportedTrackConsumed()
            expandedTrackId = id
            uiScope.launch {
                listState.animateScrollToItem(index + 1) // +1 for ImportSection
                highlightedTrackId = id
                delay(1500)
                if (highlightedTrackId == id) highlightedTrackId = null
            }
            uiScope.launch {
                snackbarHostState.showSnackbar(getString(Res.string.track_imported, name))
            }
        }

        LaunchedEffect(expandedTrackId) {
            val id = expandedTrackId ?: return@LaunchedEffect
            val index = tracks.indexOfFirst { it.id == id }
            if (index < 0) return@LaunchedEffect
            val itemIndex = index + 1 // +1 for ImportSection
            // Wait for the expanded content to be measured
            delay(50)
            val layoutInfo = listState.layoutInfo
            val itemInfo = layoutInfo.visibleItemsInfo.find { it.index == itemIndex } ?: return@LaunchedEffect
            val overshoot = (itemInfo.offset + itemInfo.size) - layoutInfo.viewportEndOffset
            if (overshoot > 0) {
                listState.animateScrollBy(overshoot.toFloat())
            }
        }

        LazyColumn(
            state = listState,
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
            if (isImporting) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(16.dp))
                        Text(
                            text = stringResource(Res.string.importing_track),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    HorizontalDivider()
                }
            }
            if (tracks.isEmpty() && !isImporting) {
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
                        expanded = expandedTrackId == track.id,
                        highlighted = highlightedTrackId == track.id,
                        isOnMap = track.id in onMapTrackIds,
                        selectionMode = selectionMode,
                        selected = track.id in selectedIds,
                        onLongPress = {
                            selectionMode = true
                            if (track.id !in selectedIds) selectedIds.add(track.id)
                        },
                        onSelectToggle = {
                            if (track.id in selectedIds) selectedIds.remove(track.id) else selectedIds.add(track.id)
                        },
                        onExpandToggle = {
                            expandedTrackId = if (expandedTrackId == track.id) null else track.id
                        },
                        onExport = { onExport(track) },
                        onSave = onSave?.let { { it(track) } },
                        onOpen = onOpen?.let { { it(track) } },
                        onDelete = { onDeleteRequest(track) },
                        onRename = { onRenameRequest(track) },
                        onShowOnMap = { onShowOnMap(track) },
                        onNavigate = { onNavigate(track) },
                        onCrop = { onCrop(track) },
                        canNavigate = !isRecording
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TrackItem(
    track: Track,
    expanded: Boolean = false,
    highlighted: Boolean = false,
    isOnMap: Boolean = false,
    selectionMode: Boolean = false,
    selected: Boolean = false,
    onLongPress: () -> Unit = {},
    onSelectToggle: () -> Unit = {},
    onExpandToggle: () -> Unit = {},
    onExport: () -> Unit,
    onSave: (() -> Unit)? = null,
    onOpen: (() -> Unit)? = null,
    onDelete: () -> Unit,
    onRename: () -> Unit,
    onShowOnMap: () -> Unit,
    onNavigate: () -> Unit = {},
    onCrop: () -> Unit = {},
    canNavigate: Boolean = true
) {
    val highlightColor by animateColorAsState(
        targetValue = if (highlighted) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else Color.Transparent,
        animationSpec = tween(durationMillis = 400),
        label = "highlight"
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(highlightColor)
            .combinedClickable(
                onClick = { if (selectionMode) onSelectToggle() else onExpandToggle() },
                onLongClick = onLongPress
            )
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (selectionMode) {
                Checkbox(
                    checked = selected,
                    onCheckedChange = { onSelectToggle() }
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = track.name,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (isOnMap) {
                        Spacer(modifier = Modifier.width(8.dp))
                        OnMapBadge()
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = formatTrackInfo(track),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (!selectionMode) {
                Icon(
                    painter = if (expanded) painterResource(Res.drawable.ic_expand_less) else painterResource(Res.drawable.ic_expand_more),
                    contentDescription = if (expanded) stringResource(Res.string.collapse) else stringResource(Res.string.expand)
                )
            }
        }

        if (expanded && !selectionMode) {
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
                OutlinedButton(
                    onClick = onNavigate,
                    modifier = Modifier.weight(1f),
                    enabled = track.points.size >= 2 && canNavigate
                ) {
                    Icon(
                        painterResource(Res.drawable.ic_my_location),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(Res.string.navigate))
                }
            }
            if (onSave != null || onOpen != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
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
                    }
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
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
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
                OutlinedButton(
                    onClick = onCrop,
                    modifier = Modifier.weight(1f),
                    enabled = track.points.size >= 2
                ) {
                    Icon(
                        painterResource(Res.drawable.ic_crop),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(Res.string.crop_track))
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

/** Small pill marking a track that is currently shown on the map. */
@Composable
private fun OnMapBadge() {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = RoundedCornerShape(50)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(Res.drawable.ic_map),
                contentDescription = null,
                modifier = Modifier.size(12.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = stringResource(Res.string.track_open_on_map),
                style = MaterialTheme.typography.labelSmall
            )
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
