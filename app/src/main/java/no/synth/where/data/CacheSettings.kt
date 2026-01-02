package no.synth.where.data

import android.content.Context
import android.content.SharedPreferences

object CacheSettings {
    private const val PREFS_NAME = "cache_settings"
    private const val KEY_MAX_CACHE_SIZE_MB = "max_cache_size_mb"

    fun getMaxCacheSizeMB(context: Context): Long {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getLong(KEY_MAX_CACHE_SIZE_MB, MapDownloadManager.DEFAULT_MAX_CACHE_SIZE_MB)
    }

    fun setMaxCacheSizeMB(context: Context, sizeMB: Long) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putLong(KEY_MAX_CACHE_SIZE_MB, sizeMB).apply()
    }
}

