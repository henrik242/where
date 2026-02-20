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
import androidx.compose.material3.ListItem
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import no.synth.where.data.Region
import no.synth.where.data.RegionTileInfo
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
    getLayerStats: suspend (String) -> Pair<Long, Int>,
    refreshTrigger: Int
) {
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
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
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
                            }
                            if (cacheSize > 0) {
                                Text(
                                    formatBytes(cacheSize),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.tertiary
                                )
                            }
                        }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LayerRegionsScreenContent(
    layerDisplayName: String,
    layerId: String,
    regions: List<Region>,
    isDownloading: Boolean,
    downloadRegionName: String?,
    downloadLayerName: String?,
    downloadProgress: Int,
    showDeleteDialog: Region?,
    onBackClick: () -> Unit,
    onStopDownload: () -> Unit,
    onStartDownload: (Region) -> Unit,
    onDeleteRequest: (Region) -> Unit,
    onConfirmDelete: (Region) -> Unit,
    onDismissDelete: () -> Unit,
    getRegionTileInfo: suspend (Region) -> RegionTileInfo?,
    refreshTrigger: Int,
    offlineModeEnabled: Boolean = false
) {
    fun cleanRegionName(name: String): String = name.substringBefore(" - ")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(layerDisplayName) },
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
                Text(
                    stringResource(Res.string.download_regions),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            if (isDownloading && downloadRegionName != null && downloadLayerName == layerId) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(stringResource(Res.string.downloading_region_ellipsis, cleanRegionName(downloadRegionName)))
                            Spacer(modifier = Modifier.height(8.dp))
                            LinearProgressIndicator(
                                progress = { downloadProgress / 100f },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "$downloadProgress%",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                TextButton(onClick = onStopDownload) {
                                    Text(stringResource(Res.string.stop))
                                }
                            }
                        }
                    }
                }
            }

            items(regions) { region ->
                var tileInfo by remember { mutableStateOf<RegionTileInfo?>(null) }

                LaunchedEffect(region, layerId, refreshTrigger) {
                    tileInfo = getRegionTileInfo(region)
                }

                val info = tileInfo
                val isDownloaded = info?.isFullyDownloaded == true
                val hasPartialDownload = (info?.downloadedTiles ?: 0) > 0

                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    ListItem(
                        headlineContent = { Text(cleanRegionName(region.name)) },
                        supportingContent = {
                            if (isDownloaded) {
                                Text(stringResource(Res.string.tiles_downloaded, info.downloadedTiles, formatBytes(info.downloadedSize)))
                            } else if (hasPartialDownload) {
                                Text(
                                    stringResource(Res.string.tiles_partial, info?.downloadedTiles ?: 0, info?.totalTiles ?: 0, formatBytes(info?.downloadedSize ?: 0))
                                )
                            } else {
                                Text(stringResource(Res.string.tiles_needed, info?.totalTiles ?: 0))
                            }
                        },
                        trailingContent = {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (hasPartialDownload || isDownloaded) {
                                    IconButton(onClick = { onDeleteRequest(region) }) {
                                        Icon(painterResource(Res.drawable.ic_delete), contentDescription = stringResource(Res.string.delete))
                                    }
                                }
                                Button(
                                    onClick = { onStartDownload(region) },
                                    enabled = !isDownloading && !offlineModeEnabled
                                ) {
                                    Text(
                                        when {
                                            isDownloaded -> "\u2713"
                                            hasPartialDownload -> stringResource(Res.string.continue_label)
                                            else -> stringResource(Res.string.download)
                                        }
                                    )
                                }
                            }
                        }
                    )
                }
            }
        }
    }

    showDeleteDialog?.let { region ->
        AlertDialog(
            onDismissRequest = onDismissDelete,
            title = { Text(stringResource(Res.string.delete_region_title, cleanRegionName(region.name))) },
            text = { Text(stringResource(Res.string.delete_region_message, layerDisplayName, cleanRegionName(region.name))) },
            confirmButton = {
                TextButton(onClick = { onConfirmDelete(region) }) {
                    Text(stringResource(Res.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissDelete) {
                    Text(stringResource(Res.string.cancel))
                }
            }
        )
    }
}
