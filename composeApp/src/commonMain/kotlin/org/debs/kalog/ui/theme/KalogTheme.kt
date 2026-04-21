package org.debs.kalog.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val KalogColors = lightColorScheme(
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

@Composable
fun KalogTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = KalogColors,
        typography = Typography(),
        content = content,
    )
}
