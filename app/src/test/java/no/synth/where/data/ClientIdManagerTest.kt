package no.synth.where.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ClientIdManagerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var scope: CoroutineScope
    private lateinit var manager: ClientIdManager

    @Before
    fun setUp() {
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val dataStore = PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = { tempFolder.newFile("client_prefs_test.preferences_pb") }
        )
        manager = ClientIdManager(dataStore)
    }

    @After
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun getClientId_generates6CharAlphanumeric() = runBlocking {
        val clientId = manager.getClientId()
        assertEquals(6, clientId.length)
        assertTrue(clientId.all { it in 'a'..'z' || it in '0'..'9' })
    }

    @Test
    fun getClientId_persistsAcrossCalls() = runBlocking {
        val first = manager.getClientId()
        val second = manager.getClientId()
        assertEquals(first, second)
    }

    @Test
    fun regenerateClientId_producesNewId() = runBlocking {
        val original = manager.getClientId()
        val regenerated = manager.regenerateClientId()
        assertEquals(6, regenerated.length)
        assertTrue(regenerated.all { it in 'a'..'z' || it in '0'..'9' })
        // With 36^6 possible values, collision is extremely unlikely
        assertNotEquals(original, regenerated)
    }

    @Test
    fun regenerateClientId_newIdPersists() = runBlocking {
        manager.getClientId()
        val regenerated = manager.regenerateClientId()
        val fetched = manager.getClientId()
        assertEquals(regenerated, fetched)
    }
}
