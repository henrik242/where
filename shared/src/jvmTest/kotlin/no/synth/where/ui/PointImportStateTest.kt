package no.synth.where.ui

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.job
import kotlinx.coroutines.joinAll
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import no.synth.where.data.InMemorySavedPointDao
import no.synth.where.data.ParsedWaypoint
import no.synth.where.data.PlatformFile
import no.synth.where.data.SavedPointsRepository
import no.synth.where.data.db.SavedPointDao
import no.synth.where.data.db.SavedPointEntity
import no.synth.where.data.geo.LatLng
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class PointImportStateTest {

    /** Holds every insert until [release] completes, so the screen can close mid-write. */
    private class GatedDao(private val inner: InMemorySavedPointDao) : SavedPointDao by inner {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        override suspend fun insertPoint(point: SavedPointEntity) {
            entered.complete(Unit)
            release.await()
            inner.insertPoint(point)
        }
    }

    @Test
    fun leavingTheScreenMidImportStillStoresThePoints() = runBlocking {
        val dir = File(System.getProperty("java.io.tmpdir"), "point-import-${System.nanoTime()}").apply { mkdirs() }
        val inner = InMemorySavedPointDao()
        val dao = GatedDao(inner)
        val repository = SavedPointsRepository(PlatformFile(dir), dao)
        val screenScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val state = PointImportState(repository, screenScope)

        state.importSelected(listOf(ParsedWaypoint("Cabin", LatLng(61.0, 9.0), "", null)))
        withTimeout(5_000) { dao.entered.await() }

        val screenJob = screenScope.coroutineContext.job
        screenJob.cancel()
        dao.release.complete(Unit)
        withTimeout(5_000) { screenJob.join() }

        assertEquals(listOf("Cabin"), inner.all().map { it.name })
        assertNull(state.error)
        assertFalse(state.isImporting)
        dir.deleteRecursively()
        Unit
    }

    /** Stores the point, then reports cancellation, as a closed screen does mid-import. */
    private class CancellingDao(private val inner: InMemorySavedPointDao) : SavedPointDao by inner {
        override suspend fun insertPoint(point: SavedPointEntity) {
            inner.insertPoint(point)
            throw CancellationException("screen closed")
        }
    }

    @Test
    fun cancellationIsNotReportedAsAnImportError() = runBlocking {
        val dir = File(System.getProperty("java.io.tmpdir"), "point-import-${System.nanoTime()}").apply { mkdirs() }
        val repository = SavedPointsRepository(PlatformFile(dir), CancellingDao(InMemorySavedPointDao()))
        val screenScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val state = PointImportState(repository, screenScope)

        state.importSelected(listOf(ParsedWaypoint("Cabin", LatLng(61.0, 9.0), "", null)))
        withTimeout(5_000) { screenScope.coroutineContext.job.children.toList().joinAll() }

        assertNull(state.error)
        assertFalse(state.isImporting)
        screenScope.coroutineContext.job.cancel()
        dir.deleteRecursively()
        Unit
    }
}
