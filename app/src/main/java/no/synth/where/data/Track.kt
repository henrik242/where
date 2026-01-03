package no.synth.where.data

import org.maplibre.android.geometry.LatLng
import java.text.SimpleDateFormat
import java.util.*

data class TrackPoint(
    val latLng: LatLng,
    val timestamp: Long,
    val altitude: Double? = null,
    val accuracy: Float? = null
)

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
<gpx version="1.1" creator="Where - Norwegian Navigation App"
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
}

