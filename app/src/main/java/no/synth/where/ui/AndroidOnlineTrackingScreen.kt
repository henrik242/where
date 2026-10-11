package no.synth.where.ui

import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import no.synth.where.resources.Res
import no.synth.where.resources.*
import no.synth.where.service.LocationTrackingService
import org.jetbrains.compose.resources.stringResource

/** Android glue for the shared [OnlineTrackingScreen]: intents and the location service. */
@Composable
fun AndroidOnlineTrackingScreen(
    onBackClick: () -> Unit,
    onNavigateToMap: () -> Unit,
) {
    val context = LocalContext.current
    val app = context.applicationContext as no.synth.where.WhereApplication
    val state = rememberRetainedState { scope ->
        OnlineTrackingState(app.userPreferences, app.liveTrackingFollower, app.clientIdManager, scope)
    }
    val shareTrackingChooserStr = stringResource(Res.string.share_tracking_chooser)
    val liveLocationTrackingStr = stringResource(Res.string.live_location_tracking)

    OnlineTrackingScreen(
        state = state,
        onBackClick = onBackClick,
        onNavigateToMap = onNavigateToMap,
        openUrl = { url -> context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) },
        onShare = { text ->
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
                putExtra(Intent.EXTRA_SUBJECT, liveLocationTrackingStr)
            }
            context.startActivity(Intent.createChooser(shareIntent, shareTrackingChooserStr))
        },
        onLiveShareStarted = { LocationTrackingService.start(context) },
    )
}
