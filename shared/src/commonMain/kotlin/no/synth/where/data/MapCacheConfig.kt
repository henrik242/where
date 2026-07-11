package no.synth.where.data

/** Shared tile-cache configuration, kept in sync across Android and iOS (Swift reads via Shared). */
object MapCacheConfig {
    /**
     * Ceiling for the ambient cache — tiles auto-saved while browsing. MapLibre defaults to only
     * ~50 MB, which evicts browsed areas quickly on an offline-first map.
     */
    val ambientCacheSizeBytes: Long = 512L * 1024 * 1024

    /**
     * Below this much free disk, downloads and auto-caching can start failing — warn the user.
     * Unrelated to [ambientCacheSizeBytes]; a disk-space floor, not a fraction of the cache cap.
     */
    val lowStorageThresholdBytes: Long = 500L * 1024 * 1024

    /** True when free storage is known (>= 0) and below [lowStorageThresholdBytes]. */
    fun isStorageLow(freeBytes: Long): Boolean = freeBytes in 0 until lowStorageThresholdBytes

    /**
     * True for the log line MapLibre emits when it cannot cache a tile — "Unable to make space for
     * entry" — the direct signal that ambient/offline caching is failing (typically storage full).
     * Matches MapLibre's hardcoded English text (logged at Info level); revisit on SDK upgrade.
     * Single-sourced here because both platforms scan their native logs for it.
     */
    fun isCacheFailureLog(message: String): Boolean = message.contains("make space", ignoreCase = true)
}
