package no.synth.where.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import no.synth.where.data.SavedPoint
import no.synth.where.util.parseHexColor
import no.synth.where.resources.Res
import no.synth.where.resources.*
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
    onSaveEdit: (String, String, String) -> Unit
) {
    Scaffold(
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
        if (savedPoints.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(Res.string.no_saved_points),
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
                                    Color(0xFFFF5722)
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
    var selectedColor by remember { mutableStateOf(point.color?.ifBlank { "#FF5722" } ?: "#FF5722") }

    val colors = listOf(
        "#FF5722" to "Red",
        "#2196F3" to "Blue",
        "#4CAF50" to "Green",
        "#FFC107" to "Yellow",
        "#9C27B0" to "Purple",
        "#FF9800" to "Orange",
        "#00BCD4" to "Cyan",
        "#E91E63" to "Pink"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.edit_point)) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(Res.string.name_label)) },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(stringResource(Res.string.description_optional)) },
                    modifier = Modifier.fillMaxWidth()
                )

                Text(stringResource(Res.string.color), style = MaterialTheme.typography.labelMedium)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    colors.forEach { (colorHex, _) ->
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(
                                    color = parseHexColor(colorHex),
                                    shape = CircleShape
                                )
                                .clickable { selectedColor = colorHex }
                                .then(
                                    if (selectedColor == colorHex) {
                                        Modifier.padding(4.dp)
                                    } else Modifier
                                )
                        )
                    }
                }
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
