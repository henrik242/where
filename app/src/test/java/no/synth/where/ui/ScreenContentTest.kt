package no.synth.where.ui

import org.junit.Test
import org.junit.Assert.*
import no.synth.where.data.RegionTileInfo

class ScreenContentTest {

    @Test
    fun savedPointsScreenContent_requiredParameters_areDocumented() {
        val requiredParameters = listOf(
            "savedPoints",
            "showEditDialog",
            "editingPoint",
            "onBackClick",
            "onEdit",
            "onDelete",
            "onShowOnMap",
            "onDismissEdit",
            "onSaveEdit"
        )
        assertEquals(
            "SavedPointsScreenContent must have 9 parameters",
            9,
            requiredParameters.size
        )
    }

    @Test
    fun onlineTrackingScreenContent_requiredParameters_areDocumented() {
        val requiredParameters = listOf(
            "isTrackingEnabled",
            "clientId",
            "showRegenerateDialog",
            "onBackClick",
            "onToggleTracking",
            "onViewOnWeb",
            "onShare",
            "onRegenerateClick",
            "onConfirmRegenerate",
            "onDismissRegenerate"
        )
        assertEquals(
            "OnlineTrackingScreenContent must have 10 parameters",
            10,
            requiredParameters.size
        )
    }

    @Test
    fun settingsScreenContent_requiredParameters_areDocumented() {
        val requiredParameters = listOf(
            "versionInfo",
            "onBackClick",
            "onDownloadClick",
            "onTracksClick",
            "onSavedPointsClick",
            "onOnlineTrackingClick",
            "crashReportingEnabled",
            "onCrashReportingChange",
            "currentLanguageLabel",
            "languages",
            "onLanguageSelected"
        )
        assertEquals(
            "SettingsScreenContent must have 11 parameters",
            11,
            requiredParameters.size
        )
    }

    @Test
    fun tracksScreenContent_requiredParameters_areDocumented() {
        val requiredParameters = listOf(
            "tracks",
            "trackToDelete",
            "trackToRename",
            "newTrackName",
            "showImportError",
            "importErrorMessage",
            "onBackClick",
            "onImport",
            "onExport",
            "onSave",
            "onOpen",
            "onDeleteRequest",
            "onConfirmDelete",
            "onDismissDelete",
            "onRenameRequest",
            "onNewTrackNameChange",
            "onConfirmRename",
            "onDismissRename",
            "onDismissImportError",
            "onContinue",
            "onShowOnMap"
        )
        assertEquals(
            "TracksScreenContent must have 21 parameters",
            21,
            requiredParameters.size
        )
    }

    @Test
    fun downloadScreenContent_requiredParameters_areDocumented() {
        val requiredParameters = listOf(
            "layers",
            "cacheSize",
            "isDownloading",
            "downloadRegionName",
            "downloadLayerName",
            "downloadProgress",
            "onBackClick",
            "onLayerClick",
            "onStopDownload",
            "getLayerStats",
            "refreshTrigger"
        )
        assertEquals(
            "DownloadScreenContent must have 11 parameters",
            11,
            requiredParameters.size
        )
    }

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
