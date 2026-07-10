package no.synth.where.data

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import no.synth.where.data.geo.LatLngBounds
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class FakeDownloadEngine : DownloadEngine {
    val started = mutableListOf<String>()
    var cancelActiveCount = 0
    private val currentGate = mutableMapOf<String, CompletableDeferred<Boolean>>()
    private var activeOnProgress: ((Int, Int) -> Unit)? = null

    override suspend fun download(item: QueuedDownload, onProgress: (Int, Int) -> Unit): Boolean {
        started += item.id
        activeOnProgress = onProgress
        val gate = CompletableDeferred<Boolean>()
        currentGate[item.id] = gate
        return gate.await()
    }

    override fun cancelActive() {
        cancelActiveCount++
    }

    fun complete(id: String, success: Boolean) {
        currentGate[id]?.complete(success)
    }

    fun emitProgress(map: Int, dem: Int) {
        activeOnProgress?.invoke(map, dem)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class DownloadQueueManagerTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var scope: TestScope
    private lateinit var engine: FakeDownloadEngine
    private var activeCount = 0
    private var idleCount = 0
    private lateinit var manager: DownloadQueueManager

    @BeforeTest
    fun setUp() {
        scope = TestScope(dispatcher)
        engine = FakeDownloadEngine()
        activeCount = 0
        idleCount = 0
        manager = DownloadQueueManager(
            engine = engine,
            scope = scope,
            onActive = { activeCount++ },
            onIdle = { idleCount++ },
            stallTimeoutMs = 0L, // watchdog disabled; exercised separately with a real timeout
        )
    }

    @AfterTest
    fun tearDown() {
        scope.cancel()
    }

    private fun item(name: String, layer: String = "kartverket", dem: Boolean = false) = QueuedDownload(
        region = Region(name, LatLngBounds(south = 59.0, west = 10.0, north = 60.0, east = 11.0)),
        layerId = layer,
        layerDisplayName = layer,
        maxZoom = 12,
        downloadDem = dem,
    )

    private fun statusOf(id: String) = manager.queue.value.first { it.id == id }.status

    @Test
    fun singleDownloadRunsToCompletion() = runTest(dispatcher) {
        manager.enqueue(item("A"))
        advanceUntilIdle()
        assertEquals(DownloadStatus.DOWNLOADING, statusOf("A-kartverket"))

        engine.complete("A-kartverket", true)
        advanceUntilIdle()
        assertEquals(DownloadStatus.COMPLETED, statusOf("A-kartverket"))
        assertEquals(1, activeCount)
        assertEquals(1, idleCount)
    }

    @Test
    fun downloadsRunSequentiallyInOrder() = runTest(dispatcher) {
        manager.enqueue(item("A"))
        manager.enqueue(item("B"))
        advanceUntilIdle()

        // Only the first is active; the second waits.
        assertEquals(listOf("A-kartverket"), engine.started)
        assertEquals(DownloadStatus.DOWNLOADING, statusOf("A-kartverket"))
        assertEquals(DownloadStatus.QUEUED, statusOf("B-kartverket"))

        engine.complete("A-kartverket", true)
        advanceUntilIdle()
        assertEquals(listOf("A-kartverket", "B-kartverket"), engine.started)
        assertEquals(DownloadStatus.DOWNLOADING, statusOf("B-kartverket"))

        engine.complete("B-kartverket", true)
        advanceUntilIdle()
        assertEquals(DownloadStatus.COMPLETED, statusOf("B-kartverket"))
        assertEquals(1, activeCount) // one continuous drain
        assertEquals(1, idleCount)
    }

    @Test
    fun enqueueDuringActiveDownloadIsPickedUp() = runTest(dispatcher) {
        manager.enqueue(item("A"))
        advanceUntilIdle()
        manager.enqueue(item("B"))
        advanceUntilIdle()
        assertEquals(DownloadStatus.QUEUED, statusOf("B-kartverket"))

        engine.complete("A-kartverket", true)
        advanceUntilIdle()
        assertEquals(listOf("A-kartverket", "B-kartverket"), engine.started)
    }

    @Test
    fun duplicateEnqueueWhileActiveIsIgnored() = runTest(dispatcher) {
        manager.enqueue(item("A"))
        advanceUntilIdle()
        manager.enqueue(item("A")) // same id, already downloading
        advanceUntilIdle()
        assertEquals(listOf("A-kartverket"), engine.started)
        assertEquals(1, manager.queue.value.size)
    }

    @Test
    fun reEnqueueAfterTerminalReprocesses() = runTest(dispatcher) {
        manager.enqueue(item("A"))
        advanceUntilIdle()
        engine.complete("A-kartverket", true)
        advanceUntilIdle()
        assertEquals(DownloadStatus.COMPLETED, statusOf("A-kartverket"))

        manager.enqueue(item("A"))
        advanceUntilIdle()
        assertEquals(DownloadStatus.DOWNLOADING, statusOf("A-kartverket"))
        assertEquals(listOf("A-kartverket", "A-kartverket"), engine.started)
        // A second drain cycle started, re-firing the foreground-service start callback.
        assertEquals(2, activeCount)

        engine.complete("A-kartverket", true)
        advanceUntilIdle()
        assertEquals(2, idleCount) // second drain also tore down when it drained
    }

    @Test
    fun stalledDownloadFailsAndQueueContinues() = runTest(dispatcher) {
        val wdEngine = FakeDownloadEngine()
        val wdManager = DownloadQueueManager(wdEngine, scope, stallTimeoutMs = 1_000L)
        wdManager.enqueue(item("A"))
        wdManager.enqueue(item("B"))
        runCurrent()
        assertEquals(listOf("A-kartverket"), wdEngine.started)

        // A never reports progress; after the stall window it fails and B takes over.
        advanceTimeBy(1_100)
        runCurrent()
        assertEquals(DownloadStatus.FAILED, wdManager.queue.value.first { it.id == "A-kartverket" }.status)
        assertEquals(1, wdEngine.cancelActiveCount)
        assertEquals(DownloadStatus.DOWNLOADING, wdManager.queue.value.first { it.id == "B-kartverket" }.status)

        // B finishes before its own watchdog window elapses.
        wdEngine.complete("B-kartverket", true)
        runCurrent()
        assertEquals(DownloadStatus.COMPLETED, wdManager.queue.value.first { it.id == "B-kartverket" }.status)
    }

    @Test
    fun stopAllFailsActiveAndQueued() = runTest(dispatcher) {
        manager.enqueue(item("A"))
        manager.enqueue(item("B"))
        advanceUntilIdle()
        assertEquals(DownloadStatus.DOWNLOADING, statusOf("A-kartverket"))

        manager.stopAll()
        advanceUntilIdle()
        assertEquals(DownloadStatus.FAILED, statusOf("A-kartverket"))
        assertEquals(DownloadStatus.FAILED, statusOf("B-kartverket"))
        assertEquals(1, engine.cancelActiveCount)
        assertEquals(1, idleCount) // drain torn down, finally ran

        // A fresh enqueue restarts processing.
        manager.enqueue(item("C"))
        advanceUntilIdle()
        assertEquals(DownloadStatus.DOWNLOADING, statusOf("C-kartverket"))
        assertEquals(2, activeCount)
    }

    @Test
    fun cancelQueuedRemovesWithoutStarting() = runTest(dispatcher) {
        manager.enqueue(item("A"))
        manager.enqueue(item("B"))
        advanceUntilIdle()

        manager.cancel("B-kartverket")
        advanceUntilIdle()
        assertTrue(manager.queue.value.none { it.id == "B-kartverket" })

        engine.complete("A-kartverket", true)
        advanceUntilIdle()
        assertEquals(listOf("A-kartverket"), engine.started) // B never started
    }

    @Test
    fun cancelActiveStopsItAndContinuesQueue() = runTest(dispatcher) {
        manager.enqueue(item("A"))
        manager.enqueue(item("B"))
        advanceUntilIdle()
        assertEquals(DownloadStatus.DOWNLOADING, statusOf("A-kartverket"))

        manager.cancel("A-kartverket")
        advanceUntilIdle()
        assertEquals(DownloadStatus.CANCELLED, statusOf("A-kartverket"))
        assertEquals(1, engine.cancelActiveCount)
        assertEquals(DownloadStatus.DOWNLOADING, statusOf("B-kartverket"))
        assertEquals(1, activeCount) // same continuous drain — worker never bounced

        engine.complete("B-kartverket", true)
        advanceUntilIdle()
        assertEquals(DownloadStatus.COMPLETED, statusOf("B-kartverket"))
        assertEquals(1, idleCount)
    }

    @Test
    fun failedItemDoesNotStallQueue() = runTest(dispatcher) {
        manager.enqueue(item("A"))
        manager.enqueue(item("B"))
        advanceUntilIdle()

        engine.complete("A-kartverket", false)
        advanceUntilIdle()
        assertEquals(DownloadStatus.FAILED, statusOf("A-kartverket"))
        assertEquals(DownloadStatus.DOWNLOADING, statusOf("B-kartverket"))

        engine.complete("B-kartverket", true)
        advanceUntilIdle()
        assertEquals(DownloadStatus.COMPLETED, statusOf("B-kartverket"))
    }

    @Test
    fun clearFinishedRemovesTerminalKeepsActive() = runTest(dispatcher) {
        manager.enqueue(item("A"))
        advanceUntilIdle()
        engine.complete("A-kartverket", true)
        advanceUntilIdle()
        manager.enqueue(item("B"))
        advanceUntilIdle()

        manager.clearFinished()
        advanceUntilIdle()
        assertTrue(manager.queue.value.none { it.id == "A-kartverket" })
        assertEquals(DownloadStatus.DOWNLOADING, statusOf("B-kartverket"))
    }

    @Test
    fun progressIsReflectedInQueue() = runTest(dispatcher) {
        manager.enqueue(item("A", dem = true))
        advanceUntilIdle()

        engine.emitProgress(map = 60, dem = 20)
        advanceUntilIdle()
        val a = manager.queue.value.first { it.id == "A-kartverket" }
        assertEquals(60, a.mapProgress)
        assertEquals(20, a.demProgress)
        assertEquals(40, a.overallProgress) // (60 + 20) / 2
    }

    @Test
    fun overallProgressIgnoresDemWhenDisabled() {
        val noDem = item("A").copy(mapProgress = 70, demProgress = -1)
        assertEquals(70, noDem.overallProgress)
    }

    @Test
    fun summaryReportsPositionAndTotal() {
        val queued = item("A").copy(status = DownloadStatus.COMPLETED)
        val active = item("B").copy(status = DownloadStatus.DOWNLOADING)
        val pending = item("C").copy(status = DownloadStatus.QUEUED)
        val summary = listOf(queued, active, pending).summary()
        assertEquals("B", summary.activeName)
        assertEquals(2, summary.position) // 1 finished + current
        assertEquals(3, summary.total)
        assertEquals(false, summary.allDone)
        assertEquals(false, summary.allSucceeded)
    }

    @Test
    fun summaryAllDoneButNotAllSucceededWhenSomeFailed() {
        val done = listOf(
            item("A").copy(status = DownloadStatus.COMPLETED),
            item("B").copy(status = DownloadStatus.FAILED),
        ).summary()
        assertNull(done.activeName)
        assertEquals(2, done.position)
        assertTrue(done.allDone)
        assertEquals(false, done.allSucceeded) // a failure means "Finished", not "All complete"
    }

    @Test
    fun summaryAllSucceededWhenEverythingCompleted() {
        val done = listOf(
            item("A").copy(status = DownloadStatus.COMPLETED),
            item("B").copy(status = DownloadStatus.COMPLETED),
        ).summary()
        assertTrue(done.allDone)
        assertTrue(done.allSucceeded)
    }
}
