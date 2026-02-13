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
        assertFalse(FylkeDownloader.hasCachedData(tempFolder.root))
    }

    @Test
    fun hasCachedData_returnsTrueWhenFileExists() {
        File(tempFolder.root, "norske_fylker_cached.json").writeText("{}")
        assertTrue(FylkeDownloader.hasCachedData(tempFolder.root))
    }

    @Test
    fun getCachedFile_returnsNullWhenNoFile() {
        assertNull(FylkeDownloader.getCachedFile(tempFolder.root))
    }

    @Test
    fun getCachedFile_returnsFileWhenExists() {
        val expected = File(tempFolder.root, "norske_fylker_cached.json")
        expected.writeText("{}")
        val result = FylkeDownloader.getCachedFile(tempFolder.root)
        assertNotNull(result)
        assertEquals(expected, result)
    }
}
