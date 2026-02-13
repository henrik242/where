package no.synth.where.data

import android.content.Context
import kotlinx.serialization.json.*
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import timber.log.Timber

data class FylkeGeoJSON(
    val type: String,
    val features: List<FylkeFeature>
) {
    companion object {
        private val json = Json { ignoreUnknownKeys = true; allowTrailingComma = true }

        fun fromGeonorge(text: String): FylkeGeoJSON {
            val root = json.parseToJsonElement(text).jsonObject
            val fylkeObj = root["Fylke"]?.jsonObject
            val geoJsonObj = fylkeObj ?: root

            val type = geoJsonObj["type"]?.jsonPrimitive?.content ?: "FeatureCollection"
            val features = geoJsonObj["features"]?.jsonArray?.map { featureElement ->
                val featureObj = featureElement.jsonObject
                val featureType = featureObj["type"]?.jsonPrimitive?.content ?: "Feature"
                val properties = featureObj["properties"]?.jsonObject?.let { props ->
                    props.entries.associate { (key, value) ->
                        key to ((value as? JsonPrimitive)?.contentOrNull ?: value.toString())
                    }
                } ?: emptyMap()
                val geometry = featureObj["geometry"]?.jsonObject?.let { geom ->
                    val geomType = geom["type"]?.jsonPrimitive?.content ?: ""
                    val coordinates = geom["coordinates"] ?: JsonNull
                    FylkeGeometry(type = geomType, coordinates = coordinates)
                } ?: FylkeGeometry("", JsonNull)
                FylkeFeature(type = featureType, properties = properties, geometry = geometry)
            } ?: emptyList()

            return FylkeGeoJSON(type = type, features = features)
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
    val coordinates: JsonElement
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
            Timber.e(e, "Error loading counties")
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
                "Polygon" -> parsePolygonCoordinates(feature.geometry.coordinates)
                "MultiPolygon" -> parseMultiPolygonCoordinates(feature.geometry.coordinates)
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

    private fun parsePolygonCoordinates(coordinates: JsonElement): List<List<List<Double>>> {
        return coordinates.jsonArray.map { ring ->
            ring.jsonArray.map { coord ->
                coord.jsonArray.map { it.jsonPrimitive.double }
            }
        }
    }

    private fun parseMultiPolygonCoordinates(coordinates: JsonElement): List<List<List<Double>>> {
        return coordinates.jsonArray.flatMap { polygon ->
            polygon.jsonArray.map { ring ->
                ring.jsonArray.map { coord ->
                    coord.jsonArray.map { it.jsonPrimitive.double }
                }
            }
        }
    }
}
