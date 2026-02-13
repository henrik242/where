package no.synth.where.util

import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import timber.log.Timber

class LoggerTest {

    private val logs = mutableListOf<LogEntry>()

    data class LogEntry(
        val priority: Int,
        val tag: String?,
        val message: String,
        val throwable: Throwable?
    )

    private val testTree = object : Timber.Tree() {
        override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
            logs.add(LogEntry(priority, tag, message, t))
        }
    }

    @Before
    fun setUp() {
        Timber.plant(testTree)
    }

    @After
    fun tearDown() {
        Timber.uproot(testTree)
        logs.clear()
    }

    @Test
    fun d_delegatesToTimber() {
        Logger.d("debug message")
        assertEquals(1, logs.size)
        assertEquals(android.util.Log.DEBUG, logs[0].priority)
        assertEquals("debug message", logs[0].message)
    }

    @Test
    fun d_formatsArgs() {
        Logger.d("value: %d", 42)
        assertEquals(1, logs.size)
        assertEquals("value: 42", logs[0].message)
    }

    @Test
    fun w_delegatesToTimber() {
        Logger.w("warning message")
        assertEquals(1, logs.size)
        assertEquals(android.util.Log.WARN, logs[0].priority)
        assertEquals("warning message", logs[0].message)
    }

    @Test
    fun e_delegatesToTimber() {
        Logger.e("error message")
        assertEquals(1, logs.size)
        assertEquals(android.util.Log.ERROR, logs[0].priority)
        assertEquals("error message", logs[0].message)
    }

    @Test
    fun e_withThrowable_delegatesToTimber() {
        val exception = RuntimeException("test error")
        Logger.e(exception, "something failed")
        assertEquals(1, logs.size)
        assertEquals(android.util.Log.ERROR, logs[0].priority)
        assertTrue(logs[0].message.startsWith("something failed"))
    }

    @Test
    fun e_withThrowable_formatsArgs() {
        val exception = IllegalStateException("bad state")
        Logger.e(exception, "failed at step %d", 3)
        assertEquals(1, logs.size)
        assertTrue(logs[0].message.startsWith("failed at step 3"))
    }
}
