package no.synth.where.data

import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class AppJsonTest {

    private val malformed = """{"lat": 59.123456, "lon": 10.654321, "name": "Home"""

    @Test
    fun appJsonErrorsLeaveOutTheInput() {
        val e = assertFailsWith<Exception> { AppJson.parseToJsonElement(malformed).jsonObject }
        assertFalse("59.123456" in e.message.orEmpty(), e.message)
    }

    @Test
    fun lenientJsonErrorsLeaveOutTheInput() {
        val e = assertFailsWith<Exception> { LenientJson.parseToJsonElement(malformed).jsonObject }
        assertFalse("59.123456" in e.message.orEmpty(), e.message)
    }
}
