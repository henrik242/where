package no.synth.where.data

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ArchiveExtractorTest {

    @Test
    fun isZipRecognizesTheLocalHeaderMagic() {
        assertTrue(ArchiveExtractor.isZip(TestFixtures.tripZip))
        assertFalse(ArchiveExtractor.isZip(TestFixtures.trackGpx.encodeToByteArray()))
        assertFalse(ArchiveExtractor.isZip(byteArrayOf()))
        assertFalse(ArchiveExtractor.isZip(byteArrayOf('P'.code.toByte(), 'K'.code.toByte())))
    }

    @Test
    fun extractInflatesMatchingEntriesAndDropsTheRest() {
        val entries = ArchiveExtractor.extract(TestFixtures.tripZip) { isTrackFileName(it) }
        assertEquals(listOf("a.gpx", "b.gpx"), entries.map { it.name })
        assertTrue(entries[0].bytes.decodeToString().contains("Alpha"))
        assertTrue(entries[1].bytes.decodeToString().contains("Beta"))
    }

    @Test
    fun extractedGpxParsesBackIntoATrack() {
        val entries = ArchiveExtractor.extract(TestFixtures.tripZip) { isTrackFileName(it) }
        val track = Track.fromBytes(entries.first().bytes)
        assertEquals("Alpha", track?.name)
        assertEquals(2, track?.points?.size)
    }

    @Test
    fun keepPredicateSelectsWhichEntriesComeBack() {
        val all = ArchiveExtractor.extract(TestFixtures.tripZip) { true }
        assertEquals(listOf("a.gpx", "b.gpx", "readme.txt"), all.map { it.name })
        val txt = ArchiveExtractor.extract(TestFixtures.tripZip) { it.endsWith(".txt") }
        assertEquals(listOf("readme.txt"), txt.map { it.name })
        assertEquals("not a track", txt.first().bytes.decodeToString().trim())
    }

    @Test
    fun extractReturnsEmptyForNonZipInput() {
        assertTrue(ArchiveExtractor.extract(TestFixtures.trackGpx.encodeToByteArray()) { true }.isEmpty())
        assertTrue(ArchiveExtractor.extract(byteArrayOf(1, 2, 3)) { true }.isEmpty())
    }

    @Test
    fun extractReturnsEmptyForTruncatedZip() {
        // Right magic bytes, but the central directory is gone: extract must bail cleanly, not crash.
        val truncated = TestFixtures.tripZip.copyOfRange(0, 60)
        assertTrue(ArchiveExtractor.isZip(truncated))
        assertTrue(ArchiveExtractor.extract(truncated) { true }.isEmpty())
    }

    @Test
    fun extractFlattensPathsAndSkipsDirsAndMacosxJunk() {
        // Finder-style zip: nested track, directory entries, and a __MACOSX/._a.gpx resource fork.
        val entries = ArchiveExtractor.extract(TestFixtures.nestedZip) { isTrackFileName(it) }
        assertEquals(listOf("a.gpx"), entries.map { it.name })
        assertEquals("Nested", Track.fromBytes(entries.single().bytes)?.name)
    }

    @Test
    fun storedEntryIsReturnedVerbatim() {
        // readme.txt is short enough that zip stored it (method 0); assert the passthrough path.
        val readme = ArchiveExtractor.extract(TestFixtures.tripZip) { true }.first { it.name == "readme.txt" }
        assertContentEquals("not a track\n".encodeToByteArray(), readme.bytes)
    }

    @Test
    fun inflateReconstructsALargeMultiBlockStreamExactly() {
        // 124 KB of highly repetitive text: exercises long back-references and multiple deflate
        // blocks, and asserts the decompressed bytes match the original exactly.
        val entry = ArchiveExtractor.extract(TestFixtures.bigZip) { it == "big.gpx" }.single()
        assertEquals(TestFixtures.bigZipContent, entry.bytes.decodeToString())
    }
}
