package org.debs.kalog.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightKalogColors = lightColorScheme(
    primary = Color(0xFF2499E8),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD8EEFD),
    onPrimaryContainer = Color(0xFF0D3048),
    secondary = Color(0xFF5B7083),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDDE8F1),
    onSecondaryContainer = Color(0xFF1C2B37),
    background = Color(0xFFF0F5F9),
    onBackground = Color(0xFF13202B),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF16232E),
    surfaceVariant = Color(0xFFE5EDF3),
    onSurfaceVariant = Color(0xFF5C6D7C),
    outline = Color(0xFFC6D3DE),
)

private val DarkKalogColors = darkColorScheme(
    primary = Color(0xFF71C7FF),
    onPrimary = Color(0xFF00344F),
    primaryContainer = Color(0xFF005074),
    onPrimaryContainer = Color(0xFFC7E7FF),
    secondary = Color(0xFFB8C8D8),
    onSecondary = Color(0xFF243140),
    secondaryContainer = Color(0xFF3A4756),
    onSecondaryContainer = Color(0xFFD6E4F2),
    background = Color(0xFF101418),
    onBackground = Color(0xFFE6EEF6),
    surface = Color(0xFF171C21),
    onSurface = Color(0xFFE7EDF4),
    surfaceVariant = Color(0xFF29323B),
    onSurfaceVariant = Color(0xFFC2CEDA),
    outline = Color(0xFF546371),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
)

@Composable
fun KalogTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkKalogColors else LightKalogColors,
        typography = Typography(),
        content = content,
    )
}
