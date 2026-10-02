package org.openprt.app.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.openprt.app.ui.theme.OpenPrtTheme
import org.openprt.app.ui.theme.ThemeMode

@RunWith(AndroidJUnit4::class)
class ApiKeyScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun apiKeyScreen_firstRun_welcomesUser() {
        composeRule.setContent { ApiKeyScreen(FIRST_RUN, RecordingActions()) }

        composeRule.onNodeWithText("Welcome to OpenPRT").assertIsDisplayed()
    }

    @Test
    fun apiKeyScreen_darkTheme_welcomesUser() {
        composeRule.setContent {
            OpenPrtTheme(ThemeMode.DARK) { ApiKeyScreen(FIRST_RUN, RecordingActions()) }
        }

        composeRule.onNodeWithText("Welcome to OpenPRT").assertIsDisplayed()
    }

    @Test
    fun apiKeyScreen_firstRunSkipClicked_dismisses() {
        val actions = RecordingActions()
        composeRule.setContent { ApiKeyScreen(FIRST_RUN, actions) }

        composeRule.onNodeWithText("Skip for now").performClick()

        assertEquals(listOf("dismiss"), actions.calls)
    }

    @Test
    fun apiKeyScreen_openedFromHome_offersCancel() {
        composeRule.setContent {
            ApiKeyScreen(
                ApiKeyUiState(visible = true, firstRun = false, hasKey = true),
                RecordingActions()
            )
        }

        composeRule.onNodeWithText("Cancel").assertIsDisplayed()
    }

    @Test
    fun apiKeyScreen_emptyInput_saveIsDisabled() {
        composeRule.setContent { ApiKeyScreen(FIRST_RUN, RecordingActions()) }

        composeRule.onNodeWithText("Save key").assertIsNotEnabled()
    }

    @Test
    fun apiKeyScreen_saveClicked_submits() {
        val actions = RecordingActions()
        composeRule.setContent { ApiKeyScreen(FIRST_RUN.copy(input = "my-key"), actions) }

        composeRule.onNodeWithText("Save key").performClick()

        assertEquals(listOf("submit"), actions.calls)
    }

    @Test
    fun apiKeyScreen_rejected_showsTrueTimeMessage() {
        composeRule.setContent {
            ApiKeyScreen(
                FIRST_RUN.copy(
                    input = "bad-key",
                    check = KeyCheckStatus.Rejected(listOf("Invalid API access key supplied"))
                ),
                RecordingActions()
            )
        }

        composeRule
            .onNodeWithText("TrueTime didn't accept this key: Invalid API access key supplied")
            .assertIsDisplayed()
    }

    @Test
    fun apiKeyScreen_unreachableSaveAnywayClicked_savesWithoutChecking() {
        val actions = RecordingActions()
        composeRule.setContent {
            ApiKeyScreen(
                FIRST_RUN.copy(input = "some-key", check = KeyCheckStatus.Unreachable),
                actions
            )
        }

        composeRule.onNodeWithText("Save without checking").performClick()

        assertEquals(listOf("saveWithoutChecking"), actions.calls)
    }

    private class RecordingActions : ApiKeyActions {
        val calls = mutableListOf<String>()

        override fun onInputChanged(input: String) {
            calls += "onInputChanged"
        }

        override fun submit() {
            calls += "submit"
        }

        override fun saveWithoutChecking() {
            calls += "saveWithoutChecking"
        }

        override fun dismiss() {
            calls += "dismiss"
        }
    }

    private companion object {
        val FIRST_RUN = ApiKeyUiState(visible = true, firstRun = true, hasKey = false)
    }
}
