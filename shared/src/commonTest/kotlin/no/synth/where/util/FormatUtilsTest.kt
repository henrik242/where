package no.synth.where.util

import kotlin.test.Test
import kotlin.test.assertEquals

class FormatUtilsTest {

    @Test
    fun formatElapsedUnderMinute() {
        assertEquals("0:00", formatElapsed(0))
        assertEquals("0:05", formatElapsed(5_000))
        assertEquals("0:59", formatElapsed(59_999))
    }

    @Test
    fun formatElapsedMinutes() {
        assertEquals("1:00", formatElapsed(60_000))
        assertEquals("12:34", formatElapsed((12 * 60 + 34) * 1000L))
    }

    @Test
    fun formatElapsedHours() {
        assertEquals("1:02:05", formatElapsed((3600 + 2 * 60 + 5) * 1000L))
        assertEquals("10:00:00", formatElapsed(10 * 3600 * 1000L))
    }

    @Test
    fun formatElapsedClampsNegative() {
        assertEquals("0:00", formatElapsed(-5_000))
    }

    @Test
    fun formatSpeedConvertsToKmh() {
        assertEquals("0.0 km/h", 0.0.formatSpeed())
        assertEquals("3.6 km/h", 1.0.formatSpeed())
        assertEquals("18.0 km/h", 5.0.formatSpeed())
    }
}
