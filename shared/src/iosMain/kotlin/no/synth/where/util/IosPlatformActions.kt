package no.synth.where.util

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.NSString
import platform.Foundation.NSURL
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.writeToFile
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIDocumentPickerViewController
import platform.UIKit.UIViewController
import platform.UniformTypeIdentifiers.UTType
import platform.UniformTypeIdentifiers.UTTypeXML
import platform.darwin.NSObject

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
object IosPlatformActions {

    fun openUrl(url: String) {
        val nsUrl = NSURL(string = url)
        UIApplication.sharedApplication.openURL(nsUrl, options = emptyMap<Any?, Any>(), completionHandler = null)
    }

    fun shareText(text: String) {
        val rootVC = topViewController() ?: return
        val activityVC = UIActivityViewController(
            activityItems = listOf(text),
            applicationActivities = null
        )
        rootVC.presentViewController(activityVC, animated = true, completion = null)
    }

    fun shareFile(fileName: String, content: String) {
        val rootVC = topViewController() ?: return
        val tempPath = NSTemporaryDirectory() + fileName
        val bytes = content.encodeToByteArray()
        val nsData = bytes.usePinned { pinned ->
            NSData.create(bytes = pinned.addressOf(0), length = bytes.size.toULong())
        }
        nsData.writeToFile(tempPath, true)
        val fileUrl = NSURL.fileURLWithPath(tempPath)
        val activityVC = UIActivityViewController(
            activityItems = listOf(fileUrl),
            applicationActivities = null
        )
        rootVC.presentViewController(activityVC, animated = true, completion = null)
    }

    fun pickFile(types: List<String>, onResult: (String?) -> Unit) {
        val rootVC = topViewController() ?: run {
            onResult(null)
            return
        }

        val utTypes = types.mapNotNull { type ->
            when (type) {
                "public.xml" -> UTTypeXML
                else -> UTType.typeWithIdentifier(type)
            }
        }.ifEmpty {
            listOf(UTTypeXML)
        }

        val delegate = DocumentPickerDelegate(onResult)
        val picker = UIDocumentPickerViewController(forOpeningContentTypes = utTypes)
        picker.delegate = delegate
        picker.allowsMultipleSelection = false

        // Store delegate ref to prevent GC
        delegateRef = delegate

        rootVC.presentViewController(picker, animated = true, completion = null)
    }

    private var delegateRef: DocumentPickerDelegate? = null

    @Suppress("PARAMETER_NAME_CHANGED_ON_OVERRIDE")
    private class DocumentPickerDelegate(
        private val onResult: (String?) -> Unit
    ) : NSObject(), platform.UIKit.UIDocumentPickerDelegateProtocol {

        override fun documentPicker(
            controller: UIDocumentPickerViewController,
            didPickDocumentsAtURLs: List<*>
        ) {
            delegateRef = null
            val url = didPickDocumentsAtURLs.firstOrNull() as? NSURL ?: run {
                onResult(null)
                return
            }

            val accessing = url.startAccessingSecurityScopedResource()
            try {
                val data = NSData.create(contentsOfURL = url) ?: run {
                    onResult(null)
                    return
                }
                val content = NSString.create(data = data, encoding = NSUTF8StringEncoding)?.toString()
                onResult(content)
            } finally {
                if (accessing) {
                    url.stopAccessingSecurityScopedResource()
                }
            }
        }

        override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) {
            delegateRef = null
            onResult(null)
        }
    }

    private fun topViewController(): UIViewController? {
        val keyWindow = UIApplication.sharedApplication.keyWindow ?: return null
        var vc = keyWindow.rootViewController ?: return null
        while (true) {
            val presented = vc.presentedViewController ?: break
            vc = presented
        }
        return vc
    }
}
