package org.openprt.app.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// PRT's navy and gold, with blue-gray neutrals instead of Material's default purple tint.
// Live data is green (tertiary) and delays are red (error) in both themes.

private val Navy = Color(0xFF17365F)
private val Gold = Color(0xFFFFC72C)

internal val LightColors: ColorScheme = lightColorScheme(
    primary = Navy,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD5E3FF),
    onPrimaryContainer = Color(0xFF001B3C),
    inversePrimary = Color(0xFFA8C8FF),
    secondary = Color(0xFF785A00),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Gold,
    onSecondaryContainer = Color(0xFF251A00),
    tertiary = Color(0xFF1E6B3A),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFA6F4B5),
    onTertiaryContainer = Color(0xFF00210B),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFF8F9FC),
    onBackground = Color(0xFF191C20),
    surface = Color(0xFFF8F9FC),
    onSurface = Color(0xFF191C20),
    surfaceVariant = Color(0xFFDFE2EB),
    onSurfaceVariant = Color(0xFF43474E),
    surfaceTint = Navy,
    inverseSurface = Color(0xFF2E3135),
    inverseOnSurface = Color(0xFFEFF0F7),
    outline = Color(0xFF73777F),
    outlineVariant = Color(0xFFC3C6CF),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFFF8F9FC),
    surfaceDim = Color(0xFFD8DAE0),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF2F3FA),
    surfaceContainer = Color(0xFFECEEF4),
    surfaceContainerHigh = Color(0xFFE6E8EE),
    surfaceContainerHighest = Color(0xFFE1E2E8)
)

internal val DarkColors: ColorScheme = darkColorScheme(
    primary = Color(0xFFA8C8FF),
    onPrimary = Color(0xFF003062),
    primaryContainer = Color(0xFF1F3F6B),
    onPrimaryContainer = Color(0xFFD5E3FF),
    inversePrimary = Color(0xFF3A5F94),
    secondary = Color(0xFFF7C04A),
    onSecondary = Color(0xFF3F2E00),
    secondaryContainer = Color(0xFF5B4300),
    onSecondaryContainer = Color(0xFFFFDF9E),
    tertiary = Color(0xFF8BD89A),
    onTertiary = Color(0xFF003916),
    tertiaryContainer = Color(0xFF005224),
    onTertiaryContainer = Color(0xFFA6F4B5),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF111318),
    onBackground = Color(0xFFE1E2E9),
    surface = Color(0xFF111318),
    onSurface = Color(0xFFE1E2E9),
    surfaceVariant = Color(0xFF43474E),
    onSurfaceVariant = Color(0xFFC3C6CF),
    surfaceTint = Color(0xFFA8C8FF),
    inverseSurface = Color(0xFFE1E2E9),
    inverseOnSurface = Color(0xFF2E3035),
    outline = Color(0xFF8D9199),
    outlineVariant = Color(0xFF43474E),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF37393E),
    surfaceDim = Color(0xFF111318),
    surfaceContainerLowest = Color(0xFF0C0E13),
    surfaceContainerLow = Color(0xFF191C20),
    surfaceContainer = Color(0xFF1D2024),
    surfaceContainerHigh = Color(0xFF282A2F),
    surfaceContainerHighest = Color(0xFF33353A)
)

/**
 * Brand colors Material's scheme has no slot for: the top bar, which is navy in the light theme
 * but a plain dark surface in the dark one, and the gold accent of the main map button.
 */
data class OpenPrtColors(
    val isDark: Boolean,
    val appBar: Color,
    val onAppBar: Color,
    val accent: Color,
    val onAccent: Color
)

internal val LightBrandColors = OpenPrtColors(
    isDark = false,
    appBar = Navy,
    onAppBar = Color(0xFFFFFFFF),
    accent = Gold,
    onAccent = Navy
)

internal val DarkBrandColors = OpenPrtColors(
    isDark = true,
    appBar = Color(0xFF1D2024),
    onAppBar = Color(0xFFE1E2E9),
    accent = Color(0xFFF7C04A),
    onAccent = Color(0xFF261A00)
)
