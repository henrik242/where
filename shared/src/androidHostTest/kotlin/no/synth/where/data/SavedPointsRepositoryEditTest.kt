package no.synth.where.data

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import no.synth.where.data.geo.LatLng
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/** A point's description has to survive being created and then edited. */
class SavedPointsRepositoryEditTest {

    private fun repo(dao: InMemorySavedPointDao): SavedPointsRepository {
        val dir = File(System.getProperty("java.io.tmpdir"), "point-repo-${System.nanoTime()}")
        dir.mkdirs()
        return SavedPointsRepository(PlatformFile(dir), dao)
    }

    @Test
    fun addPointStoresTheDescription() {
        val dao = InMemorySavedPointDao()
        val r = repo(dao)

        r.addPoint("Teltplass", LatLng(59.9, 10.7), description = "flatt og lunt")
        runBlocking { r.savedPoints.first { it.isNotEmpty() } }

        assertEquals("flatt og lunt", dao.all().single().description)
    }

    @Test
    fun updatePointRewritesNameDescriptionAndColorButNotThePosition() {
        val dao = InMemorySavedPointDao()
        val r = repo(dao)
        r.addPoint("Teltplass", LatLng(59.9, 10.7), description = "")
        val point = runBlocking { r.savedPoints.first { it.isNotEmpty() } }.single()

        r.updatePoint(point.id, "Leirplass", "god teltplass", "#4CAF50")
        val updated = runBlocking { r.savedPoints.first { it.single().description == "god teltplass" } }.single()

        assertEquals("Leirplass", updated.name)
        assertEquals("#4CAF50", updated.color)
        assertEquals(LatLng(59.9, 10.7), updated.latLng)
    }
}
