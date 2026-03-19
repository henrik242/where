package no.synth.where.data

import no.synth.where.data.geo.LatLngBounds
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.sinh
import kotlin.math.tan

data class TileCoord(val z: Int, val x: Int, val y: Int, val pixelX: Int, val pixelY: Int)

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

    fun latLngToTileCoord(lat: Double, lng: Double, zoom: Int, tileSize: Int = 256): TileCoord {
        val n = 1 shl zoom
        val xTile = floor((lng + 180.0) / 360.0 * n).toInt().coerceIn(0, n - 1)
        val latRad = lat * PI / 180.0
        val yTile = floor((1.0 - ln(tan(latRad) + 1.0 / cos(latRad)) / PI) / 2.0 * n).toInt().coerceIn(0, n - 1)

        val xFraction = ((lng + 180.0) / 360.0 * n) - xTile
        val yFraction = ((1.0 - ln(tan(latRad) + 1.0 / cos(latRad)) / PI) / 2.0 * n) - yTile
        val pixelX = (xFraction * tileSize).toInt().coerceIn(0, tileSize - 1)
        val pixelY = (yFraction * tileSize).toInt().coerceIn(0, tileSize - 1)

        return TileCoord(zoom, xTile, yTile, pixelX, pixelY)
    }

    fun tileToLatLng(z: Int, x: Int, y: Int): Pair<Double, Double> {
        val n = 1 shl z
        val lng = x.toDouble() / n * 360.0 - 180.0
        val latRad = atan(sinh(PI * (1 - 2.0 * y / n)))
        val lat = latRad * 180.0 / PI
        return Pair(lat, lng)
    }
}
