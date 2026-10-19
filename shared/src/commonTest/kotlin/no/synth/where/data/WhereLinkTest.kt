package no.synth.where.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WhereLinkTest {

    @Test
    fun singleClient() {
        assertEquals(listOf("abc123"), parseWhereGroupLink("https://where.synth.no/abc123"))
    }

    @Test
    fun group() {
        assertEquals(listOf("abc123", "def456"), parseWhereGroupLink("https://where.synth.no/abc123,def456"))
    }

    @Test
    fun trailingSlashAndQuery() {
        assertEquals(listOf("abc123"), parseWhereGroupLink("https://where.synth.no/abc123/?fbclid=xyz"))
        assertEquals(listOf("abc123"), parseWhereGroupLink("http://where.synth.no/abc123#map"))
    }

    @Test
    fun rejectsOtherLinks() {
        assertNull(parseWhereGroupLink("https://where.synth.no/"))
        assertNull(parseWhereGroupLink("https://where.synth.no/about"))
        assertNull(parseWhereGroupLink("https://where.synth.no/ABC123"))
        assertNull(parseWhereGroupLink("https://where.synth.no/abc123,"))
        assertNull(parseWhereGroupLink("https://evil.example/abc123"))
        assertNull(parseWhereGroupLink("https://where.synth.no.evil.example/abc123"))
    }
}
