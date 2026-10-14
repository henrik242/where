package no.synth.where.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CoverageTilesClientTest {

    @Test
    fun parsesTheVersion() {
        assertEquals(
            "2026-10-06",
            CoverageTilesClient.parseVersion("""{"version":"2026-10-06","built":"2026-10-06"}""")
        )
    }

    @Test
    fun rejectsMissingOrUnsafeVersions() {
        assertNull(CoverageTilesClient.parseVersion("""{"built":"2026-10-06"}"""))
        assertNull(CoverageTilesClient.parseVersion("""{"version":"../x"}"""))
        assertNull(CoverageTilesClient.parseVersion("""{"version":""}"""))
        assertNull(CoverageTilesClient.parseVersion("not json"))
    }
}
