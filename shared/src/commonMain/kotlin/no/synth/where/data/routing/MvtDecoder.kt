package no.synth.where.data.routing

/** Minimal Mapbox Vector Tile (v2) decoder: layers, feature tags and geometry. */
object MvtDecoder {
    enum class GeomType { UNKNOWN, POINT, LINESTRING, POLYGON }

    /** Rings or lines in tile coordinates (0..extent), as flat x,y pairs. */
    class Feature(val type: GeomType, val tags: Map<String, Any>, val parts: List<IntArray>)

    class Layer(val name: String, val extent: Int, val features: List<Feature>)

    /** Decodes the layers whose names are in [wanted] (all when null). */
    fun decode(data: ByteArray, wanted: Set<String>? = null): List<Layer> {
        val layers = mutableListOf<Layer>()
        val r = ProtoReader(data, 0, data.size)
        while (r.hasMore()) {
            val (field, wire) = r.tag()
            if (field == 3 && wire == 2) {
                decodeLayer(r.subReader(), wanted)?.let { layers += it }
            } else {
                r.skip(wire)
            }
        }
        return layers
    }

    /** Copies only the layers named in [wanted], byte for byte; no re-encoding. */
    fun keepLayers(data: ByteArray, wanted: Set<String>): ByteArray {
        val out = ArrayList<ByteArray>()
        val r = ProtoReader(data, 0, data.size)
        while (r.hasMore()) {
            val start = r.position
            val (field, wire) = r.tag()
            if (field == 3 && wire == 2) {
                val layer = r.subReader()
                if (layerName(layer) in wanted) out += data.copyOfRange(start, r.position)
            } else {
                r.skip(wire)
            }
        }
        val result = ByteArray(out.sumOf { it.size })
        var offset = 0
        for (chunk in out) {
            chunk.copyInto(result, offset)
            offset += chunk.size
        }
        return result
    }

    private fun layerName(r: ProtoReader): String? {
        while (r.hasMore()) {
            val (field, wire) = r.tag()
            if (field == 1 && wire == 2) return r.string()
            r.skip(wire)
        }
        return null
    }

    private class RawFeature(val type: Int, val tags: IntArray, val geometry: IntArray)

    private fun decodeLayer(r: ProtoReader, wanted: Set<String>?): Layer? {
        var name = ""
        var extent = 4096
        val keys = mutableListOf<String>()
        val values = mutableListOf<Any>()
        val raw = mutableListOf<RawFeature>()
        while (r.hasMore()) {
            val (field, wire) = r.tag()
            when {
                field == 1 && wire == 2 -> {
                    name = r.string()
                    if (wanted != null && name !in wanted) return null
                }
                field == 2 && wire == 2 -> raw += decodeFeature(r.subReader())
                field == 3 && wire == 2 -> keys += r.string()
                field == 4 && wire == 2 -> values += decodeValue(r.subReader())
                field == 5 && wire == 0 -> extent = r.varint().toInt()
                else -> r.skip(wire)
            }
        }
        val features = raw.map { f ->
            val tags = HashMap<String, Any>()
            var i = 0
            while (i + 1 < f.tags.size) {
                val k = keys.getOrNull(f.tags[i])
                val v = values.getOrNull(f.tags[i + 1])
                if (k != null && v != null) tags[k] = v
                i += 2
            }
            val type = GeomType.entries.getOrElse(f.type) { GeomType.UNKNOWN }
            Feature(type, tags, decodeGeometry(f.geometry))
        }
        return Layer(name, extent, features)
    }

    private fun decodeFeature(r: ProtoReader): RawFeature {
        var type = 0
        var tags = IntArray(0)
        var geometry = IntArray(0)
        while (r.hasMore()) {
            val (field, wire) = r.tag()
            when {
                field == 2 && wire == 2 -> tags = r.packedVarints()
                field == 3 && wire == 0 -> type = r.varint().toInt()
                field == 4 && wire == 2 -> geometry = r.packedVarints()
                else -> r.skip(wire)
            }
        }
        return RawFeature(type, tags, geometry)
    }

    private fun decodeValue(r: ProtoReader): Any {
        var value: Any = ""
        while (r.hasMore()) {
            val (field, wire) = r.tag()
            value = when {
                field == 1 && wire == 2 -> r.string()
                field == 2 && wire == 5 -> Float.fromBits(r.fixed32())
                field == 3 && wire == 1 -> Double.fromBits(r.fixed64())
                field == 4 && wire == 0 -> r.varint()
                field == 5 && wire == 0 -> r.varint()
                field == 6 && wire == 0 -> zigzag(r.varint())
                field == 7 && wire == 0 -> r.varint() != 0L
                else -> {
                    r.skip(wire)
                    value
                }
            }
        }
        return value
    }

    private fun zigzag(n: Long) = (n ushr 1) xor -(n and 1)

    /** Splits the command stream into parts: each MoveTo starts a new line or ring. */
    private fun decodeGeometry(cmds: IntArray): List<IntArray> {
        val parts = mutableListOf<IntArray>()
        var current = ArrayList<Int>()
        var x = 0
        var y = 0
        var i = 0
        while (i < cmds.size) {
            val cmd = cmds[i] and 0x7
            val count = cmds[i] ushr 3
            i++
            when (cmd) {
                1, 2 -> {
                    if (cmd == 1 && current.isNotEmpty()) {
                        parts += current.toIntArray()
                        current = ArrayList()
                    }
                    repeat(count) {
                        if (i + 1 >= cmds.size) return@repeat
                        x += zigzag(cmds[i].toLong() and 0xFFFFFFFFL).toInt()
                        y += zigzag(cmds[i + 1].toLong() and 0xFFFFFFFFL).toInt()
                        i += 2
                        current += x
                        current += y
                    }
                }
                7 -> if (current.size >= 2) {
                    current += current[0]
                    current += current[1]
                }
                else -> return parts
            }
        }
        if (current.isNotEmpty()) parts += current.toIntArray()
        return parts
    }

    private class ProtoReader(private val buf: ByteArray, private var pos: Int, private val end: Int) {
        val position get() = pos

        fun hasMore() = pos < end

        fun tag(): Pair<Int, Int> {
            val t = varint().toInt()
            return (t ushr 3) to (t and 0x7)
        }

        fun varint(): Long {
            var result = 0L
            var shift = 0
            while (pos < end) {
                val b = buf[pos++].toInt()
                result = result or ((b and 0x7F).toLong() shl shift)
                if (b and 0x80 == 0) return result
                shift += 7
            }
            return result
        }

        fun fixed32(): Int {
            var v = 0
            for (k in 0 until 4) v = v or ((buf[pos + k].toInt() and 0xFF) shl (8 * k))
            pos += 4
            return v
        }

        fun fixed64(): Long {
            var v = 0L
            for (k in 0 until 8) v = v or ((buf[pos + k].toLong() and 0xFF) shl (8 * k))
            pos += 8
            return v
        }

        fun subReader(): ProtoReader {
            val len = varint().toInt()
            val sub = ProtoReader(buf, pos, minOf(pos + len, end))
            pos += len
            return sub
        }

        fun string(): String {
            val len = varint().toInt()
            val s = buf.decodeToString(pos, minOf(pos + len, end))
            pos += len
            return s
        }

        fun packedVarints(): IntArray {
            val sub = subReader()
            val out = ArrayList<Int>()
            while (sub.hasMore()) out += sub.varint().toInt()
            return out.toIntArray()
        }

        fun skip(wire: Int) {
            when (wire) {
                0 -> varint()
                1 -> pos += 8
                2 -> pos += varint().toInt()
                5 -> pos += 4
                else -> pos = end
            }
        }
    }
}
