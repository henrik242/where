package no.synth.where.data

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class FylkeDownloaderTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun hasCachedData_returnsFalseWhenNoFile() {
        assertFalse(FylkeDownloader.hasCachedData(PlatformFile(tempFolder.root)))
    }

    @Test
    fun hasCachedData_returnsTrueWhenFileExists() {
        File(tempFolder.root, "norske_fylker_cached.json").writeText("{}")
        assertTrue(FylkeDownloader.hasCachedData(PlatformFile(tempFolder.root)))
    }

    @Test
    fun getCachedFile_returnsNullWhenNoFile() {
        assertNull(FylkeDownloader.getCachedFile(PlatformFile(tempFolder.root)))
    }

    @Test
    fun getCachedFile_returnsFileWhenExists() {
        File(tempFolder.root, "norske_fylker_cached.json").writeText("{}")
        val result = FylkeDownloader.getCachedFile(PlatformFile(tempFolder.root))
        assertNotNull(result)
        assertTrue(result!!.exists())
        assertEquals("{}", result.readText())
    }
}
