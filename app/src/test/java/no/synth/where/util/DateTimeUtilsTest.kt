package no.synth.where.util

import org.junit.Test
import org.junit.Assert.*

class DateTimeUtilsTest {

    @Test
    fun formatDateTime_knownTimestamp_producesExpectedOutput() {
        // 2024-01-15 12:30:00 UTC = 1705318200000L
        val result = formatDateTime(1705318200000L, "yyyy-MM-dd")
        assertTrue("Should contain 2024-01-15", result.contains("2024-01-15"))
    }

    @Test
    fun formatDateTime_withTimePattern_includesTime() {
        val result = formatDateTime(1705318200000L, "yyyy-MM-dd HH:mm")
        assertTrue("Should contain date", result.contains("2024-01-15"))
        assertTrue("Should contain time separator", result.contains(":"))
    }

    @Test
    fun formatDateTime_differentPatterns_producesDifferentOutput() {
        val timestamp = 1705318200000L
        val dateOnly = formatDateTime(timestamp, "yyyy-MM-dd")
        val withTime = formatDateTime(timestamp, "yyyy-MM-dd HH:mm:ss")
        assertNotEquals("Different patterns should produce different output", dateOnly, withTime)
    }

    @Test
    fun formatDateTime_epochZero_producesValidOutput() {
        val result = formatDateTime(0L, "yyyy")
        assertTrue("Should produce a valid year string", result.isNotEmpty())
    }
}
