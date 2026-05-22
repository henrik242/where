package no.synth.where.ui.map

import no.synth.where.data.geo.LatLngBounds
import no.synth.where.data.geo.toMapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng as MlLatLng
import org.maplibre.android.maps.MapLibreMap

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
