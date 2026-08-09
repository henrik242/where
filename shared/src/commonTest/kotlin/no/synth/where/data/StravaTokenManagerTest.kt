package no.synth.where.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StravaTokenManagerTest {

    // --- parseTokenResponse ---

    @Test
    fun parseTokenResponse_full() {
        val body = """
            {"token_type":"Bearer","access_token":"acc","refresh_token":"ref",
             "expires_at":1700000000,"athlete":{"id":42}}
        """.trimIndent()
        val t = StravaTokenManager.parseTokenResponse(body)!!
        assertEquals("acc", t.accessToken)
        assertEquals("ref", t.refreshToken)
        assertEquals(1700000000L, t.expiresAt)
        assertEquals(42L, t.athleteId)
    }

    @Test
    fun parseTokenResponse_refreshWithoutAthlete() {
        val body = """{"access_token":"a2","refresh_token":"r2","expires_at":123}"""
        val t = StravaTokenManager.parseTokenResponse(body)!!
        assertEquals("a2", t.accessToken)
        assertEquals(0L, t.athleteId)   // no athlete on refresh
    }

    @Test
    fun parseTokenResponse_missingTokensOrJunk() {
        assertNull(StravaTokenManager.parseTokenResponse("""{"refresh_token":"r"}"""))   // no access
        assertNull(StravaTokenManager.parseTokenResponse("""{"access_token":"a"}"""))    // no refresh
        assertNull(StravaTokenManager.parseTokenResponse("not json"))
        assertNull(StravaTokenManager.parseTokenResponse("""{"error":"invalid"}"""))
    }

    // --- isTokenFresh ---

    @Test
    fun isTokenFresh_respectsBuffer() {
        // buffer 300s
        assertTrue(StravaTokenManager.isTokenFresh(expirySeconds = 1000, nowSeconds = 600))   // 400 > 300
        assertFalse(StravaTokenManager.isTokenFresh(expirySeconds = 1000, nowSeconds = 800))  // 200 < 300
        assertFalse(StravaTokenManager.isTokenFresh(expirySeconds = 1000, nowSeconds = 1000)) // expired
    }

    // --- isCallbackValid ---

    @Test
    fun isCallbackValid_matrix() {
        assertTrue(StravaTokenManager.isCallbackValid("code", "s", "s"))
        assertFalse(StravaTokenManager.isCallbackValid(null, "s", "s"))
        assertFalse(StravaTokenManager.isCallbackValid("code", null, "s"))
        assertFalse(StravaTokenManager.isCallbackValid("code", "s", null))   // no expected state
        assertFalse(StravaTokenManager.isCallbackValid("code", "s", "other")) // mismatch
        assertFalse(StravaTokenManager.isCallbackValid("", "s", "s"))         // blank code
    }

    // --- queryParam ---

    @Test
    fun queryParam_extractsAndDecodes() {
        val url = "where://strava/connected?code=abc%20123&state=xyz"
        assertEquals("abc 123", StravaTokenManager.queryParam(url, "code"))
        assertEquals("xyz", StravaTokenManager.queryParam(url, "state"))
    }

    @Test
    fun queryParam_ignoresFragmentAndMissing() {
        val url = "where://strava/connected?state=xyz#_=_"
        assertEquals("xyz", StravaTokenManager.queryParam(url, "state"))
        assertNull(StravaTokenManager.queryParam(url, "code"))
        assertNull(StravaTokenManager.queryParam("where://strava/connected", "code"))
    }

    // --- authorizeUrl ---

    @Test
    fun authorizeUrl_containsEncodedParams() {
        val url = StravaTokenManager.authorizeUrl("12345", "abcdef")
        assertTrue(url.startsWith("https://www.strava.com/oauth/authorize?"))
        assertTrue(url.contains("client_id=12345"))
        assertTrue(url.contains("state=abcdef"))
        assertTrue(url.contains("response_type=code"))
        // redirect_uri is percent-encoded (no raw "://")
        assertTrue(url.contains("redirect_uri=https%3A%2F%2Fwhere.synth.no"))
    }
}
