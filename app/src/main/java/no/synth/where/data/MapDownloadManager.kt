package no.synth.where.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.maplibre.android.offline.OfflineManager
import org.maplibre.android.offline.OfflineRegion
import org.maplibre.android.offline.OfflineRegionError
import org.maplibre.android.offline.OfflineRegionStatus
import org.maplibre.android.offline.OfflineTilePyramidRegionDefinition
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
            Log.d("MapDownloadManager", "Starting download for region: ${region.name}")

            // Get the style JSON directly - no need to save to file
            val styleJson = MapStyle.getStyle()
            Log.d("MapDownloadManager", "Generated style JSON (length: ${styleJson.length})")

            // 2. Define region
            val pixelRatio = context.resources.displayMetrics.density
            val definition = OfflineTilePyramidRegionDefinition(
                styleJson,  // Use JSON directly instead of file URL
                region.boundingBox,
                minZoom.toDouble(),
                maxZoom.toDouble(),
                pixelRatio
            )

            Log.d("MapDownloadManager", "Region definition created: zoom ${minZoom}-${maxZoom}, pixel ratio: $pixelRatio")

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
        Log.d("MapDownloadManager", "Creating offline region...")
        offlineManager.createOfflineRegion(
            definition,
            metadata,
            object : OfflineManager.CreateOfflineRegionCallback {
                override fun onCreate(offlineRegion: OfflineRegion) {
                    Log.d("MapDownloadManager", "Offline region created successfully")
                    startDownload(offlineRegion, onProgress, onComplete)
                    continuation.resume(Unit)
                }

                override fun onError(error: String) {
                    Log.e("MapDownloadManager", "Failed to create offline region: $error")
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
        Log.d("MapDownloadManager", "Starting download...")
        offlineRegion.setObserver(object : OfflineRegion.OfflineRegionObserver {
            override fun onStatusChanged(status: OfflineRegionStatus) {
                val progress = if (status.requiredResourceCount >= 0) {
                    (100.0 * status.completedResourceCount / status.requiredResourceCount).toInt()
                } else {
                    0
                }

                Log.d("MapDownloadManager", "Download progress: $progress% (${status.completedResourceCount}/${status.requiredResourceCount})")

                if (status.isComplete) {
                    Log.d("MapDownloadManager", "Download complete!")
                    onProgress(100)
                    onComplete(true)
                    offlineRegion.setDownloadState(OfflineRegion.STATE_INACTIVE)
                    offlineRegion.setObserver(null)
                } else {
                    onProgress(progress)
                }
            }

            override fun onError(error: OfflineRegionError) {
                Log.e("MapDownloadManager", "Download error: ${error.message}, reason: ${error.reason}")
                onComplete(false)
            }

            override fun mapboxTileCountLimitExceeded(limit: Long) {
                Log.e("MapDownloadManager", "Tile count limit exceeded: $limit")
                onComplete(false)
            }
        })

        offlineRegion.setDownloadState(OfflineRegion.STATE_ACTIVE)
        Log.d("MapDownloadManager", "Download state set to ACTIVE")
    }
}

