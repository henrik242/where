package no.synth.where.util

import platform.UIKit.UIApplication
import platform.UIKit.UIWindow
import platform.UIKit.UIWindowScene

/** UIApplication.keyWindow is deprecated since iOS 13; ask the connected window scenes instead. */
internal fun keyUIWindow(): UIWindow? {
    val windows = UIApplication.sharedApplication.connectedScenes
        .filterIsInstance<UIWindowScene>()
        .flatMap { it.windows.filterIsInstance<UIWindow>() }
    return windows.firstOrNull { it.isKeyWindow() } ?: windows.firstOrNull()
}
