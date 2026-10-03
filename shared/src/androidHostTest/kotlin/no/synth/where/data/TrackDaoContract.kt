package no.synth.where.data

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import no.synth.where.data.db.SqlDelightTrackDao
import no.synth.where.data.db.TrackDao
import no.synth.where.data.db.TrackEntity
import no.synth.where.data.db.TrackPointEntity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * SQL-contract guard for [TrackDao]: the behavior every backend must honor. Subclassed for both
 * backends - the in-memory fake ([InMemoryTrackDaoContractTest]) and the real SQLDelight DAO on an
 * in-memory SQLite driver ([SqlDelightTrackDaoContractTest]) - so the two are held to one spec.
 */
abstract class TrackDaoContract {
    abstract fun newDao(): TrackDao

    private fun track(id: String, start: Long = 0L, recording: Boolean = false, source: String? = null) =
        TrackEntity(id = id, name = "track-$id", startTime = start, endTime = start + 1, isRecording = recording, sourceId = source)

    private fun point(trackId: String, order: Int) =
        TrackPointEntity(trackId = trackId, latitude = 60.0 + order, longitude = 10.0, timestamp = order.toLong(), orderIndex = order)

    @Test fun getAllTracksExcludesRecordingAndOrdersByStartTimeDesc() = runBlocking {
        val dao = newDao()
        dao.insertTrack(track("a", start = 100))
        dao.insertTrack(track("b", start = 300))
        dao.insertTrack(track("c", start = 200))
        dao.insertTrack(track("live", start = 999, recording = true))
        assertEquals(listOf("b", "c", "a"), dao.getAllTracks().first().map { it.id })
    }

    @Test fun getAllTracksOnceOrdersByStartTimeDescIncludingRecording() = runBlocking {
        val dao = newDao()
        dao.insertTrack(track("a", start = 100))
        dao.insertTrack(track("live", start = 300, recording = true))
        assertEquals(listOf("live", "a"), dao.getAllTracksOnce().map { it.id })
    }

    @Test fun insertTrackReplacesOnConflict() = runBlocking {
        val dao = newDao()
        dao.insertTrack(track("a", start = 1))
        dao.insertTrack(track("a", start = 2))
        val stored = dao.getAllTracksOnce().single { it.id == "a" }
        assertEquals(2L, stored.startTime)
    }

    @Test fun insertTrackWithPointsStoresPointsOrderedByIndex() = runBlocking {
        val dao = newDao()
        dao.insertTrackWithPoints(track("a"), listOf(point("a", 2), point("a", 0), point("a", 1)))
        assertEquals(listOf(0, 1, 2), dao.getPointsForTrack("a").map { it.orderIndex })
    }

    @Test fun insertTrackPointsAssignsDistinctAutoIds() = runBlocking {
        val dao = newDao()
        dao.insertTrackWithPoints(track("a"), listOf(point("a", 0), point("a", 1)))
        val ids = dao.getPointsForTrack("a").map { it.id }
        assertEquals(2, ids.toSet().size)
        assertTrue(ids.all { it != 0L })
    }

    @Test fun deleteTrackCascadesToPoints() = runBlocking {
        val dao = newDao()
        dao.insertTrackWithPoints(track("a"), listOf(point("a", 0), point("a", 1)))
        dao.deleteTrack("a")
        assertTrue(dao.getAllTracksOnce().none { it.id == "a" })
        assertTrue(dao.getPointsForTrack("a").isEmpty())
    }

    @Test fun replaceTrackWithPointsClearsOldPoints() = runBlocking {
        val dao = newDao()
        dao.insertTrackWithPoints(track("a"), listOf(point("a", 0), point("a", 1), point("a", 2)))
        dao.replaceTrackWithPoints(track("a"), listOf(point("a", 0)))
        assertEquals(1, dao.getPointsForTrack("a").size)
    }

    @Test fun renameTrackUpdatesName() = runBlocking {
        val dao = newDao()
        dao.insertTrack(track("a"))
        dao.renameTrack("a", "renamed")
        assertEquals("renamed", dao.getAllTracksOnce().single { it.id == "a" }.name)
    }

    @Test fun updateColorSetsAndClears() = runBlocking {
        val dao = newDao()
        dao.insertTrack(track("a"))
        dao.updateColor("a", "#123456")
        assertEquals("#123456", dao.getAllTracksOnce().single { it.id == "a" }.color)
        dao.updateColor("a", null)
        assertNull(dao.getAllTracksOnce().single { it.id == "a" }.color)
    }

    @Test fun updateFolderForTracksTagsOnlyListedIds() = runBlocking {
        val dao = newDao()
        dao.insertTrack(track("a")); dao.insertTrack(track("b")); dao.insertTrack(track("c"))
        dao.updateFolderForTracks(listOf("a", "c"), "Skiing")
        val byId = dao.getAllTracksOnce().associateBy { it.id }
        assertEquals("Skiing", byId.getValue("a").folder)
        assertEquals("Skiing", byId.getValue("c").folder)
        assertNull(byId.getValue("b").folder)
    }

    @Test fun renameFolderMovesOnlyMatching() = runBlocking {
        val dao = newDao()
        dao.insertTrack(track("a")); dao.insertTrack(track("b"))
        dao.updateFolderForTracks(listOf("a"), "Skiing")
        dao.updateFolderForTracks(listOf("b"), "Hiking")
        dao.renameFolder("Skiing", "Touring")
        val byId = dao.getAllTracksOnce().associateBy { it.id }
        assertEquals("Touring", byId.getValue("a").folder)
        assertEquals("Hiking", byId.getValue("b").folder)
    }

    @Test fun clearFolderNullsMatching() = runBlocking {
        val dao = newDao()
        dao.insertTrack(track("a"))
        dao.updateFolderForTracks(listOf("a"), "Skiing")
        dao.clearFolder("Skiing")
        assertNull(dao.getAllTracksOnce().single { it.id == "a" }.folder)
    }

    @Test fun findTrackIdBySourceIdReturnsMatchOrNull() = runBlocking {
        val dao = newDao()
        dao.insertTrack(track("a", source = "strava:route:1"))
        assertEquals("a", dao.findTrackIdBySourceId("strava:route:1"))
        assertNull(dao.findTrackIdBySourceId("strava:route:missing"))
    }

    @Test fun insertTrackReplaceCascadesToPoints() = runBlocking {
        // INSERT OR REPLACE on an existing id drops the row, cascading (FK on) to its points.
        val dao = newDao()
        dao.insertTrackWithPoints(track("a"), listOf(point("a", 0), point("a", 1)))
        dao.insertTrack(track("a").copy(name = "renamed"))
        assertTrue(dao.getPointsForTrack("a").isEmpty())
    }

    @Test fun replaceTrackWithPointsKeepsOnlyTheNewPoints() = runBlocking {
        val dao = newDao()
        dao.insertTrackWithPoints(track("a"), listOf(point("a", 0), point("a", 1), point("a", 2)))
        dao.replaceTrackWithPoints(track("a"), listOf(point("a", 5)))
        assertEquals(listOf(5), dao.getPointsForTrack("a").map { it.orderIndex })
    }

    @Test fun pointAltitudeAndAccuracyRoundTrip() = runBlocking {
        val dao = newDao()
        dao.insertTrackWithPoints(
            track("a"),
            listOf(TrackPointEntity(trackId = "a", latitude = 60.0, longitude = 10.0, timestamp = 1, altitude = 123.5, accuracy = 4.25f, orderIndex = 0)),
        )
        val p = dao.getPointsForTrack("a").single()
        assertEquals(123.5, p.altitude)
        assertEquals(4.25f, p.accuracy)
    }

    @Test fun equalStartTimesReturnBothInSomeOrder() = runBlocking {
        // Tie order is unspecified (SQLite rowid vs stable sort); only membership is contractual.
        val dao = newDao()
        dao.insertTrack(track("a", start = 100))
        dao.insertTrack(track("b", start = 100))
        assertEquals(setOf("a", "b"), dao.getAllTracksOnce().map { it.id }.toSet())
    }

    @Test fun updateFolderHandlesLargeIdList() = runBlocking {
        val dao = newDao()
        val ids = (0 until 500).map { "t$it" }
        ids.forEach { dao.insertTrack(track(it)) }
        dao.updateFolderForTracks(ids, "Big")
        assertTrue(dao.getAllTracksOnce().all { it.folder == "Big" })
    }
}

class InMemoryTrackDaoContractTest : TrackDaoContract() {
    override fun newDao() = InMemoryTrackDao()
}

class SqlDelightTrackDaoContractTest : TrackDaoContract() {
    override fun newDao() = SqlDelightTrackDao(freshSqlDelightDb(), kotlinx.coroutines.Dispatchers.Unconfined)
}
