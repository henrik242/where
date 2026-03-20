package no.synth.where.data

import no.synth.where.data.geo.LatLng
import no.synth.where.util.Logger
import no.synth.where.util.currentTimeMillis

object FitParser {

    private const val FIT_EPOCH_OFFSET = 631065600000L
    private const val SEMICIRCLE_TO_DEG = 180.0 / 2147483648.0
    private const val RECORD_MESG_NUM = 20
    private const val SINT32_INVALID = 0x7FFFFFFF
    private const val UINT16_INVALID = 0xFFFF
    private const val UINT32_INVALID = 0xFFFFFFFFL

    fun parse(data: ByteArray): List<TrackPoint> {
        try {
            val allPoints = mutableListOf<TrackPoint>()
            var fileOffset = 0

            // Parse chained FIT files (Garmin devices commonly concatenate multiple)
            while (fileOffset + 12 <= data.size) {
                val headerSize = data[fileOffset].toInt() and 0xFF
                if (headerSize < 12 || fileOffset + headerSize > data.size) break
                if (!checkMagic(data, fileOffset)) break

                val dataSize = readU32LE(data, fileOffset + 4)
                val endOffset = minOf(fileOffset + headerSize + dataSize, data.size.toLong()).toInt()

                val definitions = mutableMapOf<Int, MessageDef>()
                var lastTimestamp = 0L
                var offset = fileOffset + headerSize

                while (offset < endOffset) {
                    if (offset >= data.size) break
                    val header = data[offset].toInt() and 0xFF
                    offset++

                    if (header and 0x80 != 0) {
                        // Compressed timestamp header → data message
                        val localType = (header shr 5) and 0x03
                        val timeOffset = header and 0x1F
                        lastTimestamp = applyTimestampOffset(lastTimestamp, timeOffset)
                        val def = definitions[localType] ?: break
                        if (offset + def.dataSize > data.size) break
                        parseRecord(data, offset, def, lastTimestamp)?.let { pt ->
                            allPoints.add(pt)
                            lastTimestamp = pt.timestamp
                        }
                        offset += def.dataSize
                    } else if (header and 0x40 != 0) {
                        // Definition message
                        val localType = header and 0x0F
                        val hasDev = header and 0x20 != 0
                        val def = parseDef(data, offset, hasDev) ?: break
                        definitions[localType] = def
                        offset += def.defSize
                    } else {
                        // Normal data message
                        val localType = header and 0x0F
                        val def = definitions[localType] ?: break
                        if (offset + def.dataSize > data.size) break
                        parseRecord(data, offset, def, lastTimestamp)?.let { pt ->
                            allPoints.add(pt)
                            lastTimestamp = pt.timestamp
                        }
                        offset += def.dataSize
                    }
                }

                // Advance past this FIT file (data + 2 bytes CRC)
                fileOffset = endOffset + 2
            }

            return allPoints
        } catch (e: Exception) {
            Logger.e(e, "Error parsing FIT file")
            return emptyList()
        }
    }

    fun isFitFile(data: ByteArray): Boolean {
        if (data.size < 12) return false
        val headerSize = data[0].toInt() and 0xFF
        return headerSize >= 12 && checkMagic(data, 0)
    }

    private fun checkMagic(data: ByteArray, base: Int): Boolean =
        base + 12 <= data.size &&
        data[base + 8].toInt().toChar() == '.' &&
        data[base + 9].toInt().toChar() == 'F' &&
        data[base + 10].toInt().toChar() == 'I' &&
        data[base + 11].toInt().toChar() == 'T'

    /** Apply a 5-bit compressed timestamp offset to the last known timestamp. */
    private fun applyTimestampOffset(lastTimestampMs: Long, offset5bit: Int): Long {
        if (lastTimestampMs == 0L) return lastTimestampMs
        val lastSeconds = ((lastTimestampMs - FIT_EPOCH_OFFSET) / 1000).toInt()
        val last5bits = lastSeconds and 0x1F
        var newSeconds = (lastSeconds and 0x1F.inv()) or offset5bit
        if (offset5bit < last5bits) newSeconds += 32 // rollover
        return newSeconds * 1000L + FIT_EPOCH_OFFSET
    }

    private class FieldDef(val num: Int, val size: Int)

    private class MessageDef(
        val globalMesgNum: Int,
        val arch: Int,
        val fields: List<FieldDef>,
        val dataSize: Int,
        val defSize: Int
    )

    private fun parseDef(data: ByteArray, offset: Int, hasDev: Boolean): MessageDef? {
        if (offset + 5 > data.size) return null
        val arch = data[offset + 1].toInt() and 0xFF
        val mesgNum = if (arch == 0) readU16LE(data, offset + 2) else readU16BE(data, offset + 2)
        val numFields = data[offset + 4].toInt() and 0xFF

        var pos = offset + 5
        val fields = mutableListOf<FieldDef>()
        repeat(numFields) {
            if (pos + 3 > data.size) return null
            fields.add(FieldDef(
                num = data[pos].toInt() and 0xFF,
                size = data[pos + 1].toInt() and 0xFF
            ))
            pos += 3
        }

        var devSize = 0
        if (hasDev) {
            if (pos >= data.size) return null
            val numDevFields = data[pos].toInt() and 0xFF
            pos++
            repeat(numDevFields) {
                if (pos + 3 > data.size) return null
                devSize += data[pos + 1].toInt() and 0xFF
                pos += 3
            }
        }

        return MessageDef(
            globalMesgNum = mesgNum,
            arch = arch,
            fields = fields,
            dataSize = fields.sumOf { it.size } + devSize,
            defSize = pos - offset
        )
    }

    private fun parseRecord(data: ByteArray, offset: Int, def: MessageDef, fallbackTimestamp: Long): TrackPoint? {
        if (def.globalMesgNum != RECORD_MESG_NUM) return null

        var lat: Int? = null
        var lng: Int? = null
        var altitude: Double? = null
        var enhancedAlt: Double? = null
        var timestamp: Long? = null
        var pos = offset

        for (field in def.fields) {
            if (pos + field.size > data.size) return null
            when (field.num) {
                0 -> if (field.size == 4) {
                    val v = readS32(data, pos, def.arch)
                    if (v != SINT32_INVALID) lat = v
                }
                1 -> if (field.size == 4) {
                    val v = readS32(data, pos, def.arch)
                    if (v != SINT32_INVALID) lng = v
                }
                2 -> if (field.size == 2) {
                    val v = readU16(data, pos, def.arch)
                    if (v != UINT16_INVALID) altitude = v / 5.0 - 500.0
                }
                78 -> if (field.size == 4) {
                    val v = readU32(data, pos, def.arch)
                    if (v != UINT32_INVALID) enhancedAlt = v / 5.0 - 500.0
                }
                253 -> if (field.size == 4) {
                    val v = readU32(data, pos, def.arch)
                    if (v != UINT32_INVALID) timestamp = v * 1000L + FIT_EPOCH_OFFSET
                }
            }
            pos += field.size
        }

        if (lat == null || lng == null) return null

        return TrackPoint(
            latLng = LatLng(lat * SEMICIRCLE_TO_DEG, lng * SEMICIRCLE_TO_DEG),
            timestamp = timestamp ?: fallbackTimestamp.takeIf { it > 0 } ?: currentTimeMillis(),
            altitude = enhancedAlt ?: altitude
        )
    }

    private fun readU16LE(d: ByteArray, o: Int) =
        (d[o].toInt() and 0xFF) or ((d[o + 1].toInt() and 0xFF) shl 8)

    private fun readU16BE(d: ByteArray, o: Int) =
        ((d[o].toInt() and 0xFF) shl 8) or (d[o + 1].toInt() and 0xFF)

    private fun readU16(d: ByteArray, o: Int, arch: Int) =
        if (arch == 0) readU16LE(d, o) else readU16BE(d, o)

    private fun readU32LE(d: ByteArray, o: Int): Long =
        (d[o].toLong() and 0xFF) or
        ((d[o + 1].toLong() and 0xFF) shl 8) or
        ((d[o + 2].toLong() and 0xFF) shl 16) or
        ((d[o + 3].toLong() and 0xFF) shl 24)

    private fun readU32BE(d: ByteArray, o: Int): Long =
        ((d[o].toLong() and 0xFF) shl 24) or
        ((d[o + 1].toLong() and 0xFF) shl 16) or
        ((d[o + 2].toLong() and 0xFF) shl 8) or
        (d[o + 3].toLong() and 0xFF)

    private fun readU32(d: ByteArray, o: Int, arch: Int) =
        if (arch == 0) readU32LE(d, o) else readU32BE(d, o)

    private fun readS32(d: ByteArray, o: Int, arch: Int) =
        readU32(d, o, arch).toInt()
}
