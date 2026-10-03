package no.synth.where.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
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
        dbFile.delete()
        File("${dbFile.absolutePath}-wal").delete()
        File("${dbFile.absolutePath}-shm").delete()
    }

    private fun seed(userVersion: Int, vararg statements: String) {
        val driver = JdbcSqliteDriver(url)
        statements.forEach { driver.execute(null, it, 0) }
        driver.execute(null, "PRAGMA user_version=$userVersion", 0)
        driver.close()
    }

    private fun openAdopted(): WhereDatabase =
        WhereDatabase(JdbcSqliteDriver(url, Properties(), WhereDatabase.Schema))

    @Test fun existingV4DatabaseIsAdoptedWithoutDataLoss() {
        seed(
            4,
            """CREATE TABLE tracks (id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL,
               startTime INTEGER NOT NULL, endTime INTEGER, isRecording INTEGER NOT NULL DEFAULT 0,
               folder TEXT, sourceId TEXT, color TEXT)""",
            """CREATE TABLE track_points (id INTEGER PRIMARY KEY AUTOINCREMENT, trackId TEXT NOT NULL,
               latitude REAL NOT NULL, longitude REAL NOT NULL, timestamp INTEGER NOT NULL,
               altitude REAL, accuracy REAL, orderIndex INTEGER NOT NULL)""",
            """CREATE TABLE saved_points (id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL,
               latitude REAL NOT NULL, longitude REAL NOT NULL, description TEXT DEFAULT '',
               timestamp INTEGER NOT NULL, color TEXT DEFAULT '#FF5722')""",
            // Room's bookkeeping table; SQLDelight must ignore it.
            "CREATE TABLE room_master_table (id INTEGER PRIMARY KEY, identity_hash TEXT)",
            "INSERT INTO tracks(id, name, startTime, folder, color) VALUES ('t1', 'old', 100, 'Skiing', '#112233')",
            "INSERT INTO track_points(trackId, latitude, longitude, timestamp, orderIndex) VALUES ('t1', 60.0, 10.0, 1, 0)",
        )
        val dao = SqlDelightTrackDao(openAdopted(), Dispatchers.Unconfined)
        runBlocking {
            val track = dao.getAllTracksOnce().single()
            assertEquals("t1", track.id)
            assertEquals("Skiing", track.folder)
            assertEquals("#112233", track.color)
            assertEquals(1, dao.getPointsForTrack("t1").size)
        }
    }

    @Test fun v1DatabaseMigratesForwardPreservingData() {
        seed(
            1,
            // v1 tracks: no folder/sourceId/color (added by migrations 1/2/3).
            """CREATE TABLE tracks (id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL,
               startTime INTEGER NOT NULL, endTime INTEGER, isRecording INTEGER NOT NULL DEFAULT 0)""",
            """CREATE TABLE track_points (id INTEGER PRIMARY KEY AUTOINCREMENT, trackId TEXT NOT NULL,
               latitude REAL NOT NULL, longitude REAL NOT NULL, timestamp INTEGER NOT NULL,
               altitude REAL, accuracy REAL, orderIndex INTEGER NOT NULL)""",
            """CREATE TABLE saved_points (id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL,
               latitude REAL NOT NULL, longitude REAL NOT NULL, description TEXT DEFAULT '',
               timestamp INTEGER NOT NULL, color TEXT DEFAULT '#FF5722')""",
            "INSERT INTO tracks(id, name, startTime) VALUES ('t1', 'legacy', 100)",
        )
        val dao = SqlDelightTrackDao(openAdopted(), Dispatchers.Unconfined)
        runBlocking {
            val track = dao.getAllTracksOnce().single()
            assertEquals("legacy", track.name)
            // Columns added by the migrations exist and default to null.
            assertNull(track.folder)
            assertNull(track.sourceId)
            assertNull(track.color)
            // And the migrated schema is writable on the new columns.
            dao.updateColor("t1", "#445566")
            assertEquals("#445566", dao.getAllTracksOnce().single().color)
        }
    }
}
