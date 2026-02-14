package no.synth.where.ui

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.core.os.LocaleListCompat
import no.synth.where.BuildConfig
import no.synth.where.R

@Composable
fun SettingsScreen(
    onBackClick: () -> Unit,
    onDownloadClick: () -> Unit,
    onTracksClick: () -> Unit,
    onSavedPointsClick: () -> Unit,
    onOnlineTrackingClick: () -> Unit,
    crashReportingEnabled: Boolean,
    onCrashReportingChange: (Boolean) -> Unit
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

    SettingsScreenContent(
        versionInfo = "${BuildConfig.GIT_COMMIT_COUNT}.${BuildConfig.GIT_SHORT_SHA} ${BuildConfig.BUILD_DATE}",
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
        }
    )
}
