package no.synth.where.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URL
import no.synth.where.util.Logger
import java.util.zip.ZipInputStream

object FylkeDownloader {
    private const val GEOJSON_URL = "https://nedlasting.geonorge.no/geonorge/Basisdata/Fylker/GeoJSON/Basisdata_0000_Norge_4258_Fylker_GeoJSON.zip"
    private const val CACHE_FILENAME = "norske_fylker_cached.json"

    suspend fun downloadAndCacheFylker(context: Context): Boolean = withContext(Dispatchers.IO) {
        try {
            val cacheFile = File(context.cacheDir, CACHE_FILENAME)

            // Check if cache exists and is recent (less than 7 days old)
            if (cacheFile.exists()) {
                val ageInDays = (System.currentTimeMillis() - cacheFile.lastModified()) / (1000 * 60 * 60 * 24)
                if (ageInDays < 7) {
                    return@withContext true
                }
            }

            val url = URL(GEOJSON_URL)
            val connection = url.openConnection()
            connection.connectTimeout = 30000
            connection.readTimeout = 30000

            // Download and extract ZIP file
            connection.getInputStream().use { input ->
                ZipInputStream(input).use { zipInput ->
                    var entry = zipInput.nextEntry
                    while (entry != null) {
                        if (entry.name.endsWith(".geojson", ignoreCase = true)) {
                            cacheFile.outputStream().use { output ->
                                zipInput.copyTo(output)
                            }
                            break
                        }
                        entry = zipInput.nextEntry
                    }
                }
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

    fun getCachedFile(context: Context): File? {
        val cacheFile = File(context.cacheDir, CACHE_FILENAME)
        return if (cacheFile.exists()) cacheFile else null
    }

    fun hasCachedData(context: Context): Boolean {
        return File(context.cacheDir, CACHE_FILENAME).exists()
    }
}

