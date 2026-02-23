package no.synth.where.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import no.synth.where.util.formatBytes
import no.synth.where.resources.Res
import no.synth.where.resources.*
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

data class LayerInfo(
    val id: String,
    val displayName: String,
    val description: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadScreenContent(
    layers: List<LayerInfo>,
    cacheSize: Long,
    isDownloading: Boolean,
    downloadRegionName: String?,
    downloadLayerName: String?,
    downloadProgress: Int,
    onBackClick: () -> Unit,
    onLayerClick: (String) -> Unit,
    onStopDownload: () -> Unit,
    onDeleteLayer: (String) -> Unit,
    onClearAutoCache: () -> Unit,
    getLayerStats: suspend (String) -> Pair<Long, Int>,
    refreshTrigger: Int
) {
    var deleteLayerDialog by rememberSaveable { mutableStateOf<String?>(null) }
    var clearCacheDialog by rememberSaveable { mutableStateOf(false) }

    deleteLayerDialog?.let { layerId ->
        val layerName = layers.find { it.id == layerId }?.displayName ?: layerId
        AlertDialog(
            onDismissRequest = { deleteLayerDialog = null },
            title = { Text(stringResource(Res.string.delete_layer_title, layerName)) },
            text = { Text(stringResource(Res.string.delete_layer_message, layerName)) },
            confirmButton = {
                Button(onClick = { deleteLayerDialog = null; onDeleteLayer(layerId) }) {
                    Text(stringResource(Res.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteLayerDialog = null }) {
                    Text(stringResource(Res.string.cancel))
                }
            }
        )
    }

    if (clearCacheDialog) {
        AlertDialog(
            onDismissRequest = { clearCacheDialog = false },
            title = { Text(stringResource(Res.string.clear_cache_title)) },
            text = { Text(stringResource(Res.string.clear_cache_message)) },
            confirmButton = {
                Button(onClick = { clearCacheDialog = false; onClearAutoCache() }) {
                    Text(stringResource(Res.string.clear))
                }
            },
            dismissButton = {
                TextButton(onClick = { clearCacheDialog = false }) {
                    Text(stringResource(Res.string.cancel))
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.offline_maps)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(painterResource(Res.drawable.ic_arrow_back), contentDescription = stringResource(Res.string.back))
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                stringResource(Res.string.automatic_cache),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                stringResource(Res.string.tiles_loaded_automatically),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (cacheSize > 0) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    formatBytes(cacheSize),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.tertiary,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                        if (cacheSize > 0) {
                            IconButton(onClick = { clearCacheDialog = true }) {
                                Icon(
                                    painterResource(Res.drawable.ic_delete),
                                    contentDescription = stringResource(Res.string.delete),
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                        // Match width of chevron icon on layer cards so delete buttons align
                        Spacer(modifier = Modifier.width(24.dp))
                    }
                }
            }

            if (isDownloading && downloadRegionName != null) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        stringResource(Res.string.downloading_region, downloadRegionName),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        downloadLayerName ?: "",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                TextButton(onClick = onStopDownload) {
                                    Text(stringResource(Res.string.stop))
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            LinearProgressIndicator(
                                progress = { downloadProgress / 100f },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("$downloadProgress%", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            items(layers) { layer ->
                var stats by remember { mutableStateOf(Pair(0L, 0)) }
                LaunchedEffect(refreshTrigger, layer.id) {
                    stats = getLayerStats(layer.id)
                }

                Card(
                    onClick = { onLayerClick(layer.id) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                layer.displayName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                layer.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (stats.second > 0) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    stringResource(Res.string.tiles_stats, stats.second, formatBytes(stats.first)),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                        if (stats.second > 0) {
                            IconButton(onClick = { deleteLayerDialog = layer.id }) {
                                Icon(
                                    painterResource(Res.drawable.ic_delete),
                                    contentDescription = stringResource(Res.string.delete),
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                        Icon(
                            painterResource(Res.drawable.ic_chevron_right),
                            contentDescription = stringResource(Res.string.view_regions),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

