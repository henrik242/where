package no.synth.where.data

/**
 * Raw DEFLATE (RFC 1951) decompressor in pure Kotlin, so zip extraction works identically on every
 * platform without a JVM/Native split. Only what zip entries need: stored + fixed + dynamic blocks.
 * Throws [IllegalStateException] on malformed input; callers treat that as an unreadable entry.
 */
internal object Inflate {
    private const val MAX_BITS = 15
    // Cap on the up-front output buffer. expectedSize comes from an untrusted zip header, so a bogus
    // value must not drive a huge allocation; the buffer grows on demand past this if the data is real.
    private const val MAX_INITIAL_CAPACITY = 1 shl 20

    private val LENGTH_BASE = intArrayOf(
        3, 4, 5, 6, 7, 8, 9, 10, 11, 13, 15, 17, 19, 23, 27, 31,
        35, 43, 51, 59, 67, 83, 99, 115, 131, 163, 195, 227, 258
    )
    private val LENGTH_EXTRA = intArrayOf(
        0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 2, 2, 2, 2,
        3, 3, 3, 3, 4, 4, 4, 4, 5, 5, 5, 5, 0
    )
    private val DIST_BASE = intArrayOf(
        1, 2, 3, 4, 5, 7, 9, 13, 17, 25, 33, 49, 65, 97, 129, 193,
        257, 385, 513, 769, 1025, 1537, 2049, 3073, 4097, 6145, 8193, 12289, 16385, 24577
    )
    private val DIST_EXTRA = intArrayOf(
        0, 0, 0, 0, 1, 1, 2, 2, 3, 3, 4, 4, 5, 5, 6, 6,
        7, 7, 8, 8, 9, 9, 10, 10, 11, 11, 12, 12, 13, 13
    )
    // Order in which the code-length code lengths appear in a dynamic block header.
    private val CODE_LENGTH_ORDER = intArrayOf(16, 17, 18, 0, 8, 7, 9, 6, 10, 5, 11, 4, 12, 3, 13, 2, 14, 1, 15)

    fun inflate(input: ByteArray, expectedSize: Int = -1): ByteArray {
        val reader = BitReader(input)
        val out = ByteSink(expectedSize.coerceIn(64, MAX_INITIAL_CAPACITY))
        while (true) {
            val last = reader.readBit()
            when (reader.readBits(2)) {
                0 -> inflateStored(reader, out)
                1 -> inflateBlock(reader, out, fixedLitLen, fixedDist)
                2 -> {
                    val (litLen, dist) = readDynamicTables(reader)
                    inflateBlock(reader, out, litLen, dist)
                }
                else -> error("invalid deflate block type")
            }
            if (last == 1) break
        }
        return out.toByteArray()
    }

    private fun inflateStored(reader: BitReader, out: ByteSink) {
        reader.alignToByte()
        val len = reader.readByte() or (reader.readByte() shl 8)
        reader.readByte(); reader.readByte() // NLEN, one's complement of LEN (not validated)
        repeat(len) { out.append(reader.readByte().toByte()) }
    }

    private fun inflateBlock(reader: BitReader, out: ByteSink, litLen: Huffman, dist: Huffman) {
        while (true) {
            val symbol = litLen.decode(reader)
            when {
                symbol == 256 -> return
                symbol < 256 -> out.append(symbol.toByte())
                else -> {
                    val lengthIndex = symbol - 257
                    if (lengthIndex >= LENGTH_BASE.size) error("invalid length symbol")
                    val length = LENGTH_BASE[lengthIndex] + reader.readBits(LENGTH_EXTRA[lengthIndex])
                    val distSymbol = dist.decode(reader)
                    if (distSymbol >= DIST_BASE.size) error("invalid distance symbol")
                    val distance = DIST_BASE[distSymbol] + reader.readBits(DIST_EXTRA[distSymbol])
                    out.copyBackReference(distance, length)
                }
            }
        }
    }

    private fun readDynamicTables(reader: BitReader): Pair<Huffman, Huffman> {
        val hlit = reader.readBits(5) + 257
        val hdist = reader.readBits(5) + 1
        val hclen = reader.readBits(4) + 4
        val codeLengthLengths = IntArray(19)
        for (i in 0 until hclen) codeLengthLengths[CODE_LENGTH_ORDER[i]] = reader.readBits(3)
        val codeLengthTable = Huffman(codeLengthLengths)

        val lengths = IntArray(hlit + hdist)
        var i = 0
        while (i < lengths.size) {
            when (val sym = codeLengthTable.decode(reader)) {
                in 0..15 -> lengths[i++] = sym
                16 -> {
                    if (i == 0) error("repeat with no previous length")
                    val repeat = reader.readBits(2) + 3
                    val prev = lengths[i - 1]
                    repeat(repeat) { if (i < lengths.size) lengths[i++] = prev }
                }
                17 -> { val repeat = reader.readBits(3) + 3; repeat(repeat) { if (i < lengths.size) lengths[i++] = 0 } }
                18 -> { val repeat = reader.readBits(7) + 11; repeat(repeat) { if (i < lengths.size) lengths[i++] = 0 } }
                else -> error("invalid code length symbol")
            }
        }
        val litLen = Huffman(lengths.copyOfRange(0, hlit))
        val dist = Huffman(lengths.copyOfRange(hlit, hlit + hdist))
        return litLen to dist
    }

    private val fixedLitLen: Huffman by lazy {
        val lengths = IntArray(288)
        for (s in 0..143) lengths[s] = 8
        for (s in 144..255) lengths[s] = 9
        for (s in 256..279) lengths[s] = 7
        for (s in 280..287) lengths[s] = 8
        Huffman(lengths)
    }
    private val fixedDist: Huffman by lazy { Huffman(IntArray(30) { 5 }) }

    /** Canonical Huffman table built from per-symbol code lengths, decoded bit by bit (puff-style). */
    private class Huffman(lengths: IntArray) {
        private val count = IntArray(MAX_BITS + 1)
        private val symbols = IntArray(lengths.size)
        init {
            for (len in lengths) if (len != 0) count[len]++
            val offsets = IntArray(MAX_BITS + 2)
            for (len in 1..MAX_BITS) offsets[len + 1] = offsets[len] + count[len]
            for (sym in lengths.indices) if (lengths[sym] != 0) symbols[offsets[lengths[sym]]++] = sym
        }
        fun decode(reader: BitReader): Int {
            var code = 0
            var first = 0
            var index = 0
            for (len in 1..MAX_BITS) {
                code = code or reader.readBit()
                val c = count[len]
                if (code - first < c) return symbols[index + (code - first)]
                index += c
                first = (first + c) shl 1
                code = code shl 1
            }
            error("invalid huffman code")
        }
    }

    /** LSB-first bit reader over [data]. */
    private class BitReader(private val data: ByteArray) {
        private var pos = 0
        private var buf = 0
        private var bits = 0
        fun readBit(): Int {
            if (bits == 0) {
                if (pos >= data.size) error("unexpected end of deflate stream")
                buf = data[pos++].toInt() and 0xFF
                bits = 8
            }
            val bit = buf and 1
            buf = buf ushr 1
            bits--
            return bit
        }
        fun readBits(n: Int): Int {
            var v = 0
            for (i in 0 until n) v = v or (readBit() shl i)
            return v
        }
        fun alignToByte() { bits = 0 }
        fun readByte(): Int {
            if (pos >= data.size) error("unexpected end of deflate stream")
            return data[pos++].toInt() and 0xFF
        }
    }

    /** Growable byte output with LZ77 back-reference copies (which may overlap). */
    private class ByteSink(initialCapacity: Int) {
        private var array = ByteArray(if (initialCapacity < 16) 16 else initialCapacity)
        private var size = 0
        fun append(b: Byte) {
            ensure(size + 1)
            array[size++] = b
        }
        fun copyBackReference(distance: Int, length: Int) {
            if (distance <= 0 || distance > size) error("invalid back reference")
            ensure(size + length)
            var src = size - distance
            repeat(length) { array[size++] = array[src++] }
        }
        private fun ensure(capacity: Int) {
            if (capacity <= array.size) return
            var n = array.size
            while (n < capacity) n *= 2
            array = array.copyOf(n)
        }
        fun toByteArray(): ByteArray = array.copyOf(size)
    }
}
