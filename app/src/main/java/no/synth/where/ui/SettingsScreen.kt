package no.synth.where.ui

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.core.os.LocaleListCompat
import no.synth.where.BuildInfo
import no.synth.where.R
import no.synth.where.data.UserPreferences

@Composable
fun SettingsScreen(
    onBackClick: () -> Unit,
    onDownloadClick: () -> Unit,
    onTracksClick: () -> Unit,
    onSavedPointsClick: () -> Unit,
    onOnlineTrackingClick: () -> Unit,
    crashReportingEnabled: Boolean,
    onCrashReportingChange: (Boolean) -> Unit,
    userPreferences: UserPreferences
) {
    val languages = listOf(
        LanguageOption(null, stringResource(R.string.system_default)),
        LanguageOption("en", "English"),
        LanguageOption("nb", "Norsk bokmål")
    )

    val currentLocale = AppCompatDelegate.getApplicationLocales()
    val currentTag = if (currentLocale.isEmpty) null else currentLocale.toLanguageTags()
    val currentLabel = languages.find { it.tag == currentTag }?.displayName
        ?: languages.first().displayName

    val themeOptions = listOf(
        LanguageOption("system", stringResource(R.string.theme_system)),
        LanguageOption("light", stringResource(R.string.theme_light)),
        LanguageOption("dark", stringResource(R.string.theme_dark))
    )

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
        onThemeSelected = { userPreferences.updateThemeMode(it) }
    )
}
