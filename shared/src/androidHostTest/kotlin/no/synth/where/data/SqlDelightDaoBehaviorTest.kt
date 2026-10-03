package no.synth.where.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import no.synth.where.data.db.SqlDelightTrackDao
import no.synth.where.data.db.TrackEntity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Behaviors that only the real SQLDelight backend can express (transactions, FK enforcement,
 * query-change notifications) and the in-memory fake cannot, so they live outside the shared
 * contract. Runs against a real in-memory SQLite with foreign_keys ON, matching production.
 */
class SqlDelightDaoBehaviorTest {
    private fun track(id: String, start: Long = 0L) =
        TrackEntity(id = id, name = "t-$id", startTime = start)

    @Test fun insertingPointForMissingTrackFailsWithForeignKeysOn() {
        val db = freshSqlDelightDb()
        assertFailsWith<Throwable> {
            db.trackPointsQueries.insertTrackPoint("ghost", 1.0, 1.0, 0, null, null, 0)
        }
    }

    @Test fun transactionRollsBackWholeUnitOnConstraintFailure() = runBlocking {
        val db = freshSqlDelightDb()
        val dao = SqlDelightTrackDao(db, Dispatchers.Unconfined)
        assertFailsWith<Throwable> {
            db.transaction {
                db.tracksQueries.insertTrack("x", "n", 0, null, 0, null, null, null)
                // FK violation (parent missing) -> throws -> the whole transaction rolls back.
                db.trackPointsQueries.insertTrackPoint("nonexistent", 1.0, 1.0, 0, null, null, 0)
            }
        }
        assertTrue(dao.getAllTracksOnce().none { it.id == "x" })
    }

    @Test fun getAllTracksReemitsAfterMutation() = runBlocking {
        val db = freshSqlDelightDb()
        val dao = SqlDelightTrackDao(db, Dispatchers.Unconfined)
        val seen = mutableListOf<List<String>>()
        val job = launch(Dispatchers.Unconfined) { dao.getAllTracks().collect { seen.add(it.map { t -> t.id }) } }
        dao.insertTrack(track("a", 1))
        withTimeout(2000) { while (seen.lastOrNull() != listOf("a")) delay(10) }
        job.cancel()
        assertEquals(listOf("a"), seen.last())
    }
}
