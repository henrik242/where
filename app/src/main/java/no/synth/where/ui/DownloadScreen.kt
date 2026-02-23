package no.synth.where.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import no.synth.where.R
import no.synth.where.data.DownloadLayers
import no.synth.where.data.MapDownloadManager
import no.synth.where.service.MapDownloadService

@Composable
fun DownloadScreen(
    onBackClick: () -> Unit,
    onLayerClick: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val downloadManager = remember { MapDownloadManager(context) }
    var refreshTrigger by remember { mutableIntStateOf(0) }
    var cacheSize by remember { mutableLongStateOf(0L) }
    val downloadState by MapDownloadService.downloadState.collectAsState()

    val kartverketDesc = stringResource(R.string.layer_kartverket_desc)
    val toporasterDesc = stringResource(R.string.layer_toporaster_desc)
    val sjokartrasterDesc = stringResource(R.string.layer_sjokartraster_desc)
    val mapantDesc = stringResource(R.string.layer_mapant_desc)
    val osmDesc = stringResource(R.string.layer_osm_desc)
    val opentopomapDesc = stringResource(R.string.layer_opentopomap_desc)
    val waymarkedtrailsDesc = stringResource(R.string.layer_waymarkedtrails_desc)

    val descriptionMap = remember(kartverketDesc) {
        mapOf(
            "kartverket" to kartverketDesc,
            "toporaster" to toporasterDesc,
            "sjokartraster" to sjokartrasterDesc,
            "mapant" to mapantDesc,
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

    LaunchedEffect(Unit) {
        cacheSize = downloadManager.getAmbientCacheSize()
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
        onDeleteLayer = { layerId ->
            scope.launch {
                downloadManager.deleteAllRegionsForLayer(layerId)
                cacheSize = downloadManager.getAmbientCacheSize()
                refreshTrigger++
            }
        },
        onClearAutoCache = {
            scope.launch {
                downloadManager.clearAutoCache()
                cacheSize = 0L  // SQLite file doesn't shrink after row deletion; set directly
                refreshTrigger++
            }
        },
        getLayerStats = { layerName -> downloadManager.getLayerStats(layerName) },
        refreshTrigger = refreshTrigger
    )
}
