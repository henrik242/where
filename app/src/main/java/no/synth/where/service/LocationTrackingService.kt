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
import com.google.android.gms.location.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import no.synth.where.MainActivity
import no.synth.where.R
import no.synth.where.data.ClientIdManager
import no.synth.where.data.OnlineTrackingClient
import no.synth.where.data.TrackRepository
import no.synth.where.data.UserPreferences
import org.maplibre.android.geometry.LatLng

class LocationTrackingService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var trackRepository: TrackRepository
    private var onlineTrackingClient: OnlineTrackingClient? = null

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
        instance = this
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        trackRepository = TrackRepository.getInstance(this)

        serviceScope.launch {
            val userPreferences = UserPreferences.getInstance(this@LocationTrackingService)
            if (userPreferences.onlineTrackingEnabled) {
                enableOnlineTracking()
            }
        }

        createNotificationChannel()
    }

    fun enableOnlineTracking() {
        serviceScope.launch {
            val userPreferences = UserPreferences.getInstance(this@LocationTrackingService)
            val clientIdManager = ClientIdManager.getInstance(this@LocationTrackingService)
            val clientId = clientIdManager.getClientId()
            onlineTrackingClient = OnlineTrackingClient(
                serverUrl = userPreferences.trackingServerUrl,
                clientId = clientId
            )

            // Sync existing track if already recording
            val currentTrack = trackRepository.currentTrack.value
            if (currentTrack != null && currentTrack.isRecording) {
                onlineTrackingClient?.syncExistingTrack(currentTrack)
            } else {
                val trackName = currentTrack?.name ?: "Track"
                onlineTrackingClient?.startTrack(trackName)
            }
        }
    }

    fun disableOnlineTracking() {
        onlineTrackingClient?.stopTrack()
        onlineTrackingClient = null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, createNotification())
        startLocationUpdates()
        return START_STICKY
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
            "Track Recording",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Ongoing track recording notifications"
        }
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }

    private fun createNotification(): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            notificationIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, LocationTrackingService::class.java)
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Recording Track")
            .setContentText("Location tracking is active")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .addAction(
                R.drawable.ic_launcher_foreground,
                "Stop",
                stopPendingIntent
            )
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        fusedLocationClient.removeLocationUpdates(locationCallback)
        onlineTrackingClient?.stopTrack()
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL_ID = "LocationTrackingChannel"
        private const val NOTIFICATION_ID = 1
        private const val LOCATION_UPDATE_INTERVAL = 5000L // 5 seconds
        private const val FASTEST_UPDATE_INTERVAL = 2000L // 2 seconds

        private var instance: LocationTrackingService? = null

        fun start(context: Context) {
            val intent = Intent(context, LocationTrackingService::class.java)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, LocationTrackingService::class.java)
            context.stopService(intent)
        }

        fun enableOnlineTracking(context: Context) {
            instance?.enableOnlineTracking()
        }

        fun disableOnlineTracking(context: Context) {
            instance?.disableOnlineTracking()
        }
    }
}

