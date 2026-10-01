package org.openprt.app.destination

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.openprt.app.geo.LatLng

@RunWith(AndroidJUnit4::class)
class DestinationSearchTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun destinationSearch_withResults_showsPlaceNameAndDescription() {
        composeRule.setContent {
            DestinationSearch(
                DestinationUiState("cmu", SearchStatus.Results(listOf(CMU))),
                onQueryChanged = {},
                onPlaceSelected = {},
                onRetry = {},
                onClearDestination = {}
            )
        }

        composeRule.onNodeWithText("Carnegie Mellon University").assertIsDisplayed()
        composeRule.onNodeWithText("North Oakland, Pittsburgh").assertIsDisplayed()
    }

    @Test
    fun destinationSearch_resultClicked_reportsPlace() {
        val selected = mutableListOf<Place>()
        composeRule.setContent {
            DestinationSearch(
                DestinationUiState("cmu", SearchStatus.Results(listOf(CMU))),
                onQueryChanged = {},
                onPlaceSelected = { selected += it },
                onRetry = {},
                onClearDestination = {}
            )
        }

        composeRule.onNodeWithText("Carnegie Mellon University").performClick()

        assertEquals(listOf(CMU), selected)
    }

    @Test
    fun destinationSearch_noResults_saysNothingFoundInPittsburgh() {
        composeRule.setContent {
            DestinationSearch(
                DestinationUiState("zzzz", SearchStatus.Results(emptyList())),
                onQueryChanged = {},
                onPlaceSelected = {},
                onRetry = {},
                onClearDestination = {}
            )
        }

        composeRule.onNodeWithText("No matching places in the Pittsburgh area.").assertIsDisplayed()
    }

    @Test
    fun destinationSearch_failed_showsErrorMessage() {
        composeRule.setContent {
            DestinationSearch(
                DestinationUiState("cmu", SearchStatus.Failed(GeocodeError.Timeout)),
                onQueryChanged = {},
                onPlaceSelected = {},
                onRetry = {},
                onClearDestination = {}
            )
        }

        composeRule.onNodeWithText("Couldn't search for places.").assertIsDisplayed()
    }

    @Test
    fun destinationSearch_retryClicked_callsOnRetry() {
        var retries = 0
        composeRule.setContent {
            DestinationSearch(
                DestinationUiState("cmu", SearchStatus.Failed(GeocodeError.Timeout)),
                onQueryChanged = {},
                onPlaceSelected = {},
                onRetry = { retries++ },
                onClearDestination = {}
            )
        }

        composeRule.onNodeWithText("Retry").performClick()

        assertEquals(1, retries)
    }

    @Test
    fun destinationSearch_namedDestination_showsItsName() {
        composeRule.setContent {
            DestinationSearch(
                DestinationUiState(destination = Destination(CMU.name, CMU.location)),
                onQueryChanged = {},
                onPlaceSelected = {},
                onRetry = {},
                onClearDestination = {}
            )
        }

        composeRule.onNodeWithText("To: Carnegie Mellon University").assertIsDisplayed()
    }

    @Test
    fun destinationSearch_pinnedDestination_showsCoordinates() {
        composeRule.setContent {
            DestinationSearch(
                DestinationUiState(destination = Destination(null, LatLng(40.4612, -79.9254))),
                onQueryChanged = {},
                onPlaceSelected = {},
                onRetry = {},
                onClearDestination = {}
            )
        }

        composeRule.onNodeWithText("To: Pinned spot (40.46120, -79.92540)").assertIsDisplayed()
    }

    @Test
    fun destinationSearch_clearDestinationClicked_callsOnClearDestination() {
        var clears = 0
        composeRule.setContent {
            DestinationSearch(
                DestinationUiState(destination = Destination(CMU.name, CMU.location)),
                onQueryChanged = {},
                onPlaceSelected = {},
                onRetry = {},
                onClearDestination = { clears++ }
            )
        }

        composeRule.onNodeWithContentDescription("Clear destination").performClick()

        assertEquals(1, clears)
    }

    private companion object {
        val CMU = Place(
            "Carnegie Mellon University",
            "North Oakland, Pittsburgh",
            LatLng(40.4439193, -79.9428267)
        )
    }
}
