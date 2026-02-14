package no.synth.where.data

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import no.synth.where.util.Logger
import no.synth.where.util.currentTimeMillis

object SkiTrailDownloader {
    private const val WFS_BASE = "https://wfs.geonorge.no/skwms1/wfs.turogfriluftsruter?service=WFS&version=2.0.0&request=GetFeature&typeName=app:Skil%C3%B8ype&srsName=EPSG:4326"
    private const val PAGE_SIZE = 1000
    private const val CACHE_FILENAME = "ski_trails_cached.json"

    suspend fun downloadAndCacheSkiTrails(cacheDir: PlatformFile): Boolean = withContext(Dispatchers.Default) {
        try {
            val cacheFile = cacheDir.resolve(CACHE_FILENAME)

            if (cacheFile.exists()) {
                val ageInDays = (currentTimeMillis() - cacheFile.lastModified()) / (1000 * 60 * 60 * 24)
                if (ageInDays < 7) {
                    return@withContext true
                }
            }

            val allTrails = mutableListOf<SkiTrail>()
            val client = createDefaultHttpClient()
            try {
                var startIndex = 0
                while (true) {
                    val url = "$WFS_BASE&count=$PAGE_SIZE&startIndex=$startIndex"
                    Logger.d("Downloading ski trails page startIndex=$startIndex")
                    val gmlText = client.get(url).bodyAsText()
                    val pageTrails = parseGml(gmlText)
                    allTrails.addAll(pageTrails)
                    if (pageTrails.size < PAGE_SIZE) break
                    startIndex += PAGE_SIZE
                }
            } finally {
                client.close()
            }

            if (allTrails.isEmpty()) {
                Logger.e("No ski trail data found or parsing failed")
                return@withContext false
            }

            val json = trailsToJson(allTrails)
            cacheFile.writeBytes(json.encodeToByteArray())
            Logger.d("Cached ${allTrails.size} ski trails")
            true
        } catch (e: Exception) {
            Logger.e(e, "Failed to download ski trails")
            false
        }
    }

    fun getCachedFile(cacheDir: PlatformFile): PlatformFile? {
        val cacheFile = cacheDir.resolve(CACHE_FILENAME)
        return if (cacheFile.exists()) cacheFile else null
    }

    fun hasCachedData(cacheDir: PlatformFile): Boolean {
        return cacheDir.resolve(CACHE_FILENAME).exists()
    }

    internal fun parseGml(gml: String): List<SkiTrail> {
        val trails = mutableListOf<SkiTrail>()
        val members = gml.split("</wfs:member>")

        for (member in members) {
            val posListContent = extractTag(member, "gml:posList") ?: continue
            val coordinates = parsePosList(posListContent)
            if (coordinates.isEmpty()) continue

            val name = extractTag(member, "app:rutenavn") ?: ""
            val lit = extractTag(member, "app:belysning")?.lowercase() == "ja"
            val difficulty = extractTag(member, "app:gradering")

            trails.add(SkiTrail(name = name, coordinates = coordinates, lit = lit, difficulty = difficulty))
        }

        return trails
    }

    private fun extractTag(text: String, tagName: String): String? {
        val openTag = "<$tagName>"
        val closeTag = "</$tagName>"
        val startIdx = text.indexOf(openTag)
        if (startIdx < 0) return null
        val contentStart = startIdx + openTag.length
        val endIdx = text.indexOf(closeTag, contentStart)
        if (endIdx < 0) return null
        return text.substring(contentStart, endIdx).trim()
    }

    private fun parsePosList(posList: String): List<List<Double>> {
        val numbers = posList.trim().split("\\s+".toRegex()).mapNotNull { it.toDoubleOrNull() }
        if (numbers.size < 2 || numbers.size % 2 != 0) return emptyList()
        return (0 until numbers.size step 2).map { i ->
            listOf(numbers[i], numbers[i + 1])
        }
    }

    private fun trailsToJson(trails: List<SkiTrail>): String {
        val sb = StringBuilder()
        sb.append("[")
        trails.forEachIndexed { index, trail ->
            if (index > 0) sb.append(",")
            val escapedName = trail.name.replace("\\", "\\\\").replace("\"", "\\\"")
            val diffStr = if (trail.difficulty != null) "\"${trail.difficulty}\"" else "null"
            sb.append("{\"n\":\"$escapedName\",\"l\":${trail.lit},\"d\":$diffStr,\"c\":[")
            trail.coordinates.forEachIndexed { ci, coord ->
                if (ci > 0) sb.append(",")
                sb.append("[${coord[0]},${coord[1]}]")
            }
            sb.append("]}")
        }
        sb.append("]")
        return sb.toString()
    }
}
