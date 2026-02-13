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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import no.synth.where.data.RulerState
import no.synth.where.resources.Res
import no.synth.where.resources.*
import no.synth.where.util.formatDistance
import no.synth.where.util.parseHexColor
import org.jetbrains.compose.resources.stringResource

object MapDialogs {

    @Composable
    fun BackgroundLocationDisclosureDialog(
        onAllow: () -> Unit,
        onDeny: () -> Unit
    ) {
        AlertDialog(
            onDismissRequest = onDeny,
            title = { Text(stringResource(Res.string.background_location_access)) },
            text = {
                Column {
                    Text(
                        text = stringResource(Res.string.background_location_intro),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(Res.string.background_location_features),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = stringResource(Res.string.background_location_privacy),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = stringResource(Res.string.background_location_instruction),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = onAllow) {
                    Text(stringResource(Res.string.allow))
                }
            },
            dismissButton = {
                TextButton(onClick = onDeny) {
                    Text(stringResource(Res.string.deny))
                }
            }
        )
    }

    @Composable
    fun StopTrackDialog(
        trackNameInput: String,
        onTrackNameChange: (String) -> Unit,
        onDiscard: () -> Unit,
        onSave: () -> Unit,
        onDismiss: () -> Unit,
        isLoading: Boolean = false
    ) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(Res.string.save_track)) },
            text = {
                Column {
                    Text(
                        text = stringResource(Res.string.enter_track_name),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = trackNameInput,
                        onValueChange = onTrackNameChange,
                        label = { Text(stringResource(Res.string.track_name)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        trailingIcon = if (isLoading) {
                            { CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp) }
                        } else null
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
                        Text(stringResource(Res.string.discard))
                    }
                    TextButton(onClick = onSave) {
                        Text(stringResource(Res.string.save))
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(Res.string.cancel))
                }
            }
        )
    }

    @Composable
    fun SavePointDialog(
        pointName: String,
        onPointNameChange: (String) -> Unit,
        coordinates: String,
        onSave: () -> Unit,
        onDismiss: () -> Unit,
        isLoading: Boolean = false
    ) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(Res.string.save_location)) },
            text = {
                Column {
                    Text(
                        text = stringResource(Res.string.enter_location_name),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = pointName,
                        onValueChange = onPointNameChange,
                        label = { Text(stringResource(Res.string.location_name)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        trailingIcon = if (isLoading) {
                            { CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp) }
                        } else null
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
            title = { Text(stringResource(Res.string.edit_location)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = pointName,
                        onValueChange = onNameChange,
                        label = { Text(stringResource(Res.string.name_label)) },
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = pointDescription,
                        onValueChange = onDescriptionChange,
                        label = { Text(stringResource(Res.string.description_optional)) },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text(stringResource(Res.string.color), style = MaterialTheme.typography.labelMedium)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        availableColors.forEach { (colorHex, _) ->
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(
                                        color = parseHexColor(colorHex),
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
                        Text(stringResource(Res.string.delete))
                    }
                    TextButton(onClick = onSave) {
                        Text(stringResource(Res.string.save))
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(Res.string.cancel))
                }
            }
        )
    }

    @Composable
    fun SaveRulerAsTrackDialog(
        trackName: String,
        rulerState: RulerState,
        onTrackNameChange: (String) -> Unit,
        onSave: () -> Unit,
        onDismiss: () -> Unit,
        isLoading: Boolean = false
    ) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(Res.string.save_route_as_track)) },
            text = {
                Column {
                    Text(
                        text = stringResource(Res.string.enter_route_name),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = trackName,
                        onValueChange = onTrackNameChange,
                        label = { Text(stringResource(Res.string.track_name)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        trailingIcon = if (isLoading) {
                            { CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp) }
                        } else null
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(Res.string.ruler_points_distance, rulerState.points.size, rulerState.getTotalDistanceMeters().formatDistance()),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = onSave) {
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
}
