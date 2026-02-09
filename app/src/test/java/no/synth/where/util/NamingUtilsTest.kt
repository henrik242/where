package no.synth.where.util

import org.junit.Test
import org.junit.Assert.*

class NamingUtilsTest {

    @Test
    fun makeUnique_returnsOriginalName_whenNoConflict() {
        val result = NamingUtils.makeUnique("Track", listOf("Other"))
        assertEquals("Track", result)
    }

    @Test
    fun makeUnique_appendsSuffix_whenNameExists() {
        val result = NamingUtils.makeUnique("Track", listOf("Track"))
        assertEquals("Track (2)", result)
    }

    @Test
    fun makeUnique_incrementsSuffix_whenMultipleConflicts() {
        val existing = listOf("Track", "Track (2)", "Track (3)")
        val result = NamingUtils.makeUnique("Track", existing)
        assertEquals("Track (4)", result)
    }

    @Test
    fun makeUnique_fillsGap_whenSuffixGapExists() {
        val existing = listOf("Track", "Track (3)")
        val result = NamingUtils.makeUnique("Track", existing)
        assertEquals("Track (2)", result)
    }

    @Test
    fun makeUnique_worksWithEmptyList() {
        val result = NamingUtils.makeUnique("Track", emptyList())
        assertEquals("Track", result)
    }

    @Test
    fun makeUnique_worksWithSet() {
        val existing = setOf("Point", "Point (2)")
        val result = NamingUtils.makeUnique("Point", existing)
        assertEquals("Point (3)", result)
    }

    @Test
    fun makeUnique_handlesEmptyBaseName() {
        val result = NamingUtils.makeUnique("", listOf(""))
        assertEquals(" (2)", result)
    }
}
