package no.synth.where

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import no.synth.where.ui.theme.WhereTheme

class MainActivity : ComponentActivity() {
    private var pendingGpxUri by mutableStateOf<Uri?>(null)
    private var regionsLoaded by mutableStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        // Enable edge-to-edge display
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        handleIntent(intent)

        setContent {
            WhereTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    WhereApp(
                        pendingGpxUri = pendingGpxUri,
                        regionsLoadedTrigger = regionsLoaded,
                        onGpxHandled = { pendingGpxUri = null },
                        onRegionsLoaded = { regionsLoaded++ }
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
            intent.data?.let { uri -> pendingGpxUri = uri }
        }
    }
}

