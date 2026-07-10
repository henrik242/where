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
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import no.synth.where.MainActivity
import no.synth.where.R
import no.synth.where.WhereApplication
import no.synth.where.data.DownloadStatus
import no.synth.where.data.QueuedDownload
import no.synth.where.data.summary

/**
 * Foreground shell that keeps the process alive and shows a notification while the shared
 * [no.synth.where.data.DownloadQueueManager] drains its queue. All queue logic lives in the
 * manager (a [WhereApplication] singleton); this service only mirrors it into a notification and
 * relays the user's "Stop" action.
 */
class MapDownloadService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var collectorJob: Job? = null
    private var lastNotifiedProgress = -1
    private var lastNotifiedTime = 0L

    private val queueManager get() = (application as WhereApplication).downloadQueueManager

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CANCEL_ACTIVE) {
            queueManager.queue.value.firstOrNull { it.status == DownloadStatus.DOWNLOADING }
                ?.let { queueManager.cancel(it.id) }
            // The collector below stops the service once the queue has drained.
            return START_NOT_STICKY
        }

        startForeground(NOTIFICATION_ID, buildNotification(queueManager.queue.value))
        if (collectorJob == null) {
            collectorJob = scope.launch {
                queueManager.queue.collect { queue ->
                    if (queue.none { it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.QUEUED }) {
                        stopForegroundAndSelf()
                    } else {
                        maybeUpdateNotification(queue)
                    }
                }
            }
        }
        return START_NOT_STICKY
    }

    /** Android 14+ dataSync time budget (~6h/day). Abort so we never download outside a FGS. */
    override fun onTimeout(startId: Int) {
        queueManager.stopAll()
        stopForegroundAndSelf()
    }

    private fun stopForegroundAndSelf() {
        collectorJob?.cancel()
        collectorJob = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun maybeUpdateNotification(queue: List<QueuedDownload>) {
        val active = queue.firstOrNull { it.status == DownloadStatus.DOWNLOADING }
        val progress = active?.overallProgress ?: 0
        val now = System.currentTimeMillis()
        val changedEnough = progress == 100 ||
            now - lastNotifiedTime >= 1000 ||
            kotlin.math.abs(progress - lastNotifiedProgress) >= 5
        if (!changedEnough) return
        lastNotifiedProgress = progress
        lastNotifiedTime = now
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, buildNotification(queue))
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(CHANNEL_ID, "Map Downloads", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Ongoing map download notifications"
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(queue: List<QueuedDownload>): Notification {
        val summary = queue.summary()
        val active = queue.firstOrNull { it.status == DownloadStatus.DOWNLOADING }
        val progress = active?.overallProgress ?: 0
        val title = active?.let { "Downloading ${it.label}" } ?: "Offline maps"
        val text = "${summary.position} of ${summary.total}"

        val contentIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        val stopIntent = PendingIntent.getService(
            this, 1,
            Intent(this, MapDownloadService::class.java).apply { action = ACTION_CANCEL_ACTIVE },
            PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setProgress(100, progress, active == null)
            .setContentIntent(contentIntent)
            // Cancels the active download and lets the queue continue — "Skip", not "Stop all".
            .addAction(R.drawable.ic_launcher_foreground, "Skip", stopIntent)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL_ID = "MapDownloadChannel"
        private const val NOTIFICATION_ID = 2
        private const val ACTION_CANCEL_ACTIVE = "no.synth.where.action.CANCEL_ACTIVE_DOWNLOAD"

        /** Keep the process alive + show progress while the queue drains. Started from the
         *  foreground UI on the first enqueue; the service stops itself when the queue empties. */
        fun start(context: Context) {
            context.startForegroundService(Intent(context, MapDownloadService::class.java))
        }
    }
}
