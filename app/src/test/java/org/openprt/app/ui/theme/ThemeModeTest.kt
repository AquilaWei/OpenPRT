package org.openprt.app.ui.theme

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeModeTest {
    @Test
    fun isDark_systemModeWithPhoneDark_isDark() {
        assertTrue(ThemeMode.SYSTEM.isDark(systemDark = true))
    }

    @Test
    fun isDark_systemModeWithPhoneLight_isLight() {
        assertFalse(ThemeMode.SYSTEM.isDark(systemDark = false))
    }

    @Test
    fun isDark_lightModeWithPhoneDark_isLight() {
        assertFalse(ThemeMode.LIGHT.isDark(systemDark = true))
    }

    @Test
    fun isDark_darkModeWithPhoneLight_isDark() {
        assertTrue(ThemeMode.DARK.isDark(systemDark = false))
    }
}
