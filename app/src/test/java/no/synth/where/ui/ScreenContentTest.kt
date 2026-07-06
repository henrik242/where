package no.synth.where.ui

import org.junit.Test
import org.junit.Assert.*
import no.synth.where.data.RegionTileInfo

class ScreenContentTest {

    @Test
    fun regionTileInfo_construction() {
        val info = RegionTileInfo(
            totalTiles = 100,
            downloadedTiles = 50,
            downloadedSize = 1024 * 1024,
            isFullyDownloaded = false
        )
        assertEquals(100, info.totalTiles)
        assertEquals(50, info.downloadedTiles)
        assertEquals(1024L * 1024, info.downloadedSize)
        assertFalse(info.isFullyDownloaded)
    }

    @Test
    fun regionTileInfo_fullyDownloaded() {
        val info = RegionTileInfo(
            totalTiles = 100,
            downloadedTiles = 100,
            downloadedSize = 5 * 1024 * 1024,
            isFullyDownloaded = true
        )
        assertTrue(info.isFullyDownloaded)
        assertEquals(info.totalTiles, info.downloadedTiles)
    }

    @Test
    fun languageOption_construction() {
        val system = LanguageOption(null, "System Default")
        assertNull(system.tag)
        assertEquals("System Default", system.displayName)

        val english = LanguageOption("en", "English")
        assertEquals("en", english.tag)
        assertEquals("English", english.displayName)
    }

    @Test
    fun layerInfo_construction() {
        val layer = LayerInfo("kartverket", "Kartverket", "Topographic maps")
        assertEquals("kartverket", layer.id)
        assertEquals("Kartverket", layer.displayName)
        assertEquals("Topographic maps", layer.description)
    }
}
