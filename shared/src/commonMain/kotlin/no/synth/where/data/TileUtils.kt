package no.synth.where.data

import no.synth.where.data.geo.LatLng
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

    const val TILE_SIZE = 256

    /** Web Mercator position in global pixels at [zoom], for [TILE_SIZE] px tiles. */
    fun latLngToWorldPixel(latLng: LatLng, zoom: Int): Pair<Double, Double> {
        val size = TILE_SIZE.toDouble() * (1 shl zoom)
        val latRad = latLng.latitude * PI / 180.0
        val x = (latLng.longitude + 180.0) / 360.0 * size
        val y = (1.0 - ln(tan(latRad) + 1.0 / cos(latRad)) / PI) / 2.0 * size
        return x to y
    }

    fun worldPixelToLatLng(px: Double, py: Double, zoom: Int): LatLng {
        val size = TILE_SIZE.toDouble() * (1 shl zoom)
        val lng = px / size * 360.0 - 180.0
        val lat = atan(sinh(PI * (1.0 - 2.0 * py / size))) * 180.0 / PI
        return LatLng(lat, lng)
    }

    fun latLngToTileCoord(lat: Double, lng: Double, zoom: Int, tileSize: Int = TILE_SIZE): TileCoord {
        val n = 1 shl zoom
        val (px, py) = latLngToWorldPixel(LatLng(lat, lng), zoom)
        val tx = px / TILE_SIZE
        val ty = py / TILE_SIZE
        val xTile = floor(tx).toInt().coerceIn(0, n - 1)
        val yTile = floor(ty).toInt().coerceIn(0, n - 1)
        val pixelX = ((tx - xTile) * tileSize).toInt().coerceIn(0, tileSize - 1)
        val pixelY = ((ty - yTile) * tileSize).toInt().coerceIn(0, tileSize - 1)
        return TileCoord(zoom, xTile, yTile, pixelX, pixelY)
    }

    fun tileToLatLng(z: Int, x: Int, y: Int): LatLng =
        worldPixelToLatLng(x.toDouble() * TILE_SIZE, y.toDouble() * TILE_SIZE, z)
}
