package no.synth.where.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ImporterUtilsTest {

    // --- extractOgTitle ---

    @Test
    fun extractOgTitle_standard() {
        val html = """<meta property="og:title" content="My Cool Tour">"""
        assertEquals("My Cool Tour", ImporterUtils.extractOgTitle(html))
    }

    @Test
    fun extractOgTitle_reversedAttributes() {
        val html = """<meta content="My Cool Tour" property="og:title">"""
        assertEquals("My Cool Tour", ImporterUtils.extractOgTitle(html))
    }

    @Test
    fun extractOgTitle_noMatch() {
        assertNull(ImporterUtils.extractOgTitle("<html><body>No meta</body></html>"))
    }

    // --- extractTitleTag ---

    @Test
    fun extractTitleTag_standard() {
        assertEquals("My Page", ImporterUtils.extractTitleTag("<title>My Page</title>"))
    }

    @Test
    fun extractTitleTag_withWhitespace() {
        assertEquals("My Page", ImporterUtils.extractTitleTag("<title>  My Page  </title>"))
    }

    @Test
    fun extractTitleTag_noMatch() {
        assertNull(ImporterUtils.extractTitleTag("<html>no title</html>"))
    }

    // --- extractJsonName ---

    @Test
    fun extractJsonName_standard() {
        val html = """<script>{"name":"Morning Run","type":"Run"}</script>"""
        assertEquals("Morning Run", ImporterUtils.extractJsonName(html))
    }

    @Test
    fun extractJsonName_filtersUrl() {
        val html = """<script>{"name":"https://example.com/path"}</script>"""
        assertNull(ImporterUtils.extractJsonName(html))
    }

    @Test
    fun extractJsonName_filtersPolyline() {
        val html = """<script>{"name":"abc123def456_ghi"}</script>"""
        assertNull(ImporterUtils.extractJsonName(html))
    }

    @Test
    fun extractJsonName_noMatch() {
        assertNull(ImporterUtils.extractJsonName("<html>nothing</html>"))
    }

    // --- looksLikeName ---

    @Test
    fun looksLikeName_normalName() {
        assertTrue(ImporterUtils.looksLikeName("Morning Run"))
    }

    @Test
    fun looksLikeName_tooLong() {
        assertFalse(ImporterUtils.looksLikeName("a".repeat(200)))
    }

    @Test
    fun looksLikeName_url() {
        assertFalse(ImporterUtils.looksLikeName("https://example.com"))
    }

    @Test
    fun looksLikeName_hashLike() {
        assertFalse(ImporterUtils.looksLikeName("abc123def456_ghi"))
    }

    // --- cleanTitleSuffix ---

    @Test
    fun cleanTitleSuffix_onService() {
        assertEquals("Morning Run", ImporterUtils.cleanTitleSuffix("Morning Run on Strava", "Strava"))
    }

    @Test
    fun cleanTitleSuffix_pipeService() {
        assertEquals("My Route", ImporterUtils.cleanTitleSuffix("My Route | Strava Route", "Strava"))
    }

    @Test
    fun cleanTitleSuffix_dashService() {
        assertEquals("Sunday Run", ImporterUtils.cleanTitleSuffix("Sunday Run - Garmin Connect", "Garmin Connect"))
    }

    @Test
    fun cleanTitleSuffix_multiplePipes() {
        assertEquals("Morning Run", ImporterUtils.cleanTitleSuffix("Morning Run | Run | Strava", "Strava"))
    }

    @Test
    fun cleanTitleSuffix_multipleServiceNames() {
        assertEquals("My Hike", ImporterUtils.cleanTitleSuffix("My Hike | Garmin Connect", "Garmin Connect", "Garmin"))
    }

    @Test
    fun cleanTitleSuffix_noSuffix() {
        assertEquals("Just A Name", ImporterUtils.cleanTitleSuffix("Just A Name", "Strava"))
    }

    @Test
    fun cleanTitleSuffix_blankResult() {
        assertNull(ImporterUtils.cleanTitleSuffix("  ", "Strava"))
    }

    // --- unescapeJsonString ---

    @Test
    fun unescape_ampersand() {
        assertEquals("A & B", ImporterUtils.unescapeJsonString("A \\u0026 B"))
    }

    @Test
    fun unescape_backslash() {
        assertEquals("a\\b", ImporterUtils.unescapeJsonString("a\\\\b"))
    }

    @Test
    fun unescape_norwegianChars() {
        assertEquals("åøæ", ImporterUtils.unescapeJsonString("\\u00e5\\u00f8\\u00e6"))
    }

    @Test
    fun unescape_germanChars() {
        assertEquals("äöüß", ImporterUtils.unescapeJsonString("\\u00e4\\u00f6\\u00fc\\u00df"))
    }

    @Test
    fun unescape_noEscapes() {
        assertEquals("plain text", ImporterUtils.unescapeJsonString("plain text"))
    }
}
