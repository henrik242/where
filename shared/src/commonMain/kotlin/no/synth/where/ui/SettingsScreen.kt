package no.synth.where.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import no.synth.where.BuildInfo
import no.synth.where.data.UserPreferences
import no.synth.where.resources.Res
import no.synth.where.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
fun SettingsScreen(
    userPreferences: UserPreferences,
    currentLanguageTag: String?,
    onLanguageSelected: (String?) -> Unit,
    onSponsorClick: () -> Unit,
    onBackClick: () -> Unit,
    onDownloadClick: () -> Unit,
    onTracksClick: () -> Unit,
    onSavedPointsClick: () -> Unit,
    onOnlineTrackingClick: () -> Unit,
    onAttributionsClick: () -> Unit,
    onReleaseNotesClick: () -> Unit,
    onGuideClick: () -> Unit,
    onCrashReportingChange: (Boolean) -> Unit,
    highlightOfflineMode: Boolean = false
) {
    val languages = listOf(
        LanguageOption(null, stringResource(Res.string.system_default)),
        LanguageOption("en", "English"),
        LanguageOption("nb", "Norsk bokmål")
    )
    val currentLanguageLabel = languages.find { it.tag == currentLanguageTag }?.displayName
        ?: languages.first().displayName

    val themeOptions = listOf(
        LanguageOption("system", stringResource(Res.string.theme_system)),
        LanguageOption("light", stringResource(Res.string.theme_light)),
        LanguageOption("dark", stringResource(Res.string.theme_dark))
    )

    val crashReportingEnabled by userPreferences.crashReportingEnabled.collectAsState()
    val offlineModeEnabled by userPreferences.offlineModeEnabled.collectAsState()
    val themeMode by userPreferences.themeMode.collectAsState()
    val coordFormat by userPreferences.coordFormat.collectAsState()
    val currentThemeLabel = themeOptions.find { it.tag == themeMode }?.displayName
        ?: themeOptions.first().displayName

    SettingsScreenContent(
        versionInfo = BuildInfo.VERSION_INFO,
        onBackClick = onBackClick,
        onDownloadClick = onDownloadClick,
        onTracksClick = onTracksClick,
        onSavedPointsClick = onSavedPointsClick,
        onOnlineTrackingClick = onOnlineTrackingClick,
        offlineModeEnabled = offlineModeEnabled,
        onOfflineModeChange = { userPreferences.updateOfflineModeEnabled(it) },
        crashReportingEnabled = crashReportingEnabled,
        onCrashReportingChange = onCrashReportingChange,
        currentLanguageLabel = currentLanguageLabel,
        languages = languages,
        onLanguageSelected = onLanguageSelected,
        currentThemeLabel = currentThemeLabel,
        themeOptions = themeOptions,
        onThemeSelected = { userPreferences.updateThemeMode(it) },
        currentCoordFormat = coordFormat,
        onCoordFormatSelected = { userPreferences.updateCoordFormat(it) },
        onAttributionsClick = onAttributionsClick,
        onReleaseNotesClick = onReleaseNotesClick,
        onGuideClick = onGuideClick,
        onSponsorClick = onSponsorClick,
        highlightOfflineMode = highlightOfflineMode
    )
}
