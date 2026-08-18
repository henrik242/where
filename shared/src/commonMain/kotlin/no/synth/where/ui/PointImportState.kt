package no.synth.where.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import no.synth.where.data.ParsedWaypoint
import no.synth.where.data.PointImportCandidates
import no.synth.where.data.SavedPointsRepository
import no.synth.where.resources.Res
import no.synth.where.resources.*
import no.synth.where.util.Logger
import org.jetbrains.compose.resources.StringResource

/**
 * Drives point import: read the picked files, offer their waypoints, store the chosen ones. Both
 * platforms share this, so only picking the files stays platform-specific. [scope] must outlive
 * recomposition (Android passes viewModelScope), or a rotation would cancel an import in flight.
 */
class PointImportState(
    private val repository: SavedPointsRepository,
    private val scope: CoroutineScope,
) {
    /** True while files are being read, or the picked points stored. */
    var isImporting by mutableStateOf(false)
        private set

    /** Waypoints awaiting the user's pick; non-null puts the picker dialog up. */
    var candidates by mutableStateOf<PointImportCandidates?>(null)
        private set

    /** Points stored by the last import, for the confirmation snackbar. */
    var importedCount by mutableStateOf<Int?>(null)
        private set

    /** Message for the error dialog; non-null puts it up. */
    var error by mutableStateOf<StringResource?>(null)
        private set

    fun read(files: List<ByteArray>) = launchImport(Res.string.import_no_points_found) {
        val found = repository.readPoints(files)
        if (found.waypoints.isEmpty()) error = Res.string.import_no_points_found else candidates = found
    }

    fun importSelected(waypoints: List<ParsedWaypoint>) = launchImport(Res.string.import_points_failed) {
        importedCount = repository.importPoints(waypoints).size
        candidates = null
    }

    fun dismissCandidates() { candidates = null }

    fun onImportedCountShown() { importedCount = null }

    fun dismissError() { error = null }

    private fun launchImport(onFailure: StringResource, block: suspend () -> Unit) {
        scope.launch {
            isImporting = true
            try {
                block()
            } catch (e: Exception) {
                Logger.e(e, "Point import failed")
                candidates = null
                error = onFailure
            } finally {
                isImporting = false
            }
        }
    }
}
