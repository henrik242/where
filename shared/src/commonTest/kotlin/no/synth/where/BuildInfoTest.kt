package no.synth.where

import kotlin.test.Test
import kotlin.test.assertTrue

class BuildInfoTest {
    @Test
    fun versionInfoIsNotBlank() {
        assertTrue(BuildInfo.VERSION_INFO.isNotBlank())
    }

    @Test
    fun gitCommitCountIsNumeric() {
        assertTrue(BuildInfo.GIT_COMMIT_COUNT.all { it.isDigit() })
    }

    @Test
    fun buildDateMatchesPattern() {
        assertTrue(BuildInfo.BUILD_DATE.matches(Regex("""\d{4}-\d{2}-\d{2}""")))
    }
}
