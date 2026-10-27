package no.synth.where.location

import android.content.Context
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.Surface
import android.view.WindowManager
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.sample
import no.synth.where.data.geo.LatLng
import no.synth.where.data.geo.screenHeadingDegrees
import no.synth.where.ui.map.CompassReading
import no.synth.where.ui.map.compassReading
import kotlin.time.Duration.Companion.seconds

private const val SAMPLING_PERIOD_US = 100_000

/**
 * Compass readings from the rotation-vector sensor, about once a second. [screenHeadingDegrees]
 * follows the top of the screen while the phone lies flat and the back of the phone while it is
 * held upright. [lastPosition] gives the declination that turns magnetic north into true north.
 */
@OptIn(FlowPreview::class)
fun androidCompassReadings(context: Context, lastPosition: () -> LatLng?): Flow<CompassReading> = callbackFlow {
    val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    if (sensor == null) {
        close()
        return@callbackFlow
    }
    val matrix = FloatArray(9)
    val listener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            // Some Samsung devices throw on vectors longer than four elements; four is all it needs.
            SensorManager.getRotationMatrixFromVector(matrix, event.values.copyOf(4))
            val magnetic = screenHeadingDegrees(matrix, displayRotationDegrees(context))
            // values[4] is the estimated accuracy in radians, -1 when unavailable.
            val accuracy = event.values.getOrNull(4)?.takeIf { it >= 0f }?.let { Math.toDegrees(it.toDouble()) }
            compassReading(magnetic, lastPosition()?.let(::declinationAt) ?: 0.0, accuracy)?.let { trySend(it) }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }
    if (!sensorManager.registerListener(listener, sensor, SAMPLING_PERIOD_US)) {
        close(IllegalStateException("Rotation-vector sensor registration failed"))
        return@callbackFlow
    }
    awaitClose { sensorManager.unregisterListener(listener) }
}.sample(1.seconds)

/** Magnetic declination in degrees east, from the World Magnetic Model bundled with Android. */
private fun declinationAt(latLng: LatLng): Double =
    GeomagneticField(latLng.latitude.toFloat(), latLng.longitude.toFloat(), 0f, System.currentTimeMillis())
        .declination.toDouble()

/** Current display rotation as degrees (Surface.ROTATION_* -> 0/90/180/270). */
@Suppress("DEPRECATION")
private fun displayRotationDegrees(context: Context): Int =
    when ((context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay.rotation) {
        Surface.ROTATION_90 -> 90
        Surface.ROTATION_180 -> 180
        Surface.ROTATION_270 -> 270
        else -> 0
    }
