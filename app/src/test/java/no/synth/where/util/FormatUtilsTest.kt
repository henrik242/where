package no.synth.where.util

import org.junit.Test
import org.junit.Assert.*

class FormatUtilsTest {

    @Test
    fun formatBytes_bytes_showsB() {
        assertEquals("0 B", formatBytes(0))
        assertEquals("512 B", formatBytes(512))
        assertEquals("1023 B", formatBytes(1023))
    }

    @Test
    fun formatBytes_kilobytes_showsKB() {
        assertEquals("1 KB", formatBytes(1024))
        assertEquals("10 KB", formatBytes(10 * 1024))
        assertEquals("1023 KB", formatBytes(1023 * 1024))
    }

    @Test
    fun formatBytes_megabytes_showsMB() {
        assertEquals("1.0 MB", formatBytes(1024 * 1024))
        assertEquals("50.0 MB", formatBytes(50L * 1024 * 1024))
        assertEquals("1.5 MB", formatBytes((1.5 * 1024 * 1024).toLong()))
    }
}
