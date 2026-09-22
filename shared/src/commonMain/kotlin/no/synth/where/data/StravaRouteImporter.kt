package no.synth.where.data

import no.synth.where.util.Logger

/** Outcome of listing the athlete's routes. */
sealed interface RouteListResult {
    data class Success(val routes: List<StravaRoute>) : RouteListResult
    /** Not authenticated, or Strava rejected the token (reconnect needed). */
    data object NotAuthorized : RouteListResult
    data object RateLimited : RouteListResult
    data object Failed : RouteListResult
}

/** Why an import produced nothing, when it wasn't simply an empty selection. */
enum class ImportFailure { NOT_AUTHORIZED, FAILED }

/** Outcome of importing selected routes. [rateLimited] means the run stopped early on a 429. */
data class RouteImportResult(
    val imported: Int,
    val total: Int,
    val rateLimited: Boolean,
    val failure: ImportFailure? = null,
)

/**
 * Coordinates listing and importing a user's planned Strava routes: fetches an access token from
 * [StravaTokenManager], lists routes via [StravaApiClient], and persists selected routes through
 * [TrackRepository] (deduped by source id) into a dedicated folder.
 */
class StravaRouteImporter(
    private val api: StravaApiClient,
    private val tokenManager: StravaTokenManager,
    private val repository: TrackRepository,
) {
    /**
     * Strava can retire an access token before its stated expiry, so a 401 buys one retry with a
     * freshly minted token. Bounded by the loop: recursion here is one typo from never ending.
     */
    suspend fun listRoutes(): RouteListResult {
        var rejectedToken: String? = null
        repeat(2) {
            val token = when (val result = tokenManager.getAccessToken(rejectedToken)) {
                is TokenResult.Valid -> result.accessToken
                // Only a dead grant is worth a reconnect. Offline or a Strava outage is not.
                TokenResult.NoSession, TokenResult.Revoked -> return RouteListResult.NotAuthorized
                TokenResult.TransientFailure -> return RouteListResult.Failed
            }
            val athleteId = tokenManager.currentAthleteId()
            if (athleteId <= 0L) {
                Logger.e("Strava athlete id missing; reconnect needed")
                return RouteListResult.NotAuthorized
            }
            try {
                return RouteListResult.Success(api.listRoutes(token, athleteId))
            } catch (e: StravaApiException) {
                when {
                    e.statusCode == 401 -> rejectedToken = token
                    e.isRateLimited -> return RouteListResult.RateLimited
                    // A 403 is a scope problem. A fresh token can't fix it and the session is fine.
                    else -> { Logger.e(e, "Failed to list Strava routes"); return RouteListResult.Failed }
                }
            } catch (e: Exception) {
                Logger.e(e, "Failed to list Strava routes")
                return RouteListResult.Failed
            }
        }
        return RouteListResult.NotAuthorized   // both attempts rejected
    }

    /** Import [routes] into [folder]. Stops early (rateLimited=true) if Strava returns 429. */
    suspend fun importRoutes(routes: List<StravaRoute>, folder: String = DEFAULT_FOLDER): RouteImportResult {
        val token = when (val result = tokenManager.getAccessToken()) {
            is TokenResult.Valid -> result.accessToken
            // Same rule as listRoutes: only a dead grant is worth a reconnect.
            TokenResult.NoSession, TokenResult.Revoked ->
                return RouteImportResult(0, routes.size, false, ImportFailure.NOT_AUTHORIZED)
            TokenResult.TransientFailure ->
                return RouteImportResult(0, routes.size, false, ImportFailure.FAILED)
        }
        var imported = 0
        var rateLimited = false
        for (route in routes) {
            val gpx = try {
                api.exportRouteGpx(token, route.id)
            } catch (e: StravaApiException) {
                if (e.isRateLimited) { rateLimited = true; break }
                Logger.e(e, "Failed to export Strava route %d", route.id)
                continue
            } catch (e: Exception) {
                Logger.e(e, "Failed to export Strava route %d", route.id)
                continue
            }
            if (repository.importStravaRoute(gpx, sourceId(route.id), folder) != null) imported++
        }
        return RouteImportResult(imported, routes.size, rateLimited)
    }

    companion object {
        const val DEFAULT_FOLDER = "Strava routes"
        fun sourceId(routeId: Long): String = "strava:route:$routeId"
    }
}
