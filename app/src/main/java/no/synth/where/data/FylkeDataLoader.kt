package no.synth.where.data

import android.content.Context
import com.google.gson.Gson
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds

data class FylkeGeoJSON(
    val type: String,
    val features: List<FylkeFeature>
) {
    // Support both direct format and Geonorge's nested format
    companion object {
        fun fromGeonorge(json: String): FylkeGeoJSON {
            val gson = Gson()
            val root = gson.fromJson(json, com.google.gson.JsonObject::class.java)

            // Check if it's nested under "Fylke" key (Geonorge format)
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
    val coordinates: Any  // Can be Polygon or MultiPolygon
)

object FylkeDataLoader {
    fun loadFylker(context: Context): List<Region> {
        // Try to load from cached download first, fallback to assets
        val json = try {
            val cachedFile = FylkeDownloader.getCachedFile(context)
            if (cachedFile != null) {
                android.util.Log.d("FylkeDataLoader", "Loading from cached file: ${cachedFile.length()} bytes")
                cachedFile.readText()
            } else {
                android.util.Log.d("FylkeDataLoader", "Loading from bundled assets")
                context.assets.open("norske_fylker.json").bufferedReader().use { it.readText() }
            }
        } catch (e: Exception) {
            android.util.Log.e("FylkeDataLoader", "Error loading from cache, falling back to assets", e)
            context.assets.open("norske_fylker.json").bufferedReader().use { it.readText() }
        }

        val geoJson = FylkeGeoJSON.fromGeonorge(json)

        return geoJson.features.map { feature ->
            // Extract county name from Geonorge's property structure
            val name = when {
                feature.properties.containsKey("fylkesnavn") -> feature.properties["fylkesnavn"] as? String ?: "Unknown"
                feature.properties.containsKey("name") -> feature.properties["name"] as? String ?: "Unknown"
                else -> "Unknown"
            }

            // Extract county number
            val fylkesnummer = when {
                feature.properties.containsKey("fylkesnummer") -> feature.properties["fylkesnummer"] as? String ?: "00"
                else -> "00"
            }

            android.util.Log.d("FylkeDataLoader", "Loading county: $name ($fylkesnummer)")

            // Handle both Polygon and MultiPolygon
            val rings = when (feature.geometry.type) {
                "Polygon" -> {
                    @Suppress("UNCHECKED_CAST")
                    val coords = feature.geometry.coordinates as List<List<List<Double>>>
                    coords
                }
                "MultiPolygon" -> {
                    @Suppress("UNCHECKED_CAST")
                    val coords = feature.geometry.coordinates as List<List<List<List<Double>>>>
                    coords.flatMap { it }
                }
                else -> emptyList()
            }

            // Convert all rings to LatLng (GeoJSON is [lon, lat])
            val polygons = rings.map { ring ->
                ring.map { coord ->
                    LatLng(coord[1], coord[0])  // lat, lon
                }
            }

            // Calculate bounding box from all coordinates
            val allLatLngs = polygons.flatten()
            val lats = allLatLngs.map { it.latitude }
            val lons = allLatLngs.map { it.longitude }
            val boundingBox = LatLngBounds.from(
                lats.maxOrNull() ?: 0.0,
                lons.maxOrNull() ?: 0.0,
                lats.minOrNull() ?: 0.0,
                lons.minOrNull() ?: 0.0
            )

            Region(
                name = name,
                boundingBox = boundingBox,
                polygon = polygons
            )
        }
    }
}

