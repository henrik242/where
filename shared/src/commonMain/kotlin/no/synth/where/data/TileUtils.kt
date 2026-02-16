package no.synth.where.data

import no.synth.where.data.geo.LatLngBounds

object TileUtils {
    fun estimateTileCount(bounds: LatLngBounds, minZoom: Int, maxZoom: Int): Int {
        var totalTiles = 0
        for (zoom in minZoom..maxZoom) {
            val tilesPerSide = 1 shl zoom
            val latSpan = bounds.latitudeSpan
            val lonSpan = bounds.longitudeSpan
            val tilesAtZoom =
                ((latSpan / 180.0) * (lonSpan / 360.0) * tilesPerSide * tilesPerSide).toInt()
            totalTiles += tilesAtZoom
        }
        return totalTiles.coerceAtLeast(1)
    }
}
