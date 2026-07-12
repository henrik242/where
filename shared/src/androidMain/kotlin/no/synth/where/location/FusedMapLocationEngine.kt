package no.synth.where.location

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.os.Looper
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailabilityLight
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import java.util.concurrent.ConcurrentHashMap
import no.synth.where.util.Logger
import org.maplibre.android.location.engine.LocationEngine
import org.maplibre.android.location.engine.LocationEngineCallback
import org.maplibre.android.location.engine.LocationEngineDefault
import org.maplibre.android.location.engine.LocationEngineRequest
import org.maplibre.android.location.engine.LocationEngineResult

/**
 * MapLibre [LocationEngine] backed by Google's fused location provider.
 *
 * MapLibre's default engine is raw LocationManager: it binds to whichever provider is
 * enabled at subscribe time, so with GNSS cold the puck sits on a cell-only network fix
 * (hundreds to thousands of meters) until first fix, and if location was off at subscribe
 * time it falls back to the passive provider and only updates when some other app asks.
 * Fused blends cell, Wi-Fi and cached cross-app fixes and adapts to provider toggles,
 * which is what lets other map apps show a usable position instantly.
 */
class FusedMapLocationEngine(context: Context) : LocationEngine {

    private val client: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)
    private val listeners =
        ConcurrentHashMap<LocationEngineCallback<LocationEngineResult>, LocationCallback>()

    @SuppressLint("MissingPermission")
    override fun getLastLocation(callback: LocationEngineCallback<LocationEngineResult>) {
        client.lastLocation
            .addOnSuccessListener { location ->
                if (location != null) {
                    callback.onSuccess(LocationEngineResult.create(location))
                } else {
                    callback.onFailure(Exception("Last location unavailable"))
                }
            }
            .addOnFailureListener(callback::onFailure)
    }

    @SuppressLint("MissingPermission")
    override fun requestLocationUpdates(
        request: LocationEngineRequest,
        callback: LocationEngineCallback<LocationEngineResult>,
        looper: Looper?,
    ) {
        val listener = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                // Fused batches oldest-first while MapLibre's getLastLocation() reads index 0,
                // so forward only the newest fix.
                result.lastLocation?.let { callback.onSuccess(LocationEngineResult.create(it)) }
            }
        }
        listeners.put(callback, listener)?.let(client::removeLocationUpdates)
        client.requestLocationUpdates(
            request.toFusedRequest(),
            listener,
            looper ?: Looper.getMainLooper(),
        )
    }

    @SuppressLint("MissingPermission")
    override fun requestLocationUpdates(request: LocationEngineRequest, pendingIntent: PendingIntent?) {
        pendingIntent?.let { client.requestLocationUpdates(request.toFusedRequest(), it) }
    }

    override fun removeLocationUpdates(callback: LocationEngineCallback<LocationEngineResult>) {
        listeners.remove(callback)?.let(client::removeLocationUpdates)
    }

    override fun removeLocationUpdates(pendingIntent: PendingIntent?) {
        pendingIntent?.let(client::removeLocationUpdates)
    }

    companion object {
        private var cached: LocationEngine? = null

        /**
         * Fused engine when Play Services is available, MapLibre's default engine otherwise.
         * One engine per process: map activation reruns on every style reload, and each
         * FusedLocationProviderClient instance costs a Play Services binding.
         */
        fun getOrCreate(context: Context): LocationEngine =
            cached ?: create(context.applicationContext).also { cached = it }

        private fun create(context: Context): LocationEngine =
            if (GoogleApiAvailabilityLight.getInstance()
                    .isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS
            ) {
                Logger.d("Map location engine: fused")
                FusedMapLocationEngine(context)
            } else {
                Logger.d("Map location engine: Play Services unavailable, using MapLibre default")
                LocationEngineDefault.getDefaultLocationEngine(context)
            }
    }
}

internal fun fusedPriority(enginePriority: Int): Int = when (enginePriority) {
    LocationEngineRequest.PRIORITY_HIGH_ACCURACY -> Priority.PRIORITY_HIGH_ACCURACY
    LocationEngineRequest.PRIORITY_BALANCED_POWER_ACCURACY -> Priority.PRIORITY_BALANCED_POWER_ACCURACY
    LocationEngineRequest.PRIORITY_LOW_POWER -> Priority.PRIORITY_LOW_POWER
    else -> Priority.PRIORITY_PASSIVE
}

private fun LocationEngineRequest.toFusedRequest(): LocationRequest =
    LocationRequest.Builder(fusedPriority(priority), interval)
        .setMinUpdateIntervalMillis(fastestInterval)
        .setMinUpdateDistanceMeters(displacement)
        .setMaxUpdateDelayMillis(maxWaitTime)
        .build()
