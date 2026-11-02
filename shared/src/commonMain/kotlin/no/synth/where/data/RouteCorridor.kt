package no.synth.where.data

import no.synth.where.data.geo.LatLng
import no.synth.where.data.geo.LatLngBounds
import no.synth.where.data.geo.bounds
import no.synth.where.data.routing.CliffMap
import no.synth.where.ui.map.MapLayer
import no.synth.where.util.currentTimeMillis
import org.maplibre.spatialk.geojson.MultiPolygon
import org.maplibre.spatialk.geojson.Position
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sqrt

/** Offline map downloads along a route: a strip either side of it, down to small terrain detail. */
object RouteCorridor {
    private const val HALF_WIDTH_METERS = 150.0
    /** Contours and MapAnt's cliff marks are readable from here. */
    const val MAX_ZOOM = 16
    private const val MIN_ZOOM = 8
    /** Corner-cutting between kept points stays far inside [HALF_WIDTH_METERS]. */
    private const val POINT_SPACING_METERS = 100.0
    private const val METERS_PER_DEGREE = 111_320.0

    /**
     * Downloads along [points] for a route shown on [layerId]: that layer, plus MapAnt in Norway,
     * which is the one map that marks small cliffs, and the hillshade there when [withHillshade].
     */
    fun downloads(points: List<LatLng>, routeName: String, layerId: String, withHillshade: Boolean = false): List<QueuedDownload> {
        val inNorway = points.any(CliffMap::covers)
        val mapAnt = MapLayer.MAPANT.downloadId.takeIf { layerId != it && inNorway }
        val hillshade = DownloadLayers.HILLSHADE_ID.takeIf { withHillshade && inNorway }
        return listOfNotNull(layerId, mapAnt, hillshade).mapNotNull { download(points, routeName, it) }
    }

    /** A download of [layerId] along [points], or null for an unknown layer or a route that never moves. */
    fun download(points: List<LatLng>, routeName: String, layerId: String): QueuedDownload? {
        val layer = DownloadLayers.all.find { it.id == layerId } ?: return null
        fun moves(line: List<LatLng>) = line.size >= 2 && line.any { it != line.first() }
        // A short loop can simplify to its start alone; keep it whole then. One that never moves is no route.
        val line = simplify(points).takeIf(::moves) ?: points.takeIf(::moves) ?: return null
        val bounds = line.bounds()?.let(::pad) ?: return null
        return QueuedDownload(
            region = Region("route_${currentTimeMillis()}", bounds),
            layerId = layer.id,
            layerDisplayName = layer.displayName,
            label = routeName,
            minZoom = MIN_ZOOM,
            maxZoom = MAX_ZOOM,
            downloadDem = false,
            corridor = line,
        )
    }

    /** One rectangle per segment, stretched [HALF_WIDTH_METERS] past both ends so bends leave no gaps. */
    fun shape(line: List<LatLng>): MultiPolygon {
        val w = HALF_WIDTH_METERS
        val rects = line.zipWithNext { a, b ->
            val cosLat = cos((a.latitude + b.latitude) / 2 * PI / 180)
            // Local meters: x east, y north.
            val dx = (b.longitude - a.longitude) * METERS_PER_DEGREE * cosLat
            val dy = (b.latitude - a.latitude) * METERS_PER_DEGREE
            val len = sqrt(dx * dx + dy * dy)
            // A zero-length segment, e.g. a short loop simplified to its start, becomes a square.
            val ux = if (len > 0) dx / len else 1.0
            val uy = if (len > 0) dy / len else 0.0
            fun at(p: LatLng, along: Double, side: Double) = Position(
                longitude = p.longitude + (ux * along - uy * side) / (METERS_PER_DEGREE * cosLat),
                latitude = p.latitude + (uy * along + ux * side) / METERS_PER_DEGREE,
            )
            val ring = listOf(at(a, -w, -w), at(b, w, -w), at(b, w, w), at(a, -w, w))
            listOf(ring + listOf(ring.first()))
        }
        return MultiPolygon(coordinates = rects)
    }

    private fun simplify(points: List<LatLng>): List<LatLng> {
        if (points.size < 2) return points
        val out = mutableListOf(points.first())
        for (p in points.subList(1, points.size - 1)) {
            if (out.last().distanceTo(p) >= POINT_SPACING_METERS) out += p
        }
        out += points.last()
        return out
    }

    private fun pad(b: LatLngBounds): LatLngBounds {
        val dLat = HALF_WIDTH_METERS / METERS_PER_DEGREE
        val dLon = dLat / cos((b.north + b.south) / 2 * PI / 180)
        return LatLngBounds(south = b.south - dLat, west = b.west - dLon, north = b.north + dLat, east = b.east + dLon)
    }
}
