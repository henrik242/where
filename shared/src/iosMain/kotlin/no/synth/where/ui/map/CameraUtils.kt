package no.synth.where.ui.map

import no.synth.where.data.geo.LatLngBounds

fun MapViewProvider.animateToBounds(
    bounds: LatLngBounds,
    paddingPx: Int = MapZoomLevels.DEFAULT_PADDING_PX,
    singlePointZoom: Double = MapZoomLevels.SINGLE_POINT,
    maxZoom: Double? = null,
) {
    if (bounds.isPoint) {
        setCamera(latitude = bounds.south, longitude = bounds.west, zoom = singlePointZoom)
        return
    }
    if (maxZoom != null) {
        setCameraBounds(
            south = bounds.south,
            west = bounds.west,
            north = bounds.north,
            east = bounds.east,
            padding = paddingPx,
            maxZoom = maxZoom.toInt(),
        )
    } else {
        setCameraBounds(
            south = bounds.south,
            west = bounds.west,
            north = bounds.north,
            east = bounds.east,
            padding = paddingPx,
        )
    }
}
