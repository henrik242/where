package no.synth.where.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import no.synth.where.util.Logger

/**
 * Live location: the fused provider (GPS + network + sensors) where Google Play Services is
 * available, falling back to the platform [LocationManager] where it is not (e.g. de-Googled
 * devices). Emits the last known fix first, then updates. A missing-permission [SecurityException] is
 * swallowed so callers without location access (e.g. the download-region picker) still work.
 */
fun fusedLocationFlow(context: Context, intervalMs: Long = 2000L): Flow<Location> =
    if (playServicesAvailable(context)) fusedFlow(context, intervalMs)
    else platformFlow(context, intervalMs)

private fun playServicesAvailable(context: Context): Boolean =
    GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS

@SuppressLint("MissingPermission")
private fun fusedFlow(context: Context, intervalMs: Long): Flow<Location> = callbackFlow {
    val client = LocationServices.getFusedLocationProviderClient(context)
    val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, intervalMs).build()
    val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.lastLocation?.let { trySend(it) }
        }
    }
    try {
        client.lastLocation.addOnSuccessListener { loc -> loc?.let { trySend(it) } }
        client.requestLocationUpdates(request, callback, Looper.getMainLooper())
    } catch (e: SecurityException) {
        Logger.d("No location permission for fused updates: %s", e.message ?: "unknown")
    }
    awaitClose { client.removeLocationUpdates(callback) }
}

@SuppressLint("MissingPermission")
private fun platformFlow(context: Context, intervalMs: Long): Flow<Location> = callbackFlow {
    val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    val listener = LocationListener { trySend(it) }
    val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
    try {
        providers.mapNotNull { runCatching { manager.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }
            ?.let { trySend(it) }
        providers.filter { runCatching { manager.isProviderEnabled(it) }.getOrDefault(false) }
            .forEach { manager.requestLocationUpdates(it, intervalMs, 0f, listener, Looper.getMainLooper()) }
    } catch (e: SecurityException) {
        Logger.d("No location permission for platform updates: %s", e.message ?: "unknown")
    }
    awaitClose { manager.removeUpdates(listener) }
}
