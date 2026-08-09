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

/** Outcome of importing selected routes. [rateLimited] means the run stopped early on a 429. */
data class RouteImportResult(val imported: Int, val total: Int, val rateLimited: Boolean)

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
    suspend fun listRoutes(): RouteListResult {
        val token = tokenManager.getAccessToken() ?: return RouteListResult.NotAuthorized
        val athleteId = tokenManager.currentAthleteId()
        if (athleteId <= 0L) return RouteListResult.NotAuthorized
        return try {
            RouteListResult.Success(api.listRoutes(token, athleteId))
        } catch (e: StravaApiException) {
            when {
                e.isAuthError -> RouteListResult.NotAuthorized
                e.isRateLimited -> RouteListResult.RateLimited
                else -> { Logger.e(e, "Failed to list Strava routes"); RouteListResult.Failed }
            }
        } catch (e: Exception) {
            Logger.e(e, "Failed to list Strava routes")
            RouteListResult.Failed
        }
    }

    /** Import [routes] into [folder]. Stops early (rateLimited=true) if Strava returns 429. */
    suspend fun importRoutes(routes: List<StravaRoute>, folder: String = DEFAULT_FOLDER): RouteImportResult {
        val token = tokenManager.getAccessToken()
            ?: return RouteImportResult(0, routes.size, rateLimited = false)
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
