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
        val escapedName = name.escapeXml()
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
    <name>$escapedName</name>
    <time>${Instant.fromEpochMilliseconds(startTime)}</time>
  </metadata>
  <trk>
    <name>$escapedName</name>
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
        fun fromFIT(data: ByteArray): Track? {
            val points = FitParser.parse(data)
            if (points.isEmpty()) return null
            return Track(
                name = "Imported Track",
                points = points,
                startTime = points.minOf { it.timestamp },
                endTime = points.maxOf { it.timestamp },
                isRecording = false
            )
        }

        fun fromBytes(data: ByteArray): Track? {
            if (data.isEmpty()) return null
            return if (FitParser.isFitFile(data)) {
                fromFIT(data)
            } else {
                val text = try { data.decodeToString() } catch (_: Exception) { return null }
                if (!text.contains("<gpx", ignoreCase = true)) return null
                fromGPX(text)
            }
        }

        // Ordered by priority: prefer tracks over routes over waypoints
        private val pointTagNames = listOf("trkpt", "rtept", "wpt")

        private fun parsePointsFromTag(gpxContent: String, lower: String, tagName: String, points: MutableList<TrackPoint>) {
            val tagLower = tagName.lowercase()
            var searchFrom = 0
            while (true) {
                val start = lower.indexOf("<$tagLower ", searchFrom)
                if (start < 0) break

                val closingTag = lower.indexOf("</$tagLower>", start)
                val selfClosing = lower.indexOf("/>", start)
                val nextOpen = lower.indexOf("<", start + 1)

                val (tag, nextSearchFrom) = when {
                    selfClosing >= 0 && (closingTag < 0 || selfClosing < closingTag) &&
                        (nextOpen < 0 || selfClosing <= nextOpen) -> {
                        gpxContent.substring(start, selfClosing + 2) to selfClosing + 2
                    }
                    closingTag >= 0 -> {
                        gpxContent.substring(start, closingTag) to closingTag + tagName.length + 3
                    }
                    else -> break
                }

                searchFrom = nextSearchFrom
                val tagLc = tag.lowercase()

                val lat = tagLc.substringAfter("lat=\"", "").substringBefore("\"", "").toDoubleOrNull()
                    ?: tagLc.substringAfter("lat='", "").substringBefore("'", "").toDoubleOrNull()
                    ?: continue
                val lon = tagLc.substringAfter("lon=\"", "").substringBefore("\"", "").toDoubleOrNull()
                    ?: tagLc.substringAfter("lon='", "").substringBefore("'", "").toDoubleOrNull()
                    ?: continue

                if (lat < -90 || lat > 90 || lon < -180 || lon > 180) continue

                val ele = tagLc.substringAfter("<ele>", "").substringBefore("</ele>", "").toDoubleOrNull()
                // Extract time from original-case tag — Instant.parse requires uppercase T and Z
                val timeStart = tagLc.indexOf("<time>")
                val timeEnd = tagLc.indexOf("</time>")
                val timeStr = if (timeStart >= 0 && timeEnd > timeStart) tag.substring(timeStart + 6, timeEnd) else ""
                val timestamp = try {
                    Instant.parse(timeStr).toEpochMilliseconds()
                } catch (_: Exception) {
                    0L
                }

                points.add(
                    TrackPoint(
                        latLng = LatLng(lat, lon),
                        timestamp = timestamp,
                        altitude = ele,
                        accuracy = null
                    )
                )
            }
        }

        private fun extractName(gpxContent: String): String {
            val lower = gpxContent.lowercase()
            // Try metadata name first, then trk name, then rte name, then first name
            for (parent in listOf("<metadata>", "<trk>", "<rte>")) {
                val parentStart = lower.indexOf(parent)
                if (parentStart < 0) continue
                val parentClose = lower.indexOf(parent.replace("<", "</"), parentStart)
                val section = if (parentClose >= 0) gpxContent.substring(parentStart, parentClose) else gpxContent.substring(parentStart)
                val name = section.substringAfter("<name>", "").substringBefore("</name>", "").trim()
                if (name.isNotEmpty()) return name.unescapeXml()
            }
            val fallback = gpxContent.substringAfter("<name>", "").substringBefore("</name>", "").trim()
            return if (fallback.isNotEmpty()) fallback.unescapeXml() else "Imported Track"
        }

        fun fromGPX(gpxContent: String): Track? {
            try {
                val trackName = extractName(gpxContent)
                val lower = gpxContent.lowercase()

                // Use first non-empty type by priority: trkpt > rtept > wpt
                var trackPoints = mutableListOf<TrackPoint>()
                for (tagName in pointTagNames) {
                    parsePointsFromTag(gpxContent, lower, tagName, trackPoints)
                    if (trackPoints.isNotEmpty()) break
                }

                if (trackPoints.isEmpty()) return null

                // Replace 0L fallback timestamps with the earliest real timestamp
                val realTimestamps = trackPoints.filter { it.timestamp > 0L }
                if (realTimestamps.isNotEmpty()) {
                    val fallback = realTimestamps.minOf { it.timestamp }
                    trackPoints = trackPoints.map {
                        if (it.timestamp == 0L) it.copy(timestamp = fallback) else it
                    }.toMutableList()
                } else {
                    val now = currentTimeMillis()
                    trackPoints = trackPoints.map { it.copy(timestamp = now) }.toMutableList()
                }

                val startTime = trackPoints.minOf { it.timestamp }
                val endTime = trackPoints.maxOf { it.timestamp }

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

private fun String.escapeXml(): String = this
    .replace("&", "&amp;")
    .replace("<", "&lt;")
    .replace(">", "&gt;")
    .replace("\"", "&quot;")
    .replace("'", "&apos;")

private fun String.unescapeXml(): String = this
    .replace("&lt;", "<")
    .replace("&gt;", ">")
    .replace("&quot;", "\"")
    .replace("&apos;", "'")
    .replace("&amp;", "&")

