package no.synth.where.location

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.sample
import no.synth.where.ui.map.CompassReading
import no.synth.where.util.Logger
import no.synth.where.util.keyUIWindow
import platform.CoreLocation.CLHeading
import platform.CoreLocation.CLLocationManager
import platform.CoreLocation.CLLocationManagerDelegateProtocol
import platform.CoreLocation.kCLLocationAccuracyKilometer
import platform.Foundation.NSError
import platform.UIKit.UIInterfaceOrientationLandscapeLeft
import platform.UIKit.UIInterfaceOrientationLandscapeRight
import platform.UIKit.UIInterfaceOrientationPortraitUpsideDown
import platform.darwin.NSObject
import kotlin.time.Duration.Companion.seconds

/**
 * Compass readings from CoreLocation, about once a second. Unlike maplibre-compose's
 * IosHeadingProvider it runs coarse location updates on the same manager, which trueHeading needs,
 * corrects for the interface orientation, and survives transient heading failures.
 */
@OptIn(FlowPreview::class)
fun iosCompassReadings(): Flow<CompassReading> = callbackFlow {
    if (!CLLocationManager.headingAvailable()) {
        close()
        return@callbackFlow
    }
    val manager = CLLocationManager()
    val delegate = CompassDelegate(channel)
    manager.delegate = delegate
    manager.desiredAccuracy = kCLLocationAccuracyKilometer
    manager.startUpdatingLocation()
    manager.startUpdatingHeading()
    awaitClose {
        manager.stopUpdatingHeading()
        manager.stopUpdatingLocation()
        manager.delegate = null
    }
}.flowOn(Dispatchers.Main).sample(1.seconds)

private class CompassDelegate(private val channel: SendChannel<CompassReading>) :
    NSObject(), CLLocationManagerDelegateProtocol {

    override fun locationManager(manager: CLLocationManager, didUpdateHeading: CLHeading) {
        if (didUpdateHeading.headingAccuracy < 0.0) return
        // Magnetic only until CoreLocation has a position to compute the declination from.
        val heading = didUpdateHeading.trueHeading.takeIf { it >= 0.0 } ?: didUpdateHeading.magneticHeading
        val bearing = (heading + interfaceRotationDegrees()).mod(360.0)
        if (bearing.isFinite()) channel.trySend(CompassReading(bearing, didUpdateHeading.headingAccuracy))
    }

    // Heading failures (e.g. magnetic interference) are transient; CoreLocation keeps trying.
    override fun locationManager(manager: CLLocationManager, didFailWithError: NSError) {
        Logger.w("Compass error: ${didFailWithError.localizedDescription}")
    }
}

/** Degrees to add so a reading follows the top of the screen rather than the top of the device. */
private fun interfaceRotationDegrees(): Int =
    when (keyUIWindow()?.windowScene?.interfaceOrientation) {
        UIInterfaceOrientationLandscapeRight -> 90
        UIInterfaceOrientationPortraitUpsideDown -> 180
        UIInterfaceOrientationLandscapeLeft -> 270
        else -> 0
    }
