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
import no.synth.where.BuildInfo
import no.synth.where.MainActivity
import no.synth.where.R
import no.synth.where.WhereApplication
import no.synth.where.data.OnlineTrackingClient
import no.synth.where.data.geo.LatLng
import no.synth.where.util.currentTimeMillis
import no.synth.where.util.formatDateTime
import no.synth.where.util.remainingTimeOf

class LocationTrackingService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private val app get() = applicationContext as WhereApplication
    private val trackRepository get() = app.trackRepository
    private val userPreferences get() = app.userPreferences
    private val clientIdManager get() = app.clientIdManager

    private var onlineTrackingClient: OnlineTrackingClient? = null
    private var clientMode: ClientMode = ClientMode.NONE
    private var notificationTickJob: Job? = null

    private enum class ClientMode { NONE, RECORDING, LIVE }

    private data class State(
        val recording: Boolean,
        val shareActive: Boolean,
        val onlineActive: Boolean,
        val shareUntilMillis: Long
    )

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.lastLocation?.let { location ->
                val latLng = LatLng(location.latitude, location.longitude)
                val altitude = if (location.hasAltitude()) location.altitude else null
                val accuracy = if (location.hasAccuracy()) location.accuracy else null

                trackRepository.addTrackPoint(
                    latLng = latLng,
                    altitude = altitude,
                    accuracy = accuracy
                )

                onlineTrackingClient?.sendPoint(latLng, altitude, accuracy)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        createNotificationChannel()
        observeState()
    }

    private fun observeState() {
        serviceScope.launch {
            combine(
                trackRepository.isRecording,
                userPreferences.alwaysShareUntilMillis,
                userPreferences.onlineTrackingEnabled,
                userPreferences.offlineModeEnabled
            ) { recording, until, online, offline ->
                State(
                    recording = recording,
                    shareActive = until > currentTimeMillis(),
                    onlineActive = online && !offline,
                    shareUntilMillis = until
                )
            }.distinctUntilChanged().collect { applyState(it) }
        }
    }

    private fun applyState(state: State) {
        if (!state.recording && !state.shareActive) {
            stopNotificationTicker()
            transitionClient(ClientMode.NONE)
            stopSelf()
            return
        }

        startForeground(NOTIFICATION_ID, createNotification(state))

        if (state.shareActive) startNotificationTicker() else stopNotificationTicker()

        val desired = when {
            !state.onlineActive -> ClientMode.NONE
            state.recording -> ClientMode.RECORDING
            else -> ClientMode.LIVE
        }
        if (desired != clientMode) transitionClient(desired)
    }

    private fun startNotificationTicker() {
        if (notificationTickJob?.isActive == true) return
        notificationTickJob = serviceScope.launch {
            while (true) {
                delay(NOTIFICATION_TICK_INTERVAL)
                val s = currentState()
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

    private fun transitionClient(desired: ClientMode) {
        onlineTrackingClient?.let { c ->
            c.stopTrack()
            c.close()
        }
        onlineTrackingClient = null
        clientMode = ClientMode.NONE

        if (desired == ClientMode.NONE) return

        clientMode = desired
        serviceScope.launch {
            val clientId = clientIdManager.getClientId()
            val client = OnlineTrackingClient(
                serverUrl = userPreferences.trackingServerUrl.value,
                clientId = clientId,
                trackingHint = BuildInfo.TRACKING_HINT,
                canSend = { !userPreferences.offlineModeEnabled.value },
                onViewerCountChanged = { userPreferences.updateViewerCount(it) }
            )
            onlineTrackingClient = client

            when (desired) {
                ClientMode.RECORDING -> {
                    val current = trackRepository.currentTrack.value
                    if (current != null && current.isRecording && current.points.isNotEmpty()) {
                        client.syncExistingTrack(current)
                    } else {
                        client.startTrack(current?.name ?: "Track")
                    }
                }
                ClientMode.LIVE -> {
                    client.startTrack(liveTrackName())
                }
                ClientMode.NONE -> {}
            }
        }
    }

    private fun liveTrackName(): String =
        "Live ${formatDateTime(currentTimeMillis(), "yyyy-MM-dd HH:mm")}"

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP_SHARING -> userPreferences.stopAlwaysShare()
            ACTION_STOP_RECORDING -> trackRepository.stopRecording()
        }
        startForeground(NOTIFICATION_ID, createNotification(currentState()))
        startLocationUpdates()
        return START_STICKY
    }

    private fun currentState(): State {
        val until = userPreferences.alwaysShareUntilMillis.value
        return State(
            recording = trackRepository.isRecording.value,
            shareActive = until > currentTimeMillis(),
            onlineActive = userPreferences.onlineTrackingEnabled.value && !userPreferences.offlineModeEnabled.value,
            shareUntilMillis = until
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
            setMinUpdateDistanceMeters(5f)
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

    private fun createNotification(state: State): Notification {
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

    private fun formatRemainingShort(millis: Long): String? {
        val r = remainingTimeOf(millis)
        return when {
            r.hours <= 0 && r.minutes <= 0 && r.seconds <= 0 -> null
            r.hours > 0 && r.minutes == 0 -> "${r.hours}h"
            r.hours > 0 -> "${r.hours}h ${r.minutes}m"
            r.minutes > 0 -> "${r.minutes}m"
            else -> "${r.seconds}s"
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        fusedLocationClient.removeLocationUpdates(locationCallback)
        onlineTrackingClient?.stopTrack()
        onlineTrackingClient?.close()
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL_ID = "LocationTrackingChannel"
        private const val NOTIFICATION_ID = 1
        private const val LOCATION_UPDATE_INTERVAL = 5000L
        private const val FASTEST_UPDATE_INTERVAL = 2000L
        private const val NOTIFICATION_TICK_INTERVAL = 30_000L
        private const val ACTION_STOP_SHARING = "no.synth.where.action.STOP_SHARING"
        private const val ACTION_STOP_RECORDING = "no.synth.where.action.STOP_RECORDING"

        fun start(context: Context) {
            val intent = Intent(context, LocationTrackingService::class.java)
            context.startForegroundService(intent)
        }
    }
}
