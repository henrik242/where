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
import no.synth.where.data.OfflineTileReader
import no.synth.where.data.summary
import no.synth.where.resources.Res
import no.synth.where.resources.*
import java.io.File

@Composable
fun DownloadScreen(
    onBackClick: () -> Unit,
    onLayerClick: (String) -> Unit,
    onDownloadsClick: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var refreshTrigger by remember { mutableIntStateOf(0) }
    var cacheSize by remember { mutableLongStateOf(0L) }
    val app = context.applicationContext as no.synth.where.WhereApplication
    val downloadManager = app.mapDownloadManager
    val queue by downloadManager.queue.collectAsState()
    val downloadElevationData by app.userPreferences.downloadElevationData.collectAsState()
    val downloadMaxZoom by app.userPreferences.downloadMaxZoom.collectAsState()

    val layers = rememberLayerInfos()

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

    val freeStorageBytes = remember(refreshTrigger) {
        try {
            val dir = context.getExternalFilesDir(null) ?: context.filesDir
            android.os.StatFs(dir.absolutePath).availableBytes
        } catch (_: Exception) {
            -1L
        }
    }

    DownloadScreenContent(
        layers = layers,
        cacheSize = cacheSize,
        freeStorageBytes = freeStorageBytes,
        queueSummary = if (queue.isEmpty()) null else queue.summary(),
        onDownloadsClick = onDownloadsClick,
        onBackClick = onBackClick,
        onLayerClick = onLayerClick,
        onDeleteLayer = { layerId ->
            scope.launch {
                downloadManager.deleteLayer(layerId)
                refreshTrigger++
            }
        },
        onClearAutoCache = {
            scope.launch {
                downloadManager.clearAutoCache()
                refreshTrigger++
            }
        },
        downloadElevationData = downloadElevationData,
        demCacheSize = remember(refreshTrigger) { OfflineTileReader.getDemCacheSize() },
        onDownloadElevationDataChange = { enabled ->
            app.userPreferences.updateDownloadElevationData(enabled)
            if (!enabled) {
                scope.launch {
                    OfflineTileReader.clearAllDemTiles()
                    refreshTrigger++
                }
            }
        },
        downloadMaxZoom = downloadMaxZoom,
        onDownloadMaxZoomChange = { app.userPreferences.updateDownloadMaxZoom(it) },
        getLayerStats = { layerName -> downloadManager.getLayerStats(layerName) },
        refreshTrigger = refreshTrigger
    )
}
