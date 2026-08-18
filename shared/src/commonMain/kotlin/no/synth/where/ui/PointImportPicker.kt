package no.synth.where.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.selection.triStateToggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import no.synth.where.data.ParsedWaypoint
import no.synth.where.data.geo.CoordinateFormatter
import no.synth.where.resources.Res
import no.synth.where.resources.*
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Lets the user pick which of the [waypoints] just read from the picked files to keep. Selection is
 * by list position, since nothing about a waypoint is guaranteed unique - two files can hold the
 * same name at the same spot. Everything starts selected; the header checkbox flips all/none.
 */
@Composable
fun PointImportPickerDialog(
    waypoints: List<ParsedWaypoint>,
    failedCount: Int,
    importing: Boolean,
    onImport: (List<ParsedWaypoint>) -> Unit,
    onDismiss: () -> Unit,
) {
    val selected = remember(waypoints) { mutableStateListOf<Int>().apply { addAll(waypoints.indices) } }
    val allState = when (selected.size) {
        0 -> ToggleableState.Off
        waypoints.size -> ToggleableState.On
        else -> ToggleableState.Indeterminate
    }

    AlertDialog(
        onDismissRequest = { if (!importing) onDismiss() },
        title = { Text(stringResource(Res.string.import_points)) },
        text = {
            Column {
                if (failedCount > 0) {
                    Text(
                        text = pluralStringResource(Res.plurals.import_files_without_points, failedCount, failedCount),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .triStateToggleable(
                            state = allState,
                            enabled = !importing,
                            role = Role.Checkbox,
                            onClick = {
                                val selectAll = allState != ToggleableState.On
                                selected.clear()
                                if (selectAll) selected.addAll(waypoints.indices)
                            }
                        )
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TriStateCheckbox(state = allState, onClick = null, enabled = !importing)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(
                            if (allState == ToggleableState.On) Res.string.select_none else Res.string.select_all
                        )
                    )
                }
                HorizontalDivider()

                LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                    itemsIndexed(waypoints) { index, waypoint ->
                        val checked = selected.contains(index)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .toggleable(
                                    value = checked,
                                    enabled = !importing,
                                    role = Role.Checkbox,
                                    onValueChange = { on ->
                                        if (on) selected.add(index) else selected.remove(index)
                                    }
                                )
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(checked = checked, onCheckedChange = null, enabled = !importing)
                            Spacer(Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(waypoint.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    text = waypoint.description.ifBlank {
                                        CoordinateFormatter.formatLatLng(waypoint.latLng)
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onImport(selected.sorted().map { waypoints[it] }) },
                enabled = selected.isNotEmpty() && !importing
            ) {
                if (importing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(Res.string.importing))
                } else {
                    Text(stringResource(Res.string.import_label))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !importing) {
                Text(stringResource(Res.string.cancel))
            }
        }
    )
}
