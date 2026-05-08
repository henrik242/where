package no.synth.where.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationListener
import android.location.LocationManager
import android.os.PowerManager
import androidx.core.content.ContextCompat
import no.synth.where.util.Logger

/**
 * Forces the GPS chip on while the map is visible. FusedLocationProviderClient —
 * even at HIGH_ACCURACY — can leave the GNSS hardware idle in low-signal terrain
 * when it judges Wi-Fi/cell-derived position is good enough; the result is stale
 * fixes in forests. Holding an active GPS_PROVIDER subscription keeps the chip
 * powered, which shortens TTFF for fused's own subscription and prevents the
 * "stale dot until another app wakes the chip" symptom.
 */
class GpsKeepAlive(context: Context) {
    private val appContext = context.applicationContext
    private val locationManager =
        appContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val powerManager =
        appContext.getSystemService(Context.POWER_SERVICE) as PowerManager

    private var listener: LocationListener? = null

    @SuppressLint("MissingPermission")
    fun start() {
        if (listener != null) return
        if (ContextCompat.checkSelfPermission(
                appContext, Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) return
        if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) return
        // Respect the user's battery saver: skip the keep-alive entirely.
        if (powerManager.isPowerSaveMode) return

        val l = LocationListener { _ -> }
        try {
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                MIN_TIME_MS,
                MIN_DISTANCE_M,
                l
            )
            listener = l
        } catch (e: SecurityException) {
            Logger.w(e, "GPS keep-alive start denied")
        } catch (e: IllegalArgumentException) {
            Logger.w(e, "GPS keep-alive start rejected")
        }
    }

    fun stop() {
        val l = listener ?: return
        try {
            locationManager.removeUpdates(l)
        } catch (e: SecurityException) {
            Logger.w(e, "GPS keep-alive stop denied")
        }
        listener = null
    }

    companion object {
        private const val MIN_TIME_MS = 1000L
        private const val MIN_DISTANCE_M = 0f
    }
}
