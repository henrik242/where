package no.synth.where.util

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ZipUtilsTest {

    private fun createZip(vararg entries: Pair<String, String>, stored: Boolean = false): ByteArray {
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            if (stored) zos.setLevel(0)
            for ((name, content) in entries) {
                zos.putNextEntry(ZipEntry(name))
                zos.write(content.toByteArray())
                zos.closeEntry()
            }
        }
        return baos.toByteArray()
    }

    @Test
    fun extractStoredFile() {
        val zip = createZip("test.txt" to "hello world", stored = true)
        val result = extractFirstFileFromZip(zip, ".txt")
        assertArrayEquals("hello world".toByteArray(), result)
    }

    @Test
    fun extractDeflatedFile() {
        val zip = createZip("test.txt" to "hello world")
        val result = extractFirstFileFromZip(zip, ".txt")
        assertArrayEquals("hello world".toByteArray(), result)
    }

    @Test
    fun extractByExtensionSkipsNonMatching() {
        val zip = createZip("readme.txt" to "ignore me", "data.json" to """{"key":"value"}""")
        val result = extractFirstFileFromZip(zip, ".json")
        assertArrayEquals("""{"key":"value"}""".toByteArray(), result)
    }

    @Test
    fun returnsNullForMissingExtension() {
        val zip = createZip("test.txt" to "hello world")
        val result = extractFirstFileFromZip(zip, ".csv")
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
