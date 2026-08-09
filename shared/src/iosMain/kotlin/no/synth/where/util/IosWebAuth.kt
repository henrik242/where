package no.synth.where.util

import kotlinx.cinterop.ExperimentalForeignApi
import platform.AuthenticationServices.ASPresentationAnchor
import platform.AuthenticationServices.ASWebAuthenticationPresentationContextProvidingProtocol
import platform.AuthenticationServices.ASWebAuthenticationSession
import platform.Foundation.NSError
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIWindow
import platform.darwin.NSObject

/**
 * Presents an in-app OAuth session via ASWebAuthenticationSession (Apple's OAuth primitive):
 * it keeps the flow in-process, intercepts the [callbackScheme] redirect (including through the
 * backend's https->custom-scheme bounce) itself, and returns focus automatically. [onResult]
 * gets the callback URL, or null if the user cancelled / it failed.
 */
@OptIn(ExperimentalForeignApi::class)
object IosWebAuth {
    // Strong refs so ARC keeps the session + anchor provider alive until the callback fires.
    private var session: ASWebAuthenticationSession? = null
    private var provider: AnchorProvider? = null

    fun start(url: String, callbackScheme: String, onResult: (String?) -> Unit) {
        val nsUrl = NSURL.URLWithString(url) ?: run { onResult(null); return }
        val anchorProvider = AnchorProvider()
        val webSession = ASWebAuthenticationSession(
            uRL = nsUrl,
            callbackURLScheme = callbackScheme,
            completionHandler = { callbackURL: NSURL?, _: NSError? ->
                session = null
                provider = null
                onResult(callbackURL?.absoluteString)
            }
        )
        webSession.presentationContextProvider = anchorProvider
        provider = anchorProvider
        session = webSession
        webSession.start()
    }
}

@OptIn(ExperimentalForeignApi::class)
private class AnchorProvider : NSObject(), ASWebAuthenticationPresentationContextProvidingProtocol {
    override fun presentationAnchorForWebAuthenticationSession(
        session: ASWebAuthenticationSession
    ): ASPresentationAnchor {
        @Suppress("DEPRECATION")
        return UIApplication.sharedApplication.keyWindow ?: UIWindow()
    }
}
