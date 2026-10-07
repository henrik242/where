package no.synth.where.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.Looper
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
 * Live location from the fused provider, which blends GPS, network and sensors, so fixes keep coming
 * indoors and during a GPS cold start (unlike subscribing to a single provider). Emits the last
 * known fix first, then updates. A missing-permission [SecurityException] is swallowed so callers
 * that have no location access (e.g. the download-region picker) still work.
 */
@SuppressLint("MissingPermission")
fun fusedLocationFlow(context: Context, intervalMs: Long = 2000L): Flow<Location> = callbackFlow {
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
