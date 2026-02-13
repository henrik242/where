package no.synth.where.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import no.synth.where.MainActivity
import no.synth.where.R
import dagger.hilt.android.AndroidEntryPoint
import no.synth.where.data.MapDownloadManager
import no.synth.where.data.Region
import no.synth.where.data.RegionsRepository

@AndroidEntryPoint
class MapDownloadService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var downloadManager: MapDownloadManager
    private var currentDownloadJob: kotlinx.coroutines.Job? = null
    private var currentRegionName: String? = null
    private var lastNotificationUpdate = 0L
    private var lastNotificationProgress = -1

    data class DownloadState(
        val region: Region? = null,
        val layerName: String? = null,
        val progress: Int = 0,
        val isDownloading: Boolean = false
    )

    override fun onCreate() {
        super.onCreate()
        downloadManager = MapDownloadManager(this)
        createNotificationChannel()
        
        // If service is being recreated but state shows download in progress,
        // reset the state as the download was interrupted
        if (_downloadState.value.isDownloading) {
            _downloadState.value = DownloadState()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_DOWNLOAD -> {
                val regionName = intent.getStringExtra(EXTRA_REGION_NAME)
                val layerName = intent.getStringExtra(EXTRA_LAYER_NAME)
                val minZoom = intent.getIntExtra(EXTRA_MIN_ZOOM, 5)
                val maxZoom = intent.getIntExtra(EXTRA_MAX_ZOOM, 12)

                if (regionName != null && layerName != null) {
                    val regions = RegionsRepository.getRegions(cacheDir)
                    val region = regions.find { it.name == regionName }
                    if (region != null) {
                        startDownload(region, layerName, minZoom, maxZoom)
                    }
                }
            }
            ACTION_STOP_DOWNLOAD -> {
                stopDownload()
            }
        }
        return START_NOT_STICKY
    }

    private fun startDownload(region: Region, layerName: String, minZoom: Int, maxZoom: Int) {
        // Cancel any existing download
        currentDownloadJob?.cancel()
        currentRegionName?.let { downloadManager.stopDownload(it) }
        
        currentRegionName = "${region.name}-$layerName"
        lastNotificationUpdate = 0L
        lastNotificationProgress = -1
        
        _downloadState.value = DownloadState(
            region = region,
            layerName = layerName,
            progress = 0,
            isDownloading = true
        )

        startForeground(NOTIFICATION_ID, createNotification(region.name, 0))

        currentDownloadJob = serviceScope.launch {
            downloadManager.downloadRegion(
                region = region,
                layerName = layerName,
                minZoom = minZoom,
                maxZoom = maxZoom,
                onProgress = { progress ->
                    _downloadState.value = _downloadState.value.copy(progress = progress)
                    // Throttle notification updates to avoid overwhelming the system
                    val now = System.currentTimeMillis()
                    val timeSinceLastUpdate = now - lastNotificationUpdate
                    val progressChange = kotlin.math.abs(progress - lastNotificationProgress)
                    
                    // Update notification if: 
                    // - At least 1 second has passed since last update, OR
                    // - Progress changed by 5% or more, OR
                    // - Progress reached 100%
                    if (timeSinceLastUpdate >= 1000 || progressChange >= 5 || progress == 100) {
                        updateNotification(region.name, progress)
                        lastNotificationUpdate = now
                        lastNotificationProgress = progress
                    }
                },
                onComplete = { success ->
                    _downloadState.value = DownloadState()
                    currentRegionName = null
                    stopSelf()
                }
            )
        }
    }

    private fun stopDownload() {
        currentDownloadJob?.cancel()
        currentDownloadJob = null
        currentRegionName?.let { downloadManager.stopDownload(it) }
        currentRegionName = null
        _downloadState.value = DownloadState()
        stopSelf()
    }

    private fun updateNotification(regionName: String, progress: Int) {
        val notification = createNotification(regionName, progress)
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Map Downloads",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Ongoing map download notifications"
        }
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }

    private fun createNotification(regionName: String, progress: Int): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            notificationIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, MapDownloadService::class.java).apply {
            action = ACTION_STOP_DOWNLOAD
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Downloading $regionName")
            .setContentText("$progress% complete")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setProgress(100, progress, false)
            .setContentIntent(pendingIntent)
            .addAction(
                R.drawable.ic_launcher_foreground,
                "Stop",
                stopPendingIntent
            )
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL_ID = "MapDownloadChannel"
        private const val NOTIFICATION_ID = 2
        private const val ACTION_START_DOWNLOAD = "no.synth.where.action.START_DOWNLOAD"
        private const val ACTION_STOP_DOWNLOAD = "no.synth.where.action.STOP_DOWNLOAD"
        private const val EXTRA_REGION_NAME = "extra_region_name"
        private const val EXTRA_LAYER_NAME = "extra_layer_name"
        private const val EXTRA_MIN_ZOOM = "extra_min_zoom"
        private const val EXTRA_MAX_ZOOM = "extra_max_zoom"

        private val _downloadState = MutableStateFlow(DownloadState())
        val downloadState: StateFlow<DownloadState> = _downloadState.asStateFlow()

        fun startDownload(
            context: Context,
            region: Region,
            layerName: String,
            minZoom: Int = 5,
            maxZoom: Int = 12
        ) {
            val intent = Intent(context, MapDownloadService::class.java).apply {
                action = ACTION_START_DOWNLOAD
                putExtra(EXTRA_REGION_NAME, region.name)
                putExtra(EXTRA_LAYER_NAME, layerName)
                putExtra(EXTRA_MIN_ZOOM, minZoom)
                putExtra(EXTRA_MAX_ZOOM, maxZoom)
            }
            context.startForegroundService(intent)
        }

        fun stopDownload(context: Context) {
            val intent = Intent(context, MapDownloadService::class.java).apply {
                action = ACTION_STOP_DOWNLOAD
            }
            context.startService(intent)
        }
    }
}
