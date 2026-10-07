package no.synth.where.data

import android.content.Context
import java.io.File

actual object LegacyOfflineCache {
    /** Set once from WhereApplication before use. */
    lateinit var appContext: Context

    // The old MapDownloadManager pointed MapLibre's resources/offline cache at
    // getExternalFilesDir()/maplibre-tiles and used the default mbgl-offline.db.
    private fun legacyPaths(): List<File> =
        if (!this::appContext.isInitialized) emptyList()
        else listOfNotNull(
            appContext.getExternalFilesDir(null)?.let { File(it, "maplibre-tiles") },
            File(appContext.filesDir, "mbgl-offline.db"),
        )

    actual fun exists(): Boolean = legacyPaths().any { it.exists() }

    actual fun deleteAll() {
        legacyPaths().forEach { runCatching { it.deleteRecursively() } }
    }
}
