package no.synth.where.data

import kotlinx.serialization.json.*
import no.synth.where.util.Logger

object SkiTrailDataLoader {
    private val json = Json { ignoreUnknownKeys = true }

    fun loadSkiTrails(cacheDir: PlatformFile): List<SkiTrail> {
        val text = try {
            val cachedFile = SkiTrailDownloader.getCachedFile(cacheDir)
            if (cachedFile != null) {
                cachedFile.readText()
            } else {
                return emptyList()
            }
        } catch (e: Exception) {
            Logger.e(e, "Error loading ski trails")
            return emptyList()
        }

        return try {
            val array = json.parseToJsonElement(text).jsonArray
            array.mapNotNull { element ->
                try {
                    val obj = element.jsonObject
                    val name = obj["n"]?.jsonPrimitive?.content ?: ""
                    val lit = obj["l"]?.jsonPrimitive?.boolean ?: false
                    val difficulty = obj["d"]?.jsonPrimitive?.contentOrNull
                    val coords = obj["c"]?.jsonArray?.map { coord ->
                        coord.jsonArray.map { it.jsonPrimitive.double }
                    } ?: emptyList()
                    if (coords.isEmpty()) null
                    else SkiTrail(name = name, coordinates = coords, lit = lit, difficulty = difficulty)
                } catch (e: Exception) {
                    null
                }
            }
        } catch (e: Exception) {
            Logger.e(e, "Error parsing ski trails JSON")
            emptyList()
        }
    }
}
