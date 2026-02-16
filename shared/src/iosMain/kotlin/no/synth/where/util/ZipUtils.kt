package no.synth.where.util

import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.UByteVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import platform.zlib.Z_FINISH
import platform.zlib.Z_OK
import platform.zlib.Z_STREAM_END
import platform.zlib.inflate
import platform.zlib.inflateEnd
import platform.zlib.inflateInit2
import platform.zlib.z_stream

@OptIn(ExperimentalForeignApi::class)
actual fun extractFirstFileFromZip(zipData: ByteArray, extension: String): ByteArray? {
    var offset = 0
    while (offset + 30 <= zipData.size) {
        // Check local file header signature: PK\x03\x04
        if (zipData[offset] != 0x50.toByte() ||
            zipData[offset + 1] != 0x4B.toByte() ||
            zipData[offset + 2] != 0x03.toByte() ||
            zipData[offset + 3] != 0x04.toByte()
        ) break

        val compressionMethod = readUShort(zipData, offset + 8)
        val compressedSize = readUInt(zipData, offset + 18)
        val uncompressedSize = readUInt(zipData, offset + 22)
        val fileNameLength = readUShort(zipData, offset + 26)
        val extraFieldLength = readUShort(zipData, offset + 28)

        val fileNameStart = offset + 30
        val fileName = zipData.decodeToString(fileNameStart, fileNameStart + fileNameLength)
        val dataStart = fileNameStart + fileNameLength + extraFieldLength

        if (fileName.endsWith(extension, ignoreCase = true)) {
            return when (compressionMethod) {
                0 -> zipData.copyOfRange(dataStart, dataStart + compressedSize)
                8 -> inflateData(zipData, dataStart, compressedSize, uncompressedSize)
                else -> null
            }
        }

        offset = dataStart + compressedSize
    }
    return null
}

private fun readUShort(data: ByteArray, offset: Int): Int =
    (data[offset].toInt() and 0xFF) or ((data[offset + 1].toInt() and 0xFF) shl 8)

private fun readUInt(data: ByteArray, offset: Int): Int =
    (data[offset].toInt() and 0xFF) or
            ((data[offset + 1].toInt() and 0xFF) shl 8) or
            ((data[offset + 2].toInt() and 0xFF) shl 16) or
            ((data[offset + 3].toInt() and 0xFF) shl 24)

@OptIn(ExperimentalForeignApi::class)
private fun inflateData(zipData: ByteArray, dataStart: Int, compressedSize: Int, uncompressedSize: Int): ByteArray? {
    val compressed = zipData.copyOfRange(dataStart, dataStart + compressedSize)
    val output = ByteArray(uncompressedSize)

    return memScoped {
        val stream = alloc<z_stream>()
        stream.next_in = null
        stream.avail_in = 0u
        stream.zalloc = null
        stream.zfree = null
        stream.opaque = null

        // -15 = raw deflate (no zlib/gzip header)
        if (inflateInit2(stream.ptr, -15) != Z_OK) return null

        compressed.usePinned { pinnedIn ->
            output.usePinned { pinnedOut ->
                stream.next_in = pinnedIn.addressOf(0).reinterpret<UByteVar>()
                stream.avail_in = compressedSize.convert()
                stream.next_out = pinnedOut.addressOf(0).reinterpret<UByteVar>()
                stream.avail_out = uncompressedSize.convert()

                val result = inflate(stream.ptr, Z_FINISH)
                inflateEnd(stream.ptr)

                if (result == Z_STREAM_END || result == Z_OK) {
                    val produced = uncompressedSize - stream.avail_out.toInt()
                    if (produced == uncompressedSize) output else output.copyOf(produced)
                } else {
                    null
                }
            }
        }
    }
}
