package org.openprt.app.details

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
import org.openprt.app.data.truetime.TrueTimeError
import org.openprt.app.departures.DepartureItem
import org.openprt.app.geo.LatLng
import org.openprt.app.map.StopMarker

@RunWith(AndroidJUnit4::class)
class DepartureDetailsPanelTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun detailsPanel_whenShown_showsRouteAndBoardingStop() {
        composeRule.setContent {
            DepartureDetailsPanel(
                DepartureDetailsUiState(DEPARTURE, RouteStatus.Loading),
                onBack = {}
            )
        }

        composeRule.onNodeWithText("61C").assertIsDisplayed()
        composeRule
            .onNodeWithText("Board at Forbes Ave at Morewood · 2 min walk")
            .assertIsDisplayed()
    }

    @Test
    fun detailsPanel_backClicked_callsOnBack() {
        var backs = 0
        composeRule.setContent {
            DepartureDetailsPanel(
                DepartureDetailsUiState(DEPARTURE, RouteStatus.Loading),
                onBack = { backs++ }
            )
        }

        composeRule.onNodeWithContentDescription("Back to nearby departures").performClick()

        assertEquals(1, backs)
    }

    @Test
    fun detailsPanel_routeReady_marksBoardingStop() {
        composeRule.setContent {
            DepartureDetailsPanel(
                DepartureDetailsUiState(DEPARTURE, RouteStatus.Ready(SHAPE)),
                onBack = {}
            )
        }

        composeRule.onNodeWithText("Board here").assertIsDisplayed()
    }

    @Test
    fun detailsPanel_routeReady_listsStopsFromBoardingStopOn() {
        composeRule.setContent {
            DepartureDetailsPanel(
                DepartureDetailsUiState(DEPARTURE, RouteStatus.Ready(SHAPE)),
                onBack = {}
            )
        }

        composeRule.onNodeWithText("Forbes Ave at Morewood").assertIsDisplayed()
        composeRule.onNodeWithText("Fifth Ave at Craig St").assertIsDisplayed()
    }

    @Test
    fun detailsPanel_routeFailed_showsError() {
        composeRule.setContent {
            DepartureDetailsPanel(
                DepartureDetailsUiState(DEPARTURE, RouteStatus.Failed(TrueTimeError.Timeout)),
                onBack = {}
            )
        }

        composeRule.onNodeWithText("Couldn't load the route", substring = true).assertIsDisplayed()
    }

    @Test
    fun detailsPanel_routeNotFound_saysBusStoppedReporting() {
        composeRule.setContent {
            DepartureDetailsPanel(
                DepartureDetailsUiState(DEPARTURE, RouteStatus.NotFound),
                onBack = {}
            )
        }

        composeRule
            .onNodeWithText("This bus is no longer reporting its route", substring = true)
            .assertIsDisplayed()
    }

    private companion object {
        val DEPARTURE = DepartureItem(
            route = "61C",
            direction = "OUTBOUND",
            destination = "McKeesport",
            stopName = "Forbes Ave at Morewood",
            walkMinutes = 2,
            minutesUntilDeparture = 5,
            delayed = false,
            stopId = "7117",
            vehicleId = "5601"
        )

        val BOARDING = StopMarker("7117", "Forbes Ave at Morewood", LatLng(40.4445, -79.9429))

        val SHAPE = RouteShape(
            line = listOf(LatLng(40.4409, -79.9991), LatLng(40.4445, -79.9429)),
            stops = listOf(
                StopMarker("20690", "Fifth Ave at Wood St", LatLng(40.4409, -79.9991)),
                BOARDING,
                StopMarker("2635", "Fifth Ave at Craig St", LatLng(40.4447, -79.9483))
            ),
            boardingStop = BOARDING
        )
    }
}
