package no.synth.where.desktop

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import org.maplibre.compose.desktop.ProvideMapPresentationHost
import org.maplibre.compose.desktop.rememberAwtComposeMapPresentationHost

/**
 * Desktop (JVM) entry point: starts the shared services over a SQLite database under ~/.where, then
 * shows the Compose UI. The desktop MapLibre map renders into a presentation host bound to the AWT
 * window, which must sit above any map in the tree.
 */
fun main() {
    startApp()
    application {
        Window(onCloseRequest = ::exitApplication, title = "Where") {
            ProvideMapPresentationHost(host = rememberAwtComposeMapPresentationHost(window)) {
                DesktopApp()
            }
        }
    }
}
