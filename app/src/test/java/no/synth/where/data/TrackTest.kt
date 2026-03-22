package no.synth.where.data

import org.junit.Test
import org.junit.Assert.*
import no.synth.where.data.geo.LatLng

class TrackTest {

    @Test
    fun toGPX_containsTrackName() {
        val track = Track(
            name = "Morning Run",
            points = listOf(
                TrackPoint(latLng = LatLng(59.9, 10.7), timestamp = 1000L)
            ),
            startTime = 1000L,
            endTime = 2000L
        )
        val gpx = track.toGPX()
        assertTrue(gpx.contains("<name>Morning Run</name>"))
    }

    @Test
    fun toGPX_containsTrackPoints() {
        val track = Track(
            name = "Test",
            points = listOf(
                TrackPoint(latLng = LatLng(59.9139, 10.7522), timestamp = 1000L),
                TrackPoint(latLng = LatLng(60.3913, 5.3221), timestamp = 2000L)
            ),
            startTime = 1000L,
            endTime = 2000L
        )
        val gpx = track.toGPX()
        assertTrue(gpx.contains("lat=\"59.9139\""))
        assertTrue(gpx.contains("lon=\"10.7522\""))
        assertTrue(gpx.contains("lat=\"60.3913\""))
        assertTrue(gpx.contains("lon=\"5.3221\""))
    }

    @Test
    fun toGPX_containsElevation_whenPresent() {
        val track = Track(
            name = "Test",
            points = listOf(
                TrackPoint(latLng = LatLng(59.9, 10.7), timestamp = 1000L, altitude = 150.5)
            ),
            startTime = 1000L
        )
        val gpx = track.toGPX()
        assertTrue(gpx.contains("<ele>150.5</ele>"))
    }

    @Test
    fun toGPX_omitsElevation_whenNull() {
        val track = Track(
            name = "Test",
            points = listOf(
                TrackPoint(latLng = LatLng(59.9, 10.7), timestamp = 1000L, altitude = null)
            ),
            startTime = 1000L
        )
        val gpx = track.toGPX()
        assertFalse(gpx.contains("<ele>"))
    }

    @Test
    fun toGPX_isValidXml() {
        val track = Track(
            name = "Test",
            points = listOf(
                TrackPoint(latLng = LatLng(59.9, 10.7), timestamp = 1000L)
            ),
            startTime = 1000L
        )
        val gpx = track.toGPX()
        assertTrue(gpx.startsWith("<?xml"))
        assertTrue(gpx.contains("<gpx"))
        assertTrue(gpx.contains("</gpx>"))
        assertTrue(gpx.contains("<trk>"))
        assertTrue(gpx.contains("</trk>"))
    }

    @Test
    fun fromGPX_parsesTrackName() {
        val gpx = """<?xml version="1.0" encoding="UTF-8"?>
            <gpx version="1.1"><trk><name>Hike</name><trkseg>
            <trkpt lat="59.9" lon="10.7"><time>2025-01-01T12:00:00Z</time></trkpt>
            </trkseg></trk></gpx>"""

        val track = Track.fromGPX(gpx)
        assertNotNull(track)
        assertEquals("Hike", requireNotNull(track).name)
    }

    @Test
    fun fromGPX_parsesCoordinates() {
        val gpx = """<?xml version="1.0"?>
            <gpx><trk><name>Test</name><trkseg>
            <trkpt lat="63.4305" lon="10.3951"><time>2025-01-01T12:00:00Z</time></trkpt>
            <trkpt lat="63.4310" lon="10.3960"><time>2025-01-01T12:01:00Z</time></trkpt>
            </trkseg></trk></gpx>"""

        val track = requireNotNull(Track.fromGPX(gpx))
        assertEquals(2, track.points.size)
        assertEquals(63.4305, track.points[0].latLng.latitude, 0.0001)
        assertEquals(10.3951, track.points[0].latLng.longitude, 0.0001)
    }

    @Test
    fun fromGPX_parsesElevation() {
        val gpx = """<?xml version="1.0"?>
            <gpx><trk><name>Test</name><trkseg>
            <trkpt lat="59.9" lon="10.7"><ele>100.5</ele><time>2025-01-01T12:00:00Z</time></trkpt>
            </trkseg></trk></gpx>"""

        val track = requireNotNull(Track.fromGPX(gpx))
        assertEquals(100.5, requireNotNull(track.points[0].altitude), 0.01)
    }

    @Test
    fun fromGPX_returnsNull_forEmptyTrackpoints() {
        val gpx = """<?xml version="1.0"?>
            <gpx><trk><name>Empty</name><trkseg></trkseg></trk></gpx>"""

        assertNull(Track.fromGPX(gpx))
    }

    @Test
    fun fromGPX_returnsNull_forInvalidInput() {
        assertNull(Track.fromGPX("not xml at all"))
    }

    @Test
    fun toGPX_fromGPX_roundTrip() {
        val original = Track(
            name = "Round Trip",
            points = listOf(
                TrackPoint(latLng = LatLng(59.9139, 10.7522), timestamp = 1704110400000L, altitude = 50.0),
                TrackPoint(latLng = LatLng(59.9200, 10.7600), timestamp = 1704110460000L, altitude = 55.0)
            ),
            startTime = 1704110400000L,
            endTime = 1704110460000L
        )

        val gpx = original.toGPX()
        val parsed = requireNotNull(Track.fromGPX(gpx))

        assertEquals(original.name, parsed.name)
        assertEquals(original.points.size, parsed.points.size)
        assertEquals(original.points[0].latLng.latitude, parsed.points[0].latLng.latitude, 0.0001)
        assertEquals(original.points[0].latLng.longitude, parsed.points[0].latLng.longitude, 0.0001)
        assertEquals(requireNotNull(original.points[0].altitude), requireNotNull(parsed.points[0].altitude), 0.01)
    }

    @Test
    fun getDistanceMeters_returnsZero_forSinglePoint() {
        val track = Track(
            name = "Single",
            points = listOf(
                TrackPoint(latLng = LatLng(59.9, 10.7), timestamp = 1000L)
            ),
            startTime = 1000L
        )
        assertEquals(0.0, track.getDistanceMeters(), 0.01)
    }

    @Test
    fun getDistanceMeters_returnsZero_forEmptyTrack() {
        val track = Track(name = "Empty", points = emptyList(), startTime = 1000L)
        assertEquals(0.0, track.getDistanceMeters(), 0.01)
    }

    @Test
    fun getDistanceMeters_calculatesDistance_forTwoPoints() {
        // Oslo to Trondheim ~= 390 km
        val track = Track(
            name = "Oslo-Trondheim",
            points = listOf(
                TrackPoint(latLng = LatLng(59.9139, 10.7522), timestamp = 1000L),
                TrackPoint(latLng = LatLng(63.4305, 10.3951), timestamp = 2000L)
            ),
            startTime = 1000L,
            endTime = 2000L
        )
        val distance = track.getDistanceMeters()
        assertTrue("Distance should be roughly 390km", distance > 380_000 && distance < 400_000)
    }

    @Test
    fun fromGPX_parsesFractionalSeconds() {
        val gpx = """<?xml version="1.0"?>
            <gpx><trk><name>Test</name><trkseg>
            <trkpt lat="59.9" lon="10.7"><time>2025-01-01T12:00:00.500Z</time></trkpt>
            </trkseg></trk></gpx>"""

        val track = requireNotNull(Track.fromGPX(gpx))
        assertEquals(1735732800500L, track.points[0].timestamp)
    }

    @Test
    fun fromGPX_parsesTimezoneOffset() {
        val gpx = """<?xml version="1.0"?>
            <gpx><trk><name>Test</name><trkseg>
            <trkpt lat="59.9" lon="10.7"><time>2025-01-01T13:00:00+01:00</time></trkpt>
            </trkseg></trk></gpx>"""

        val track = requireNotNull(Track.fromGPX(gpx))
        assertEquals(1735732800000L, track.points[0].timestamp)
    }

    @Test
    fun toGPX_fromGPX_roundTrip_withFractionalSeconds() {
        val original = Track(
            name = "Fractional",
            points = listOf(
                TrackPoint(latLng = LatLng(59.9, 10.7), timestamp = 1704110400123L)
            ),
            startTime = 1704110400123L,
            endTime = 1704110400123L
        )

        val gpx = original.toGPX()
        val parsed = requireNotNull(Track.fromGPX(gpx))

        assertEquals(original.points[0].timestamp, parsed.points[0].timestamp)
    }

    @Test
    fun getDurationMillis_calculatesCorrectly() {
        val track = Track(
            name = "Test",
            points = emptyList(),
            startTime = 1000L,
            endTime = 61000L
        )
        assertEquals(60000L, track.getDurationMillis())
    }

    @Test
    fun fromGPX_parsesWaypoints() {
        val gpx = """<?xml version="1.0"?>
            <gpx version="1.1">
            <wpt lat="59.9139" lon="10.7522"><ele>50.0</ele><time>2025-01-01T12:00:00Z</time></wpt>
            <wpt lat="60.3913" lon="5.3221"><time>2025-01-01T13:00:00Z</time></wpt>
            </gpx>"""

        val track = requireNotNull(Track.fromGPX(gpx))
        assertEquals(2, track.points.size)
        assertEquals(59.9139, track.points[0].latLng.latitude, 0.0001)
        assertEquals(50.0, requireNotNull(track.points[0].altitude), 0.01)
    }

    @Test
    fun fromGPX_parsesRoutePoints() {
        val gpx = """<?xml version="1.0"?>
            <gpx version="1.1">
            <rte><name>My Route</name>
            <rtept lat="59.9139" lon="10.7522"><time>2025-01-01T12:00:00Z</time></rtept>
            <rtept lat="60.3913" lon="5.3221"><time>2025-01-01T13:00:00Z</time></rtept>
            </rte></gpx>"""

        val track = requireNotNull(Track.fromGPX(gpx))
        assertEquals("My Route", track.name)
        assertEquals(2, track.points.size)
        assertEquals(60.3913, track.points[1].latLng.latitude, 0.0001)
    }

    @Test
    fun fromGPX_parsesSelfClosingPoints() {
        val gpx = """<?xml version="1.0"?>
            <gpx version="1.1">
            <wpt lat="59.9139" lon="10.7522"/>
            <wpt lat="60.3913" lon="5.3221" />
            </gpx>"""

        val track = requireNotNull(Track.fromGPX(gpx))
        assertEquals(2, track.points.size)
        assertEquals(59.9139, track.points[0].latLng.latitude, 0.0001)
        assertEquals(60.3913, track.points[1].latLng.latitude, 0.0001)
    }

    @Test
    fun fromGPX_prefersTrkptOverOtherTypes() {
        val gpx = """<?xml version="1.0"?>
            <gpx version="1.1">
            <wpt lat="59.0" lon="10.0"><time>2025-01-01T12:00:00Z</time></wpt>
            <trk><trkseg>
            <trkpt lat="60.0" lon="11.0"><time>2025-01-01T13:00:00Z</time></trkpt>
            </trkseg></trk>
            <rte><rtept lat="61.0" lon="12.0"><time>2025-01-01T14:00:00Z</time></rtept></rte>
            </gpx>"""

        val track = requireNotNull(Track.fromGPX(gpx))
        assertEquals(1, track.points.size)
        assertEquals(60.0, track.points[0].latLng.latitude, 0.0001)
    }

    @Test
    fun fromGPX_fallsBackToRteptWhenNoTrkpt() {
        val gpx = """<?xml version="1.0"?>
            <gpx version="1.1">
            <wpt lat="59.0" lon="10.0"><time>2025-01-01T12:00:00Z</time></wpt>
            <rte><rtept lat="61.0" lon="12.0"><time>2025-01-01T14:00:00Z</time></rtept></rte>
            </gpx>"""

        val track = requireNotNull(Track.fromGPX(gpx))
        assertEquals(1, track.points.size)
        assertEquals(61.0, track.points[0].latLng.latitude, 0.0001)
    }

    @Test
    fun fromGPX_parsesMultipleTrackSegments() {
        val gpx = """<?xml version="1.0"?>
            <gpx version="1.1"><trk><name>Multi Seg</name>
            <trkseg>
            <trkpt lat="59.0" lon="10.0"><time>2025-01-01T12:00:00Z</time></trkpt>
            </trkseg>
            <trkseg>
            <trkpt lat="60.0" lon="11.0"><time>2025-01-01T13:00:00Z</time></trkpt>
            </trkseg>
            </trk></gpx>"""

        val track = requireNotNull(Track.fromGPX(gpx))
        assertEquals(2, track.points.size)
    }

    @Test
    fun fromGPX_handlesPointsWithNoTime() {
        val gpx = """<?xml version="1.0"?>
            <gpx version="1.1">
            <wpt lat="59.9" lon="10.7"><name>Campsite</name></wpt>
            </gpx>"""

        val track = requireNotNull(Track.fromGPX(gpx))
        assertEquals(1, track.points.size)
        assertEquals(59.9, track.points[0].latLng.latitude, 0.0001)
    }

    @Test
    fun fromGPX_prefersMetadataName() {
        val gpx = """<?xml version="1.0"?>
            <gpx version="1.1">
            <metadata><name>My Activity</name></metadata>
            <trk><name>Track 1</name><trkseg>
            <trkpt lat="59.9" lon="10.7"><time>2025-01-01T12:00:00Z</time></trkpt>
            </trkseg></trk></gpx>"""

        val track = requireNotNull(Track.fromGPX(gpx))
        assertEquals("My Activity", track.name)
    }

    @Test
    fun fromGPX_prefersTrackNameOverWaypointName() {
        val gpx = """<?xml version="1.0"?>
            <gpx version="1.1">
            <wpt lat="59.0" lon="10.0"><name>Campsite</name></wpt>
            <trk><name>Day Hike</name><trkseg>
            <trkpt lat="59.9" lon="10.7"><time>2025-01-01T12:00:00Z</time></trkpt>
            </trkseg></trk></gpx>"""

        val track = requireNotNull(Track.fromGPX(gpx))
        assertEquals("Day Hike", track.name)
    }

    @Test
    fun fromGPX_decodesXmlEntitiesInName() {
        val gpx = """<?xml version="1.0"?>
            <gpx version="1.1"><trk><name>Hike &amp; Bike &lt;3</name><trkseg>
            <trkpt lat="59.9" lon="10.7"><time>2025-01-01T12:00:00Z</time></trkpt>
            </trkseg></trk></gpx>"""

        val track = requireNotNull(Track.fromGPX(gpx))
        assertEquals("Hike & Bike <3", track.name)
    }

    @Test
    fun toGPX_escapesXmlInName() {
        val track = Track(
            name = "Run < 5k & fast",
            points = listOf(
                TrackPoint(latLng = LatLng(59.9, 10.7), timestamp = 1000L)
            ),
            startTime = 1000L
        )
        val gpx = track.toGPX()
        assertTrue(gpx.contains("<name>Run &lt; 5k &amp; fast</name>"))
    }

    @Test
    fun toGPX_fromGPX_roundTrip_withSpecialCharsInName() {
        val original = Track(
            name = "Tom & Jerry's <Run>",
            points = listOf(
                TrackPoint(latLng = LatLng(59.9, 10.7), timestamp = 1704110400000L)
            ),
            startTime = 1704110400000L,
            endTime = 1704110400000L
        )

        val gpx = original.toGPX()
        val parsed = requireNotNull(Track.fromGPX(gpx))
        assertEquals(original.name, parsed.name)
    }

    @Test
    fun fromGPX_skipsInvalidCoordinates() {
        val gpx = """<?xml version="1.0"?>
            <gpx version="1.1"><trk><trkseg>
            <trkpt lat="999" lon="10.7"><time>2025-01-01T12:00:00Z</time></trkpt>
            <trkpt lat="59.9" lon="10.7"><time>2025-01-01T12:01:00Z</time></trkpt>
            </trkseg></trk></gpx>"""

        val track = requireNotNull(Track.fromGPX(gpx))
        assertEquals(1, track.points.size)
        assertEquals(59.9, track.points[0].latLng.latitude, 0.0001)
    }

    @Test
    fun fromGPX_timelessPointsGetEarliestRealTimestamp() {
        val gpx = """<?xml version="1.0"?>
            <gpx version="1.1"><trk><trkseg>
            <trkpt lat="59.9" lon="10.7"><time>2025-01-01T12:00:00Z</time></trkpt>
            <trkpt lat="60.0" lon="11.0"/>
            </trkseg></trk></gpx>"""

        val track = requireNotNull(Track.fromGPX(gpx))
        assertEquals(2, track.points.size)
        assertEquals(1735732800000L, track.points[0].timestamp)
        assertEquals(1735732800000L, track.points[1].timestamp)
    }

    @Test
    fun fromGPX_handlesSingleQuotedAttributes() {
        val gpx = """<?xml version="1.0"?>
            <gpx version="1.1"><trk><trkseg>
            <trkpt lat='59.9' lon='10.7'><time>2025-01-01T12:00:00Z</time></trkpt>
            </trkseg></trk></gpx>"""

        val track = requireNotNull(Track.fromGPX(gpx))
        assertEquals(1, track.points.size)
        assertEquals(59.9, track.points[0].latLng.latitude, 0.0001)
    }
}
