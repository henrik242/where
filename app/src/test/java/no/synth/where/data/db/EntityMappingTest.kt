package no.synth.where.data.db

import no.synth.where.data.SavedPoint
import no.synth.where.data.Track
import no.synth.where.data.TrackPoint
import org.junit.Test
import org.junit.Assert.*
import no.synth.where.data.geo.LatLng

/**
 * Tests for the mapping logic between Room entities and domain models,
 * as used by TrackRepository and SavedPointsRepository.
 */
class EntityMappingTest {

    @Test
    fun trackEntity_fromTrack_mapsAllFields() {
        val track = Track(
            id = "t-1",
            name = "Morning Run",
            points = emptyList(),
            startTime = 1000L,
            endTime = 2000L,
            isRecording = false
        )
        val entity = TrackEntity(
            id = track.id,
            name = track.name,
            startTime = track.startTime,
            endTime = track.endTime,
            isRecording = track.isRecording
        )

        assertEquals("t-1", entity.id)
        assertEquals("Morning Run", entity.name)
        assertEquals(1000L, entity.startTime)
        assertEquals(2000L, entity.endTime)
        assertFalse(entity.isRecording)
    }

    @Test
    fun trackPointEntity_fromTrackPoint_mapsAllFields() {
        val point = TrackPoint(
            latLng = LatLng(63.4305, 10.3951),
            timestamp = 5000L,
            altitude = 150.0,
            accuracy = 3.5f
        )
        val entity = TrackPointEntity(
            trackId = "t-1",
            latitude = point.latLng.latitude,
            longitude = point.latLng.longitude,
            timestamp = point.timestamp,
            altitude = point.altitude,
            accuracy = point.accuracy,
            orderIndex = 0
        )

        assertEquals("t-1", entity.trackId)
        assertEquals(63.4305, entity.latitude, 0.0001)
        assertEquals(10.3951, entity.longitude, 0.0001)
        assertEquals(5000L, entity.timestamp)
        assertEquals(150.0, entity.altitude!!, 0.01)
        assertEquals(3.5f, entity.accuracy!!, 0.01f)
        assertEquals(0, entity.orderIndex)
    }

    @Test
    fun trackPointEntity_nullOptionalFields() {
        val entity = TrackPointEntity(
            trackId = "t-1",
            latitude = 59.9,
            longitude = 10.7,
            timestamp = 1000L,
            altitude = null,
            accuracy = null,
            orderIndex = 0
        )

        assertNull(entity.altitude)
        assertNull(entity.accuracy)
    }

    @Test
    fun trackEntity_toTrack_roundTrip() {
        val entity = TrackEntity(
            id = "t-2",
            name = "Evening Walk",
            startTime = 3000L,
            endTime = 4000L,
            isRecording = false
        )
        val pointEntities = listOf(
            TrackPointEntity(trackId = "t-2", latitude = 59.9, longitude = 10.7, timestamp = 3000L, altitude = 10.0, accuracy = 5.0f, orderIndex = 0),
            TrackPointEntity(trackId = "t-2", latitude = 59.91, longitude = 10.71, timestamp = 3500L, altitude = 12.0, accuracy = 4.0f, orderIndex = 1)
        )

        // Simulate the repository mapping logic
        val track = Track(
            id = entity.id,
            name = entity.name,
            points = pointEntities.map { pe ->
                TrackPoint(
                    latLng = LatLng(pe.latitude, pe.longitude),
                    timestamp = pe.timestamp,
                    altitude = pe.altitude,
                    accuracy = pe.accuracy
                )
            },
            startTime = entity.startTime,
            endTime = entity.endTime,
            isRecording = entity.isRecording
        )

        assertEquals("t-2", track.id)
        assertEquals("Evening Walk", track.name)
        assertEquals(2, track.points.size)
        assertEquals(59.9, track.points[0].latLng.latitude, 0.0001)
        assertEquals(59.91, track.points[1].latLng.latitude, 0.0001)
        assertEquals(3000L, track.startTime)
        assertEquals(4000L, track.endTime)
        assertFalse(track.isRecording)
    }

    @Test
    fun savedPointEntity_fromSavedPoint_mapsAllFields() {
        val point = SavedPoint(
            id = "sp-1",
            name = "Home",
            latLng = LatLng(59.9139, 10.7522),
            description = "My home",
            timestamp = 1000L,
            color = "#2196F3"
        )
        val entity = SavedPointEntity(
            id = point.id,
            name = point.name,
            latitude = point.latLng.latitude,
            longitude = point.latLng.longitude,
            description = point.description,
            timestamp = point.timestamp,
            color = point.color
        )

        assertEquals("sp-1", entity.id)
        assertEquals("Home", entity.name)
        assertEquals(59.9139, entity.latitude, 0.0001)
        assertEquals(10.7522, entity.longitude, 0.0001)
        assertEquals("My home", entity.description)
        assertEquals(1000L, entity.timestamp)
        assertEquals("#2196F3", entity.color)
    }

    @Test
    fun savedPointEntity_toSavedPoint_roundTrip() {
        val entity = SavedPointEntity(
            id = "sp-2",
            name = "Office",
            latitude = 59.9127,
            longitude = 10.7461,
            description = "Work",
            timestamp = 2000L,
            color = "#FF5722"
        )

        // Simulate the repository mapping logic
        val point = SavedPoint(
            id = entity.id,
            name = entity.name,
            latLng = LatLng(entity.latitude, entity.longitude),
            description = entity.description,
            timestamp = entity.timestamp,
            color = entity.color
        )

        assertEquals("sp-2", point.id)
        assertEquals("Office", point.name)
        assertEquals(59.9127, point.latLng.latitude, 0.0001)
        assertEquals(10.7461, point.latLng.longitude, 0.0001)
        assertEquals("Work", point.description)
        assertEquals("#FF5722", point.color)
    }

    @Test
    fun savedPointEntity_defaultValues() {
        val entity = SavedPointEntity(
            id = "sp-3",
            name = "Quick Save",
            latitude = 60.0,
            longitude = 11.0
        )

        assertEquals("", entity.description)
        assertEquals("#FF5722", entity.color)
    }

    @Test
    fun trackPointEntities_preserveOrder() {
        val points = listOf(
            TrackPoint(latLng = LatLng(59.0, 10.0), timestamp = 1000L),
            TrackPoint(latLng = LatLng(60.0, 11.0), timestamp = 2000L),
            TrackPoint(latLng = LatLng(61.0, 12.0), timestamp = 3000L)
        )

        val entities = points.mapIndexed { index, point ->
            TrackPointEntity(
                trackId = "t-1",
                latitude = point.latLng.latitude,
                longitude = point.latLng.longitude,
                timestamp = point.timestamp,
                orderIndex = index
            )
        }

        assertEquals(0, entities[0].orderIndex)
        assertEquals(1, entities[1].orderIndex)
        assertEquals(2, entities[2].orderIndex)
        assertEquals(59.0, entities[0].latitude, 0.0001)
        assertEquals(60.0, entities[1].latitude, 0.0001)
        assertEquals(61.0, entities[2].latitude, 0.0001)
    }
}
