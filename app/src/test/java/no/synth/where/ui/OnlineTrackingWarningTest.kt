package no.synth.where.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Offline mode blocks every upload, so a live share reaches nobody while the countdown keeps
 * ticking. The tracking screen has to say so, and its one-tap way out has to reach the caller.
 */
@RunWith(RobolectricTestRunner::class)
class OnlineTrackingWarningTest {

    @get:Rule
    val compose = createComposeRule()

    private fun setContent(
        trackingEnabled: Boolean,
        offline: Boolean = false,
        liveShareUntilMillis: Long = 0L,
        backgroundLocationMissing: Boolean = false,
        onDisableOfflineMode: () -> Unit = {},
        onOpenLocationSettings: () -> Unit = {},
    ) {
        compose.setContent {
            MaterialTheme {
                OnlineTrackingScreenContent(
                    isTrackingEnabled = trackingEnabled,
                    clientId = "abc123",
                    showRegenerateDialog = false,
                    showTrackingInfoDialog = false,
                    onBackClick = {},
                    onToggleTracking = {},
                    onViewOnWeb = {},
                    onShare = {},
                    onRegenerateClick = {},
                    onConfirmRegenerate = {},
                    onDismissRegenerate = {},
                    onConfirmTrackingInfo = {},
                    onDismissTrackingInfo = {},
                    liveShareUntilMillis = liveShareUntilMillis,
                    offlineModeEnabled = offline,
                    onDisableOfflineMode = onDisableOfflineMode,
                    backgroundLocationMissing = backgroundLocationMissing,
                    onOpenLocationSettings = onOpenLocationSettings,
                )
            }
        }
    }

    // Substrings: the resources contain typographic punctuation that is easy to get wrong here.
    private val warning = "Offline mode is on"
    private val backgroundWarning = "sharing stops when you leave the app"

    @Test
    fun warnsAndOffersTheWayOutWhenOfflineModeBlocksSharing() {
        var disabled = false
        setContent(trackingEnabled = true, offline = true, onDisableOfflineMode = { disabled = true })

        compose.onNodeWithText(warning, substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Turn off offline mode").performScrollTo().performClick()

        assertTrue("the card's button must reach onDisableOfflineMode", disabled)
    }

    @Test
    fun noWarningWhenOfflineModeIsOff() {
        setContent(trackingEnabled = true, offline = false)

        compose.onNodeWithText(warning, substring = true).assertDoesNotExist()
    }

    @Test
    fun warnsWhileASharingRunsWithoutBackgroundLocation() {
        // A running share drives a per-second countdown effect: hold the clock so it can't spin.
        compose.mainClock.autoAdvance = false
        var settingsOpened = false
        setContent(
            trackingEnabled = true,
            liveShareUntilMillis = System.currentTimeMillis() + 60_000L,
            backgroundLocationMissing = true,
            onOpenLocationSettings = { settingsOpened = true },
        )

        compose.onNodeWithText(backgroundWarning, substring = true)
            .performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Open Settings").performScrollTo().performClick()

        assertTrue("the card's button must reach onOpenLocationSettings", settingsOpened)
    }

    @Test
    fun noBackgroundWarningWhileNoShareIsRunning() {
        // Nothing is being sent, so the permission has no consequence yet.
        setContent(trackingEnabled = true, backgroundLocationMissing = true)

        compose.onNodeWithText(backgroundWarning, substring = true).assertDoesNotExist()
    }

    @Test
    fun noWarningWhileOnlineTrackingIsDisabled() {
        // Nothing would be uploaded either way, so the warning would only be noise.
        setContent(trackingEnabled = false, offline = true)

        compose.onNodeWithText(warning, substring = true).assertDoesNotExist()
    }
}
