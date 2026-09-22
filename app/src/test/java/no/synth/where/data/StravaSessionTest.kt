package no.synth.where.data

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import io.mockk.mockk
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException

/**
  * A flaky network must never cost the user their Strava session; only a rejected grant does.
  * Drives [StravaTokenManager] and [StravaRouteImporter] against a real DataStore and a mock Strava.
  */
class StravaSessionTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var scope: CoroutineScope
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var prefs: UserPreferences

    /** Requests the mocked Strava served, by endpoint. */
    private val calls = mutableMapOf<String, Int>()
    private val tokenCalls get() = calls["token"] ?: 0
    private val routeCalls get() = calls["routes"] ?: 0

    @Before
    fun setUp() {
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        dataStore = PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = { tempFolder.newFile("strava_prefs.preferences_pb") }
        )
        prefs = UserPreferences(dataStore)
    }

    @After
    fun tearDown() {
        scope.cancel()
    }

    /** Credentials plus an expired access token: the state every refresh starts from. */
    private suspend fun connectWithStaleToken() = connect(expirySeconds = 1L)

    private suspend fun connect(expirySeconds: Long) {
        prefs.setStravaCredentials("client-id", "client-secret")
        // setStravaCredentials persists in the background, and the manager reads it back off disk.
        withTimeout(5_000) { while (prefs.readStravaClientId() == null) yield() }
        prefs.cacheStravaTokens("old-access", "old-refresh", expirySeconds, athleteId = 42L)
    }

    private fun client(handler: MockRequestHandleScope.(HttpRequestData) -> HttpResponseData) =
        HttpClient(MockEngine { request ->
            val endpoint = if (request.url.encodedPath.endsWith("/oauth/token")) "token" else "routes"
            calls[endpoint] = (calls[endpoint] ?: 0) + 1
            handler(request)
        })

    private fun MockRequestHandleScope.respondJson(body: String) =
        respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))

    private val tokenResponse = """
        {"access_token":"new-access","refresh_token":"new-refresh","expires_at":4102444800}
    """.trimIndent()

    private val routesResponse = """[{"id":7,"name":"Ridge","distance":1000.0,"elevation_gain":50.0}]"""

    /** Strava's answer for a refresh token it no longer honours. */
    private val deadTokenBody = """
        {"message":"Bad Request","errors":[{"resource":"RefreshToken","field":"refresh_token","code":"invalid"}]}
    """.trimIndent()

    /** Strava's answer for a wrong client id or secret - same status, different resource. */
    private val badCredentialsBody = """
        {"message":"Bad Request","errors":[{"resource":"Application","field":"client_secret","code":"invalid"}]}
    """.trimIndent()

    private fun MockRequestHandleScope.respondBadRequest(body: String) =
        respond(body, HttpStatusCode.BadRequest, headersOf(HttpHeaders.ContentType, "application/json"))

    // --- token refresh ---

    @Test
    fun refresh_networkError_keepsSession() = runBlocking {
        connectWithStaleToken()
        val manager = StravaTokenManager(prefs, client { throw IOException("offline") })

        assertEquals(TokenResult.TransientFailure, manager.getAccessToken())
        // The refresh token is the session: a lost connection must not throw it away.
        assertEquals("old-refresh", prefs.readStravaRefreshToken())
    }

    @Test
    fun refresh_serverError_keepsSession() = runBlocking {
        connectWithStaleToken()
        val manager = StravaTokenManager(prefs, client { respondError(HttpStatusCode.BadGateway) })

        assertEquals(TokenResult.TransientFailure, manager.getAccessToken())
        assertEquals("old-refresh", prefs.readStravaRefreshToken())
    }

    @Test
    fun refresh_rateLimited_keepsSession() = runBlocking {
        connectWithStaleToken()
        val manager = StravaTokenManager(prefs, client { respondError(HttpStatusCode.TooManyRequests) })

        assertEquals(TokenResult.TransientFailure, manager.getAccessToken())
        assertEquals("old-refresh", prefs.readStravaRefreshToken())
    }

    @Test
    fun refresh_badClientCredentials_keepsSession() = runBlocking {
        connectWithStaleToken()
        // Same 400 as a dead refresh token, but the user can fix the secret, so the session lives.
        val manager = StravaTokenManager(prefs, client { respondBadRequest(badCredentialsBody) })

        assertEquals(TokenResult.TransientFailure, manager.getAccessToken())
        assertEquals("old-refresh", prefs.readStravaRefreshToken())
    }

    @Test
    fun refresh_deadRefreshToken_isRevoked() = runBlocking {
        connectWithStaleToken()
        val manager = StravaTokenManager(prefs, client { respondBadRequest(deadTokenBody) })

        assertEquals(TokenResult.Revoked, manager.getAccessToken())
        // Even then the manager doesn't self-clear: dropping the session is the caller's call.
        assertEquals("old-refresh", prefs.readStravaRefreshToken())
    }

    @Test
    fun refresh_noRefreshToken_isNoSession() = runBlocking {
        prefs.setStravaCredentials("client-id", "client-secret")
        withTimeout(5_000) { while (prefs.readStravaClientId() == null) yield() }
        val manager = StravaTokenManager(prefs, client { error("no request expected") })

        assertEquals(TokenResult.NoSession, manager.getAccessToken())
        assertEquals(0, tokenCalls)
    }

    @Test
    fun refresh_success_persistsRotatedRefreshTokenBeforeReturning() = runBlocking {
        connectWithStaleToken()
        val manager = StravaTokenManager(prefs, client { respondJson(tokenResponse) })

        assertEquals(TokenResult.Valid("new-access"), manager.getAccessToken())
        // The rotated token must be on disk before the caller holds an access token, or a process
        // death right after (an app upgrade) strands the session.
        assertEquals("new-refresh", prefs.readStravaRefreshToken())
        assertEquals(42L, prefs.readStravaAthleteId())   // refresh responses carry no athlete
    }

    @Test
    fun accessToken_stillFreshOnDisk_skipsRefresh() = runBlocking {
        connect(expirySeconds = 4102444800L)
        val manager = StravaTokenManager(prefs, client { error("no request expected") })

        assertEquals(TokenResult.Valid("old-access"), manager.getAccessToken())
        assertEquals(0, tokenCalls)
    }

    @Test
    fun accessToken_readsTokenAndAthleteIdFromDiskOnColdStart() = runBlocking {
        connect(expirySeconds = 4102444800L)
        // A cold start runs before the DataStore collector fills the in-memory cache.
        // hydrateFromDisk = false keeps it empty instead of racing the collector.
        val coldPrefs = UserPreferences(dataStore, hydrateFromDisk = false)
        assertNull(coldPrefs.stravaAccessToken.value)
        assertEquals(0L, coldPrefs.stravaAthleteId.value)
        val manager = StravaTokenManager(coldPrefs, client { error("no request expected") })

        assertEquals(TokenResult.Valid("old-access"), manager.getAccessToken())
        assertEquals(42L, manager.currentAthleteId())
        assertEquals(0, tokenCalls)
    }

    // --- route listing ---

    private fun importer(httpClient: HttpClient) = StravaRouteImporter(
        StravaApiClient(httpClient),
        StravaTokenManager(prefs, httpClient),
        // Never reached: every test here stops at the token step, before any route is persisted.
        mockk(relaxed = true),
    )

    @Test
    fun listRoutes_networkErrorDuringRefresh_isFailedNotNotAuthorized() = runBlocking {
        connectWithStaleToken()
        // NotAuthorized makes both UIs drop the session; a dropped connection must not do that.
        assertEquals(RouteListResult.Failed, importer(client { throw IOException("offline") }).listRoutes())
    }

    @Test
    fun listRoutes_deadRefreshToken_isNotAuthorized() = runBlocking {
        connectWithStaleToken()
        assertEquals(
            RouteListResult.NotAuthorized,
            importer(client { respondBadRequest(deadTokenBody) }).listRoutes()
        )
    }

    @Test
    fun listRoutes_unauthorizedApiCall_mintsAFreshTokenAndRetriesWithIt() = runBlocking {
        connect(expirySeconds = 4102444800L)   // the cached token looks fine, Strava disagrees
        val presented = mutableListOf<String?>()
        val httpClient = client { request ->
            if (request.url.encodedPath.endsWith("/oauth/token")) respondJson(tokenResponse)
            else {
                presented += request.headers[HttpHeaders.Authorization]
                // Strava retired "old-access" early; only the freshly minted one is accepted.
                if (request.headers[HttpHeaders.Authorization] == "Bearer new-access") respondJson(routesResponse)
                else respondError(HttpStatusCode.Unauthorized)
            }
        }

        val result = importer(httpClient).listRoutes()

        assertEquals(1, (result as RouteListResult.Success).routes.size)
        assertEquals(listOf("Bearer old-access", "Bearer new-access"), presented)
        assertEquals(1, tokenCalls)   // exactly one refresh, not one per attempt
        assertEquals(2, routeCalls)
    }

    @Test
    fun listRoutes_unauthorizedAfterRefresh_isNotAuthorized() = runBlocking {
        connectWithStaleToken()
        val httpClient = client { request ->
            if (request.url.encodedPath.endsWith("/oauth/token")) respondJson(tokenResponse)
            else respondError(HttpStatusCode.Unauthorized)
        }

        assertEquals(RouteListResult.NotAuthorized, importer(httpClient).listRoutes())
        assertEquals(2, routeCalls)   // tried once more with a fresh token before giving up
    }

    @Test
    fun listRoutes_forbidden_keepsSessionAndSpendsNoRefresh() = runBlocking {
        connect(expirySeconds = 4102444800L)
        // A new token can't fix a scope problem, and NotAuthorized would delete the session.
        val httpClient = client { respondError(HttpStatusCode.Forbidden) }

        assertEquals(RouteListResult.Failed, importer(httpClient).listRoutes())
        assertEquals(0, tokenCalls)
        assertEquals(1, routeCalls)
    }

    @Test
    fun importRoutes_deadRefreshToken_reportsNotAuthorized() = runBlocking {
        connectWithStaleToken()
        val routes = listOf(StravaRoute(7L, "Ridge", 1000.0, 50.0, starred = false))

        val result = importer(client { respondBadRequest(deadTokenBody) }).importRoutes(routes)

        assertEquals(ImportFailure.NOT_AUTHORIZED, result.failure)
    }

    @Test
    fun importRoutes_transientTokenFailure_importsNothingAndKeepsSession() = runBlocking {
        connectWithStaleToken()
        val routes = listOf(StravaRoute(7L, "Ridge", 1000.0, 50.0, starred = false))

        val result = importer(client { throw IOException("offline") }).importRoutes(routes)

        assertEquals(0, result.imported)
        assertEquals(1, result.total)
        // FAILED, not NOT_AUTHORIZED: the UI must offer a retry, not drop the session.
        assertEquals(ImportFailure.FAILED, result.failure)
        assertEquals("old-refresh", prefs.readStravaRefreshToken())
    }
}
