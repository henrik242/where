package no.synth.where.ui.map

import no.synth.where.data.Track
import no.synth.where.data.TrackCropState
import no.synth.where.data.TrackPoint
import no.synth.where.data.TrackUtils
import no.synth.where.data.geo.LatLng
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MultiTrackRenderTest {

    private fun track(id: String, lat: Double, count: Int = 3) = Track(
        id = id,
        name = id,
        points = (0 until count).map {
            TrackPoint(LatLng(lat, 10.0 + it * 0.001), timestamp = it.toLong())
        },
        startTime = 0L,
    )

    @Test
    fun colorPaletteWrapsAndIsStable() {
        assertEquals(TrackColors.palette[0], TrackColors.forIndex(0))
        assertEquals(TrackColors.palette[0], TrackColors.forIndex(TrackColors.palette.size))
        assertEquals(TrackColors.palette[1], TrackColors.forIndex(TrackColors.palette.size + 1))
    }

    @Test
    fun renderableTracksAssignsPaletteByIndex() {
        val tracks = listOf(track("a", 60.0), track("b", 61.0))
        val out = renderableTracks(tracks, focusedId = null, recording = null)
        assertEquals(2, out.size)
        assertEquals(TrackColors.forIndex(0), out[0].color)
        assertEquals(TrackColors.forIndex(1), out[1].color)
        // No focus: all full opacity.
        assertTrue(out.all { it.opacity == 0.9 })
    }

    @Test
    fun focusEmphasizesOneAndDimsOthers() {
        val tracks = listOf(track("a", 60.0), track("b", 61.0))
        val out = renderableTracks(tracks, focusedId = "b", recording = null)
        val a = out.first { it.id == "a" }
        val b = out.first { it.id == "b" }
        assertEquals(0.3, a.opacity)
        assertEquals(0.9, b.opacity)
        assertTrue(b.width > a.width)
    }

    @Test
    fun recordingTrackIsRedAndAppendedLast() {
        val out = renderableTracks(
            viewing = listOf(track("a", 60.0)),
            focusedId = null,
            recording = track("rec", 62.0),
        )
        assertEquals(2, out.size)
        assertEquals(TrackColors.RECORDING, out.last().color)
        assertEquals("rec", out.last().id)
    }

    @Test
    fun shortTracksAreSkipped() {
        val out = renderableTracks(listOf(track("a", 60.0, count = 1)), null, null)
        assertTrue(out.isEmpty())
    }

    @Test
    fun buildTracksGeoJsonEmitsPerFeatureProperties() {
        val json = buildTracksGeoJson(
            renderableTracks(listOf(track("a", 60.0), track("b", 61.0)), focusedId = "a", recording = null)
        )
        assertTrue(json.startsWith("""{"type":"FeatureCollection","features":["""))
        assertEquals(2, Regex("\"type\":\"Feature\"").findAll(json).count())
        assertTrue(json.contains(""""id":"a""""))
        assertTrue(json.contains(""""color":"${TrackColors.forIndex(0)}""""))
        assertTrue(json.contains(""""opacity":0.3""")) // b dimmed
    }

    @Test
    fun findTappedTrackReturnsClosestWithinTolerance() {
        val a = track("a", 60.0)
        val b = track("b", 60.01) // ~1.1km north
        val tap = LatLng(60.00005, 10.001) // right on track a
        val hit = TrackUtils.findTappedTrack(tap, listOf(a, b), maxDistanceMeters = 50.0)
        assertNotNull(hit)
        assertEquals("a", hit.id)
    }

    @Test
    fun findTappedTrackReturnsNullWhenAllFar() {
        val a = track("a", 60.0)
        val tap = LatLng(61.0, 11.0)
        assertNull(TrackUtils.findTappedTrack(tap, listOf(a), maxDistanceMeters = 50.0))
    }

    @Test
    fun cropSplitsFocusedTrackIntoGreyEndsAndColoredKept() {
        val t = track("a", 60.0, count = 6) // indices 0..5
        val out = renderableTracks(
            listOf(t), focusedId = "a", recording = null,
            crop = TrackCropState("a", startIndex = 2, endIndex = 4),
        )
        val head = out.first { it.id == "a-head" }
        val kept = out.first { it.id == "a-kept" }
        val tail = out.first { it.id == "a-tail" }
        assertEquals(TrackColors.TRIMMED, head.color)
        assertEquals(TrackColors.TRIMMED, tail.color)
        assertEquals(TrackColors.forIndex(0), kept.color)
        // Boundary points shared: head 0..2 (3), kept 2..4 (3), tail 4..5 (2).
        assertEquals(3, head.points.size)
        assertEquals(3, kept.points.size)
        assertEquals(2, tail.points.size)
        assertTrue(kept.width > head.width)
    }

    @Test
    fun cropAtStartOmitsHead() {
        val t = track("a", 60.0, count = 6)
        val out = renderableTracks(
            listOf(t), focusedId = "a", recording = null,
            crop = TrackCropState("a", startIndex = 0, endIndex = 3),
        )
        assertNull(out.firstOrNull { it.id == "a-head" })
        assertNotNull(out.firstOrNull { it.id == "a-kept" })
        assertNotNull(out.firstOrNull { it.id == "a-tail" })
    }

    @Test
    fun cropAtEndOmitsTail() {
        val t = track("a", 60.0, count = 6)
        val out = renderableTracks(
            listOf(t), focusedId = "a", recording = null,
            crop = TrackCropState("a", startIndex = 2, endIndex = 5),
        )
        assertNotNull(out.firstOrNull { it.id == "a-head" })
        assertNotNull(out.firstOrNull { it.id == "a-kept" })
        assertNull(out.firstOrNull { it.id == "a-tail" })
    }

    @Test
    fun cropSharesBoundaryCoordinates() {
        val t = track("a", 60.0, count = 6)
        val out = renderableTracks(
            listOf(t), focusedId = "a", recording = null,
            crop = TrackCropState("a", startIndex = 2, endIndex = 4),
        )
        val head = out.first { it.id == "a-head" }
        val kept = out.first { it.id == "a-kept" }
        val tail = out.first { it.id == "a-tail" }
        // The split shares boundary points so grey meets color with no gap or off-by-one.
        assertEquals(head.points.last().latLng, kept.points.first().latLng)
        assertEquals(kept.points.last().latLng, tail.points.first().latLng)
    }

    @Test
    fun combinedBoundsSpansAllTracks() {
        val bounds = Track.combinedBounds(listOf(track("a", 60.0), track("b", 61.0)))
        assertNotNull(bounds)
        assertEquals(60.0, bounds.south)
        assertEquals(61.0, bounds.north)
    }

    @Test
    fun combinedBoundsEmptyIsNull() {
        assertNull(Track.combinedBounds(emptyList()))
    }

    @Test
    fun trackMarkerGeoJsonEmptyWhenNoMarker() {
        val empty = """{"type":"FeatureCollection","features":[]}"""
        val t = track("a", 60.0, count = 3)
        assertEquals(empty, buildTrackMarkerGeoJson(listOf(t), "a", null))
        assertEquals(empty, buildTrackMarkerGeoJson(listOf(t), null, 1))
        assertEquals(empty, buildTrackMarkerGeoJson(listOf(t), "missing", 1))
        assertEquals(empty, buildTrackMarkerGeoJson(listOf(t), "a", 99)) // index out of range
    }

    @Test
    fun trackMarkerGeoJsonEmitsColoredPointInLngLatOrder() {
        val t = track("a", 60.0, count = 3) // points at (60.0, 10.000/10.001/10.002)
        val json = buildTrackMarkerGeoJson(listOf(t), "a", 1)
        assertTrue(json.contains(""""type":"Point""""))
        assertTrue(json.contains("""[10.001,60.0]"""))   // GeoJSON is [lng,lat] of index 1
        assertTrue(json.contains(""""color":"${TrackColors.forIndex(0)}""""))
    }

    @Test
    fun elevationMarkerFallsBackToFocusedViewingTrack() {
        // No navigation track: identical to the focused-viewing-track marker.
        val t = track("a", 60.0, count = 3)
        assertEquals(
            buildTrackMarkerGeoJson(listOf(t), "a", 1),
            buildElevationMarkerGeoJson(listOf(t), "a", navigationTrack = null, markerIndex = 1),
        )
    }

    @Test
    fun elevationMarkerUsesNavigationTrackInRouteColor() {
        // During navigation the viewing set is empty; the marked point comes from the route.
        val nav = track("route", 61.0, count = 3) // (61.0, 10.000/10.001/10.002)
        val json = buildElevationMarkerGeoJson(emptyList(), null, navigationTrack = nav, markerIndex = 2)
        assertTrue(json.contains("""[10.002,61.0]"""))
        assertTrue(json.contains(""""color":"${NavColors.remaining}""""))
    }

    @Test
    fun elevationMarkerEmptyWhenNavigatingWithNoMarker() {
        val empty = """{"type":"FeatureCollection","features":[]}"""
        val nav = track("route", 61.0, count = 3)
        assertEquals(empty, buildElevationMarkerGeoJson(emptyList(), null, nav, markerIndex = null))
        assertEquals(empty, buildElevationMarkerGeoJson(emptyList(), null, nav, markerIndex = 99))
    }
}
