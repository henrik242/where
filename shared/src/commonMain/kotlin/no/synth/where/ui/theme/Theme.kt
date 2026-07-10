package no.synth.where.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Earth-tone palette: warm brown primary, olive/moss tertiary, sand containers. Every container /
// surface-variant / outline role is set explicitly so none of Material's default lavender shows.
private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF7C5730),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFDCBE),
    onPrimaryContainer = Color(0xFF2B1700),
    secondary = Color(0xFF6F5B40),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFF8DEC0),
    onSecondaryContainer = Color(0xFF271904),
    tertiary = Color(0xFF57633B),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFDBE9B4),
    onTertiaryContainer = Color(0xFF151F00),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFFFF8F4),
    onBackground = Color(0xFF211A13),
    surface = Color(0xFFFFF8F4),
    onSurface = Color(0xFF211A13),
    surfaceVariant = Color(0xFFF1E1CF),
    onSurfaceVariant = Color(0xFF504539),
    outline = Color(0xFF837568),
    outlineVariant = Color(0xFFD5C3B4),
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFEFBD91),
    onPrimary = Color(0xFF4A2800),
    primaryContainer = Color(0xFF613F1C),
    onPrimaryContainer = Color(0xFFFFDCBE),
    secondary = Color(0xFFDBC3A4),
    onSecondary = Color(0xFF3C2D1A),
    secondaryContainer = Color(0xFF54432E),
    onSecondaryContainer = Color(0xFFF8DEC0),
    tertiary = Color(0xFFBFCD92),
    onTertiary = Color(0xFF2A3407),
    tertiaryContainer = Color(0xFF40481D),
    onTertiaryContainer = Color(0xFFDBE9B4),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF191210),
    onBackground = Color(0xFFEEE0D4),
    surface = Color(0xFF191210),
    onSurface = Color(0xFFEEE0D4),
    surfaceVariant = Color(0xFF504539),
    onSurfaceVariant = Color(0xFFD4C3B4),
    outline = Color(0xFFAE9F90),
    outlineVariant = Color(0xFF504539),
)

@Composable
fun WhereTheme(
    themeMode: String = "system",
    darkTheme: Boolean = when (themeMode) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    },
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
