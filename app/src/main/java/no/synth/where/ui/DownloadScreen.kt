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
import no.synth.where.data.DownloadLayers
import no.synth.where.resources.Res
import no.synth.where.resources.*
import org.jetbrains.compose.resources.stringResource
import no.synth.where.data.MapDownloadManager
import no.synth.where.service.MapDownloadService
import java.io.File

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

    val kartverketDesc = stringResource(Res.string.layer_kartverket_desc)
    val toporasterDesc = stringResource(Res.string.layer_toporaster_desc)
    val sjokartrasterDesc = stringResource(Res.string.layer_sjokartraster_desc)
    val mapantDesc = stringResource(Res.string.layer_mapant_desc)
    val osmDesc = stringResource(Res.string.layer_osm_desc)
    val opentopomapDesc = stringResource(Res.string.layer_opentopomap_desc)
    val waymarkedtrailsDesc = stringResource(Res.string.layer_waymarkedtrails_desc)
    val avalanchezonesDesc = stringResource(Res.string.layer_avalanchezones_desc)

    val descriptionMap = remember(kartverketDesc) {
        mapOf(
            "kartverket" to kartverketDesc,
            "toporaster" to toporasterDesc,
            "sjokartraster" to sjokartrasterDesc,
            "mapant" to mapantDesc,
            "osm" to osmDesc,
            "opentopomap" to opentopomapDesc,
            "waymarkedtrails" to waymarkedtrailsDesc,
            "avalanchezones" to avalanchezonesDesc,
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
        onDeleteLayer = { layerId ->
            scope.launch {
                downloadManager.deleteAllRegionsForLayer(layerId)
                refreshTrigger++
            }
        },
        onClearAutoCache = {
            scope.launch {
                downloadManager.clearAutoCache()
                refreshTrigger++
            }
        },
        getLayerStats = { layerName -> downloadManager.getLayerStats(layerName) },
        refreshTrigger = refreshTrigger
    )
}
