package no.synth.where.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FitParserTest {

    @Test
    fun isFitFile_validHeader() {
        val data = buildMinimalFitFile(
            lat = 715827883,  // ~60.0°
            lon = 128849018,  // ~10.8°
            timestamp = 1000000000
        )
        assertTrue(FitParser.isFitFile(data))
    }

    @Test
    fun isFitFile_tooShort() {
        assertFalse(FitParser.isFitFile(ByteArray(5)))
    }

    @Test
    fun isFitFile_wrongMagic() {
        val data = ByteArray(14)
        data[0] = 14
        assertFalse(FitParser.isFitFile(data))
    }

    @Test
    fun isFitFile_gpxContent() {
        val gpx = """<?xml version="1.0"?><gpx></gpx>""".encodeToByteArray()
        assertFalse(FitParser.isFitFile(gpx))
    }

    @Test
    fun parse_singleRecord() {
        val data = buildMinimalFitFile(
            lat = 715827883,  // ~60.0°
            lon = 128849018,  // ~10.8°
            timestamp = 1000000000
        )
        val points = FitParser.parse(data)
        assertEquals(1, points.size)

        val point = points[0]
        // 715827883 * (180.0 / 2^31) ≈ 60.0
        assertTrue(point.latLng.latitude > 59.99 && point.latLng.latitude < 60.01)
        // 128849018 * (180.0 / 2^31) ≈ 10.8
        assertTrue(point.latLng.longitude > 10.79 && point.latLng.longitude < 10.81)
        // timestamp: 1000000000 * 1000 + 631065600000
        assertEquals(1631065600000L, point.timestamp)
    }

    @Test
    fun parse_withAltitude() {
        val data = buildFitFileWithAltitude(
            lat = 715827883,
            lon = 128849018,
            timestamp = 1000000000,
            altitudeRaw = 5000  // (5000 / 5.0) - 500.0 = 500.0m
        )
        val points = FitParser.parse(data)
        assertEquals(1, points.size)
        assertNotNull(points[0].altitude)
        assertEquals(500.0, points[0].altitude!!, 0.01)
    }

    @Test
    fun parse_skipsInvalidLatLon() {
        // 0x7FFFFFFF = invalid sentinel for sint32
        val data = buildMinimalFitFile(
            lat = 0x7FFFFFFF,
            lon = 128849018,
            timestamp = 1000000000
        )
        val points = FitParser.parse(data)
        assertEquals(0, points.size)
    }

    @Test
    fun parse_emptyData() {
        assertEquals(0, FitParser.parse(ByteArray(0)).size)
    }

    @Test
    fun fromFIT_returnTrack() {
        val data = buildMinimalFitFile(
            lat = 715827883,
            lon = 128849018,
            timestamp = 1000000000
        )
        val track = Track.fromFIT(data)
        assertNotNull(track)
        assertEquals(1, track.points.size)
        assertEquals("Imported Track", track.name)
    }

    @Test
    fun fromFIT_invalidReturnsNull() {
        assertNull(Track.fromFIT(ByteArray(10)))
    }

    @Test
    fun fromBytes_fitFile() {
        val data = buildMinimalFitFile(
            lat = 715827883,
            lon = 128849018,
            timestamp = 1000000000
        )
        val track = Track.fromBytes(data)
        assertNotNull(track)
        assertEquals(1, track.points.size)
    }

    @Test
    fun fromBytes_gpxContent() {
        val gpx = """<?xml version="1.0"?>
            <gpx version="1.1">
              <trk><name>Test</name><trkseg>
                <trkpt lat="60.0" lon="10.8">
                  <time>2021-09-08T00:00:00Z</time>
                </trkpt>
              </trkseg></trk>
            </gpx>""".encodeToByteArray()
        val track = Track.fromBytes(gpx)
        assertNotNull(track)
        assertEquals("Test", track.name)
    }

    @Test
    fun fromBytes_emptyReturnsNull() {
        assertNull(Track.fromBytes(ByteArray(0)))
    }

    @Test
    fun fromBytes_garbageBinaryReturnsNull() {
        val garbage = ByteArray(256) { (it * 37).toByte() }
        assertNull(Track.fromBytes(garbage))
    }

    @Test
    fun parse_skipsInvalidLon() {
        val data = buildMinimalFitFile(
            lat = 715827883,
            lon = 0x7FFFFFFF,
            timestamp = 1000000000
        )
        assertEquals(0, FitParser.parse(data).size)
    }

    @Test
    fun parse_multipleRecords() {
        val def = byteArrayOf(
            0x40, 0x00, 0x00, 0x14, 0x00, 0x03,
            0x00, 0x04, 0x85.toByte(),
            0x01, 0x04, 0x85.toByte(),
            0xFD.toByte(), 0x04, 0x86.toByte()
        )

        val record1 = ByteArray(1 + 4 + 4 + 4).also {
            it[0] = 0x00
            writeS32LE(it, 1, 715827883)
            writeS32LE(it, 5, 128849018)
            writeU32LE(it, 9, 1000000000)
        }
        val record2 = ByteArray(1 + 4 + 4 + 4).also {
            it[0] = 0x00
            writeS32LE(it, 1, 715827883)
            writeS32LE(it, 5, 128849018)
            writeU32LE(it, 9, 1000000010)
        }

        val points = FitParser.parse(wrapFit(def + record1 + record2))
        assertEquals(2, points.size)
        assertTrue(points[1].timestamp > points[0].timestamp)
    }

    @Test
    fun parse_bigEndianArchitecture() {
        val def = byteArrayOf(
            0x40, 0x00,
            0x01,                           // architecture: BE
            0x00, 0x14,                     // global mesg num: 20 in BE
            0x03,
            0x00, 0x04, 0x85.toByte(),
            0x01, 0x04, 0x85.toByte(),
            0xFD.toByte(), 0x04, 0x86.toByte()
        )

        val record = ByteArray(1 + 4 + 4 + 4)
        record[0] = 0x00
        writeS32BE(record, 1, 715827883)
        writeS32BE(record, 5, 128849018)
        writeU32BE(record, 9, 1000000000)

        val points = FitParser.parse(wrapFit(def + record))
        assertEquals(1, points.size)
        assertTrue(points[0].latLng.latitude > 59.99 && points[0].latLng.latitude < 60.01)
        assertEquals(1631065600000L, points[0].timestamp)
    }

    @Test
    fun parse_skipsNonRecordMessages() {
        // Define local type 0 as file_id (mesg_num=0), local type 1 as Record (mesg_num=20)
        val fileIdDef = byteArrayOf(
            0x40, 0x00, 0x00, 0x00, 0x00,   // definition, local 0, LE, mesg_num=0
            0x01,                             // 1 field
            0x00, 0x01, 0x00                  // field 0: 1 byte, enum
        )
        val fileIdData = byteArrayOf(0x00, 0x04) // data for local 0: 1 byte

        val recordDef = byteArrayOf(
            0x41, 0x00, 0x00, 0x14, 0x00,   // definition, local 1, LE, mesg_num=20
            0x03,
            0x00, 0x04, 0x85.toByte(),
            0x01, 0x04, 0x85.toByte(),
            0xFD.toByte(), 0x04, 0x86.toByte()
        )
        val recordData = ByteArray(1 + 4 + 4 + 4).also {
            it[0] = 0x01 // data, local type 1
            writeS32LE(it, 1, 715827883)
            writeS32LE(it, 5, 128849018)
            writeU32LE(it, 9, 1000000000)
        }

        val points = FitParser.parse(wrapFit(fileIdDef + fileIdData + recordDef + recordData))
        assertEquals(1, points.size)
    }

    @Test
    fun parse_chainedFitFiles() {
        val fit1 = buildMinimalFitFile(lat = 715827883, lon = 128849018, timestamp = 1000000000)
        val fit2 = buildMinimalFitFile(lat = 715827883, lon = 128849018, timestamp = 1000000100)
        val chained = fit1 + fit2

        val points = FitParser.parse(chained)
        assertEquals(2, points.size)
    }

    // --- Tests using real fixture files ---

    @Test
    fun parse_realFitFile_extractsAllPoints() {
        val points = FitParser.parse(TestFixtures.activityFit)
        assertEquals(14, points.size)
    }

    @Test
    fun parse_realFitFile_hasValidCoordinates() {
        val points = FitParser.parse(TestFixtures.activityFit)
        for (point in points) {
            assertTrue(point.latLng.latitude in -90.0..90.0, "lat out of range: ${point.latLng.latitude}")
            assertTrue(point.latLng.longitude in -180.0..180.0, "lon out of range: ${point.latLng.longitude}")
        }
    }

    @Test
    fun parse_realFitFile_hasTimestamps() {
        val points = FitParser.parse(TestFixtures.activityFit)
        // All timestamps should be monotonically non-decreasing
        for (i in 1 until points.size) {
            assertTrue(points[i].timestamp >= points[i - 1].timestamp,
                "Timestamps not monotonic at index $i")
        }
        // Should be a real FIT timestamp (after FIT epoch), not a currentTimeMillis fallback
        val fitEpochMs = 631065600000L
        assertTrue(points[0].timestamp > fitEpochMs, "Timestamp before FIT epoch: ${points[0].timestamp}")
        assertTrue(points.last().timestamp > points[0].timestamp, "Track should span some time")
    }

    @Test
    fun parse_realFitFile_hasAltitude() {
        val points = FitParser.parse(TestFixtures.activityFit)
        val withAltitude = points.count { it.altitude != null }
        assertTrue(withAltitude > 0, "Expected some points with altitude")
    }

    @Test
    fun fromFIT_realFile_returnsTrack() {
        val track = Track.fromFIT(TestFixtures.activityFit)
        assertNotNull(track)
        assertEquals(14, track.points.size)
        assertEquals("Imported Track", track.name)
        assertTrue(track.startTime < track.endTime!!)
    }

    @Test
    fun fromBytes_realFitFile() {
        val track = Track.fromBytes(TestFixtures.activityFit)
        assertNotNull(track)
        assertEquals(14, track.points.size)
    }

    @Test
    fun fromGPX_realGpxFixture() {
        val track = Track.fromGPX(TestFixtures.trackGpx)
        assertNotNull(track)
        assertEquals("Unionsleden Karlstad - Moss", track.name)
        assertEquals(10, track.points.size)
        assertTrue(track.points.all { it.altitude != null }, "All points should have elevation")
        assertTrue(track.points[0].latLng.latitude > 59.37)
        assertTrue(track.points[0].latLng.longitude > 13.48)
    }

    @Test
    fun fromBytes_realGpxFixture() {
        val track = Track.fromBytes(TestFixtures.trackGpx.encodeToByteArray())
        assertNotNull(track)
        assertEquals("Unionsleden Karlstad - Moss", track.name)
        assertEquals(10, track.points.size)
    }

    // Builds a minimal FIT file with a single Record message (lat, lon, timestamp)
    private fun buildMinimalFitFile(lat: Int, lon: Int, timestamp: Long): ByteArray {
        val def = byteArrayOf(
            0x40,                                     // definition, local type 0
            0x00,                                     // reserved
            0x00,                                     // architecture: LE
            0x14, 0x00,                               // global mesg num: 20 (Record)
            0x03,                                     // 3 fields
            0x00, 0x04, 0x85.toByte(),                // field 0 (lat): 4 bytes, sint32
            0x01, 0x04, 0x85.toByte(),                // field 1 (lon): 4 bytes, sint32
            0xFD.toByte(), 0x04, 0x86.toByte()        // field 253 (timestamp): 4 bytes, uint32
        )

        val record = ByteArray(1 + 4 + 4 + 4)
        record[0] = 0x00 // data, local type 0
        writeS32LE(record, 1, lat)
        writeS32LE(record, 5, lon)
        writeU32LE(record, 9, timestamp)

        return wrapFit(def + record)
    }

    // Builds a FIT file with altitude (lat, lon, altitude, timestamp)
    private fun buildFitFileWithAltitude(lat: Int, lon: Int, timestamp: Long, altitudeRaw: Int): ByteArray {
        val def = byteArrayOf(
            0x40,
            0x00, 0x00, 0x14, 0x00,
            0x04,                                     // 4 fields
            0x00, 0x04, 0x85.toByte(),                // lat
            0x01, 0x04, 0x85.toByte(),                // lon
            0x02, 0x02, 0x84.toByte(),                // field 2 (altitude): 2 bytes, uint16
            0xFD.toByte(), 0x04, 0x86.toByte()        // timestamp
        )

        val record = ByteArray(1 + 4 + 4 + 2 + 4)
        record[0] = 0x00
        writeS32LE(record, 1, lat)
        writeS32LE(record, 5, lon)
        writeU16LE(record, 9, altitudeRaw)
        writeU32LE(record, 11, timestamp)

        return wrapFit(def + record)
    }

    private fun wrapFit(dataRecords: ByteArray): ByteArray {
        val header = ByteArray(14)
        header[0] = 14                               // header size
        header[1] = 0x20                              // protocol version
        header[2] = 0x08; header[3] = 0x00            // profile version LE
        // data size (LE)
        header[4] = (dataRecords.size and 0xFF).toByte()
        header[5] = ((dataRecords.size shr 8) and 0xFF).toByte()
        header[6] = ((dataRecords.size shr 16) and 0xFF).toByte()
        header[7] = ((dataRecords.size shr 24) and 0xFF).toByte()
        header[8] = '.'.code.toByte()
        header[9] = 'F'.code.toByte()
        header[10] = 'I'.code.toByte()
        header[11] = 'T'.code.toByte()
        header[12] = 0x00; header[13] = 0x00          // header CRC

        val crc = byteArrayOf(0x00, 0x00)
        return header + dataRecords + crc
    }

    private fun writeS32LE(buf: ByteArray, offset: Int, value: Int) {
        buf[offset] = (value and 0xFF).toByte()
        buf[offset + 1] = ((value shr 8) and 0xFF).toByte()
        buf[offset + 2] = ((value shr 16) and 0xFF).toByte()
        buf[offset + 3] = ((value shr 24) and 0xFF).toByte()
    }

    private fun writeU32LE(buf: ByteArray, offset: Int, value: Long) {
        buf[offset] = (value and 0xFF).toByte()
        buf[offset + 1] = ((value shr 8) and 0xFF).toByte()
        buf[offset + 2] = ((value shr 16) and 0xFF).toByte()
        buf[offset + 3] = ((value shr 24) and 0xFF).toByte()
    }

    private fun writeU16LE(buf: ByteArray, offset: Int, value: Int) {
        buf[offset] = (value and 0xFF).toByte()
        buf[offset + 1] = ((value shr 8) and 0xFF).toByte()
    }

    private fun writeS32BE(buf: ByteArray, offset: Int, value: Int) {
        buf[offset] = ((value shr 24) and 0xFF).toByte()
        buf[offset + 1] = ((value shr 16) and 0xFF).toByte()
        buf[offset + 2] = ((value shr 8) and 0xFF).toByte()
        buf[offset + 3] = (value and 0xFF).toByte()
    }

    private fun writeU32BE(buf: ByteArray, offset: Int, value: Long) {
        buf[offset] = ((value shr 24) and 0xFF).toByte()
        buf[offset + 1] = ((value shr 16) and 0xFF).toByte()
        buf[offset + 2] = ((value shr 8) and 0xFF).toByte()
        buf[offset + 3] = (value and 0xFF).toByte()
    }
}
