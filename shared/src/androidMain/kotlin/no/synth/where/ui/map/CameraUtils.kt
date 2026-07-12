package no.synth.where.ui.map

import no.synth.where.data.geo.LatLngBounds
import no.synth.where.data.geo.toMapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng as MlLatLng
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.maps.MapLibreMap

/**
 * MapLibre's [org.maplibre.android.location.LocationComponent.isLocationComponentEnabled] getter
 * throws LocationComponentNotInitializedException until the component has been activated, which
 * only happens inside a style's onStyleLoaded callback (and only with location permission granted).
 * Effects here fire the moment the map exists -- before the first style load finishes -- so treat a
 * not-yet-activated component as "not enabled" instead of letting the getter throw. The exception
 * type is package-private in MapLibre and can't be named, so catch its RuntimeException supertype.
 */
val MapLibreMap.isLocationComponentEnabledSafe: Boolean
    get() = try {
        locationComponent.isLocationComponentEnabled
    } catch (_: RuntimeException) {
        false
    }

/**
 * Apply a [CameraFollowMode] to the location component. No-op until the component has a fix
 * (it is re-applied once enabled, and on every style reload). Set [snapZoom] only on the user's
 * mode change so follow zooms in from a far-out view; leave it false when merely restoring the
 * mode after a style reload, otherwise a reload would yank a deliberately zoomed-out view back in.
 */
fun MapLibreMap.applyFollowMode(mode: CameraFollowMode, snapZoom: Boolean = false) {
    if (!isLocationComponentEnabledSafe) return
    val lc = locationComponent
    lc.cameraMode = when (mode) {
        CameraFollowMode.OFF -> CameraMode.NONE
        CameraFollowMode.FOLLOW -> CameraMode.TRACKING
        CameraFollowMode.FOLLOW_HEADING -> CameraMode.TRACKING_COMPASS
    }
    if (snapZoom && mode != CameraFollowMode.OFF && cameraPosition.zoom < MapZoomLevels.FOLLOW_MIN) {
        lc.zoomWhileTracking(MapZoomLevels.FOLLOW_MIN)
    }
}

fun MapLibreMap.animateToBounds(
    bounds: LatLngBounds,
    paddingPx: Int = MapZoomLevels.DEFAULT_PADDING_PX,
    singlePointZoom: Double = MapZoomLevels.SINGLE_POINT,
    maxZoom: Double? = null,
) {
    if (bounds.isPoint) {
        animateCamera(
            CameraUpdateFactory.newLatLngZoom(
                MlLatLng(bounds.south, bounds.west),
                singlePointZoom
            )
        )
        return
    }
    val target = getCameraForLatLngBounds(
        bounds.toMapLibre(),
        intArrayOf(paddingPx, paddingPx, paddingPx, paddingPx)
    ) ?: return
    val clampedZoom = if (maxZoom != null) minOf(target.zoom, maxZoom) else target.zoom
    val clamped = CameraPosition.Builder(target).zoom(clampedZoom).build()
    animateCamera(CameraUpdateFactory.newCameraPosition(clamped))
}
