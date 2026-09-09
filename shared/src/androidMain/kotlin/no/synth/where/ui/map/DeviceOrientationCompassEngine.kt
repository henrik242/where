package no.synth.where.ui.map

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import android.view.Display
import android.view.Surface
import no.synth.where.data.geo.screenHeadingDegrees
import no.synth.where.util.Logger
import org.maplibre.android.location.CompassEngine
import org.maplibre.android.location.CompassListener

/**
 * Heading source for MapLibre's location component, replacing its stock
 * LocationComponentCompassEngine.
 *
 * The stock engine decides whether the phone is flat or upright from pitch and roll measured
 * *after* remapping the sensor matrix by the display rotation. When the physical orientation and
 * the display rotation disagree -- a phone held on its side, resting in a lap, or one whose
 * rotation is locked -- that test lands in the wrong branch and the reported heading is 90 degrees
 * out, while still tracking every turn faithfully. [screenHeadingDegrees] makes the same choice
 * from gravity, which cannot be fooled that way; this class only feeds it sensor data.
 *
 * Sensor rates mirror the stock engine: samples at 100 ms, headings pushed at most every 500 ms
 * (the location component interpolates the camera between them).
 */
internal class DeviceOrientationCompassEngine(context: Context) : CompassEngine, SensorEventListener {

    private companion object {
        const val SENSOR_DELAY_MICROS = 100 * 1000
        const val UPDATE_RATE_MS = 500L
        // Matches the stock engine's smoothing for the accelerometer + magnetometer fallback.
        const val ALPHA = 0.45f
    }

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    /**
     * Held rather than re-resolved so the engine keeps no reference to the activity. A context
     * with no display (an application context) leaves this null, in which case the natural
     * orientation is assumed -- it only matters while the phone lies flat.
     */
    private val display: Display? = try {
        context.display
    } catch (_: UnsupportedOperationException) {
        null
    }

    private val rotationVectorSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private val accelerometerSensor = if (rotationVectorSensor == null) {
        Logger.d("Rotation vector sensor missing, falling back to accelerometer + magnetic field")
        sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    } else {
        null
    }
    private val magneticFieldSensor = if (rotationVectorSensor == null) {
        sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
    } else {
        null
    }

    private val listeners = mutableListOf<CompassListener>()
    private val rotationMatrix = FloatArray(9)
    private var rotationVector: FloatArray? = null
    private var gravity: FloatArray? = null
    private var magneticField: FloatArray? = null
    private var lastHeading = 0f
    private var lastAccuracyStatus = SensorManager.SENSOR_STATUS_ACCURACY_HIGH
    private var nextUpdateAt = 0L

    override fun addCompassListener(listener: CompassListener) {
        if (listeners.isEmpty()) registerSensorListeners()
        listeners.add(listener)
    }

    override fun removeCompassListener(listener: CompassListener) {
        listeners.remove(listener)
        if (listeners.isEmpty()) unregisterSensorListeners()
    }

    override fun getLastHeading(): Float = lastHeading

    override fun getLastAccuracySensorStatus(): Int = lastAccuracyStatus

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_ROTATION_VECTOR -> rotationVector = event.values
            Sensor.TYPE_ACCELEROMETER -> gravity = lowPassFilter(event.values, gravity)
            Sensor.TYPE_MAGNETIC_FIELD -> magneticField = lowPassFilter(event.values, magneticField)
            else -> return
        }
        updateHeading()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        if (accuracy == lastAccuracyStatus) return
        lastAccuracyStatus = accuracy
        listeners.toList().forEach { it.onCompassAccuracyChange(accuracy) }
    }

    private fun updateHeading() {
        val now = SystemClock.elapsedRealtime()
        if (now < nextUpdateAt) return

        val vector = rotationVector
        if (vector != null) {
            // Some Samsung devices throw from getRotationMatrixFromVector on vectors longer than
            // four elements, and the leading four are all it needs.
            val truncated = if (vector.size > 4) vector.copyOf(4) else vector
            SensorManager.getRotationMatrixFromVector(rotationMatrix, truncated)
        } else {
            val gravityValues = gravity ?: return
            val magneticValues = magneticField ?: return
            if (!SensorManager.getRotationMatrix(rotationMatrix, null, gravityValues, magneticValues)) return
        }

        nextUpdateAt = now + UPDATE_RATE_MS
        lastHeading = screenHeadingDegrees(rotationMatrix, displayRotationDegrees()).toFloat()
        // Snapshot: reacting to a heading can change the camera mode, which adds or removes
        // compass listeners on this very list.
        listeners.toList().forEach { it.onCompassChanged(lastHeading) }
    }

    /** Surface.ROTATION_* as degrees; the display is only consulted once per pushed heading. */
    private fun displayRotationDegrees(): Int = when (display?.rotation) {
        Surface.ROTATION_90 -> 90
        Surface.ROTATION_180 -> 180
        Surface.ROTATION_270 -> 270
        else -> 0
    }

    private fun lowPassFilter(newValues: FloatArray, smoothed: FloatArray?): FloatArray {
        if (smoothed == null) return newValues.copyOf()
        for (i in newValues.indices) {
            smoothed[i] += ALPHA * (newValues[i] - smoothed[i])
        }
        return smoothed
    }

    private fun registerSensorListeners() {
        rotationVectorSensor?.let { sensorManager.registerListener(this, it, SENSOR_DELAY_MICROS) }
        accelerometerSensor?.let { sensorManager.registerListener(this, it, SENSOR_DELAY_MICROS) }
        magneticFieldSensor?.let { sensorManager.registerListener(this, it, SENSOR_DELAY_MICROS) }
    }

    private fun unregisterSensorListeners() {
        rotationVectorSensor?.let { sensorManager.unregisterListener(this, it) }
        accelerometerSensor?.let { sensorManager.unregisterListener(this, it) }
        magneticFieldSensor?.let { sensorManager.unregisterListener(this, it) }
    }
}
