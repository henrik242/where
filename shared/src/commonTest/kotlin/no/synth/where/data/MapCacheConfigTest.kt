package no.synth.where.data

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MapCacheConfigTest {

    @Test
    fun storageLowWhenBelowThreshold() {
        assertTrue(MapCacheConfig.isStorageLow(100L * 1024 * 1024)) // 100 MB free
    }

    @Test
    fun storageOkWhenAboveThreshold() {
        assertFalse(MapCacheConfig.isStorageLow(2L * 1024 * 1024 * 1024)) // 2 GB free
    }

    @Test
    fun unknownStorageIsNotReportedLow() {
        assertFalse(MapCacheConfig.isStorageLow(-1)) // couldn't determine -> don't warn
    }

    @Test
    fun storageLowWhenExactlyZeroFree() {
        assertTrue(MapCacheConfig.isStorageLow(0)) // full disk must warn (inclusive lower bound)
    }

    @Test
    fun storageOkAtExactThreshold() {
        assertFalse(MapCacheConfig.isStorageLow(MapCacheConfig.lowStorageThresholdBytes)) // exclusive upper bound
    }

    @Test
    fun cacheFailureLogMatchesMakeSpace() {
        assertTrue(MapCacheConfig.isCacheFailureLog("Unable to make space for entry"))
    }

    @Test
    fun cacheFailureLogIsCaseInsensitive() {
        assertTrue(MapCacheConfig.isCacheFailureLog("UNABLE TO MAKE SPACE FOR ENTRY"))
    }

    @Test
    fun cacheFailureLogIgnoresUnrelatedMessages() {
        assertFalse(MapCacheConfig.isCacheFailureLog("Tile loaded successfully"))
    }
}
