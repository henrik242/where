package no.synth.where.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import no.synth.where.data.SavedPoint
import no.synth.where.data.SavedPointsRepository
import org.maplibre.android.geometry.LatLng
import androidx.core.graphics.toColorInt

@Composable
fun SavedPointsScreen(
    onBackClick: () -> Unit,
    onShowOnMap: (SavedPoint) -> Unit = {}
) {
    val context = LocalContext.current
    val repository = remember { SavedPointsRepository.getInstance(context) }
    val savedPoints = repository.savedPoints

    var showEditDialog by remember { mutableStateOf(false) }
    var editingPoint by remember { mutableStateOf<SavedPoint?>(null) }

    SavedPointsScreenContent(
        savedPoints = savedPoints,
        showEditDialog = showEditDialog,
        editingPoint = editingPoint,
        onBackClick = onBackClick,
        onEdit = { point ->
            editingPoint = point
            showEditDialog = true
        },
        onDelete = { point -> repository.deletePoint(point.id) },
        onShowOnMap = onShowOnMap,
        onDismissEdit = {
            showEditDialog = false
            editingPoint = null
        },
        onSaveEdit = { name, description, color ->
            repository.updatePoint(editingPoint!!.id, name, description, color)
            showEditDialog = false
            editingPoint = null
        }
    )
}

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
                title = { Text("Saved Points") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
                    text = "No saved points yet.\nLong press on the map to save a point.",
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
                                if (!point.color.isNullOrBlank()) {
                                    Color(point.color.toColorInt())
                                } else {
                                    Color(0xFFFF5722) // Default red color
                                }
                            } catch (_: Exception) {
                                Color(0xFFFF5722) // Fallback to default red color
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
                    if (!point.description.isNullOrBlank()) {
                        Text(
                            text = point.description,
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
                    onClick = onEdit,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        Icons.Filled.Edit,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Edit")
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
        title = { Text("Edit Point") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description (optional)") },
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Color", style = MaterialTheme.typography.labelMedium)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    colors.forEach { (colorHex, colorName) ->
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(
                                    color = Color(colorHex.toColorInt()),
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
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Preview(showSystemUi = true)
@Composable
private fun SavedPointsScreenPreview() {
    val samplePoints = listOf(
        SavedPoint("1", "Cabin", LatLng(59.9139, 10.7522), "Summer cabin", color = "#4CAF50"),
        SavedPoint("2", "Fishing spot", LatLng(60.3913, 5.3221), null, color = "#2196F3"),
        SavedPoint("3", "Viewpoint", LatLng(61.2275, 7.0940), "Great view", color = "#FF5722")
    )
    SavedPointsScreenContent(
        savedPoints = samplePoints,
        showEditDialog = false,
        editingPoint = null,
        onBackClick = {},
        onEdit = {},
        onDelete = {},
        onShowOnMap = {},
        onDismissEdit = {},
        onSaveEdit = { _, _, _ -> }
    )
}

