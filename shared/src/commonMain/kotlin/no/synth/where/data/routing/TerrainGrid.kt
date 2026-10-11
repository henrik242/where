package no.synth.where.data.routing

import no.synth.where.data.geo.LatLng
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.sinh
import kotlin.math.tan

/** Feature bits per grid cell, set by the vector rasterizer. */
object CellFlags {
    const val WATER = 1
    const val STREAM = 2
    const val RIVER = 4
    const val PATH = 8
    const val BRIDGE = 16
    const val WETLAND = 32
    const val GLACIER = 64
}

/**
 * Elevation raster in Web Mercator pixel space at [zoom] (256 px tiles). Cell (0,0) is global
 * pixel ([originPx], [originPy]). Cells are treated as square with side [cellSizeMeters].
 */
class TerrainGrid(
    val width: Int,
    val height: Int,
    val zoom: Int,
    val originPx: Int,
    val originPy: Int,
    val cellSizeMeters: Double,
    val elevations: FloatArray,
    val flags: ByteArray = ByteArray(width * height),
) {
    init {
        require(elevations.size == width * height) { "elevations size mismatch" }
        require(flags.size == width * height) { "flags size mismatch" }
    }

    fun index(x: Int, y: Int) = y * width + x

    fun hasFlag(index: Int, flag: Int) = flags[index].toInt() and flag != 0

    /**
     * Whether entering [index] means wading. A mapped path over a stream almost always has an
     * untagged footbridge or culvert, so only rivers count there.
     */
    fun isWaterwayCrossing(index: Int): Boolean {
        if (hasFlag(index, CellFlags.BRIDGE)) return false
        if (hasFlag(index, CellFlags.RIVER)) return true
        return hasFlag(index, CellFlags.STREAM) && !hasFlag(index, CellFlags.PATH)
    }

    fun addFlag(index: Int, flag: Int) {
        flags[index] = (flags[index].toInt() or flag).toByte()
    }

    fun cellToLatLng(x: Int, y: Int): LatLng =
        Mercator.pixelToLatLng(originPx + x + 0.5, originPy + y + 0.5, zoom)

    /** Cell index for [latLng], or -1 when outside the grid. */
    fun latLngToIndex(latLng: LatLng): Int {
        val (px, py) = Mercator.latLngToPixel(latLng, zoom)
        val x = floor(px).toInt() - originPx
        val y = floor(py).toInt() - originPy
        if (x !in 0 until width || y !in 0 until height) return -1
        return index(x, y)
    }
}

object Mercator {
    const val TILE_SIZE = 256
    private const val EARTH_CIRCUMFERENCE = 40075016.686

    fun worldSize(zoom: Int) = TILE_SIZE.toDouble() * (1 shl zoom)

    fun latLngToPixel(latLng: LatLng, zoom: Int): Pair<Double, Double> {
        val size = worldSize(zoom)
        val latRad = latLng.latitude * PI / 180.0
        val x = (latLng.longitude + 180.0) / 360.0 * size
        val y = (1.0 - ln(tan(latRad) + 1.0 / cos(latRad)) / PI) / 2.0 * size
        return x to y
    }

    fun pixelToLatLng(px: Double, py: Double, zoom: Int): LatLng {
        val size = worldSize(zoom)
        val lng = px / size * 360.0 - 180.0
        val lat = atan(sinh(PI * (1.0 - 2.0 * py / size))) * 180.0 / PI
        return LatLng(lat, lng)
    }

    fun metersPerPixel(latitude: Double, zoom: Int) =
        EARTH_CIRCUMFERENCE * cos(latitude * PI / 180.0) / worldSize(zoom)
}
