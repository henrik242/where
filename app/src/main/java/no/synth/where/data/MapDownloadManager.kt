package no.synth.where.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.maplibre.android.offline.OfflineManager
import org.maplibre.android.offline.OfflineRegion
import org.maplibre.android.offline.OfflineRegionError
import org.maplibre.android.offline.OfflineRegionStatus
import org.maplibre.android.offline.OfflineTilePyramidRegionDefinition
import java.io.File
import java.nio.charset.Charset
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class MapDownloadManager(private val context: Context) {

    private val offlineManager = OfflineManager.getInstance(context)

    suspend fun downloadRegion(
        region: Region,
        minZoom: Int = 5,
        maxZoom: Int = 15,
        onProgress: (Int) -> Unit,
        onComplete: (Boolean) -> Unit
    ) {
        withContext(Dispatchers.IO) {
            // 1. Create style file
            val styleFile = File(context.cacheDir, "kartverket_style.json")
            styleFile.writeText(MapStyle.KARTVERKET_STYLE_JSON, Charset.forName("UTF-8"))
            val styleUrl = "file://${styleFile.absolutePath}"

            // 2. Define region
            val pixelRatio = context.resources.displayMetrics.density
            val definition = OfflineTilePyramidRegionDefinition(
                styleUrl,
                region.boundingBox,
                minZoom.toDouble(),
                maxZoom.toDouble(),
                pixelRatio
            )

            // 3. Metadata
            val metadata = region.name.toByteArray(Charset.forName("UTF-8"))

            // 4. Create offline region
            createOfflineRegion(definition, metadata, onProgress, onComplete)
        }
    }

    private suspend fun createOfflineRegion(
        definition: OfflineTilePyramidRegionDefinition,
        metadata: ByteArray,
        onProgress: (Int) -> Unit,
        onComplete: (Boolean) -> Unit
    ) = suspendCoroutine<Unit> { continuation ->
        offlineManager.createOfflineRegion(
            definition,
            metadata,
            object : OfflineManager.CreateOfflineRegionCallback {
                override fun onCreate(offlineRegion: OfflineRegion) {
                    startDownload(offlineRegion, onProgress, onComplete)
                    continuation.resume(Unit)
                }

                override fun onError(error: String) {
                    onComplete(false)
                    continuation.resume(Unit)
                }
            }
        )
    }

    private fun startDownload(
        offlineRegion: OfflineRegion,
        onProgress: (Int) -> Unit,
        onComplete: (Boolean) -> Unit
    ) {
        offlineRegion.setObserver(object : OfflineRegion.OfflineRegionObserver {
            override fun onStatusChanged(status: OfflineRegionStatus) {
                val progress = if (status.requiredResourceCount >= 0) {
                    (100.0 * status.completedResourceCount / status.requiredResourceCount).toInt()
                } else {
                    0
                }

                if (status.isComplete) {
                    onProgress(100)
                    onComplete(true)
                    offlineRegion.setDownloadState(OfflineRegion.STATE_INACTIVE)
                    offlineRegion.setObserver(null)
                } else {
                    onProgress(progress)
                }
            }

            override fun onError(error: OfflineRegionError) {
                onComplete(false)
            }

            override fun mapboxTileCountLimitExceeded(limit: Long) {
                onComplete(false)
            }
        })

        offlineRegion.setDownloadState(OfflineRegion.STATE_ACTIVE)
    }
}

