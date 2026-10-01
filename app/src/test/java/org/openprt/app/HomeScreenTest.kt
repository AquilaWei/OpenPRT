package org.openprt.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.openprt.app.geo.LatLng
import org.openprt.app.location.LocationError
import org.openprt.app.location.LocationUiState

@RunWith(AndroidJUnit4::class)
class HomeScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun homeScreen_whenShown_displaysAppTitle() {
        composeRule.setContent { HomeScreen(LocationUiState.Loading) }

        composeRule.onNodeWithText("OpenPRT").assertIsDisplayed()
    }

    @Test
    fun homeScreen_permissionDenied_tellsUserDowntownIsShown() {
        composeRule.setContent { HomeScreen(LocationUiState.PermissionDenied()) }

        composeRule
            .onNodeWithText("Location permission denied", substring = true)
            .assertIsDisplayed()
    }

    @Test
    fun homeScreen_timedOut_showsTimeoutMessage() {
        composeRule.setContent { HomeScreen(LocationUiState.Failed(LocationError.Timeout)) }

        composeRule
            .onNodeWithText("Couldn't get your location (timed out)", substring = true)
            .assertIsDisplayed()
    }

    @Test
    fun homeScreen_located_showsCoordinates() {
        composeRule.setContent { HomeScreen(LocationUiState.Located(LatLng(40.4443, -79.9532))) }

        composeRule.onNodeWithText("Your location: 40.44430, -79.95320").assertIsDisplayed()
    }
}
