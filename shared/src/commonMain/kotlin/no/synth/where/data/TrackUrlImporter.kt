package no.synth.where.data

import io.ktor.client.HttpClient
import no.synth.where.util.Logger

/**
 * Dispatches URL-based track imports to the appropriate service-specific importer.
 * Auto-detects Strava vs Garmin Connect from the URL.
 */
class TrackUrlImporter(
    private val client: HttpClient = createDefaultHttpClient()
) {

    private val stravaImporter by lazy { StravaImporter(client) }
    private val garminImporter by lazy { GarminImporter(client) }
    private val komootImporter by lazy { KomootImporter(client) }
    private val utNoImporter by lazy { UtNoImporter(client) }

    suspend fun importFromUrl(
        input: String,
        addElevation: Boolean = true
    ): Track? {
        val service = detectService(input)
        Logger.d("Detected import service: $service for input: $input")
        return when (service) {
            Service.STRAVA -> stravaImporter.importFromUrl(input, addElevation)
            Service.GARMIN -> garminImporter.importFromUrl(input, addElevation)
            Service.KOMOOT -> komootImporter.importFromUrl(input, addElevation)
            Service.UT_NO -> utNoImporter.importFromUrl(input, addElevation)
            Service.UNKNOWN -> {
                Logger.e("Could not detect service from URL: $input")
                null
            }
        }
    }

    enum class Service { STRAVA, GARMIN, KOMOOT, UT_NO, UNKNOWN }

    companion object {
        fun detectService(input: String): Service {
            val trimmed = input.trim()
            return when {
                trimmed.contains("garmin.com", ignoreCase = true) -> Service.GARMIN
                trimmed.contains("strava.com", ignoreCase = true) -> Service.STRAVA
                trimmed.contains("strava.app.link", ignoreCase = true) -> Service.STRAVA
                trimmed.contains("komoot.com", ignoreCase = true) -> Service.KOMOOT
                trimmed.contains("komoot.de", ignoreCase = true) -> Service.KOMOOT
                trimmed.contains("ut.no", ignoreCase = true) -> Service.UT_NO
                // Bare numeric IDs: assume Strava (more common for sharing)
                StravaImporter.parseStravaUrl(trimmed) != null -> Service.STRAVA
                else -> Service.UNKNOWN
            }
        }
    }
}
