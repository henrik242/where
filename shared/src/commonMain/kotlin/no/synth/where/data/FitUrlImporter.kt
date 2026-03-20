package no.synth.where.data

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import no.synth.where.util.Logger

class FitUrlImporter(
    private val client: HttpClient = createDefaultHttpClient()
) {

    suspend fun importFromUrl(
        url: String,
        addElevation: Boolean = true
    ): Track? {
        val bytes: ByteArray = try {
            client.get(url.trim()).body()
        } catch (e: Exception) {
            Logger.e(e, "Failed to fetch FIT from URL: $url")
            return null
        }

        val track = Track.fromFIT(bytes)
        if (track == null) {
            Logger.e("Failed to parse FIT content from URL: $url")
            return null
        }

        Logger.d("Parsed FIT from URL: ${track.points.size} points")

        val hasElevation = track.points.any { it.altitude != null }
        if (hasElevation || !addElevation) return track

        val enrichedPoints = ImporterUtils.enrichWithElevation(
            client,
            track.points.map { it.latLng }
        )
        val merged = track.points.mapIndexed { i, pt ->
            pt.copy(altitude = enrichedPoints.getOrNull(i)?.altitude)
        }
        return track.copy(points = merged)
    }

    companion object {
        private val fitUrlPattern = Regex("""^https?://.+/[^/]+\.fit(\?.*)?$""", RegexOption.IGNORE_CASE)

        fun isFitUrl(input: String): Boolean = fitUrlPattern.matches(input.trim())
    }
}
