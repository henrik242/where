package no.synth.where.data

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import no.synth.where.data.geo.LatLng
import no.synth.where.util.Logger
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sqrt

data class TerrainInfo(
    val elevation: Double,
    val slopeDegrees: Double
)

object TerrainClient {
    var client: HttpClient = createDefaultHttpClient()

    private const val OFFSET_METERS = 10.0
    private const val LAT_OFFSET = OFFSET_METERS / 111320.0
    private fun lonOffset(lat: Double) = OFFSET_METERS / (111320.0 * cos(lat * PI / 180.0))

    private const val DEM_MAX_ZOOM = 15

    suspend fun getTerrainInfo(latLng: LatLng): TerrainInfo? = withContext(Dispatchers.Default) {
        getTerrainInfoFromDem(latLng) ?: getTerrainInfoFromApi(latLng)
    }

    private suspend fun getTerrainInfoFromDem(latLng: LatLng): TerrainInfo? {
        return try {
            val lat = latLng.latitude
            val lon = latLng.longitude
            val dLat = LAT_OFFSET
            val dLon = lonOffset(lat)

            val zoom = OfflineTileReader.bestCachedZoom(lat, lon, DEM_MAX_ZOOM) ?: DEM_MAX_ZOOM
            val centerCoord = TileUtils.latLngToTileCoord(lat, lon, zoom)

            val tileData = OfflineTileReader.readCachedTile(OfflineTileReader.DEM_TILE_URL, centerCoord.z, centerCoord.x, centerCoord.y)
                ?: return null

            val centerElev = DemTileDecoder.decodeElevation(tileData, centerCoord.pixelX, centerCoord.pixelY)
                ?: return null

            val northCoord = TileUtils.latLngToTileCoord(lat + dLat, lon, zoom)
            val southCoord = TileUtils.latLngToTileCoord(lat - dLat, lon, zoom)
            val eastCoord = TileUtils.latLngToTileCoord(lat, lon + dLon, zoom)
            val westCoord = TileUtils.latLngToTileCoord(lat, lon - dLon, zoom)

            val northElev = decodeFromCoord(northCoord, centerCoord, tileData) ?: return null
            val southElev = decodeFromCoord(southCoord, centerCoord, tileData) ?: return null
            val eastElev = decodeFromCoord(eastCoord, centerCoord, tileData) ?: return null
            val westElev = decodeFromCoord(westCoord, centerCoord, tileData) ?: return null

            val dzDy = (northElev - southElev) / (2 * OFFSET_METERS)
            val dzDx = (eastElev - westElev) / (2 * OFFSET_METERS)
            val slopeRadians = atan2(sqrt(dzDx * dzDx + dzDy * dzDy), 1.0)
            val slopeDegrees = slopeRadians * 180.0 / PI

            TerrainInfo(elevation = centerElev, slopeDegrees = slopeDegrees)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Logger.e(e, "Error decoding DEM tile")
            null
        }
    }

    private suspend fun decodeFromCoord(coord: TileCoord, centerCoord: TileCoord, centerTileData: ByteArray): Double? {
        val data = if (coord.x == centerCoord.x && coord.y == centerCoord.y) {
            centerTileData
        } else {
            OfflineTileReader.readCachedTile(OfflineTileReader.DEM_TILE_URL, coord.z, coord.x, coord.y) ?: return null
        }
        return DemTileDecoder.decodeElevation(data, coord.pixelX, coord.pixelY)
    }

    private suspend fun getTerrainInfoFromApi(latLng: LatLng): TerrainInfo? {
        return try {
            val lat = latLng.latitude
            val lon = latLng.longitude
            val dLat = LAT_OFFSET
            val dLon = lonOffset(lat)

            val points = listOf(
                listOf(lon, lat),           // center
                listOf(lon, lat + dLat),    // north
                listOf(lon, lat - dLat),    // south
                listOf(lon + dLon, lat),    // east
                listOf(lon - dLon, lat)     // west
            )
            val punkter = points.joinToString(",", prefix = "[", postfix = "]") { "[${it[0]},${it[1]}]" }
            val url = "https://ws.geonorge.no/hoydedata/v1/punkt?punkter=$punkter&koordsys=4258"

            val response = client.get(url)
            if (response.status.value !in 200..299) return null

            val json = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            val results = json["punkter"]?.jsonArray ?: return null
            if (results.size < 5) return null

            val elevations = results.map { element ->
                element.jsonObject["z"]?.jsonPrimitive?.content?.toDoubleOrNull()
            }

            val centerElev = elevations[0] ?: return null
            val northElev = elevations[1] ?: return null
            val southElev = elevations[2] ?: return null
            val eastElev = elevations[3] ?: return null
            val westElev = elevations[4] ?: return null

            val dzDy = (northElev - southElev) / (2 * OFFSET_METERS)
            val dzDx = (eastElev - westElev) / (2 * OFFSET_METERS)
            val slopeRadians = atan2(sqrt(dzDx * dzDx + dzDy * dzDy), 1.0)
            val slopeDegrees = slopeRadians * 180.0 / PI

            TerrainInfo(
                elevation = centerElev,
                slopeDegrees = slopeDegrees
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Logger.e(e, "Error fetching terrain info from API")
            null
        }
    }
}
