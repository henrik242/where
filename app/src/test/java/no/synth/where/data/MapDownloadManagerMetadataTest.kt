package no.synth.where.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.*
import org.junit.Test

class MapDownloadManagerMetadataTest {

    @Test
    fun metadata_roundTrip_preservesFields() {
        val metadata = buildJsonObject {
            put("name", "Trondheim-kartverket")
            put("layer", "kartverket")
            put("region", "Trondheim")
        }.toString()

        val parsed = Json.parseToJsonElement(metadata).jsonObject
        assertEquals("Trondheim-kartverket", parsed["name"]?.jsonPrimitive?.content)
        assertEquals("kartverket", parsed["layer"]?.jsonPrimitive?.content)
        assertEquals("Trondheim", parsed["region"]?.jsonPrimitive?.content)
    }

    @Test
    fun metadata_roundTrip_bytesAndBack() {
        val metadata = buildJsonObject {
            put("name", "Bergen-toporaster")
            put("layer", "toporaster")
            put("region", "Bergen")
        }.toString()

        val bytes = metadata.toByteArray()
        val restored = String(bytes)
        val parsed = Json.parseToJsonElement(restored).jsonObject

        assertEquals("Bergen-toporaster", parsed["name"]?.jsonPrimitive?.content)
        assertEquals("toporaster", parsed["layer"]?.jsonPrimitive?.content)
        assertEquals("Bergen", parsed["region"]?.jsonPrimitive?.content)
    }

    @Test
    fun metadata_specialCharacters_inRegionName() {
        val metadata = buildJsonObject {
            put("name", "Troms og Finnmark-kartverket")
            put("layer", "kartverket")
            put("region", "Troms og Finnmark")
        }.toString()

        val bytes = metadata.toByteArray()
        val restored = String(bytes)
        val parsed = Json.parseToJsonElement(restored).jsonObject

        assertEquals("Troms og Finnmark", parsed["region"]?.jsonPrimitive?.content)
    }

    @Test
    fun metadata_matchesByName() {
        val regionName = "Oslo-kartverket"
        val metadata = buildJsonObject {
            put("name", regionName)
            put("layer", "kartverket")
            put("region", "Oslo")
        }.toString()

        val parsed = Json.parseToJsonElement(metadata).jsonObject
        val matches = parsed["name"]?.jsonPrimitive?.content == regionName
        assertTrue(matches)
    }

    @Test
    fun metadata_filtersByLayer() {
        val entries = listOf(
            buildJsonObject {
                put("name", "Oslo-kartverket")
                put("layer", "kartverket")
                put("region", "Oslo")
            }.toString(),
            buildJsonObject {
                put("name", "Oslo-toporaster")
                put("layer", "toporaster")
                put("region", "Oslo")
            }.toString(),
            buildJsonObject {
                put("name", "Bergen-kartverket")
                put("layer", "kartverket")
                put("region", "Bergen")
            }.toString()
        )

        val kartverketEntries = entries.filter { entry ->
            val json = Json.parseToJsonElement(entry).jsonObject
            json["layer"]?.jsonPrimitive?.content == "kartverket"
        }

        assertEquals(2, kartverketEntries.size)
    }
}
