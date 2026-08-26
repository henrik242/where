package no.synth.where

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import no.synth.where.data.UserPreferences
import no.synth.where.ui.theme.WhereTheme

class MainActivity : AppCompatActivity() {
    private var pendingGpxUri by mutableStateOf<Uri?>(null)
    private var pendingImportUrl by mutableStateOf<String?>(null)
    private var pendingFollowClientIds by mutableStateOf<List<String>>(emptyList())
    private val userPreferences: UserPreferences get() = (application as WhereApplication).userPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        // Enable edge-to-edge display
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        handleIntent(intent)

        setContent {
            val themeMode by userPreferences.themeMode.collectAsState()
            val darkTheme = when (themeMode) {
                "dark" -> true
                "light" -> false
                else -> isSystemInDarkTheme()
            }
            val view = LocalView.current
            SideEffect {
                val window = (view.context as Activity).window
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            }
            WhereTheme(themeMode = themeMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    WhereApp(
                        pendingGpxUri = pendingGpxUri,
                        pendingImportUrl = pendingImportUrl,
                        pendingFollowClientIds = pendingFollowClientIds,
                        onGpxHandled = { pendingGpxUri = null },
                        onImportUrlHandled = { pendingImportUrl = null },
                        onFollowHandled = { pendingFollowClientIds = emptyList() }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        if (intent.action == Intent.ACTION_VIEW) {
            val uri = intent.data ?: return
            // Strava OAuth redirect (where://strava/connected?code&state): exchange the code
            // on-device (on the app scope, so it survives Activity recreation). The Tracks screen
            // reports the outcome via StravaTokenManager.authOutcome.
            if (uri.scheme == "where" && uri.host == "strava") {
                (application as WhereApplication).handleStravaRedirect(uri.toString())
                return
            }
            if ((uri.scheme == "https" || uri.scheme == "http") && uri.host == "where.synth.no") {
                // where.synth.no/<id> or /<id>,<id>,..., the same group link the web viewer takes.
                val path = uri.path?.removePrefix("/") ?: ""
                if (path.matches(Regex("^[a-z0-9]{6}(,[a-z0-9]{6})*$"))) {
                    pendingFollowClientIds = path.split(",")
                    return
                }
            }
            if (uri.scheme == "https" || uri.scheme == "http") {
                pendingImportUrl = uri.toString()
            } else {
                pendingGpxUri = uri
            }
        }
    }
}

