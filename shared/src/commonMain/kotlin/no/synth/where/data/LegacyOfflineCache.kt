package no.synth.where.data

/**
 * The old native-MapLibre offline cache left behind by versions before the maplibre-compose
 * migration. The new engine uses a different store, so those downloads are orphaned - we show a
 * one-time "re-download your offline maps" notice to upgraders and reclaim the dead storage.
 */
expect object LegacyOfflineCache {
    /** True if a legacy native offline cache is present (i.e. this is an upgrade, not a fresh install). */
    fun exists(): Boolean

    /** Remove the orphaned legacy cache to reclaim disk; the user re-downloads through the new engine. */
    fun deleteAll()
}
