package no.synth.where.data

import no.synth.where.data.geo.LatLng
import no.synth.where.util.Logger

/** A `<wpt>` read out of a GPX file, before it is given an id and stored as a [SavedPoint]. */
data class ParsedWaypoint(
    val name: String,
    val latLng: LatLng,
    val description: String,
    val timestamp: Long?
)

/**
 * Reads `<wpt>` elements as standalone points. `<trkpt>`/`<rtept>` are deliberately ignored: they
 * are a track's shape, and importing them as points would spray hundreds of markers on the map.
 */
object GpxWaypoints {
    /** Name given to a waypoint that carries none of its own, and whose file has no name either. */
    const val DEFAULT_NAME = "Imported Point"

    fun parse(gpxContent: String): List<ParsedWaypoint> = try {
        val lower = gpxContent.lowercase()
        val documentName = gpxDocumentName(gpxContent, lower)
        buildList {
            forEachGpxElement(gpxContent, lower, "wpt") { element ->
                val lat = element.gpxAttr("lat")?.toDoubleOrNull() ?: return@forEachGpxElement
                val lon = element.gpxAttr("lon")?.toDoubleOrNull() ?: return@forEachGpxElement
                if (lat < -90 || lat > 90 || lon < -180 || lon > 180) return@forEachGpxElement

                add(
                    ParsedWaypoint(
                        name = element.gpxChild("name")?.trim()?.unescapeXml()?.ifEmpty { null }
                            ?: documentName
                            ?: DEFAULT_NAME,
                        latLng = LatLng(lat, lon),
                        // <cmt> is what some exporters use for the note <desc> normally holds.
                        description = (element.gpxChild("desc") ?: element.gpxChild("cmt"))
                            ?.trim()?.unescapeXml().orEmpty(),
                        timestamp = element.gpxTimeMillis()
                    )
                )
            }
        }
    } catch (e: Exception) {
        Logger.e(e, "Error parsing GPX waypoints")
        emptyList()
    }

    /** Waypoints in [data] when it is GPX; empty for anything else (FIT carries no waypoints). */
    fun parse(data: ByteArray): List<ParsedWaypoint> =
        gpxTextOrNull(data)?.let { parse(it) } ?: emptyList()
}
