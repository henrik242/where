package no.synth.where.util

import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.ExperimentalResourceApi
import no.synth.where.resources.Res
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertNull

@OptIn(ExperimentalResourceApi::class)
class ZipUtilsTest {

    private fun readZip(name: String): ByteArray = runBlocking {
        Res.readBytes("files/$name")
    }

    @Test
    fun extractStoredFile() {
        val result = extractFirstFileFromZip(readZip("stored.zip"), ".txt")
        assertContentEquals("hello world".encodeToByteArray(), result)
    }

    @Test
    fun extractDeflatedFile() {
        val result = extractFirstFileFromZip(readZip("deflated.zip"), ".txt")
        assertContentEquals("hello world".encodeToByteArray(), result)
    }

    @Test
    fun extractByExtensionSkipsNonMatching() {
        val result = extractFirstFileFromZip(readZip("multifile.zip"), ".json")
        assertContentEquals("""{"key":"value"}""".encodeToByteArray(), result)
    }

    @Test
    fun returnsNullForMissingExtension() {
        val result = extractFirstFileFromZip(readZip("stored.zip"), ".csv")
        assertNull(result)
    }

    @Test
    fun returnsNullForInvalidData() {
        val result = extractFirstFileFromZip(byteArrayOf(0x00, 0x01, 0x02), ".txt")
        assertNull(result)
    }

    @Test
    fun returnsNullForEmptyData() {
        val result = extractFirstFileFromZip(byteArrayOf(), ".txt")
        assertNull(result)
    }
}
