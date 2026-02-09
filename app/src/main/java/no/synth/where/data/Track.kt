package no.synth.where.data

import kotlinx.serialization.Serializable
import no.synth.where.data.serialization.LatLngSerializer
import org.maplibre.android.geometry.LatLng
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.*

@Serializable
data class TrackPoint(
    @Serializable(with = LatLngSerializer::class)
    val latLng: LatLng,
    val timestamp: Long,
    val altitude: Double? = null,
    val accuracy: Float? = null
)

@Serializable
data class Track(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val points: List<TrackPoint>,
    val startTime: Long,
    val endTime: Long? = null,
    val isRecording: Boolean = false
) {
    fun toGPX(): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }

        val trackPointsXml = points.joinToString("\n") { point ->
            val timestamp = dateFormat.format(Date(point.timestamp))
            val elevation = point.altitude?.let { "\n        <ele>$it</ele>" } ?: ""
            """      <trkpt lat="${point.latLng.latitude}" lon="${point.latLng.longitude}">$elevation
        <time>$timestamp</time>
      </trkpt>"""
        }

        return """<?xml version="1.0" encoding="UTF-8"?>
<gpx version="1.1" creator="Where - Norwegian Maps"
  xmlns="http://www.topografix.com/GPX/1/1"
  xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
  xsi:schemaLocation="http://www.topografix.com/GPX/1/1 http://www.topografix.com/GPX/1/1/gpx.xsd">
  <metadata>
    <name>$name</name>
    <time>${dateFormat.format(Date(startTime))}</time>
  </metadata>
  <trk>
    <name>$name</name>
    <trkseg>
$trackPointsXml
    </trkseg>
  </trk>
</gpx>"""
    }

    fun getDistanceMeters(): Double {
        if (points.size < 2) return 0.0
        var distance = 0.0
        for (i in 1 until points.size) {
            distance += points[i - 1].latLng.distanceTo(points[i].latLng)
        }
        return distance
    }

    fun getDurationMillis(): Long {
        return (endTime ?: System.currentTimeMillis()) - startTime
    }

    companion object {
        fun fromGPX(gpxContent: String): Track? {
            try {
                val trackName = gpxContent
                    .substringAfter("<name>", "")
                    .substringBefore("</name>", "Imported Track")
                    .trim()

                val trackPoints = mutableListOf<TrackPoint>()
                val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }

                val trkptPattern = Regex("""<trkpt lat="([^"]+)" lon="([^"]+)">(.*?)</trkpt>""", RegexOption.DOT_MATCHES_ALL)
                trkptPattern.findAll(gpxContent).forEach { match ->
                    val lat = match.groupValues[1].toDoubleOrNull() ?: return@forEach
                    val lon = match.groupValues[2].toDoubleOrNull() ?: return@forEach
                    val content = match.groupValues[3]

                    val ele = content.substringAfter("<ele>", "").substringBefore("</ele>", "").toDoubleOrNull()
                    val timeStr = content.substringAfter("<time>", "").substringBefore("</time>", "")
                    val timestamp = try {
                        dateFormat.parse(timeStr)?.time ?: System.currentTimeMillis()
                    } catch (_: Exception) {
                        System.currentTimeMillis()
                    }

                    trackPoints.add(
                        TrackPoint(
                            latLng = LatLng(lat, lon),
                            timestamp = timestamp,
                            altitude = ele,
                            accuracy = null
                        )
                    )
                }

                if (trackPoints.isEmpty()) return null

                val startTime = trackPoints.minOfOrNull { it.timestamp } ?: System.currentTimeMillis()
                val endTime = trackPoints.maxOfOrNull { it.timestamp } ?: System.currentTimeMillis()

                return Track(
                    name = trackName,
                    points = trackPoints,
                    startTime = startTime,
                    endTime = endTime,
                    isRecording = false
                )
            } catch (e: Exception) {
                Timber.e(e, "Error parsing GPX")
                return null
            }
        }
    }
}

