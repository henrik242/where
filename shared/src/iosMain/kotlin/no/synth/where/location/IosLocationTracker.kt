package no.synth.where.location

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCSignatureOverride
import kotlinx.cinterop.useContents
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import no.synth.where.data.OnlineTrackingCoordinator
import no.synth.where.data.TrackRepository
import no.synth.where.data.geo.LatLng
import no.synth.where.util.Logger
import platform.CoreLocation.CLLocation
import platform.CoreLocation.CLLocationManager
import platform.CoreLocation.CLLocationManagerDelegateProtocol
import platform.CoreLocation.CLActivityTypeFitness
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedAlways
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedWhenInUse
import platform.CoreLocation.kCLLocationAccuracyBest
import platform.darwin.NSObject

@OptIn(ExperimentalForeignApi::class)
class IosLocationTracker(
    private val trackRepository: TrackRepository,
    private val coordinator: OnlineTrackingCoordinator,
) : NSObject(), CLLocationManagerDelegateProtocol {

    private val locationManager = CLLocationManager()
    private var _lastLocation: CLLocation? = null
    val lastLocation: CLLocation? get() = _lastLocation

    private var keepAliveActive = false
    private var trackingActive = false

    init {
        locationManager.delegate = this
        locationManager.desiredAccuracy = kCLLocationAccuracyBest
        locationManager.distanceFilter = 1.0
        locationManager.pausesLocationUpdatesAutomatically = false
        locationManager.activityType = CLActivityTypeFitness
        applyBackgroundUpdatesFlag()
    }

    // Only while actually tracking: the tracker is a process-wide singleton, so a flag left on
    // after a share ends would keep CoreLocation running in the background for the app's lifetime.
    private fun applyBackgroundUpdatesFlag() {
        locationManager.allowsBackgroundLocationUpdates = trackingActive && hasAlwaysPermission
    }

    val hasPermission: Boolean
        get() {
            val status = CLLocationManager.authorizationStatus()
            return status == kCLAuthorizationStatusAuthorizedWhenInUse ||
                status == kCLAuthorizationStatusAuthorizedAlways
        }

    val hasAlwaysPermission: Boolean
        get() = CLLocationManager.authorizationStatus() == kCLAuthorizationStatusAuthorizedAlways

    private val _alwaysPermissionGranted = MutableStateFlow(hasAlwaysPermission)

    /**
     * Observable mirror of [hasAlwaysPermission] — without it a live share keeps sending only
     * while the app is in the foreground, which the tracking screen warns about.
     */
    val alwaysPermissionGranted: StateFlow<Boolean> = _alwaysPermissionGranted.asStateFlow()

    fun requestPermission() {
        locationManager.requestWhenInUseAuthorization()
    }

    fun requestAlwaysPermission() {
        locationManager.requestAlwaysAuthorization()
    }

    fun startTracking() {
        trackingActive = true
        applyBackgroundUpdatesFlag()
        locationManager.startUpdatingLocation()
    }

    fun stopTracking() {
        trackingActive = false
        applyBackgroundUpdatesFlag()
        if (!keepAliveActive) locationManager.stopUpdatingLocation()
    }

    /**
     * Hold the GNSS chip warm while the map is visible. CoreLocation otherwise
     * de-prioritises updates outside an active recording session, producing the
     * same "stale dot in forest" symptom as Android's fused provider.
     */
    fun startKeepAlive() {
        keepAliveActive = true
        locationManager.startUpdatingLocation()
    }

    fun stopKeepAlive() {
        keepAliveActive = false
        if (!trackingActive) locationManager.stopUpdatingLocation()
    }

    @ObjCSignatureOverride
    override fun locationManager(manager: CLLocationManager, didUpdateLocations: List<*>) {
        val location = didUpdateLocations.lastOrNull() as? CLLocation ?: return
        if (location.sourceInformation?.isSimulatedBySoftware() == true) return
        if (location.horizontalAccuracy < 0 ||
            location.horizontalAccuracy > MAX_ACCEPTABLE_ACCURACY_M
        ) return
        _lastLocation = location

        val altitude = if (location.verticalAccuracy >= 0) location.altitude else null
        val coordinate = location.coordinate.useContents {
            LatLng(latitude, longitude)
        }
        val accuracy = location.horizontalAccuracy.toFloat()

        if (trackRepository.isRecording.value) {
            trackRepository.addTrackPoint(
                latLng = coordinate,
                altitude = altitude,
                accuracy = accuracy
            )
        }
        coordinator.sendPoint(coordinate, altitude, accuracy)
    }

    @ObjCSignatureOverride
    override fun locationManager(manager: CLLocationManager, didFailWithError: platform.Foundation.NSError) {
        Logger.e("Location error: ${didFailWithError.localizedDescription}")
    }

    override fun locationManagerDidChangeAuthorization(manager: CLLocationManager) {
        _alwaysPermissionGranted.value = hasAlwaysPermission
        applyBackgroundUpdatesFlag()
        if (hasPermission && (keepAliveActive || trackingActive)) {
            locationManager.startUpdatingLocation()
        }
    }
}

private const val MAX_ACCEPTABLE_ACCURACY_M = 50.0
