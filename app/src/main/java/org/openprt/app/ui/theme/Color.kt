package org.openprt.app.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// A plain, Apple-style look (user request, 2026-10-05): iOS grouped backgrounds, white (or
// near-black) cards without borders, gray secondary text and a single blue accent. The iOS
// system colors are darkened where needed so text still meets WCAG AA (ThemeContrastTest).
// Live data is green (tertiary), delays red (error), and the stop to board at gold, as on the map.

private val Gold = Color(0xFFFFD60A)
private val OnGold = Color(0xFF3A2E00)

internal val LightColors: ColorScheme = lightColorScheme(
    primary = Color(0xFF0066CC),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE3EEFB),
    onPrimaryContainer = Color(0xFF00438A),
    inversePrimary = Color(0xFF4DA3FF),
    secondary = Color(0xFF636366),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Gold,
    onSecondaryContainer = OnGold,
    tertiary = Color(0xFF1E7B34),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFDDF5E3),
    onTertiaryContainer = Color(0xFF0B5A23),
    error = Color(0xFFD70015),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFE5E5),
    onErrorContainer = Color(0xFF8A0010),
    // iOS "grouped" background: cards are white on light gray.
    background = Color(0xFFF2F2F7),
    onBackground = Color(0xFF1C1C1E),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1C1C1E),
    surfaceVariant = Color(0xFFE5E5EA),
    onSurfaceVariant = Color(0xFF636366),
    // White stays white when raised: Apple shows depth with shadows, not tinted surfaces.
    surfaceTint = Color(0xFFFFFFFF),
    inverseSurface = Color(0xFF2C2C2E),
    inverseOnSurface = Color(0xFFF2F2F7),
    outline = Color(0xFF8E8E93),
    outlineVariant = Color(0xFFE5E5EA),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFFFFFFFF),
    surfaceDim = Color(0xFFE5E5EA),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF2F2F7),
    surfaceContainer = Color(0xFFF2F2F7),
    surfaceContainerHigh = Color(0xFFFFFFFF),
    surfaceContainerHighest = Color(0xFFE5E5EA)
)

internal val DarkColors: ColorScheme = darkColorScheme(
    primary = Color(0xFF4DA3FF),
    onPrimary = Color(0xFF00213F),
    primaryContainer = Color(0xFF0B2C4D),
    onPrimaryContainer = Color(0xFFA9D1FF),
    inversePrimary = Color(0xFF0066CC),
    secondary = Color(0xFFAEAEB2),
    onSecondary = Color(0xFF1C1C1E),
    secondaryContainer = Gold,
    onSecondaryContainer = OnGold,
    tertiary = Color(0xFF30D158),
    onTertiary = Color(0xFF003914),
    tertiaryContainer = Color(0xFF0F3D1C),
    onTertiaryContainer = Color(0xFF7BE495),
    error = Color(0xFFFF6961),
    onError = Color(0xFF4D0005),
    errorContainer = Color(0xFF4D0F0F),
    onErrorContainer = Color(0xFFFFB3AE),
    // iOS dark grouped background: near-black cards on black.
    background = Color(0xFF000000),
    onBackground = Color(0xFFF2F2F7),
    surface = Color(0xFF1C1C1E),
    onSurface = Color(0xFFF2F2F7),
    surfaceVariant = Color(0xFF2C2C2E),
    onSurfaceVariant = Color(0xFFAEAEB2),
    surfaceTint = Color(0xFF1C1C1E),
    inverseSurface = Color(0xFFF2F2F7),
    inverseOnSurface = Color(0xFF1C1C1E),
    outline = Color(0xFF636366),
    outlineVariant = Color(0xFF38383A),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF2C2C2E),
    surfaceDim = Color(0xFF000000),
    surfaceContainerLowest = Color(0xFF000000),
    surfaceContainerLow = Color(0xFF000000),
    surfaceContainer = Color(0xFF1C1C1E),
    surfaceContainerHigh = Color(0xFF2C2C2E),
    surfaceContainerHighest = Color(0xFF3A3A3C)
)

/**
 * Colors Material's scheme has no slot for: the top bar, plain like an iOS navigation bar, and
 * the gold [accent] that marks the stop to board at, matching the map's boarding stop.
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
    appBar = Color(0xFFFFFFFF),
    onAppBar = Color(0xFF1C1C1E),
    accent = Gold,
    onAccent = OnGold
)

internal val DarkBrandColors = OpenPrtColors(
    isDark = true,
    appBar = Color(0xFF000000),
    onAppBar = Color(0xFFF2F2F7),
    accent = Gold,
    onAccent = OnGold
)
