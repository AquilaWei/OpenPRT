package org.openprt.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf

/** Which theme the user picked; [SYSTEM] follows the phone's dark mode setting. */
enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK
}

/** Brand colors of the current theme; read it like `MaterialTheme.colorScheme`. */
val LocalOpenPrtColors = staticCompositionLocalOf { LightBrandColors }

/** Whether [mode] means dark, given whether the phone is in dark mode. */
fun ThemeMode.isDark(systemDark: Boolean): Boolean = when (this) {
    ThemeMode.SYSTEM -> systemDark
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

/** OpenPRT's navy-and-gold Material theme, light or dark as [mode] says. */
@Composable
fun OpenPrtTheme(mode: ThemeMode = ThemeMode.SYSTEM, content: @Composable () -> Unit) {
    val dark = mode.isDark(isSystemInDarkTheme())
    CompositionLocalProvider(
        LocalOpenPrtColors provides if (dark) DarkBrandColors else LightBrandColors
    ) {
        MaterialTheme(colorScheme = if (dark) DarkColors else LightColors, content = content)
    }
}
