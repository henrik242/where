package no.synth.where.data

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import no.synth.where.data.db.SavedPointDao
import no.synth.where.data.db.SavedPointEntity
import no.synth.where.data.db.SqlDelightSavedPointDao
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * SQL-contract guard for [SavedPointDao]; see [TrackDaoContract]. Subclass per backend so the
 * Room -> SQLDelight swap is held to one spec.
 */
abstract class SavedPointDaoContract {
    abstract fun newDao(): SavedPointDao

    private fun pt(id: String, ts: Long) =
        SavedPointEntity(id = id, name = "p-$id", latitude = 60.0, longitude = 10.0, timestamp = ts)

    @Test fun getAllPointsOrdersByTimestampDesc() = runBlocking {
        val dao = newDao()
        dao.insertPoint(pt("a", 100))
        dao.insertPoint(pt("b", 300))
        dao.insertPoint(pt("c", 200))
        assertEquals(listOf("b", "c", "a"), dao.getAllPoints().first().map { it.id })
    }

    @Test fun insertPointReplacesOnConflict() = runBlocking {
        val dao = newDao()
        dao.insertPoint(pt("a", 1))
        dao.insertPoint(pt("a", 2).copy(name = "renamed"))
        val stored = dao.getAllPoints().first().single()
        assertEquals("renamed", stored.name)
        assertEquals(2L, stored.timestamp)
    }

    @Test fun deletePointByIdRemovesOnlyThatPoint() = runBlocking {
        val dao = newDao()
        dao.insertPoint(pt("a", 1)); dao.insertPoint(pt("b", 2))
        dao.deletePointById("a")
        val ids = dao.getAllPoints().first().map { it.id }
        assertTrue("a" !in ids && "b" in ids)
    }

    @Test fun insertPointPreservesNullDescriptionAndColor() = runBlocking {
        val dao = newDao()
        dao.insertPoint(SavedPointEntity(id = "a", name = "p", latitude = 60.0, longitude = 10.0, description = null, timestamp = 1, color = null))
        val stored = dao.getAllPoints().first().single()
        assertNull(stored.description)
        assertNull(stored.color)
    }

    @Test fun updatePointSetsNameDescriptionColor() = runBlocking {
        val dao = newDao()
        dao.insertPoint(pt("a", 1))
        dao.updatePoint("a", "newName", "newDesc", "#00FF00")
        val stored = dao.getAllPoints().first().single()
        assertEquals("newName", stored.name)
        assertEquals("newDesc", stored.description)
        assertEquals("#00FF00", stored.color)
    }
}

class InMemorySavedPointDaoContractTest : SavedPointDaoContract() {
    override fun newDao() = InMemorySavedPointDao()
}

class SqlDelightSavedPointDaoContractTest : SavedPointDaoContract() {
    override fun newDao() = SqlDelightSavedPointDao(freshSqlDelightDb(), kotlinx.coroutines.Dispatchers.Unconfined)
}
