package no.synth.where.ui.map

import no.synth.where.data.Track
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
}
