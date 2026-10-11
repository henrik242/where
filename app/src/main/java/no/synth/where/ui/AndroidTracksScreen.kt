package no.synth.where.ui

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.browser.customtabs.CustomTabsIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import no.synth.where.data.PickedFile
import no.synth.where.data.Track
import no.synth.where.resources.Res
import no.synth.where.resources.*
import no.synth.where.util.Logger
import org.jetbrains.compose.resources.stringResource
import java.io.File

/** Android glue for the shared [TracksScreen]: SAF picker, deep-link import prompts, share/save/open intents. */
@Composable
fun AndroidTracksScreen(
    pendingImportUrl: String? = null,
    pendingImportFileUri: String? = null,
    onBackClick: () -> Unit,
    onShowMap: () -> Unit,
    onNavigationStarted: () -> Unit,
) {
    val context = LocalContext.current
    val app = context.applicationContext as no.synth.where.WhereApplication
    val state = rememberRetainedState { scope ->
        TracksScreenState(app.trackRepository, app.userPreferences, app.stravaTokenManager, app.stravaRouteImporter, scope)
    }
    val scope = rememberCoroutineScope()

    var urlToConfirm by remember { mutableStateOf<String?>(null) }
    var fileUriToConfirm by remember { mutableStateOf<String?>(null) }

    val shareTrackChooserStr = stringResource(Res.string.share_track_chooser)
    val openTrackChooserStr = stringResource(Res.string.open_track_chooser)
    val savedToPathFmt = stringResource(Res.string.saved_to_path)
    val failedToSaveTrackFmt = stringResource(Res.string.failed_to_save_track)

    LaunchedEffect(pendingImportUrl) {
        if (pendingImportUrl != null) urlToConfirm = pendingImportUrl
    }

    LaunchedEffect(pendingImportFileUri) {
        if (pendingImportFileUri != null) fileUriToConfirm = pendingImportFileUri
    }

    fun readAndImport(uris: List<Uri>) {
        scope.launch {
            val files = withContext(Dispatchers.IO) {
                uris.mapNotNull { uri ->
                    runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } }
                        .getOrNull()
                        ?.let { PickedFile(displayNameForUri(context, uri), it) }
                }
            }
            state.importFiles(files)
        }
    }

    urlToConfirm?.let { url ->
        AlertDialog(
            onDismissRequest = { urlToConfirm = null },
            title = { Text(stringResource(Res.string.import_from_title)) },
            text = { Text(stringResource(Res.string.import_from_message, friendlySourceName(url))) },
            confirmButton = {
                TextButton(onClick = {
                    urlToConfirm = null
                    state.importFromUrl(url)
                }) { Text(stringResource(Res.string.import_label)) }
            },
            dismissButton = {
                TextButton(onClick = { urlToConfirm = null }) {
                    Text(stringResource(Res.string.cancel))
                }
            }
        )
    }

    fileUriToConfirm?.let { uriString ->
        // Resolve the display name off the main thread (a cross-process ContentResolver query),
        // showing the cheap last-path-segment fallback until it returns, so it never runs in
        // composition on every recomposition.
        val fileName by produceState(uriString.toUri().lastPathSegment ?: uriString, uriString) {
            value = withContext(Dispatchers.IO) { displayNameForUri(context, uriString.toUri()) }
        }
        AlertDialog(
            onDismissRequest = { fileUriToConfirm = null },
            title = { Text(stringResource(Res.string.import_from_title)) },
            text = { Text(stringResource(Res.string.import_file_message, fileName)) },
            confirmButton = {
                TextButton(onClick = {
                    fileUriToConfirm = null
                    readAndImport(listOf(uriString.toUri()))
                }) { Text(stringResource(Res.string.import_label)) }
            },
            dismissButton = {
                TextButton(onClick = { fileUriToConfirm = null }) {
                    Text(stringResource(Res.string.cancel))
                }
            }
        )
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) readAndImport(uris)
    }

    TracksScreen(
        state = state,
        onBackClick = onBackClick,
        onShowMap = onShowMap,
        onImportClick = { filePickerLauncher.launch("*/*") },
        onExport = { track -> shareTrack(context, track, shareTrackChooserStr) },
        openUrl = { url -> launchCustomTab(context, url) },
        // The redirect comes back as a deep link to MainActivity, which completes the exchange.
        stravaAuth = { url, _ -> launchCustomTab(context, url) },
        onSave = { track ->
            withContext(Dispatchers.IO) { saveTrackToDownloads(context, track, savedToPathFmt, failedToSaveTrackFmt) }
        },
        onOpen = { track -> openTrack(context, track, openTrackChooserStr, shareTrackChooserStr) },
        onNavigationStarted = onNavigationStarted,
    )
}

private fun launchCustomTab(context: Context, url: String) {
    CustomTabsIntent.Builder().build().launchUrl(context, url.toUri())
}

private fun displayNameForUri(context: Context, uri: Uri): String {
    if (uri.scheme == "content") {
        runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (index >= 0) cursor.getString(index)?.let { return it }
                    }
                }
        }
    }
    return uri.lastPathSegment ?: uri.toString()
}

private fun friendlySourceName(url: String): String {
    val host = url.toUri().host ?: return url
    return when {
        "garmin.com" in host -> "Garmin Connect"
        "komoot.com" in host || "komoot.de" in host -> "Komoot"
        "ut.no" in host -> "UT.no"
        else -> host
    }
}

private fun shareTrack(context: Context, track: Track, chooserTitle: String) {
    try {
        val gpxContent = track.toGPX()
        val file = File(context.cacheDir, track.gpxFileName())
        file.writeText(gpxContent)

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/gpx+xml"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, track.name)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        context.startActivity(Intent.createChooser(shareIntent, chooserTitle))
    } catch (e: Exception) {
        Logger.e(e, "Track operation error")
    }
}

private fun saveTrackToDownloads(context: Context, track: Track, savedFmt: String, failedFmt: String): String {
    return try {
        val gpxContent = track.toGPX()
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, track.gpxFileName())
            put(MediaStore.Downloads.MIME_TYPE, "application/gpx+xml")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }

        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: throw java.io.IOException("Could not create Downloads entry")

        try {
            resolver.openOutputStream(uri)?.use { it.write(gpxContent.toByteArray()) }
                ?: throw java.io.IOException("Could not open output stream")
            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw e
        }

        String.format(savedFmt, android.os.Environment.DIRECTORY_DOWNLOADS)
    } catch (e: Exception) {
        Logger.e(e, "Track operation error")
        String.format(failedFmt, e.message ?: e.toString())
    }
}

private fun openTrack(context: Context, track: Track, chooserTitle: String, shareChooserTitle: String) {
    try {
        val gpxContent = track.toGPX()
        val file = File(context.cacheDir, track.gpxFileName())
        file.writeText(gpxContent)

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        val openIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/gpx+xml")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        try {
            context.startActivity(Intent.createChooser(openIntent, chooserTitle))
        } catch (_: android.content.ActivityNotFoundException) {
            shareTrack(context, track, shareChooserTitle)
        }
    } catch (e: Exception) {
        Logger.e(e, "Track operation error")
    }
}
