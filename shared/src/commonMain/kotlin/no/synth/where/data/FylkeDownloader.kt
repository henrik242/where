package no.synth.where.data

import io.ktor.client.request.get
import io.ktor.client.statement.readRawBytes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import no.synth.where.util.Logger
import no.synth.where.util.currentTimeMillis
import no.synth.where.util.extractFirstFileFromZip

object FylkeDownloader {
    private const val GEOJSON_URL = "https://nedlasting.geonorge.no/geonorge/Basisdata/Fylker/GeoJSON/Basisdata_0000_Norge_4258_Fylker_GeoJSON.zip"
    private const val CACHE_FILENAME = "norske_fylker_cached.json"

    suspend fun downloadAndCacheFylker(cacheDir: PlatformFile): Boolean = withContext(Dispatchers.Default) {
        try {
            val cacheFile = cacheDir.resolve(CACHE_FILENAME)

            if (cacheFile.exists()) {
                val ageInDays = (currentTimeMillis() - cacheFile.lastModified()) / (1000 * 60 * 60 * 24)
                if (ageInDays < 7) {
                    return@withContext true
                }
                cacheFile.delete()
            }

            val client = createDefaultHttpClient()
            try {
                val zipBytes = client.get(GEOJSON_URL).readRawBytes()
                Logger.d("FylkeDownloader: downloaded %s bytes", zipBytes.size.toString())
                val extracted = extractFirstFileFromZip(zipBytes, ".geojson")
                Logger.d("FylkeDownloader: extracted %s bytes", (extracted?.size ?: 0).toString())
                if (extracted != null) {
                    cacheFile.writeBytes(extracted)
                }
            } finally {
                client.close()
            }

            if (!cacheFile.exists() || cacheFile.length() == 0L) {
                Logger.e("No GeoJSON file found in ZIP or extraction failed")
                return@withContext false
            }

            true
        } catch (e: Exception) {
            Logger.e(e, "Failed to download counties")
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
}
