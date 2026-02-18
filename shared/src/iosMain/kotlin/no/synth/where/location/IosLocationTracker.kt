package no.synth.where.location

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import no.synth.where.data.OnlineTrackingClient
import no.synth.where.data.TrackRepository
import no.synth.where.data.geo.LatLng
import no.synth.where.util.Logger
import platform.CoreLocation.CLLocation
import platform.CoreLocation.CLLocationManager
import platform.CoreLocation.CLLocationManagerDelegateProtocol
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedAlways
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedWhenInUse
import platform.CoreLocation.kCLLocationAccuracyBest
import platform.darwin.NSObject

@OptIn(ExperimentalForeignApi::class)
class IosLocationTracker(
    private val trackRepository: TrackRepository
) : NSObject(), CLLocationManagerDelegateProtocol {

    private val locationManager = CLLocationManager()
    private var _lastLocation: CLLocation? = null
    val lastLocation: CLLocation? get() = _lastLocation
    var onlineTrackingClient: OnlineTrackingClient? = null

    init {
        locationManager.delegate = this
        locationManager.desiredAccuracy = kCLLocationAccuracyBest
        locationManager.distanceFilter = 5.0
    }

    val hasPermission: Boolean
        get() {
            val status = CLLocationManager.authorizationStatus()
            return status == kCLAuthorizationStatusAuthorizedWhenInUse ||
                status == kCLAuthorizationStatusAuthorizedAlways
        }

    fun requestPermission() {
        locationManager.requestWhenInUseAuthorization()
    }

    fun startTracking() {
        locationManager.startUpdatingLocation()
    }

    fun stopTracking() {
        locationManager.stopUpdatingLocation()
    }

    @Suppress("CONFLICTING_OVERLOADS", "PARAMETER_NAME_CHANGED_ON_OVERRIDE")
    override fun locationManager(manager: CLLocationManager, didUpdateLocations: List<*>) {
        val location = didUpdateLocations.lastOrNull() as? CLLocation ?: return
        _lastLocation = location

        if (trackRepository.isRecording.value) {
            val altitude = if (location.verticalAccuracy >= 0) location.altitude else null
            val coordinate = location.coordinate.useContents {
                LatLng(latitude, longitude)
            }
            val accuracy = location.horizontalAccuracy.toFloat()
            trackRepository.addTrackPoint(
                latLng = coordinate,
                altitude = altitude,
                accuracy = accuracy
            )
            onlineTrackingClient?.sendPoint(coordinate, altitude, accuracy)
        }
    }

    @Suppress("CONFLICTING_OVERLOADS", "PARAMETER_NAME_CHANGED_ON_OVERRIDE")
    override fun locationManager(manager: CLLocationManager, didFailWithError: platform.Foundation.NSError) {
        Logger.e("Location error: ${didFailWithError.localizedDescription}")
    }
}
