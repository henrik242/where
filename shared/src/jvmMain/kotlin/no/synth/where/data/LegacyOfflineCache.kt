package no.synth.where.data

/** No desktop predecessor shipped a native offline cache, so there is nothing to migrate. */
actual object LegacyOfflineCache {
    actual fun exists(): Boolean = false
    actual fun deleteAll() {}
}
