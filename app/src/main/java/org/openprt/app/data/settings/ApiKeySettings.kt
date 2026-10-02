package org.openprt.app.data.settings

import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The TrueTime key in use. [key] is blank when there is none, in which case live data reports
 * a missing key; [onboardingDone] records that the user saved a key or chose to skip.
 */
data class ApiKeyState(val key: String, val onboardingDone: Boolean) {
    /** Whether to greet the user with the key setup before the home screen. */
    val needsOnboarding: Boolean get() = key.isBlank() && !onboardingDone
}

/**
 * Keeps the TrueTime key the user entered in [prefs], the app's private storage (not backed up,
 * since the manifest disables backup). A key entered in the app wins over [builtInKey], the
 * PRT_API_KEY a developer may have put in local.properties.
 *
 * Writes go to [prefs] with `apply`, which returns before the disk write finishes; [state] is
 * updated right away, so the next TrueTime request already uses the new key.
 */
class ApiKeySettings(private val prefs: SharedPreferences, private val builtInKey: String) {
    private val mutableState = MutableStateFlow(read())
    val state: StateFlow<ApiKeyState> = mutableState.asStateFlow()

    /** The key for the next TrueTime request; blank when there is none. */
    fun currentKey(): String = mutableState.value.key

    /** Stores [key] (trimmed) in place of any earlier one and ends the onboarding. */
    fun save(key: String) {
        prefs.edit {
            putString(KEY_API_KEY, key.trim())
            putBoolean(KEY_ONBOARDING_DONE, true)
        }
        mutableState.value = read()
    }

    /** Ends the onboarding without a key; live data stays unavailable until one is saved. */
    fun skip() {
        prefs.edit { putBoolean(KEY_ONBOARDING_DONE, true) }
        mutableState.value = read()
    }

    private fun read(): ApiKeyState {
        val saved = prefs.getString(KEY_API_KEY, null)?.takeIf { it.isNotBlank() }
        return ApiKeyState(
            key = saved ?: builtInKey,
            onboardingDone = prefs.getBoolean(KEY_ONBOARDING_DONE, false)
        )
    }

    companion object {
        /** Name of the SharedPreferences file the app passes in. */
        const val PREFS_NAME = "truetime"

        private const val KEY_API_KEY = "api_key"
        private const val KEY_ONBOARDING_DONE = "onboarding_done"
    }
}
