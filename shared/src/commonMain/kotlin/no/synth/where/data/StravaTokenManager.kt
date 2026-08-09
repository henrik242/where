package no.synth.where.data

import io.ktor.client.HttpClient
import io.ktor.client.request.forms.submitForm
import io.ktor.client.statement.bodyAsText
import io.ktor.http.decodeURLQueryComponent
import io.ktor.http.encodeURLParameter
import io.ktor.http.isSuccess
import io.ktor.http.parameters
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import no.synth.where.util.Logger
import no.synth.where.util.currentTimeMillis
import no.synth.where.util.secureRandomHex

/**
 * On-device Strava OAuth using the user's own API app credentials (BYO). The client id + secret are
 * entered in the app and stored in [UserPreferences]; token exchange and refresh talk directly to
 * Strava. The Where backend only bounces the OAuth redirect to the app's custom scheme — it holds no
 * secret and stores no tokens.
 */
class StravaTokenManager(
    private val prefs: UserPreferences,
    private val client: HttpClient = createDefaultHttpClient(),
) {
    private val refreshMutex = Mutex()

    // Emits the outcome of an OAuth round-trip (true = connected, false = failed/denied) so the UI
    // can confirm success or report failure — the redirect completes outside any screen's scope.
    private val _authOutcome = MutableSharedFlow<Boolean>(replay = 0, extraBufferCapacity = 1)
    val authOutcome: SharedFlow<Boolean> = _authOutcome.asSharedFlow()

    // True while the code->token exchange is running, so the UI can show a "Connecting…" state.
    private val _exchanging = MutableStateFlow(false)
    val exchanging: StateFlow<Boolean> = _exchanging.asStateFlow()

    fun saveCredentials(clientId: String, clientSecret: String) =
        prefs.setStravaCredentials(clientId.trim(), clientSecret.trim())

    /** Build the Strava authorize URL and persist a fresh CSRF state, or null if credentials unset. */
    fun buildAuthorizeUrl(): String? {
        val clientId = prefs.stravaClientIdValue()?.takeIf { it.isNotBlank() } ?: return null
        val state = secureRandomHex(16)
        prefs.setStravaOAuthState(state)
        return authorizeUrl(clientId, state)
    }

    /** Parse a `where://strava/connected?...` callback URL and complete the handshake (both platforms). */
    suspend fun handleCallbackUrl(rawUrl: String): Boolean {
        if (!rawUrl.startsWith(CALLBACK_URL_PREFIX)) return false
        return handleCallback(queryParam(rawUrl, "code"), queryParam(rawUrl, "state"))
    }

    /** Verify CSRF state and exchange the code on-device. Reads credentials with a cold-start-safe read. */
    suspend fun handleCallback(code: String?, state: String?): Boolean {
        _exchanging.value = true
        val ok = try {
            exchangeAuthCode(code, state)
        } finally {
            _exchanging.value = false
        }
        _authOutcome.tryEmit(ok)
        return ok
    }

    private suspend fun exchangeAuthCode(code: String?, state: String?): Boolean {
        val expected = prefs.readStravaOAuthState()
        prefs.setStravaOAuthState(null)
        if (!isCallbackValid(code, state, expected)) {
            Logger.e("Strava callback rejected: missing code or state mismatch")
            return false
        }
        val clientId = prefs.readStravaClientId() ?: return false
        val clientSecret = prefs.readStravaClientSecret() ?: return false
        val tokens = postTokens(
            parameters {
                append("client_id", clientId)
                append("client_secret", clientSecret)
                append("code", code!!)
                append("grant_type", "authorization_code")
            }
        ) ?: return false
        prefs.cacheStravaTokens(tokens.accessToken, tokens.refreshToken, tokens.expiresAt, tokens.athleteId)
        return true
    }

    /** Return a valid access token, refreshing on-device (single-flight) when the cached one is stale. */
    suspend fun getAccessToken(): String? {
        val nowSec = currentTimeMillis() / 1000
        prefs.stravaAccessToken.value?.let { if (isTokenFresh(prefs.stravaTokenExpiry.value, nowSec)) return it }
        return refreshMutex.withLock {
            // Re-check inside the lock: a concurrent caller may have refreshed already.
            val now = currentTimeMillis() / 1000
            prefs.stravaAccessToken.value?.let { if (isTokenFresh(prefs.stravaTokenExpiry.value, now)) return@withLock it }
            refreshAccessToken()
        }
    }

    private suspend fun refreshAccessToken(): String? {
        val refresh = prefs.readStravaRefreshToken() ?: return null
        val clientId = prefs.readStravaClientId() ?: return null
        val clientSecret = prefs.readStravaClientSecret() ?: return null
        val tokens = postTokens(
            parameters {
                append("client_id", clientId)
                append("client_secret", clientSecret)
                append("grant_type", "refresh_token")
                append("refresh_token", refresh)
            }
        ) ?: return null
        prefs.cacheStravaTokens(
            tokens.accessToken,
            tokens.refreshToken,
            tokens.expiresAt,
            if (tokens.athleteId > 0L) tokens.athleteId else prefs.stravaAthleteId.value,
        )
        return tokens.accessToken
    }

    private suspend fun postTokens(form: io.ktor.http.Parameters): TokenResponse? {
        return try {
            val resp = client.submitForm(TOKEN_URL, formParameters = form)
            if (!resp.status.isSuccess()) {
                Logger.e("Strava token request failed: %d", resp.status.value)
                return null
            }
            parseTokenResponse(resp.bodyAsText())
        } catch (e: Exception) {
            Logger.e(e, "Strava token request error")
            null
        }
    }

    /** The connected athlete's id (needed for the routes endpoint); 0 when unknown. */
    fun currentAthleteId(): Long = prefs.stravaAthleteId.value

    /** Drop the connected session (e.g. after Strava reports the token is no longer valid). */
    fun clearSession() = prefs.clearStravaTokens()

    /** Revoke the grant (if any) and forget the stored client id + secret entirely. */
    suspend fun forgetCredentials() {
        disconnect()
        prefs.clearStravaCredentials()
    }

    /** Revoke the grant with Strava and forget the session (credentials are kept for reconnect). */
    suspend fun disconnect() {
        val access = prefs.stravaAccessToken.value ?: getAccessToken()
        if (access != null) {
            try {
                client.submitForm(DEAUTH_URL, formParameters = parameters { append("access_token", access) })
            } catch (e: Exception) {
                Logger.e(e, "Strava deauthorize failed (clearing locally anyway)")
            }
        }
        prefs.clearStravaTokens()
    }

    data class TokenResponse(
        val accessToken: String,
        val refreshToken: String,
        val expiresAt: Long,
        val athleteId: Long,
    )

    companion object {
        /** Users set their Strava app's Authorization Callback Domain to this host. */
        const val CALLBACK_DOMAIN = "where.synth.no"
        const val REDIRECT_URI = "https://where.synth.no/api/strava/redirect"
        private const val CALLBACK_URL_PREFIX = "where://strava"
        // read_all is required to list the athlete's private routes.
        private const val SCOPE = "read,read_all"
        private const val TOKEN_URL = "https://www.strava.com/api/v3/oauth/token"
        private const val DEAUTH_URL = "https://www.strava.com/oauth/deauthorize"
        private const val EXPIRY_BUFFER_SECONDS = 300L
        private val json = Json { ignoreUnknownKeys = true; isLenient = true }

        fun authorizeUrl(clientId: String, state: String): String =
            "https://www.strava.com/oauth/authorize" +
                "?client_id=${clientId.encodeURLParameter()}" +
                "&redirect_uri=${REDIRECT_URI.encodeURLParameter()}" +
                "&response_type=code" +
                "&approval_prompt=auto" +
                "&scope=${SCOPE.encodeURLParameter()}" +
                "&state=${state.encodeURLParameter()}"

        fun isCallbackValid(code: String?, state: String?, expected: String?): Boolean =
            !code.isNullOrBlank() && !state.isNullOrBlank() && !expected.isNullOrBlank() && state == expected

        fun isTokenFresh(expirySeconds: Long, nowSeconds: Long, bufferSeconds: Long = EXPIRY_BUFFER_SECONDS): Boolean =
            expirySeconds - nowSeconds > bufferSeconds

        fun parseTokenResponse(body: String): TokenResponse? {
            return try {
                val obj = json.parseToJsonElement(body).jsonObject
                val access = obj["access_token"]?.jsonPrimitive?.contentOrNull ?: return null
                val refresh = obj["refresh_token"]?.jsonPrimitive?.contentOrNull ?: return null
                TokenResponse(
                    accessToken = access,
                    refreshToken = refresh,
                    expiresAt = obj["expires_at"]?.jsonPrimitive?.longOrNull ?: 0L,
                    athleteId = obj["athlete"]?.jsonObject?.get("id")?.jsonPrimitive?.longOrNull ?: 0L,
                )
            } catch (e: Exception) {
                null
            }
        }

        /** Extract a query parameter from a raw URL (handles a trailing #fragment and %-encoding). */
        fun queryParam(url: String, key: String): String? {
            val query = url.substringAfter('?', "").substringBefore('#')
            if (query.isEmpty()) return null
            return query.split("&")
                .firstOrNull { it.substringBefore('=') == key }
                ?.substringAfter('=', "")
                ?.decodeURLQueryComponent(plusIsSpace = true)
        }
    }
}
