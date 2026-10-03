package org.openprt.app.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.openprt.app.data.settings.ApiKeySettings
import org.openprt.app.data.truetime.ApiKeyChecker
import org.openprt.app.data.truetime.KeyCheck

/** Where checking the entered key stands. */
sealed interface KeyCheckStatus {
    data object Idle : KeyCheckStatus

    data object Checking : KeyCheckStatus

    /** TrueTime refused the key; it was not saved. */
    data class Rejected(val messages: List<String>) : KeyCheckStatus

    /** TrueTime could not be reached; the user may save the key unchecked. */
    data object Unreachable : KeyCheckStatus
}

data class ApiKeyUiState(
    /** Whether the key screen covers the home screen. */
    val visible: Boolean,
    /** First launch: the screen welcomes the user and offers to skip rather than cancel. */
    val firstRun: Boolean,
    /** Whether a key is already in use, so a new one would replace it. */
    val hasKey: Boolean,
    val input: String = "",
    val check: KeyCheckStatus = KeyCheckStatus.Idle
)

/**
 * The TrueTime key screen: shown on first launch while there is no key, and again whenever the
 * user opens it from the home screen. A key is checked with TrueTime before it is saved, so a
 * mistyped key is caught here rather than as a vague failure on the departures list.
 */
class ApiKeyViewModel(private val settings: ApiKeySettings, private val checker: ApiKeyChecker) :
    ViewModel(),
    ApiKeyActions {
    private val mutableState = MutableStateFlow(
        ApiKeyUiState(
            visible = settings.state.value.needsOnboarding,
            firstRun = settings.state.value.needsOnboarding,
            hasKey = settings.currentKey().isNotBlank()
        )
    )
    val state: StateFlow<ApiKeyUiState> = mutableState.asStateFlow()

    private var checking: Job? = null

    /** Opens the screen from the home screen to enter a different key. */
    fun open() {
        mutableState.value = ApiKeyUiState(
            visible = true,
            firstRun = false,
            hasKey = settings.currentKey().isNotBlank()
        )
    }

    override fun onInputChanged(input: String) {
        checking?.cancel()
        mutableState.update { it.copy(input = input, check = KeyCheckStatus.Idle) }
    }

    /** Checks the entered key with TrueTime and saves it if TrueTime accepts it. */
    override fun submit() {
        val key = mutableState.value.input.trim()
        if (key.isEmpty() || mutableState.value.check == KeyCheckStatus.Checking) return
        mutableState.update { it.copy(check = KeyCheckStatus.Checking) }
        checking = viewModelScope.launch {
            when (val result = checker.check(key)) {
                KeyCheck.Valid -> saveAndClose(key)

                is KeyCheck.Rejected -> mutableState.update {
                    it.copy(check = KeyCheckStatus.Rejected(result.messages))
                }

                is KeyCheck.Unreachable -> mutableState.update {
                    it.copy(check = KeyCheckStatus.Unreachable)
                }
            }
        }
    }

    /** Saves the entered key without asking TrueTime, offered when TrueTime was unreachable. */
    override fun saveWithoutChecking() {
        val key = mutableState.value.input.trim()
        if (key.isNotEmpty()) saveAndClose(key)
    }

    /**
     * Closes the screen without changing the key. On first launch this counts as skipping, so
     * the screen does not come back on the next launch.
     */
    override fun dismiss() {
        checking?.cancel()
        if (mutableState.value.firstRun) settings.skip()
        mutableState.update { it.copy(visible = false, input = "", check = KeyCheckStatus.Idle) }
    }

    private fun saveAndClose(key: String) {
        settings.save(key)
        mutableState.value = ApiKeyUiState(visible = false, firstRun = false, hasKey = true)
    }
}
