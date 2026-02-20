package no.synth.where.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.launch
import no.synth.where.R
import no.synth.where.data.DownloadLayers
import no.synth.where.data.MapDownloadManager
import no.synth.where.data.PlatformFile
import no.synth.where.data.Region
import no.synth.where.data.RegionsRepository
import no.synth.where.service.MapDownloadService
import java.io.File

@Composable
fun DownloadScreen(
    onBackClick: () -> Unit,
    onLayerClick: (String) -> Unit
) {
    val context = LocalContext.current
    val downloadManager = remember { MapDownloadManager(context) }
    var refreshTrigger by remember { mutableIntStateOf(0) }
    var cacheSize by remember { mutableLongStateOf(0L) }
    val downloadState by MapDownloadService.downloadState.collectAsState()

    val kartverketDesc = stringResource(R.string.layer_kartverket_desc)
    val toporasterDesc = stringResource(R.string.layer_toporaster_desc)
    val sjokartrasterDesc = stringResource(R.string.layer_sjokartraster_desc)
    val osmDesc = stringResource(R.string.layer_osm_desc)
    val opentopomapDesc = stringResource(R.string.layer_opentopomap_desc)
    val waymarkedtrailsDesc = stringResource(R.string.layer_waymarkedtrails_desc)

    val descriptionMap = remember(kartverketDesc) {
        mapOf(
            "kartverket" to kartverketDesc,
            "toporaster" to toporasterDesc,
            "sjokartraster" to sjokartrasterDesc,
            "osm" to osmDesc,
            "opentopomap" to opentopomapDesc,
            "waymarkedtrails" to waymarkedtrailsDesc,
        )
    }

    val layers = remember(descriptionMap) {
        DownloadLayers.all.map { layer ->
            LayerInfo(layer.id, layer.displayName, descriptionMap[layer.id] ?: "")
        }
    }

    LaunchedEffect(refreshTrigger) {
        val maplibreTilesDir = File(context.getExternalFilesDir(null), "maplibre-tiles")
        cacheSize = if (maplibreTilesDir.exists()) {
            maplibreTilesDir.walkTopDown().sumOf { file ->
                if (file.isFile) file.length() else 0L
            }
        } else {
            context.cacheDir.walkTopDown().sumOf { file ->
                if (file.isFile) file.length() else 0L
            }
        }
    }

    DownloadScreenContent(
        layers = layers,
        cacheSize = cacheSize,
        isDownloading = downloadState.isDownloading,
        downloadRegionName = downloadState.region?.name,
        downloadLayerName = downloadState.layerName,
        downloadProgress = downloadState.progress,
        onBackClick = onBackClick,
        onLayerClick = onLayerClick,
        onStopDownload = { MapDownloadService.stopDownload(context) },
        getLayerStats = { layerName -> downloadManager.getLayerStats(layerName) },
        refreshTrigger = refreshTrigger
    )
}

@Composable
fun LayerRegionsScreen(
    layerId: String,
    onBackClick: () -> Unit,
    offlineModeEnabled: Boolean = false
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val downloadManager = remember { MapDownloadManager(context) }
    val regions = remember { RegionsRepository.getRegions(PlatformFile(context.cacheDir)) }

    val downloadState by MapDownloadService.downloadState.collectAsState()
    var refreshTrigger by remember { mutableIntStateOf(0) }
    var showDeleteDialog by remember { mutableStateOf<Region?>(null) }

    LaunchedEffect(downloadState.isDownloading) {
        if (!downloadState.isDownloading && downloadState.region != null) {
            refreshTrigger++
        }
    }

    val layerDisplayName = remember(layerId) {
        DownloadLayers.all.find { it.id == layerId }?.displayName ?: layerId
    }

    LayerRegionsScreenContent(
        layerDisplayName = layerDisplayName,
        layerId = layerId,
        regions = regions,
        isDownloading = downloadState.isDownloading,
        downloadRegionName = downloadState.region?.name,
        downloadLayerName = downloadState.layerName,
        downloadProgress = downloadState.progress,
        showDeleteDialog = showDeleteDialog,
        onBackClick = onBackClick,
        onStopDownload = { MapDownloadService.stopDownload(context) },
        onStartDownload = { region ->
            MapDownloadService.startDownload(
                context = context,
                region = region,
                layerName = layerId,
                minZoom = 5,
                maxZoom = 12
            )
        },
        onDeleteRequest = { region -> showDeleteDialog = region },
        onConfirmDelete = { region ->
            scope.launch {
                downloadManager.deleteRegionTiles(region, layerId)
                showDeleteDialog = null
                refreshTrigger++
            }
        },
        onDismissDelete = { showDeleteDialog = null },
        getRegionTileInfo = { region -> downloadManager.getRegionTileInfo(region, layerId) },
        refreshTrigger = refreshTrigger,
        offlineModeEnabled = offlineModeEnabled
    )
}
