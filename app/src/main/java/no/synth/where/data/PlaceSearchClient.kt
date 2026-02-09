package no.synth.where.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.maplibre.android.geometry.LatLng
import timber.log.Timber
import java.util.concurrent.TimeUnit

object PlaceSearchClient {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    data class SearchResult(
        val name: String,
        val type: String,
        val municipality: String,
        val latLng: LatLng
    )

    suspend fun search(query: String): List<SearchResult> = withContext(Dispatchers.IO) {
        try {
            val url = "https://ws.geonorge.no/stedsnavn/v1/sted".toHttpUrl().newBuilder()
                .addQueryParameter("sok", query)
                .addQueryParameter("treffPerSide", "10")
                .build()

            val request = Request.Builder()
                .url(url)
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return@withContext emptyList()

            val json = JSONObject(response.body.string())
            val navn = json.optJSONArray("navn") ?: return@withContext emptyList()

            val results = mutableListOf<SearchResult>()
            for (i in 0 until navn.length()) {
                val item = navn.getJSONObject(i)

                val stedsnavnArr = item.optJSONArray("stedsnavn")
                val name = stedsnavnArr
                    ?.optJSONObject(0)
                    ?.optString("skrivemåte", "")
                    ?: ""
                if (name.isBlank()) continue

                val type = item.optString("navneobjekttype", "")

                val kommunerArr = item.optJSONArray("kommuner")
                val municipality = kommunerArr
                    ?.optJSONObject(0)
                    ?.optString("kommunenavn", "")
                    ?: ""

                val reprPunkt = item.optJSONObject("representasjonspunkt")
                    ?: continue
                val lon = reprPunkt.optDouble("øst", Double.NaN)
                val lat = reprPunkt.optDouble("nord", Double.NaN)
                if (lon.isNaN() || lat.isNaN()) continue

                results.add(SearchResult(name, type, municipality, LatLng(lat, lon)))
            }
            results
        } catch (e: Exception) {
            Timber.e(e, "Error searching places")
            emptyList()
        }
    }
}
