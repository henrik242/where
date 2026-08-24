package no.synth.where.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import no.synth.where.data.SavedPoint
import no.synth.where.ui.map.PointColorPicker
import no.synth.where.ui.map.PointColors
import no.synth.where.util.parseHexColor
import no.synth.where.resources.Res
import no.synth.where.resources.*
import org.jetbrains.compose.resources.getPluralString
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavedPointsScreenContent(
    savedPoints: List<SavedPoint>,
    showEditDialog: Boolean,
    editingPoint: SavedPoint?,
    onBackClick: () -> Unit,
    onEdit: (SavedPoint) -> Unit,
    onDelete: (SavedPoint) -> Unit,
    onShowOnMap: (SavedPoint) -> Unit,
    onDismissEdit: () -> Unit,
    onSaveEdit: (String, String, String) -> Unit,
    pointImport: PointImportState,
    onPickFiles: () -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(pointImport.importedCount) {
        val count = pointImport.importedCount ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(getPluralString(Res.plurals.points_imported, count, count))
        pointImport.onImportedCountShown()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.saved_points)) },
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
            item { ImportPointsSection(onImport = onPickFiles) }
            if (pointImport.isImporting) {
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
                            text = stringResource(Res.string.importing),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    HorizontalDivider()
                }
            }
            if (savedPoints.isEmpty() && !pointImport.isImporting) {
                item {
                    Box(
                        modifier = Modifier
                            .fillParentMaxHeight(0.5f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(Res.string.no_saved_points),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            items(savedPoints, key = { it.id }) { point ->
                SavedPointItem(
                    point = point,
                    onEdit = { onEdit(point) },
                    onDelete = { onDelete(point) },
                    onShowOnMap = { onShowOnMap(point) }
                )
                HorizontalDivider()
            }
        }
    }

    pointImport.candidates?.let { candidates ->
        PointImportPickerDialog(
            waypoints = candidates.waypoints,
            failedCount = candidates.failedCount,
            importing = pointImport.isImporting,
            onImport = { pointImport.importSelected(it) },
            onDismiss = { pointImport.dismissCandidates() }
        )
    }

    pointImport.error?.let { message ->
        AlertDialog(
            onDismissRequest = { pointImport.dismissError() },
            title = { Text(stringResource(Res.string.import_failed)) },
            text = { Text(stringResource(message)) },
            confirmButton = {
                TextButton(onClick = { pointImport.dismissError() }) { Text(stringResource(Res.string.ok)) }
            }
        )
    }

    if (showEditDialog && editingPoint != null) {
        EditPointDialog(
            point = editingPoint,
            onDismiss = onDismissEdit,
            onSave = onSaveEdit
        )
    }
}

@Composable
private fun ImportPointsSection(onImport: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            OutlinedButton(
                onClick = onImport,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    painterResource(Res.drawable.ic_file_upload),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(stringResource(Res.string.import_points))
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(Res.string.import_points_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun SavedPointItem(
    point: SavedPoint,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onShowOnMap: () -> Unit = {}
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
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(
                            color = try {
                                val c = point.color
                                if (!c.isNullOrBlank()) {
                                    parseHexColor(c)
                                } else {
                                    parseHexColor(PointColors.DEFAULT)
                                }
                            } catch (_: Exception) {
                                Color(0xFFFF5722)
                            },
                            shape = CircleShape
                        )
                )

                Spacer(modifier = Modifier.width(16.dp))

                Column {
                    Text(
                        text = point.name,
                        style = MaterialTheme.typography.titleMedium
                    )
                    val desc = point.description
                    if (!desc.isNullOrBlank()) {
                        Text(
                            text = desc,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = "${point.latLng.latitude.toString().take(8)}, ${point.latLng.longitude.toString().take(8)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
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
                    onClick = onEdit,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        painterResource(Res.drawable.ic_edit),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(Res.string.edit))
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditPointDialog(
    point: SavedPoint,
    onDismiss: () -> Unit,
    onSave: (String, String, String) -> Unit
) {
    var name by remember { mutableStateOf(point.name) }
    var description by remember { mutableStateOf(point.description ?: "") }
    var selectedColor by remember { mutableStateOf(point.color?.ifBlank { PointColors.DEFAULT } ?: PointColors.DEFAULT) }

    val colors = PointColors.withSelected(point.color)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.edit_point)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(Res.string.name_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(stringResource(Res.string.description_optional)) },
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth()
                )

                Text(stringResource(Res.string.color), style = MaterialTheme.typography.labelMedium)
                PointColorPicker(
                    colors = colors,
                    selectedColor = selectedColor,
                    onColorChange = { selectedColor = it }
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(name, description, selectedColor) },
                enabled = name.isNotBlank()
            ) {
                Text(stringResource(Res.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.cancel))
            }
        }
    )
}
