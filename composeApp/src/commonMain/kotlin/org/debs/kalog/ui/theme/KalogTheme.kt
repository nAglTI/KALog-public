package org.debs.kalog.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val LightKalogColors = lightColorScheme(
    primary = Color(0xFF2F6A4B),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDFEAD9),
    onPrimaryContainer = Color(0xFF14110C),
    secondary = Color(0xFF6B6558),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF0EBDE),
    onSecondaryContainer = Color(0xFF14110C),
    tertiary = Color(0xFF9A7527),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFF0EBDE),
    onTertiaryContainer = Color(0xFF14110C),
    background = Color(0xFFF5F3EF),
    onBackground = Color(0xFF14110C),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF14110C),
    surfaceVariant = Color(0xFFECE9E2),
    onSurfaceVariant = Color(0xFF6B6558),
    outline = Color(0xFFD9D4C8),
    outlineVariant = Color(0xFFE8E4DA),
    error = Color(0xFFA4432A),
    onError = Color.White,
    errorContainer = Color(0xFFF4DDD5),
    onErrorContainer = Color(0xFF14110C),
)

private val DarkKalogColors = darkColorScheme(
    primary = Color(0xFF7CBD96),
    onPrimary = Color(0xFF0C0C0A),
    primaryContainer = Color(0xFF1E2A23),
    onPrimaryContainer = Color(0xFFECE9E0),
    secondary = Color(0xFF8B867A),
    onSecondary = Color(0xFF0C0C0A),
    secondaryContainer = Color(0xFF222018),
    onSecondaryContainer = Color(0xFFECE9E0),
    tertiary = Color(0xFFD4A85A),
    onTertiary = Color(0xFF0C0C0A),
    tertiaryContainer = Color(0xFF222018),
    onTertiaryContainer = Color(0xFFECE9E0),
    background = Color(0xFF12120F),
    onBackground = Color(0xFFECE9E0),
    surface = Color(0xFF1A1A15),
    onSurface = Color(0xFFECE9E0),
    surfaceVariant = Color(0xFF0C0C0A),
    onSurfaceVariant = Color(0xFF8B867A),
    outline = Color(0xFF2B2A24),
    outlineVariant = Color(0xFF24241E),
    error = Color(0xFFD07A5F),
    onError = Color(0xFF0C0C0A),
    errorContainer = Color(0xFF3A1F18),
    onErrorContainer = Color(0xFFECE9E0),
)

private val KalogShapes = Shapes(
    small = RoundedCornerShape(6.dp),
    medium = RoundedCornerShape(10.dp),
    large = RoundedCornerShape(14.dp),
    extraLarge = RoundedCornerShape(20.dp),
)

private val KalogTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.Serif,
        fontSize = 48.sp,
        lineHeight = 52.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.sp,
    ),
    displayMedium = TextStyle(
        fontFamily = FontFamily.Serif,
        fontSize = 40.sp,
        lineHeight = 44.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Serif,
        fontSize = 22.sp,
        lineHeight = 27.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.Serif,
        fontSize = 20.sp,
        lineHeight = 25.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.sp,
    ),
    titleLarge = TextStyle(
        fontSize = 15.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.sp,
    ),
    titleMedium = TextStyle(
        fontSize = 14.sp,
        lineHeight = 19.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.sp,
    ),
    titleSmall = TextStyle(
        fontSize = 13.sp,
        lineHeight = 18.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.sp,
    ),
    bodyLarge = TextStyle(
        fontSize = 15.sp,
        lineHeight = 22.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = 0.sp,
    ),
    bodyMedium = TextStyle(
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = 0.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = 0.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontSize = 10.sp,
        lineHeight = 14.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.sp,
    ),
)

@Composable
fun KalogTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkKalogColors else LightKalogColors,
        typography = KalogTypography,
        shapes = KalogShapes,
        content = content,
    )
}
