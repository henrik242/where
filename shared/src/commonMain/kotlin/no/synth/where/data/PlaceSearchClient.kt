package no.synth.where.data

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import no.synth.where.data.geo.LatLng
import no.synth.where.util.Logger

object PlaceSearchClient {
    var client: HttpClient = createDefaultHttpClient()

    data class SearchResult(
        val name: String,
        val type: String,
        val municipality: String,
        val latLng: LatLng
    )

    suspend fun search(query: String): List<SearchResult> = withContext(Dispatchers.IO) {
        try {
            val response = client.get("https://ws.geonorge.no/stedsnavn/v1/sted") {
                url {
                    parameters.append("sok", query)
                    parameters.append("treffPerSide", "10")
                }
            }
            if (response.status.value !in 200..299) return@withContext emptyList()

            val json = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            val navn = json["navn"]?.jsonArray ?: return@withContext emptyList()

            val results = mutableListOf<SearchResult>()
            for (item in navn) {
                val itemObj = item.jsonObject

                val name = itemObj["stedsnavn"]?.jsonArray
                    ?.firstOrNull()?.jsonObject
                    ?.get("skrivemåte")?.jsonPrimitive?.content
                    ?: ""
                if (name.isBlank()) continue

                val type = itemObj["navneobjekttype"]?.jsonPrimitive?.content ?: ""

                val municipality = itemObj["kommuner"]?.jsonArray
                    ?.firstOrNull()?.jsonObject
                    ?.get("kommunenavn")?.jsonPrimitive?.content
                    ?: ""

                val reprPunkt = itemObj["representasjonspunkt"]?.jsonObject ?: continue
                val lon = reprPunkt["øst"]?.jsonPrimitive?.double ?: continue
                val lat = reprPunkt["nord"]?.jsonPrimitive?.double ?: continue

                results.add(SearchResult(name, type, municipality, LatLng(lat, lon)))
            }
            results
        } catch (e: Exception) {
            Logger.e(e, "Error searching places")
            emptyList()
        }
    }
}
