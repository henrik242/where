package no.synth.where.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GpxWaypointsTest {

    // Trimmed from the Outdooractive export attached to issue #88.
    private val outdooractive = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <gpx version="1.1" creator="outdooractive - http://www.outdooractive.com" xmlns="http://www.topografix.com/GPX/1/1">
          <metadata>
            <name>59-Puttåsen</name>
            <author><name>Vidar Ibenfeldt - Community</name></author>
            <time>2020-07-09T11:39:53.088Z</time>
          </metadata>
          <wpt lon="10.897" lat="59.9202">
            <ele>342</ele>
            <name>59-Puttåsen</name>
            <src>viewranger-poidata.21430.9983286</src>
            <type>Waypoint</type>
          </wpt>
        </gpx>
    """.trimIndent()

    @Test
    fun parsesAnOutdooractiveWaypointExport() {
        val points = GpxWaypoints.parse(outdooractive)

        assertEquals(1, points.size)
        assertEquals("59-Puttåsen", points[0].name)
        assertEquals(59.9202, points[0].latLng.latitude)
        assertEquals(10.897, points[0].latLng.longitude)
        assertEquals("", points[0].description)
    }

    @Test
    fun parsesEveryWaypointInAFile() {
        val points = GpxWaypoints.parse(
            """
            <gpx version="1.1">
              <wpt lat="59.9" lon="10.7"><name>First</name><desc>By the lake</desc></wpt>
              <wpt lat="60.1" lon="11.2"><name>Second</name></wpt>
              <wpt lat="60.4" lon="5.3" />
            </gpx>
            """.trimIndent()
        )

        assertEquals(listOf("First", "Second", GpxWaypoints.DEFAULT_NAME), points.map { it.name })
        assertEquals("By the lake", points[0].description)
        assertEquals(60.4, points[2].latLng.latitude)
    }

    @Test
    fun fallsBackToTheDocumentNameForAnUnnamedWaypoint() {
        val points = GpxWaypoints.parse(
            """
            <gpx version="1.1">
              <metadata><name>03-Lutvann</name></metadata>
              <wpt lat="59.8919" lon="10.8586"><ele>202</ele></wpt>
            </gpx>
            """.trimIndent()
        )

        assertEquals(listOf("03-Lutvann"), points.map { it.name })
    }

    @Test
    fun readsCmtWhenThereIsNoDesc() {
        val points = GpxWaypoints.parse(
            """<gpx><wpt lat="59.9" lon="10.7"><name>Hut</name><cmt>Locked in winter</cmt></wpt></gpx>"""
        )

        assertEquals("Locked in winter", points[0].description)
    }

    @Test
    fun unescapesNameAndDescription() {
        val points = GpxWaypoints.parse(
            """<gpx><wpt lat="59.9" lon="10.7"><name>Blåstien &amp; Co</name><desc>&lt;note&gt;</desc></wpt></gpx>"""
        )

        assertEquals("Blåstien & Co", points[0].name)
        assertEquals("<note>", points[0].description)
    }

    @Test
    fun ignoresTrackAndRoutePoints() {
        val points = GpxWaypoints.parse(
            """
            <gpx version="1.1">
              <trk><name>A hike</name><trkseg>
                <trkpt lat="59.9" lon="10.7"><time>2025-01-01T12:00:00Z</time></trkpt>
              </trkseg></trk>
              <rte><rtept lat="60.0" lon="10.8"/></rte>
            </gpx>
            """.trimIndent()
        )

        assertTrue(points.isEmpty())
    }

    @Test
    fun skipsWaypointsWithOutOfRangeCoordinates() {
        val points = GpxWaypoints.parse(
            """<gpx><wpt lat="91.0" lon="10.7"><name>Nope</name></wpt><wpt lat="59.9" lon="10.7"><name>Yes</name></wpt></gpx>"""
        )

        assertEquals(listOf("Yes"), points.map { it.name })
    }

    @Test
    fun readsTheWaypointTime() {
        val points = GpxWaypoints.parse(
            """<gpx><wpt lat="59.9" lon="10.7"><name>Stamped</name><time>2020-07-09T11:39:53Z</time></wpt></gpx>"""
        )

        assertEquals(1594294793000L, points[0].timestamp)
    }

    @Test
    fun aWaypointDoesNotInheritFromTheTrackThatFollowsIt() {
        val points = GpxWaypoints.parse(
            """
            <gpx version="1.1">
              <metadata><name>Doc</name></metadata>
              <wpt lat="59.9" lon="10.7"><name>Hut</name></wpt>
              <trk><name>A hike</name><desc>Track note</desc><trkseg>
                <trkpt lat="60.0" lon="10.0"><time>2025-01-01T12:00:00Z</time></trkpt>
              </trkseg></trk>
            </gpx>
            """.trimIndent()
        )

        assertEquals(listOf("Hut"), points.map { it.name })
        assertEquals("", points[0].description)
        assertNull(points[0].timestamp)
    }

    @Test
    fun readsAWaypointWhoseAttributesWrapToTheNextLine() {
        val points = GpxWaypoints.parse("<gpx><wpt\n  lat=\"59.9\" lon=\"10.7\"><name>Wrapped</name></wpt></gpx>")

        assertEquals(listOf("Wrapped"), points.map { it.name })
    }

    @Test
    fun anAuthorOnlyDocumentDoesNotNamePointsAfterTheAuthor() {
        val points = GpxWaypoints.parse(
            """<gpx><metadata><author><name>outdooractive</name></author></metadata>""" +
                """<wpt lat="59.9" lon="10.7"><ele>202</ele></wpt></gpx>"""
        )

        assertEquals(listOf(GpxWaypoints.DEFAULT_NAME), points.map { it.name })
    }

    @Test
    fun bytesThatAreNotGpxYieldNothing() {
        assertTrue(GpxWaypoints.parse(byteArrayOf(1, 2, 3)).isEmpty())
        assertTrue(GpxWaypoints.parse(ByteArray(0)).isEmpty())
        assertTrue(GpxWaypoints.parse("just text".encodeToByteArray()).isEmpty())
    }

    @Test
    fun parsesGpxBytes() {
        assertEquals(1, GpxWaypoints.parse(outdooractive.encodeToByteArray()).size)
    }
}
