package no.synth.where.data

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import no.synth.where.util.Logger

class GpxUrlImporter(
    private val client: HttpClient = createDefaultHttpClient()
) {

    suspend fun importFromUrl(
        url: String,
        addElevation: Boolean = true
    ): Track? {
        val gpxContent = try {
            client.get(url.trim()).bodyAsText()
        } catch (e: Exception) {
            Logger.e(e, "Failed to fetch GPX from URL: $url")
            return null
        }

        val track = Track.fromGPX(gpxContent)
        if (track == null) {
            Logger.e("Failed to parse GPX content from URL: $url")
            return null
        }

        Logger.d("Parsed GPX from URL: ${track.points.size} points, name=${track.name}")

        val hasElevation = track.points.any { it.altitude != null }
        if (hasElevation || !addElevation) return track

        val enrichedPoints = ImporterUtils.enrichWithElevation(
            client,
            track.points.map { it.latLng }
        )
        return track.copy(points = enrichedPoints)
    }

    companion object {
        private val gpxUrlPattern = Regex("""^https?://.+\.gpx(\?.*)?$""", RegexOption.IGNORE_CASE)

        fun isGpxUrl(input: String): Boolean = gpxUrlPattern.matches(input.trim())
    }
}
