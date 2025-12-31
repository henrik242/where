package no.synth.where.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.osmdroid.tileprovider.cachemanager.CacheManager
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.views.MapView

class MapDownloadManager(private val context: Context) {

    suspend fun downloadRegion(
        region: Region,
        minZoom: Int = 5,
        maxZoom: Int = 15,
        onProgress: (Int) -> Unit,
        onComplete: (Boolean) -> Unit
    ) {
        withContext(Dispatchers.Main) {
            val mapView = MapView(context).apply {
                setTileSource(KartverketTileSource)
            }

            val cacheManager = object : CacheManager(mapView) {
                override fun getDownloadingDialog(
                    ctx: Context?,
                    pTask: CacheManager.CacheManagerTask?
                ): CacheManager.CacheManagerDialog {
                    // Return a dummy dialog that suppresses the default UI
                    return object : CacheManager.CacheManagerDialog(ctx, pTask) {
                        override fun getUITitle(): String {
                            return "Downloading"
                        }

                        /*
                        override fun show() {
                            // Do nothing to suppress the dialog
                        }
                        */
                    }
                }
            }

            try {
                cacheManager.downloadAreaAsync(
                    context, // Pass context to satisfy CacheManager, but dialog won't show
                    region.boundingBox,
                    minZoom,
                    maxZoom,
                    object : CacheManager.CacheManagerCallback {
                        override fun onTaskComplete() {
                            onComplete(true)
                            mapView.onDetach()
                        }

                        override fun onTaskFailed(errors: Int) {
                            onComplete(false)
                            mapView.onDetach()
                        }

                        override fun updateProgress(progress: Int, currentZoomLevel: Int, zoomLevelMax: Int, totalTiles: Int) {
                            // This might also be called by CacheManager, so we can use it too
                            val percentage = if (totalTiles > 0) (progress * 100) / totalTiles else 0
                            onProgress(percentage)
                        }

                        override fun downloadStarted() {
                            // Started
                        }

                        override fun setPossibleTilesInArea(total: Int) {
                            // Total tiles
                        }
                    }
                )
            } catch (e: Exception) {
                e.printStackTrace()
                onComplete(false)
                mapView.onDetach()
            }
        }
    }
}

