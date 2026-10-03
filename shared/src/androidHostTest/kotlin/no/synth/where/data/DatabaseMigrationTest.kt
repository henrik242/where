package no.synth.where.data

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import no.synth.where.data.db.SqlDelightSavedPointDao
import no.synth.where.data.db.SqlDelightTrackDao
import no.synth.where.data.db.WhereDatabase
import java.io.File
import java.util.Properties
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Guards the Room -> SQLDelight adoption. A plain JDBC driver seeds a database the way Room left it
 * (tables + PRAGMA user_version); the schema-aware driver then opens it and runs create/migrate
 * gated on user_version vs Schema.version (4), exactly as AndroidSqliteDriver/NativeSqliteDriver do.
 */
class DatabaseMigrationTest {
    private val dbFile = File.createTempFile("where-adopt-${System.nanoTime()}", ".db").also { it.delete() }
    private val url = "jdbc:sqlite:${dbFile.absolutePath}"

    @AfterTest fun cleanup() {
        listOf("", "-wal", "-shm", "-journal").forEach { File("${dbFile.absolutePath}$it").delete() }
    }

    private fun seed(userVersion: Int, vararg statements: String) {
        val driver = JdbcSqliteDriver(url)
        statements.forEach { driver.execute(null, it, 0) }
        driver.execute(null, "PRAGMA user_version=$userVersion", 0)
        driver.close()
    }

    private fun openAdopted(): WhereDatabase =
        WhereDatabase(JdbcSqliteDriver(url, Properties(), WhereDatabase.Schema))

    private fun readUserVersion(): Long {
        val driver = JdbcSqliteDriver(url)
        val result = driver.executeQuery(null, "PRAGMA user_version", { cursor ->
            cursor.next()
            QueryResult.Value(cursor.getLong(0))
        }, 0)
        driver.close()
        return result.value ?: -1L
    }

    @Test fun existingV4DatabaseIsAdoptedWithoutMigrationOrDataLoss() {
        seed(
            4,
            TRACKS_V4, TRACK_POINTS, SAVED_POINTS,
            // Room's bookkeeping table; SQLDelight must ignore it.
            "CREATE TABLE room_master_table (id INTEGER PRIMARY KEY, identity_hash TEXT)",
            "INSERT INTO tracks(id, name, startTime, folder, color) VALUES ('t1', 'old', 100, 'Skiing', '#112233')",
            "INSERT INTO track_points(trackId, latitude, longitude, timestamp, altitude, accuracy, orderIndex) VALUES ('t1', 60.0, 10.0, 1, 12.5, 3.25, 0)",
            "INSERT INTO saved_points(id, name, latitude, longitude, timestamp) VALUES ('sp1', 'Old Point', 59.0, 11.0, 5)",
        )
        val db = openAdopted()
        val trackDao = SqlDelightTrackDao(db, Dispatchers.Unconfined)
        val pointDao = SqlDelightSavedPointDao(db, Dispatchers.Unconfined)
        runBlocking {
            val track = trackDao.getAllTracksOnce().single()
            assertEquals("t1", track.id)
            assertEquals("Skiing", track.folder)
            assertEquals("#112233", track.color)
            // Track points round-trip with content (not just count), incl. altitude/accuracy.
            val p = trackDao.getPointsForTrack("t1").single()
            assertEquals(60.0, p.latitude)
            assertEquals(10.0, p.longitude)
            assertEquals(12.5, p.altitude)
            assertEquals(3.25f, p.accuracy)
            assertEquals(0, p.orderIndex)
            // Saved points adopt too.
            assertEquals("Old Point", pointDao.getAllPoints().first().single().name)
        }
        // No migration ran: user_version is still the one Room left.
        assertEquals(4L, readUserVersion())
    }

    @Test fun v1DatabaseMigratesForward() = migratesForwardFrom(1, TRACKS_V1)

    @Test fun v2DatabaseMigratesForward() = migratesForwardFrom(2, TRACKS_V2)

    @Test fun v3DatabaseMigratesForward() = migratesForwardFrom(3, TRACKS_V3)

    /** Seeds a legacy track (+ a point) at [startVersion], opens via the schema-aware driver (runs
     *  the ALTERs up to 4), and asserts data survives, new columns default null, and schema is at v4. */
    private fun migratesForwardFrom(startVersion: Int, tracksCreate: String) {
        seed(
            startVersion,
            tracksCreate, TRACK_POINTS, SAVED_POINTS,
            "INSERT INTO tracks(id, name, startTime) VALUES ('t1', 'legacy', 100)",
            "INSERT INTO track_points(trackId, latitude, longitude, timestamp, orderIndex) VALUES ('t1', 60.0, 10.0, 1, 0)",
        )
        val dao = SqlDelightTrackDao(openAdopted(), Dispatchers.Unconfined)
        runBlocking {
            val track = dao.getAllTracksOnce().single()
            assertEquals("legacy", track.name)
            assertEquals(1, dao.getPointsForTrack("t1").size)
            // Columns added by migrations 1/2/3 exist and default to null.
            assertNull(track.folder)
            assertNull(track.sourceId)
            assertNull(track.color)
            // Migrated schema is writable on the new columns.
            dao.updateColor("t1", "#445566")
            assertEquals("#445566", dao.getAllTracksOnce().single().color)
        }
        assertEquals(4L, readUserVersion())
    }

    private companion object {
        const val TRACKS_V1 = """CREATE TABLE tracks (id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL,
            startTime INTEGER NOT NULL, endTime INTEGER, isRecording INTEGER NOT NULL DEFAULT 0)"""
        const val TRACKS_V2 = """CREATE TABLE tracks (id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL,
            startTime INTEGER NOT NULL, endTime INTEGER, isRecording INTEGER NOT NULL DEFAULT 0, folder TEXT)"""
        const val TRACKS_V3 = """CREATE TABLE tracks (id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL,
            startTime INTEGER NOT NULL, endTime INTEGER, isRecording INTEGER NOT NULL DEFAULT 0, folder TEXT, sourceId TEXT)"""
        const val TRACKS_V4 = """CREATE TABLE tracks (id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL,
            startTime INTEGER NOT NULL, endTime INTEGER, isRecording INTEGER NOT NULL DEFAULT 0, folder TEXT, sourceId TEXT, color TEXT)"""
        const val TRACK_POINTS = """CREATE TABLE track_points (id INTEGER PRIMARY KEY AUTOINCREMENT, trackId TEXT NOT NULL,
            latitude REAL NOT NULL, longitude REAL NOT NULL, timestamp INTEGER NOT NULL,
            altitude REAL, accuracy REAL, orderIndex INTEGER NOT NULL)"""
        const val SAVED_POINTS = """CREATE TABLE saved_points (id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL,
            latitude REAL NOT NULL, longitude REAL NOT NULL, description TEXT DEFAULT '',
            timestamp INTEGER NOT NULL, color TEXT DEFAULT '#FF5722')"""
    }
}
