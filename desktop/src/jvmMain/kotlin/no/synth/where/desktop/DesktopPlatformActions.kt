package no.synth.where.desktop

import no.synth.where.data.PickedFile
import java.awt.Desktop
import java.awt.FileDialog
import java.awt.Frame
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.io.File
import java.net.URI

/** Desktop equivalents of the iOS/Android platform actions, over AWT. */
object DesktopPlatformActions {

    fun openUrl(url: String) {
        runCatching {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI(url))
            }
        }
    }

    /** Opens a multi-select file dialog; returns the chosen files read into memory, or empty. */
    fun pickFiles(): List<PickedFile> {
        val dialog = FileDialog(null as Frame?, "Import").apply {
            isMultipleMode = true
            isVisible = true
        }
        return dialog.files.orEmpty().filter { it.isFile }.map { PickedFile(it.name, it.readBytes()) }
    }

    /** Opens a save dialog seeded with [suggestedName], writing [bytes] to the chosen file. */
    fun saveFile(suggestedName: String, bytes: ByteArray) {
        val dialog = FileDialog(null as Frame?, "Export", FileDialog.SAVE).apply {
            file = suggestedName
            isVisible = true
        }
        val dir = dialog.directory ?: return
        val name = dialog.file ?: return
        runCatching { File(dir, name).writeBytes(bytes) }
    }

    fun copyToClipboard(text: String) {
        runCatching {
            Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
        }
    }
}
