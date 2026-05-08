package no.synth.where.service

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.Granularity
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import no.synth.where.MainActivity
import no.synth.where.R
import no.synth.where.WhereApplication
import no.synth.where.data.geo.LatLng
import no.synth.where.util.RemainingTime
import no.synth.where.util.currentTimeMillis
import no.synth.where.util.remainingTimeOf

/**
 * Foreground service that owns the fused-location stream and the platform
 * notification while either recording or live-share is active. Client
 * lifecycle (creation, transitions between RECORDING/LIVE/NONE) is delegated
 * to [no.synth.where.data.OnlineTrackingCoordinator].
 */
class LocationTrackingService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private val app get() = applicationContext as WhereApplication
    private val trackRepository get() = app.trackRepository
    private val userPreferences get() = app.userPreferences
    private val coordinator get() = app.onlineTrackingCoordinator

    private var notificationTickJob: Job? = null

    private data class NotificationState(
        val recording: Boolean,
        val shareActive: Boolean,
        val shareUntilMillis: Long,
    ) {
        val anyActive: Boolean get() = recording || shareActive
    }

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.lastLocation?.let { location ->
                if (location.isMock) return@let
                if (!location.hasAccuracy() || location.accuracy > MAX_ACCEPTABLE_ACCURACY_M) return@let

                val latLng = LatLng(location.latitude, location.longitude)
                val altitude = if (location.hasAltitude()) location.altitude else null
                val accuracy = location.accuracy

                trackRepository.addTrackPoint(
                    latLng = latLng,
                    altitude = altitude,
                    accuracy = accuracy
                )
                coordinator.sendPoint(latLng, altitude, accuracy)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        createNotificationChannel()
        observeNotificationState()
    }

    private fun observeNotificationState() {
        serviceScope.launch {
            combine(
                trackRepository.isRecording,
                userPreferences.liveShareUntilMillis,
            ) { recording, until ->
                NotificationState(
                    recording = recording,
                    shareActive = until > currentTimeMillis(),
                    shareUntilMillis = until,
                )
            }.distinctUntilChanged().collect { state ->
                if (!state.anyActive) {
                    stopNotificationTicker()
                    stopSelf()
                    return@collect
                }
                startForeground(NOTIFICATION_ID, createNotification(state))
                if (state.shareActive) startNotificationTicker() else stopNotificationTicker()
            }
        }
    }

    private fun startNotificationTicker() {
        if (notificationTickJob?.isActive == true) return
        notificationTickJob = serviceScope.launch {
            while (true) {
                delay(NOTIFICATION_TICK_INTERVAL)
                val s = currentNotificationState()
                if (!s.shareActive) break
                val nm = getSystemService(NotificationManager::class.java)
                nm?.notify(NOTIFICATION_ID, createNotification(s))
            }
        }
    }

    private fun stopNotificationTicker() {
        notificationTickJob?.cancel()
        notificationTickJob = null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP_SHARING -> userPreferences.stopLiveShare()
            ACTION_STOP_RECORDING -> trackRepository.stopRecording()
        }
        startForeground(NOTIFICATION_ID, createNotification(currentNotificationState()))
        startLocationUpdates()
        return START_STICKY
    }

    private fun currentNotificationState(): NotificationState {
        val until = userPreferences.liveShareUntilMillis.value
        return NotificationState(
            recording = trackRepository.isRecording.value,
            shareActive = until > currentTimeMillis(),
            shareUntilMillis = until,
        )
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        if (!hasLocationPermission()) return

        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            LOCATION_UPDATE_INTERVAL
        ).apply {
            setMinUpdateIntervalMillis(FASTEST_UPDATE_INTERVAL)
            setMaxUpdateDelayMillis(LOCATION_UPDATE_INTERVAL * 2)
            setWaitForAccurateLocation(false)
            setMinUpdateDistanceMeters(MIN_UPDATE_DISTANCE_METERS)
            setMaxUpdateAgeMillis(0)
            setGranularity(Granularity.GRANULARITY_FINE)
        }.build()

        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            Looper.getMainLooper()
        )
    }

    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.notification_channel_description)
        }
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }

    private fun createNotification(state: NotificationState): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            notificationIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        val title: String
        val text: String
        when {
            state.recording && state.shareActive -> {
                title = getString(R.string.notification_recording_title)
                val remaining = formatRemainingShort(state.shareUntilMillis - currentTimeMillis())
                text = if (remaining != null)
                    getString(R.string.notification_recording_and_sharing_text, remaining)
                else getString(R.string.notification_recording_and_sharing_text_no_time)
            }
            state.recording -> {
                title = getString(R.string.notification_recording_title)
                text = getString(R.string.notification_recording_text)
            }
            else -> {
                title = getString(R.string.notification_sharing_title)
                val remaining = formatRemainingShort(state.shareUntilMillis - currentTimeMillis())
                text = if (remaining != null)
                    getString(R.string.notification_sharing_text, remaining)
                else getString(R.string.notification_sharing_text_no_time)
            }
        }

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

        if (state.shareActive) {
            builder.addAction(
                0,
                getString(R.string.notification_action_stop_sharing),
                pendingIntentForAction(ACTION_STOP_SHARING, requestCode = 1)
            )
        }
        if (state.recording) {
            builder.addAction(
                0,
                getString(R.string.notification_action_stop_recording),
                pendingIntentForAction(ACTION_STOP_RECORDING, requestCode = 2)
            )
        }
        return builder.build()
    }

    private fun pendingIntentForAction(action: String, requestCode: Int): PendingIntent {
        val intent = Intent(this, LocationTrackingService::class.java).setAction(action)
        return PendingIntent.getService(this, requestCode, intent, PendingIntent.FLAG_IMMUTABLE)
    }

    private fun formatRemainingShort(millis: Long): String? =
        when (val r = remainingTimeOf(millis)) {
            RemainingTime.Zero -> null
            is RemainingTime.HoursOnly -> getString(R.string.duration_hours_only, r.hours)
            is RemainingTime.HoursAndMinutes ->
                getString(R.string.duration_hours_minutes, r.hours, r.minutes)
            is RemainingTime.MinutesOnly -> getString(R.string.duration_minutes, r.minutes)
            is RemainingTime.SecondsOnly -> getString(R.string.duration_seconds, r.seconds)
        }

    override fun onDestroy() {
        super.onDestroy()
        fusedLocationClient.removeLocationUpdates(locationCallback)
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL_ID = "LocationTrackingChannel"
        private const val NOTIFICATION_ID = 1
        private const val LOCATION_UPDATE_INTERVAL = 2000L
        private const val FASTEST_UPDATE_INTERVAL = 1000L
        private const val MIN_UPDATE_DISTANCE_METERS = 1f
        private const val MAX_ACCEPTABLE_ACCURACY_M = 50f
        private const val NOTIFICATION_TICK_INTERVAL = 30_000L
        private const val ACTION_STOP_SHARING = "no.synth.where.action.STOP_SHARING"
        private const val ACTION_STOP_RECORDING = "no.synth.where.action.STOP_RECORDING"

        fun start(context: Context) {
            val intent = Intent(context, LocationTrackingService::class.java)
            context.startForegroundService(intent)
        }
    }
}
