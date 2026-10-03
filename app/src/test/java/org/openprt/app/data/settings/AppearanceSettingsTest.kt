package org.openprt.app.data.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.openprt.app.ui.theme.ThemeMode

@RunWith(AndroidJUnit4::class)
class AppearanceSettingsTest {
    private val prefs: SharedPreferences = ApplicationProvider
        .getApplicationContext<Context>()
        .getSharedPreferences(AppearanceSettings.PREFS_NAME, Context.MODE_PRIVATE)

    @Test
    fun themeMode_neverSet_followsSystem() {
        val settings = AppearanceSettings(prefs)

        assertEquals(ThemeMode.SYSTEM, settings.themeMode.value)
    }

    @Test
    fun setThemeMode_dark_isDarkRightAway() {
        val settings = AppearanceSettings(prefs)

        settings.setThemeMode(ThemeMode.DARK)

        assertEquals(ThemeMode.DARK, settings.themeMode.value)
    }

    @Test
    fun setThemeMode_thenAppRestarted_isKept() {
        AppearanceSettings(prefs).setThemeMode(ThemeMode.LIGHT)

        val afterRestart = AppearanceSettings(prefs)

        assertEquals(ThemeMode.LIGHT, afterRestart.themeMode.value)
    }

    @Test
    fun themeMode_unknownStoredValue_followsSystem() {
        prefs.edit { putString("theme_mode", "SEPIA") }

        val settings = AppearanceSettings(prefs)

        assertEquals(ThemeMode.SYSTEM, settings.themeMode.value)
    }
}
