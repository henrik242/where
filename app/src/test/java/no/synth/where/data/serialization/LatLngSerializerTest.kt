package no.synth.where.data.serialization

import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import no.synth.where.data.SavedPoint
import no.synth.where.data.TrackPoint
import org.junit.Test
import org.junit.Assert.*
import org.maplibre.android.geometry.LatLng

class LatLngSerializerTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun trackPoint_roundTrip() {
        val original = TrackPoint(
            latLng = LatLng(59.9139, 10.7522),
            timestamp = 1000L,
            altitude = 150.0,
            accuracy = 5.0f
        )
        val encoded = json.encodeToString(original)
        val decoded = json.decodeFromString<TrackPoint>(encoded)

        assertEquals(original.latLng.latitude, decoded.latLng.latitude, 0.0001)
        assertEquals(original.latLng.longitude, decoded.latLng.longitude, 0.0001)
        assertEquals(original.timestamp, decoded.timestamp)
        assertEquals(original.altitude, decoded.altitude)
        assertEquals(original.accuracy, decoded.accuracy)
    }

    @Test
    fun savedPoint_roundTrip() {
        val original = SavedPoint(
            id = "test-id",
            name = "Oslo",
            latLng = LatLng(59.9139, 10.7522),
            description = "Capital of Norway",
            timestamp = 2000L,
            color = "#FF5722"
        )
        val encoded = json.encodeToString(original)
        val decoded = json.decodeFromString<SavedPoint>(encoded)

        assertEquals(original.id, decoded.id)
        assertEquals(original.name, decoded.name)
        assertEquals(original.latLng.latitude, decoded.latLng.latitude, 0.0001)
        assertEquals(original.latLng.longitude, decoded.latLng.longitude, 0.0001)
        assertEquals(original.description, decoded.description)
        assertEquals(original.color, decoded.color)
    }

    @Test
    fun trackPoint_nullOptionalFields_roundTrip() {
        val original = TrackPoint(
            latLng = LatLng(60.0, 11.0),
            timestamp = 3000L,
            altitude = null,
            accuracy = null
        )
        val encoded = json.encodeToString(original)
        val decoded = json.decodeFromString<TrackPoint>(encoded)

        assertNull(decoded.altitude)
        assertNull(decoded.accuracy)
    }

    @Test
    fun latLng_jsonFormat_containsLatitudeAndLongitude() {
        val point = TrackPoint(
            latLng = LatLng(63.4305, 10.3951),
            timestamp = 0L
        )
        val encoded = json.encodeToString(point)

        assertTrue("JSON should contain latitude", encoded.contains("63.4305"))
        assertTrue("JSON should contain longitude", encoded.contains("10.3951"))
    }

    @Test
    fun savedPoint_list_roundTrip() {
        val points = listOf(
            SavedPoint(id = "1", name = "A", latLng = LatLng(59.0, 10.0)),
            SavedPoint(id = "2", name = "B", latLng = LatLng(60.0, 11.0))
        )
        val encoded = json.encodeToString(points)
        val decoded = json.decodeFromString<List<SavedPoint>>(encoded)

        assertEquals(2, decoded.size)
        assertEquals("A", decoded[0].name)
        assertEquals("B", decoded[1].name)
        assertEquals(59.0, decoded[0].latLng.latitude, 0.0001)
        assertEquals(60.0, decoded[1].latLng.latitude, 0.0001)
    }
}
