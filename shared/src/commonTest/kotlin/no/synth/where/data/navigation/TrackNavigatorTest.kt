package no.synth.where.data.navigation

import no.synth.where.data.Track
import no.synth.where.data.TrackPoint
import no.synth.where.data.geo.LatLng
import kotlin.math.PI
import kotlin.math.cos
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TrackNavigatorTest {

    private val baseLat = 60.0
    private val baseLng = 10.0
    private val mPerLat = 111_320.0
    private val mPerLng = 111_320.0 * cos(baseLat * PI / 180.0)

    // Build a LatLng offset (eastMeters, northMeters) from the base origin.
    private fun at(eastMeters: Double, northMeters: Double): LatLng =
        LatLng(baseLat + northMeters / mPerLat, baseLng + eastMeters / mPerLng)

    private fun track(points: List<Pair<LatLng, Double?>>): Track =
        Track(
            name = "test",
            points = points.map { (ll, alt) -> TrackPoint(latLng = ll, timestamp = 0L, altitude = alt) },
            startTime = 0L
        )

    // 3 vertices ~100 m apart going east, no altitude.
    private fun straightTrack(): Track = track(
        listOf(
            at(0.0, 0.0) to null,
            at(100.0, 0.0) to null,
            at(200.0, 0.0) to null
        )
    )

    @Test
    fun standingOnMiddleVertex() {
        val nav = TrackNavigator(straightTrack(), reversed = false)
        val p = nav.progressAt(at(100.0, 0.0))
        assertTrue(p.onCourse)
        assertTrue(p.offCourseMeters < 2.0, "offCourse was ${p.offCourseMeters}")
        assertEquals(100.0, p.remainingMeters, 3.0)
    }

    @Test
    fun offCourseWithHysteresis() {
        val nav = TrackNavigator(straightTrack(), reversed = false)
        // 40 m north of mid-segment (between vertex 0 and 1, at 50 m east).
        val p40 = nav.progressAt(at(50.0, 40.0))
        assertFalse(p40.onCourse)
        assertEquals(40.0, p40.offCourseMeters, 3.0)
        // 30 m: above exit threshold (25), so stays off-course once tripped.
        val p30 = nav.progressAt(at(50.0, 30.0))
        assertFalse(p30.onCourse)
        // 10 m: below exit threshold, back on course.
        val p10 = nav.progressAt(at(50.0, 10.0))
        assertTrue(p10.onCourse)
    }

    @Test
    fun remainingMonotonicOnOutAndBack() {
        // Out east 0..300, then back to 0 (return leg appended).
        val out = (0..3).map { at(it * 100.0, 0.0) to null }
        val back = (2 downTo 0).map { at(it * 100.0, 0.0) to null }
        val nav = TrackNavigator(track(out + back), reversed = false)

        var prev = Double.MAX_VALUE
        val first = nav.progressAt(at(0.0, 0.0)).remainingMeters
        var last = first
        for (east in listOf(0.0, 50.0, 120.0, 200.0, 280.0)) {
            val r = nav.progressAt(at(east, 0.0)).remainingMeters
            assertTrue(r < prev + 1.0, "remaining not decreasing: $r after $prev at ${east}m")
            prev = r
            last = r
        }
        // And it actually fell by a real margin, not just stayed flat.
        assertTrue(first - last > 200.0, "remaining barely moved: $first -> $last")
    }

    // Altitudes 100 -> 150 -> 120 across the straight track.
    private fun altitudeTrack(): Track = track(
        listOf(
            at(0.0, 0.0) to 100.0,
            at(100.0, 0.0) to 150.0,
            at(200.0, 0.0) to 120.0
        )
    )

    @Test
    fun ascentDescentFromStart() {
        val nav = TrackNavigator(altitudeTrack(), reversed = false)
        val p = nav.progressAt(at(0.0, 0.0))
        assertEquals(50.0, p.remainingAscent ?: 0.0, 1.0)
        assertEquals(30.0, p.remainingDescent ?: 0.0, 1.0)
    }

    @Test
    fun ascentDescentReversedSwaps() {
        val nav = TrackNavigator(altitudeTrack(), reversed = true)
        // Reversed altitudes: 120 -> 150 -> 100 => ascent 30, descent 50.
        val p = nav.progressAt(at(200.0, 0.0))
        assertEquals(30.0, p.remainingAscent ?: 0.0, 1.0)
        assertEquals(50.0, p.remainingDescent ?: 0.0, 1.0)
    }

    @Test
    fun nullAltitudeGivesNoAscent() {
        val nav = TrackNavigator(straightTrack(), reversed = false)
        val p = nav.progressAt(at(0.0, 0.0))
        assertNull(p.remainingAscent)
        assertNull(p.remainingDescent)
    }

    @Test
    fun arrivedNearEnd() {
        val nav = TrackNavigator(straightTrack(), reversed = false)
        val p = nav.progressAt(at(200.0, 10.0))
        assertTrue(p.atEnd)
    }

    @Test
    fun ascentDescentMidSegment() {
        // Halfway up the 100->150 climb: 25 m of that climb still ahead, plus the later descent.
        val nav = TrackNavigator(altitudeTrack(), reversed = false)
        val p = nav.progressAt(at(50.0, 0.0))
        assertEquals(25.0, p.remainingAscent ?: 0.0, 1.0)
        assertEquals(30.0, p.remainingDescent ?: 0.0, 1.0)
    }

    @Test
    fun altitudeDeadbandSuppressesNoise() {
        // Flat first segment, then sub-2 m wiggles: the suffix ascent/descent must be zero.
        val nav = TrackNavigator(
            track(
                listOf(
                    at(0.0, 0.0) to 100.0,
                    at(100.0, 0.0) to 100.0,
                    at(200.0, 0.0) to 101.0,
                    at(300.0, 0.0) to 100.0
                )
            ),
            reversed = false
        )
        val p = nav.progressAt(at(0.0, 0.0))
        assertEquals(0.0, p.remainingAscent ?: -1.0, 0.001)
        assertEquals(0.0, p.remainingDescent ?: -1.0, 0.001)
    }

    @Test
    fun altitudeMissingOnCurrentSegmentFallsBackToSuffix() {
        // Altitude present overall but null on the current segment -> suffix-only, not null.
        val nav = TrackNavigator(
            track(
                listOf(
                    at(0.0, 0.0) to 100.0,
                    at(100.0, 0.0) to null,
                    at(200.0, 0.0) to 150.0,
                    at(300.0, 0.0) to 160.0
                )
            ),
            reversed = false
        )
        val p = nav.progressAt(at(0.0, 0.0))
        assertEquals(10.0, p.remainingAscent ?: -1.0, 1.0)
        assertEquals(0.0, p.remainingDescent ?: -1.0, 1.0)
    }

    @Test
    fun reacquiresViaGlobalSearchAfterBigJump() {
        // 50 vertices, ~100 m apart. Advance the cursor near the start, then jump far beyond
        // the forward window; the global fallback must relocate the cursor.
        val nav = TrackNavigator(
            track((0..49).map { at(it * 100.0, 0.0) to null }),
            reversed = false
        )
        nav.progressAt(at(550.0, 0.0))
        assertEquals(5, nav.currentSegment())
        nav.progressAt(at(4850.0, 0.0))
        assertEquals(48, nav.currentSegment())
    }

    @Test
    fun atEndViaDistanceToLastPointOnLoop() {
        // Loop whose last point sits next to the start: standing at the start snaps to the first
        // segment (remaining large) yet we are within arrival distance of the finish.
        val nav = TrackNavigator(
            track(
                listOf(
                    at(0.0, 0.0) to null,
                    at(300.0, 0.0) to null,
                    at(300.0, 300.0) to null,
                    at(5.0, 5.0) to null
                )
            ),
            reversed = false
        )
        val p = nav.progressAt(at(0.0, 0.0))
        assertTrue(p.atEnd)
        assertTrue(p.remainingMeters > 25.0, "remaining was ${p.remainingMeters}")
    }

    @Test
    fun degenerateTracks() {
        val empty = TrackNavigator(track(emptyList()), reversed = false).progressAt(at(0.0, 0.0))
        assertTrue(empty.atEnd)
        assertTrue(empty.onCourse)
        assertNull(empty.remainingAscent)

        val single = TrackNavigator(track(listOf(at(0.0, 0.0) to null)), reversed = false)
            .progressAt(at(10.0, 0.0))
        assertTrue(single.atEnd)
        assertTrue(single.onCourse)
    }
}
