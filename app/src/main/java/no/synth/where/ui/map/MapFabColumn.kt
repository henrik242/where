package no.synth.where.ui.map

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Straighten
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import no.synth.where.R

@Composable
internal fun LayerMenuItem(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    DropdownMenuItem(
        text = { Text((if (isSelected) "✓ " else "") + text) },
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
            Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.search_places))
        }

        Spacer(modifier = Modifier.size(8.dp))

        SmallFloatingActionButton(
            onClick = { onLayerMenuToggle(true) },
            modifier = Modifier.size(48.dp),
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Icon(Icons.Filled.Layers, contentDescription = stringResource(R.string.layers_and_overlays))
        }

        DropdownMenu(
            expanded = showLayerMenu,
            onDismissRequest = { onLayerMenuToggle(false) }
        ) {
            MenuSection(stringResource(R.string.map_layers))
            LayerMenuItem(stringResource(R.string.kartverket_norway), selectedLayer == MapLayer.KARTVERKET) { onLayerSelected(MapLayer.KARTVERKET) }
            LayerMenuItem(stringResource(R.string.kartverket_toporaster), selectedLayer == MapLayer.TOPORASTER) { onLayerSelected(MapLayer.TOPORASTER) }
            LayerMenuItem(stringResource(R.string.kartverket_sjokart), selectedLayer == MapLayer.SJOKARTRASTER) { onLayerSelected(MapLayer.SJOKARTRASTER) }
            LayerMenuItem("OpenStreetMap", selectedLayer == MapLayer.OSM) { onLayerSelected(MapLayer.OSM) }
            LayerMenuItem("OpenTopoMap", selectedLayer == MapLayer.OPENTOPOMAP) { onLayerSelected(MapLayer.OPENTOPOMAP) }
            HorizontalDivider()
            MenuSection(stringResource(R.string.overlays))
            LayerMenuItem(stringResource(R.string.waymarked_trails_osm), showWaymarkedTrails) { onWaymarkedTrailsToggle() }
            LayerMenuItem(stringResource(R.string.county_borders_norway), showCountyBorders) { onCountyBordersToggle() }
            LayerMenuItem(stringResource(R.string.saved_points), showSavedPoints) { onSavedPointsToggle() }
        }

        Spacer(modifier = Modifier.size(8.dp))

        SmallFloatingActionButton(
            onClick = onRecordStopClick,
            modifier = Modifier.size(48.dp),
            containerColor = if (isRecording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.surfaceVariant
        ) {
            Icon(
                if (isRecording) Icons.Filled.Stop else Icons.Filled.FiberManualRecord,
                contentDescription = if (isRecording) stringResource(R.string.stop_recording) else stringResource(R.string.start_recording),
                tint = if (isRecording) Color.White else Color.Red
            )
        }

        Spacer(modifier = Modifier.size(8.dp))

        SmallFloatingActionButton(
            onClick = onMyLocationClick,
            modifier = Modifier.size(48.dp),
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Icon(Icons.Filled.MyLocation, contentDescription = stringResource(R.string.my_location))
        }

        Spacer(modifier = Modifier.size(8.dp))

        SmallFloatingActionButton(
            onClick = onRulerToggle,
            modifier = Modifier.size(48.dp),
            containerColor = if (rulerActive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
        ) {
            Icon(
                Icons.Filled.Straighten,
                contentDescription = stringResource(R.string.ruler),
                tint = if (rulerActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.size(8.dp))

        SmallFloatingActionButton(
            onClick = onSettingsClick,
            modifier = Modifier.size(48.dp),
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.settings))
        }
    }
}
