package org.openprt.app.destination

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlin.time.Duration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

    // The To line (56 dp) under the From line (48 dp), a divider and the 8 dp margins.
    @Test
    fun destinationSearch_destinationChosen_staysTwoLinesTall() {
        composeRule.setContent {
            DestinationSearch(
                DestinationUiState(destination = Destination(CMU.name, CMU.location)),
                onQueryChanged = {},
                onPlaceSelected = {},
                onRetry = {},
                onClearDestination = {},
                modifier = Modifier.testTag("search")
            )
        }

        val height = composeRule.onNodeWithTag("search").getUnclippedBoundsInRoot().height
        assertTrue(height <= 130.dp)
    }

    @Test
    fun destinationSearch_destinationChosen_hidesSearchField() {
        composeRule.setContent {
            DestinationSearch(
                DestinationUiState(destination = Destination(CMU.name, CMU.location)),
                onQueryChanged = {},
                onPlaceSelected = {},
                onRetry = {},
                onClearDestination = {}
            )
        }

        composeRule.onNode(hasSetTextAction()).assertDoesNotExist()
    }

    @Test
    fun destinationSearch_destinationClicked_showsSearchFieldAgain() {
        composeRule.setContent {
            DestinationSearch(
                DestinationUiState(destination = Destination(CMU.name, CMU.location)),
                onQueryChanged = {},
                onPlaceSelected = {},
                onRetry = {},
                onClearDestination = {}
            )
        }

        composeRule.onNodeWithText("To: Carnegie Mellon University").performClick()

        composeRule.onNode(hasSetTextAction()).assertIsDisplayed()
    }

    @Test
    fun destinationSearch_noOriginChosen_showsFromMyLocation() {
        composeRule.setContent {
            DestinationSearch(
                DestinationUiState(destination = Destination(CMU.name, CMU.location)),
                onQueryChanged = {},
                onPlaceSelected = {},
                onRetry = {},
                onClearDestination = {}
            )
        }

        composeRule.onNodeWithText("From: My location").assertIsDisplayed()
    }

    @Test
    fun destinationSearch_originChosen_showsItsName() {
        composeRule.setContent {
            DestinationSearch(
                DestinationUiState(
                    destination = Destination(null, PINNED),
                    origin = Destination(CMU.name, CMU.location)
                ),
                onQueryChanged = {},
                onPlaceSelected = {},
                onRetry = {},
                onClearDestination = {}
            )
        }

        composeRule.onNodeWithText("From: Carnegie Mellon University").assertIsDisplayed()
    }

    @Test
    fun destinationSearch_noDestinationOrOrigin_hasNoFromLine() {
        composeRule.setContent {
            DestinationSearch(
                DestinationUiState(),
                onQueryChanged = {},
                onPlaceSelected = {},
                onRetry = {},
                onClearDestination = {}
            )
        }

        composeRule.onNodeWithText("From: My location").assertDoesNotExist()
    }

    @Test
    fun destinationSearch_fromClicked_editsOrigin() {
        var edits = 0
        composeRule.setContent {
            DestinationSearch(
                DestinationUiState(destination = Destination(CMU.name, CMU.location)),
                onQueryChanged = {},
                onPlaceSelected = {},
                onRetry = {},
                onClearDestination = {},
                onEditOrigin = { edits++ }
            )
        }

        composeRule.onNodeWithText("From: My location").performClick()

        assertEquals(1, edits)
    }

    @Test
    fun destinationSearch_editingOrigin_saysMapCanBeLongPressed() {
        composeRule.setContent {
            DestinationSearch(
                DestinationUiState(
                    destination = Destination(CMU.name, CMU.location),
                    editing = Endpoint.ORIGIN
                ),
                onQueryChanged = {},
                onPlaceSelected = {},
                onRetry = {},
                onClearDestination = {}
            )
        }

        composeRule.onNodeWithText("Search, or long-press the map to start from a spot.")
            .assertIsDisplayed()
    }

    @Test
    fun destinationSearch_editingOriginCancelled_reportsIt() {
        var cancels = 0
        composeRule.setContent {
            DestinationSearch(
                DestinationUiState(editing = Endpoint.ORIGIN),
                onQueryChanged = {},
                onPlaceSelected = {},
                onRetry = {},
                onClearDestination = {},
                onCancelOriginEdit = { cancels++ }
            )
        }

        composeRule.onNodeWithContentDescription("Stop choosing a starting point").performClick()

        assertEquals(1, cancels)
    }

    @Test
    fun destinationSearch_originResultClicked_fillsFromLine() {
        val viewModel = DestinationViewModel(
            { _, _, _ -> GeocodeResult.Success(listOf(CMU)) },
            debounce = Duration.ZERO
        )
        viewModel.onMapLongPress(PINNED)
        viewModel.editOrigin()
        composeRule.setContent {
            val state by viewModel.state.collectAsState()
            DestinationSearch(
                state,
                onQueryChanged = viewModel::onQueryChanged,
                onPlaceSelected = viewModel::selectPlace,
                onRetry = viewModel::retry,
                onClearDestination = viewModel::clearDestination,
                onEditOrigin = viewModel::editOrigin,
                onCancelOriginEdit = viewModel::cancelOriginEdit
            )
        }

        composeRule.onNode(hasSetTextAction()).performTextInput("cmu")
        composeRule.onNodeWithText("North Oakland, Pittsburgh").performClick()

        composeRule.onNodeWithText("From: Carnegie Mellon University").assertIsDisplayed()
    }

    @Test
    fun destinationSearch_clearOriginClicked_reportsIt() {
        var clears = 0
        composeRule.setContent {
            DestinationSearch(
                DestinationUiState(
                    destination = Destination(null, PINNED),
                    origin = Destination(CMU.name, CMU.location)
                ),
                onQueryChanged = {},
                onPlaceSelected = {},
                onRetry = {},
                onClearDestination = {},
                onClearOrigin = { clears++ }
            )
        }

        composeRule.onNodeWithContentDescription("Start from my location").performClick()

        assertEquals(1, clears)
    }

    @Test
    fun destinationSearch_swapClicked_reportsIt() {
        var swaps = 0
        composeRule.setContent {
            DestinationSearch(
                DestinationUiState(destination = Destination(CMU.name, CMU.location)),
                onQueryChanged = {},
                onPlaceSelected = {},
                onRetry = {},
                onClearDestination = {},
                onSwap = { swaps++ }
            )
        }

        composeRule.onNodeWithContentDescription("Swap start and destination").performClick()

        assertEquals(1, swaps)
    }

    @Test
    fun destinationSearch_destinationIsSwappedUserLocation_saysSo() {
        composeRule.setContent {
            DestinationSearch(
                DestinationUiState(
                    destination = Destination(null, PINNED, wasUserLocation = true),
                    origin = Destination(CMU.name, CMU.location)
                ),
                onQueryChanged = {},
                onPlaceSelected = {},
                onRetry = {},
                onClearDestination = {}
            )
        }

        composeRule.onNodeWithText("To: My location (pinned)").assertIsDisplayed()
    }

    private companion object {
        val PINNED = LatLng(40.4612, -79.9254)

        val CMU = Place(
            "Carnegie Mellon University",
            "North Oakland, Pittsburgh",
            LatLng(40.4439193, -79.9428267)
        )
    }
}
