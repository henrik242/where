package no.synth.where.data

import kotlinx.serialization.Serializable
import no.synth.where.data.serialization.LatLngSerializer
import no.synth.where.data.geo.LatLng
import no.synth.where.util.Logger
import no.synth.where.util.currentTimeMillis
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@Serializable
data class TrackPoint(
    @Serializable(with = LatLngSerializer::class)
    val latLng: LatLng,
    val timestamp: Long,
    val altitude: Double? = null,
    val accuracy: Float? = null
)

@Serializable
data class Track @OptIn(ExperimentalUuidApi::class) constructor(
    val id: String = Uuid.random().toString(),
    val name: String,
    val points: List<TrackPoint>,
    val startTime: Long,
    val endTime: Long? = null,
    val isRecording: Boolean = false
) {
    fun toGPX(): String {
        val trackPointsXml = points.joinToString("\n") { point ->
            val timestamp = Instant.fromEpochMilliseconds(point.timestamp).toString()
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
    <time>${Instant.fromEpochMilliseconds(startTime)}</time>
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
        return (endTime ?: currentTimeMillis()) - startTime
    }

    companion object {
        fun fromGPX(gpxContent: String): Track? {
            try {
                val trackName = gpxContent
                    .substringAfter("<name>", "")
                    .substringBefore("</name>", "Imported Track")
                    .trim()

                val trackPoints = mutableListOf<TrackPoint>()

                var searchFrom = 0
                while (true) {
                    val trkptStart = gpxContent.indexOf("<trkpt ", searchFrom)
                    if (trkptStart < 0) break
                    val trkptEnd = gpxContent.indexOf("</trkpt>", trkptStart)
                    if (trkptEnd < 0) break
                    searchFrom = trkptEnd + 8

                    val tag = gpxContent.substring(trkptStart, trkptEnd)
                    val lat = tag.substringAfter("lat=\"", "").substringBefore("\"", "").toDoubleOrNull() ?: continue
                    val lon = tag.substringAfter("lon=\"", "").substringBefore("\"", "").toDoubleOrNull() ?: continue

                    val ele = tag.substringAfter("<ele>", "").substringBefore("</ele>", "").toDoubleOrNull()
                    val timeStr = tag.substringAfter("<time>", "").substringBefore("</time>", "")
                    val timestamp = try {
                        Instant.parse(timeStr).toEpochMilliseconds()
                    } catch (_: Exception) {
                        currentTimeMillis()
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

                val startTime = trackPoints.minOfOrNull { it.timestamp } ?: currentTimeMillis()
                val endTime = trackPoints.maxOfOrNull { it.timestamp } ?: currentTimeMillis()

                return Track(
                    name = trackName,
                    points = trackPoints,
                    startTime = startTime,
                    endTime = endTime,
                    isRecording = false
                )
            } catch (e: Exception) {
                Logger.e(e, "Error parsing GPX")
                return null
            }
        }
    }
}

