package no.synth.where.data

import android.content.Context
import com.google.gson.Gson
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds

data class FylkeGeoJSON(
    val type: String,
    val features: List<FylkeFeature>
) {
    companion object {
        fun fromGeonorge(json: String): FylkeGeoJSON {
            val gson = Gson()
            val root = gson.fromJson(json, com.google.gson.JsonObject::class.java)
            val fylkeObj = root.getAsJsonObject("Fylke")
            return if (fylkeObj != null) {
                gson.fromJson(fylkeObj, FylkeGeoJSON::class.java)
            } else {
                gson.fromJson(json, FylkeGeoJSON::class.java)
            }
        }
    }
}

data class FylkeFeature(
    val type: String,
    val properties: Map<String, Any>,
    val geometry: FylkeGeometry
)

data class FylkeGeometry(
    val type: String,
    val coordinates: Any
)

object FylkeDataLoader {
    fun loadFylker(context: Context): List<Region> {
        val json = try {
            val cachedFile = FylkeDownloader.getCachedFile(context)
            if (cachedFile != null) {
                cachedFile.readText()
            } else {
                return emptyList()
            }
        } catch (e: Exception) {
            android.util.Log.e("FylkeDataLoader", "Error loading counties: ${e.message}")
            return emptyList()
        }

        val geoJson = FylkeGeoJSON.fromGeonorge(json)
        return geoJson.features.map { feature ->
            val name = when {
                feature.properties.containsKey("fylkesnavn") -> feature.properties["fylkesnavn"] as? String ?: "Unknown"
                feature.properties.containsKey("name") -> feature.properties["name"] as? String ?: "Unknown"
                else -> "Unknown"
            }

            val rings = when (feature.geometry.type) {
                "Polygon" -> {
                    @Suppress("UNCHECKED_CAST")
                    feature.geometry.coordinates as List<List<List<Double>>>
                }

                "MultiPolygon" -> {
                    @Suppress("UNCHECKED_CAST")
                    val coords = feature.geometry.coordinates as List<List<List<List<Double>>>>
                    coords.flatMap { it }
                }

                else -> emptyList()
            }

            val polygons = rings.map { ring ->
                ring.map { coord -> LatLng(coord[1], coord[0]) }
            }

            val allLatLngs = polygons.flatten()
            val lats = allLatLngs.map { it.latitude }
            val lons = allLatLngs.map { it.longitude }

            Region(
                name = name,
                boundingBox = LatLngBounds.from(
                    lats.maxOrNull() ?: 0.0,
                    lons.maxOrNull() ?: 0.0,
                    lats.minOrNull() ?: 0.0,
                    lons.minOrNull() ?: 0.0
                ),
                polygon = polygons
            )
        }
    }
}

