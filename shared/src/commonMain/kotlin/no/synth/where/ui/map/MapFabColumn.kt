package no.synth.where.ui.map

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import no.synth.where.resources.Res
import no.synth.where.resources.*
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun LayerMenuItem(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    DropdownMenuItem(
        text = { Text((if (isSelected) "\u2713 " else "") + text) },
        onClick = onClick
    )
}

@Composable
internal fun MenuSection(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelSmall,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
fun MapFabColumn(
    isRecording: Boolean,
    rulerActive: Boolean,
    showLayerMenu: Boolean,
    selectedLayer: MapLayer,
    showWaymarkedTrails: Boolean,
    showCountyBorders: Boolean,
    showSavedPoints: Boolean,
    onSearchClick: () -> Unit,
    onLayerMenuToggle: (Boolean) -> Unit,
    onLayerSelected: (MapLayer) -> Unit,
    onWaymarkedTrailsToggle: () -> Unit,
    onCountyBordersToggle: () -> Unit,
    onSavedPointsToggle: () -> Unit,
    onRecordStopClick: () -> Unit,
    onMyLocationClick: () -> Unit,
    onRulerToggle: () -> Unit,
    onSettingsClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.End) {
        SmallFloatingActionButton(
            onClick = onSearchClick,
            modifier = Modifier.size(48.dp),
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Icon(painterResource(Res.drawable.ic_search), contentDescription = stringResource(Res.string.search_places))
        }

        Spacer(modifier = Modifier.size(8.dp))

        SmallFloatingActionButton(
            onClick = { onLayerMenuToggle(true) },
            modifier = Modifier.size(48.dp),
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Icon(painterResource(Res.drawable.ic_layers), contentDescription = stringResource(Res.string.layers_and_overlays))
        }

        DropdownMenu(
            expanded = showLayerMenu,
            onDismissRequest = { onLayerMenuToggle(false) }
        ) {
            MenuSection(stringResource(Res.string.map_layers))
            LayerMenuItem(stringResource(Res.string.kartverket_norway), selectedLayer == MapLayer.KARTVERKET) { onLayerSelected(MapLayer.KARTVERKET) }
            LayerMenuItem(stringResource(Res.string.kartverket_toporaster), selectedLayer == MapLayer.TOPORASTER) { onLayerSelected(MapLayer.TOPORASTER) }
            LayerMenuItem(stringResource(Res.string.kartverket_sjokart), selectedLayer == MapLayer.SJOKARTRASTER) { onLayerSelected(MapLayer.SJOKARTRASTER) }
            LayerMenuItem("OpenStreetMap", selectedLayer == MapLayer.OSM) { onLayerSelected(MapLayer.OSM) }
            LayerMenuItem("OpenTopoMap", selectedLayer == MapLayer.OPENTOPOMAP) { onLayerSelected(MapLayer.OPENTOPOMAP) }
            HorizontalDivider()
            MenuSection(stringResource(Res.string.overlays))
            LayerMenuItem(stringResource(Res.string.waymarked_trails_osm), showWaymarkedTrails) { onWaymarkedTrailsToggle() }
            LayerMenuItem(stringResource(Res.string.county_borders_norway), showCountyBorders) { onCountyBordersToggle() }
            LayerMenuItem(stringResource(Res.string.saved_points), showSavedPoints) { onSavedPointsToggle() }
        }

        Spacer(modifier = Modifier.size(8.dp))

        SmallFloatingActionButton(
            onClick = onRecordStopClick,
            modifier = Modifier.size(48.dp),
            containerColor = if (isRecording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.surfaceVariant
        ) {
            Icon(
                if (isRecording) painterResource(Res.drawable.ic_stop) else painterResource(Res.drawable.ic_fiber_manual_record),
                contentDescription = if (isRecording) stringResource(Res.string.stop_recording) else stringResource(Res.string.start_recording),
                tint = if (isRecording) Color.White else Color.Red
            )
        }

        Spacer(modifier = Modifier.size(8.dp))

        SmallFloatingActionButton(
            onClick = onMyLocationClick,
            modifier = Modifier.size(48.dp),
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Icon(painterResource(Res.drawable.ic_my_location), contentDescription = stringResource(Res.string.my_location))
        }

        Spacer(modifier = Modifier.size(8.dp))

        SmallFloatingActionButton(
            onClick = onRulerToggle,
            modifier = Modifier.size(48.dp),
            containerColor = if (rulerActive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
        ) {
            Icon(
                painterResource(Res.drawable.ic_straighten),
                contentDescription = stringResource(Res.string.ruler),
                tint = if (rulerActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.size(8.dp))

        SmallFloatingActionButton(
            onClick = onSettingsClick,
            modifier = Modifier.size(48.dp),
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Icon(painterResource(Res.drawable.ic_settings), contentDescription = stringResource(Res.string.settings))
        }
    }
}
