package no.synth.where.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import no.synth.where.data.StravaRoute
import no.synth.where.resources.Res
import no.synth.where.resources.*
import no.synth.where.util.formatKm
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

/**
 * Everything the Tracks screen needs to offer Strava route import, bundled so
 * [TracksScreenContent]'s signature stays small.
 */
class StravaImportHandlers(
    /** True once the user has saved their own Strava API client id + secret. */
    val credentialsSet: Boolean,
    /** Current stored client id, used to prefill the setup dialog (secret is never shown). */
    val clientId: String?,
    val connected: Boolean,
    /** True while the OAuth code->token exchange is in flight (show a "Connecting…" state). */
    val connecting: Boolean,
    val loadingRoutes: Boolean,
    val importing: Boolean,
    /** Non-null once routes are loaded: the picker dialog is shown while this is set. */
    val routes: List<StravaRoute>?,
    val onSaveCredentials: (clientId: String, clientSecret: String) -> Unit,
    val onRemoveCredentials: () -> Unit,
    val onOpenApiSettings: () -> Unit,
    val onConnect: () -> Unit,
    val onLoadRoutes: () -> Unit,
    val onDismissRoutes: () -> Unit,
    val onImport: (List<StravaRoute>) -> Unit,
    val onDisconnect: () -> Unit,
)

@Composable
fun StravaRoutePickerDialog(
    routes: List<StravaRoute>,
    importing: Boolean,
    onImport: (List<StravaRoute>) -> Unit,
    onDismiss: () -> Unit,
) {
    var starredOnly by remember { mutableStateOf(false) }
    val selectedIds = remember { mutableStateListOf<Long>() }
    val visible = if (starredOnly) routes.filter { it.starred } else routes
    // Only import what's currently visible, so hidden (filtered-out) selections aren't imported.
    val visibleSelected = visible.filter { selectedIds.contains(it.id) }

    AlertDialog(
        onDismissRequest = { if (!importing) onDismiss() },
        title = { Text(stringResource(Res.string.strava_routes_title)) },
        text = {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .toggleable(
                            value = starredOnly,
                            enabled = !importing,
                            role = Role.Checkbox,
                            onValueChange = { starredOnly = it }
                        )
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(checked = starredOnly, onCheckedChange = null, enabled = !importing)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(Res.string.strava_starred_only))
                }

                if (visible.isEmpty()) {
                    Text(
                        // Distinguish "no routes at all" from "the starred filter hid them all".
                        stringResource(
                            if (starredOnly && routes.isNotEmpty()) Res.string.strava_no_starred
                            else Res.string.strava_no_routes
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                        items(visible, key = { it.id }) { route ->
                            val checked = selectedIds.contains(route.id)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .toggleable(
                                        value = checked,
                                        enabled = !importing,
                                        role = Role.Checkbox,
                                        onValueChange = { on ->
                                            if (on) selectedIds.add(route.id) else selectedIds.remove(route.id)
                                        }
                                    )
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(checked = checked, onCheckedChange = null, enabled = !importing)
                                Spacer(Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(route.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        text = routeSubtitle(route),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (route.starred) {
                                    Text(
                                        stringResource(Res.string.strava_starred),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onImport(visibleSelected) },
                enabled = visibleSelected.isNotEmpty() && !importing
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

private fun routeSubtitle(route: StravaRoute): String {
    val km = route.distanceMeters.formatKm()
    val elev = route.elevationGainMeters.roundToInt()
    return "$km km · $elev m"
}
