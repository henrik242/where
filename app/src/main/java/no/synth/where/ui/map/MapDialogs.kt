package no.synth.where.ui.map

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.core.graphics.toColorInt
import no.synth.where.data.RulerState
import no.synth.where.util.formatDistance

/**
 * Dialogs for the map screen.
 */
object MapDialogs {

    /**
     * Dialog for stopping and saving a track.
     */
    @Composable
    fun StopTrackDialog(
        trackNameInput: String,
        onTrackNameChange: (String) -> Unit,
        onDiscard: () -> Unit,
        onSave: () -> Unit,
        onDismiss: () -> Unit
    ) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Save Track") },
            text = {
                Column {
                    Text(
                        text = "Enter a name for your track:",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = trackNameInput,
                        onValueChange = onTrackNameChange,
                        label = { Text("Track Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        onClick = onDiscard,
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("Discard")
                    }
                    TextButton(onClick = onSave) {
                        Text("Save")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        )
    }

    /**
     * Dialog for saving a new point.
     */
    @Composable
    fun SavePointDialog(
        pointName: String,
        onPointNameChange: (String) -> Unit,
        coordinates: String,
        onSave: () -> Unit,
        onDismiss: () -> Unit
    ) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Save Location") },
            text = {
                Column {
                    Text(
                        text = "Enter a name for this location:",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = pointName,
                        onValueChange = onPointNameChange,
                        label = { Text("Location Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = coordinates,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = onSave) {
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

    /**
     * Dialog for viewing and editing a saved point.
     */
    @Composable
    fun PointInfoDialog(
        pointName: String,
        pointDescription: String,
        pointColor: String,
        coordinates: String,
        availableColors: List<Pair<String, String>>,
        onNameChange: (String) -> Unit,
        onDescriptionChange: (String) -> Unit,
        onColorChange: (String) -> Unit,
        onDelete: () -> Unit,
        onSave: () -> Unit,
        onDismiss: () -> Unit
    ) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Edit Location") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = pointName,
                        onValueChange = onNameChange,
                        label = { Text("Name") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = pointDescription,
                        onValueChange = onDescriptionChange,
                        label = { Text("Description (optional)") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text("Color", style = MaterialTheme.typography.labelMedium)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        availableColors.forEach { (colorHex, _) ->
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(
                                        color = Color(colorHex.toColorInt()),
                                        shape = CircleShape
                                    )
                                    .clickable { onColorChange(colorHex) }
                                    .then(
                                        if (pointColor == colorHex) {
                                            Modifier.padding(4.dp)
                                        } else Modifier
                                    )
                            )
                        }
                    }

                    Text(
                        text = coordinates,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        onClick = onDelete,
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("Delete")
                    }
                    TextButton(onClick = onSave) {
                        Text("Save")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        )
    }

    /**
     * Dialog for saving ruler measurement as a track.
     */
    @Composable
    fun SaveRulerAsTrackDialog(
        trackName: String,
        rulerState: RulerState,
        onTrackNameChange: (String) -> Unit,
        onSave: () -> Unit,
        onDismiss: () -> Unit
    ) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Save Route as Track") },
            text = {
                Column {
                    Text(
                        text = "Enter a name for this route:",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = trackName,
                        onValueChange = onTrackNameChange,
                        label = { Text("Track Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "${rulerState.points.size} points • ${
                            rulerState.getTotalDistanceMeters().formatDistance()
                        }",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = onSave) {
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
}

