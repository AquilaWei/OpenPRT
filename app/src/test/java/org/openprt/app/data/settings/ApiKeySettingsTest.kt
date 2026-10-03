package org.openprt.app.data.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ApiKeySettingsTest {
    private val prefs: SharedPreferences = ApplicationProvider
        .getApplicationContext<Context>()
        .getSharedPreferences(ApiKeySettings.PREFS_NAME, Context.MODE_PRIVATE)

    @Test
    fun state_noKeyAnywhere_needsOnboarding() {
        val settings = ApiKeySettings(prefs, builtInKey = "")

        assertTrue(settings.state.value.needsOnboarding)
    }

    @Test
    fun state_builtInKey_doesNotNeedOnboarding() {
        val settings = ApiKeySettings(prefs, builtInKey = "built-in")

        assertFalse(settings.state.value.needsOnboarding)
    }

    @Test
    fun currentKey_builtInKeyOnly_isBuiltInKey() {
        val settings = ApiKeySettings(prefs, builtInKey = "built-in")

        assertEquals("built-in", settings.currentKey())
    }

    @Test
    fun save_newKey_isCurrentKeyRightAway() {
        val settings = ApiKeySettings(prefs, builtInKey = "")

        settings.save("entered")

        assertEquals("entered", settings.currentKey())
    }

    @Test
    fun save_keyWithSurroundingSpaces_storesTrimmedKey() {
        val settings = ApiKeySettings(prefs, builtInKey = "")

        settings.save("  entered \n")

        assertEquals("entered", settings.currentKey())
    }

    @Test
    fun save_thenAppRestarted_keyIsStillThere() {
        ApiKeySettings(prefs, builtInKey = "").save("entered")

        val afterRestart = ApiKeySettings(prefs, builtInKey = "")

        assertEquals("entered", afterRestart.currentKey())
    }

    @Test
    fun save_withBuiltInKey_enteredKeyWins() {
        val settings = ApiKeySettings(prefs, builtInKey = "built-in")

        settings.save("entered")

        assertEquals("entered", settings.currentKey())
    }

    @Test
    fun skip_withoutKey_noLongerNeedsOnboardingAfterRestart() {
        ApiKeySettings(prefs, builtInKey = "").skip()

        val afterRestart = ApiKeySettings(prefs, builtInKey = "")

        assertFalse(afterRestart.state.value.needsOnboarding)
    }

    @Test
    fun skip_withoutKey_keyStaysBlank() {
        val settings = ApiKeySettings(prefs, builtInKey = "")

        settings.skip()

        assertEquals("", settings.currentKey())
    }
}
