package no.synth.where.data.routing

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class MvtDecoderTest {
    // Tiny protobuf writer, enough to build a vector tile by hand.
    private class Pb {
        val bytes = ArrayList<Byte>()
        fun varint(v: Long) {
            var n = v
            while (n and 0x7FL.inv() != 0L) {
                bytes += ((n and 0x7F) or 0x80).toByte()
                n = n ushr 7
            }
            bytes += n.toByte()
        }
        fun tag(field: Int, wire: Int) = varint(((field shl 3) or wire).toLong())
        fun bytesField(field: Int, data: ByteArray) {
            tag(field, 2)
            varint(data.size.toLong())
            bytes.addAll(data.toList())
        }
        fun packed(field: Int, values: List<Int>) = bytesField(field, Pb().apply { values.forEach { varint(it.toLong()) } }.toByteArray())
        fun toByteArray() = bytes.toByteArray()
    }

    private fun zz(n: Int) = (n shl 1) xor (n shr 31)

    @Test
    fun decodesLayerTagsAndGeometry() {
        val feature = Pb().apply {
            packed(2, listOf(0, 0, 1, 1)) // class=stream, name=Bekken
            tag(3, 0); varint(2) // LINESTRING
            // MoveTo(10,20), LineTo(+5,+0), LineTo(+0,-3)
            packed(4, listOf((1 shl 3) or 1, zz(10), zz(20), (2 shl 3) or 2, zz(5), zz(0), zz(0), zz(-3)))
        }.toByteArray()
        val value1 = Pb().apply { bytesField(1, "stream".encodeToByteArray()) }.toByteArray()
        val value2 = Pb().apply { bytesField(1, "Bekken".encodeToByteArray()) }.toByteArray()
        val layer = Pb().apply {
            bytesField(1, "waterway".encodeToByteArray())
            bytesField(2, feature)
            bytesField(3, "class".encodeToByteArray())
            bytesField(3, "name".encodeToByteArray())
            bytesField(4, value1)
            bytesField(4, value2)
            tag(5, 0); varint(4096)
        }.toByteArray()
        val skipped = Pb().apply { bytesField(1, "poi".encodeToByteArray()) }.toByteArray()
        val tile = Pb().apply {
            bytesField(3, skipped)
            bytesField(3, layer)
        }.toByteArray()

        val layers = MvtDecoder.decode(tile, setOf("waterway"))
        assertEquals(1, layers.size)
        val f = layers.single().features.single()
        assertEquals(MvtDecoder.GeomType.LINESTRING, f.type)
        assertEquals("stream", f.tags["class"])
        assertEquals("Bekken", f.tags["name"])
        assertContentEquals(intArrayOf(10, 20, 15, 20, 15, 17), f.parts.single())
    }
}
