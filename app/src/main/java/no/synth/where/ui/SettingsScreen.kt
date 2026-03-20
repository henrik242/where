package no.synth.where.ui

import android.content.Intent
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import androidx.core.os.LocaleListCompat
import no.synth.where.BuildInfo
import no.synth.where.data.UserPreferences
import no.synth.where.resources.Res
import no.synth.where.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
fun SettingsScreen(
    onBackClick: () -> Unit,
    onDownloadClick: () -> Unit,
    onTracksClick: () -> Unit,
    onSavedPointsClick: () -> Unit,
    onOnlineTrackingClick: () -> Unit,
    onAttributionsClick: () -> Unit,
    userPreferences: UserPreferences,
    onCrashReportingChange: (Boolean) -> Unit,
    highlightOfflineMode: Boolean = false
) {
    val languages = listOf(
        LanguageOption(null, stringResource(Res.string.system_default)),
        LanguageOption("en", "English"),
        LanguageOption("nb", "Norsk bokmål")
    )

    val currentLocale = AppCompatDelegate.getApplicationLocales()
    val currentTag = if (currentLocale.isEmpty) null else currentLocale.toLanguageTags()
    val currentLabel = languages.find { it.tag == currentTag }?.displayName
        ?: languages.first().displayName

    val themeOptions = listOf(
        LanguageOption("system", stringResource(Res.string.theme_system)),
        LanguageOption("light", stringResource(Res.string.theme_light)),
        LanguageOption("dark", stringResource(Res.string.theme_dark))
    )

    val context = LocalContext.current
    val crashReportingEnabled by userPreferences.crashReportingEnabled.collectAsState()
    val offlineModeEnabled by userPreferences.offlineModeEnabled.collectAsState()
    val themeMode by userPreferences.themeMode.collectAsState()
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
        currentLanguageLabel = currentLabel,
        languages = languages,
        onLanguageSelected = { tag ->
            val locales = if (tag == null) {
                LocaleListCompat.getEmptyLocaleList()
            } else {
                LocaleListCompat.forLanguageTags(tag)
            }
            AppCompatDelegate.setApplicationLocales(locales)
        },
        currentThemeLabel = currentThemeLabel,
        themeOptions = themeOptions,
        onThemeSelected = { userPreferences.updateThemeMode(it) },
        onAttributionsClick = onAttributionsClick,
        onSponsorClick = {
            context.startActivity(Intent(Intent.ACTION_VIEW, "https://buymeacoffee.com/henrik242".toUri()))
        },
        highlightOfflineMode = highlightOfflineMode
    )
}
