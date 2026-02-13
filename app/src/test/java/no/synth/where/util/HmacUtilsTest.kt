package no.synth.where.util

import org.junit.Test
import org.junit.Assert.*

class HmacUtilsTest {

    @Test
    fun generateSignature_returnsExpectedBase64() {
        val signature = HmacUtils.generateSignature("hello", "secret")
        assertEquals("iKqz7ejTrflNJquQ07r9SiCDBww7zOnAFO4EpEOEfAs=", signature)
    }

    @Test
    fun generateSignature_isDeterministic() {
        val sig1 = HmacUtils.generateSignature("data", "key")
        val sig2 = HmacUtils.generateSignature("data", "key")
        assertEquals(sig1, sig2)
    }

    @Test
    fun generateSignature_changesWithDifferentKey() {
        val sig1 = HmacUtils.generateSignature("data", "key1")
        val sig2 = HmacUtils.generateSignature("data", "key2")
        assertNotEquals(sig1, sig2)
    }

    @Test
    fun generateSignature_changesWithDifferentData() {
        val sig1 = HmacUtils.generateSignature("data1", "key")
        val sig2 = HmacUtils.generateSignature("data2", "key")
        assertNotEquals(sig1, sig2)
    }
}
