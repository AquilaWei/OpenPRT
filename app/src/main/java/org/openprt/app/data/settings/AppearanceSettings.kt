package org.openprt.app.data.settings

import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.openprt.app.ui.theme.ThemeMode

/**
 * Keeps the theme the user picked in [prefs]. An unknown stored value (from a later app version)
 * reads as [ThemeMode.SYSTEM] rather than failing.
 */
class AppearanceSettings(private val prefs: SharedPreferences) {
    private val mutableThemeMode = MutableStateFlow(read())
    val themeMode: StateFlow<ThemeMode> = mutableThemeMode.asStateFlow()

    fun setThemeMode(mode: ThemeMode) {
        prefs.edit { putString(KEY_THEME_MODE, mode.name) }
        mutableThemeMode.value = mode
    }

    private fun read(): ThemeMode {
        val stored = prefs.getString(KEY_THEME_MODE, null)
        return ThemeMode.entries.firstOrNull { it.name == stored } ?: ThemeMode.SYSTEM
    }

    companion object {
        /** Name of the SharedPreferences file the app passes in. */
        const val PREFS_NAME = "appearance"

        private const val KEY_THEME_MODE = "theme_mode"
    }
}
