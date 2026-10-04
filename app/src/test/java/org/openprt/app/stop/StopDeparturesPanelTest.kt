package org.openprt.app.stop

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.openprt.app.data.truetime.TrueTimeError
import org.openprt.app.departures.DepartureItem
import org.openprt.app.geo.LatLng
import org.openprt.app.map.StopMarker

@RunWith(AndroidJUnit4::class)
class StopDeparturesPanelTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun stopPanel_shown_displaysStopNameInTitleCase() {
        composeRule.setContent {
            StopDeparturesPanel(LIVE_STATE, onBack = {}, onDepartureClick = {})
        }

        composeRule.onNodeWithText("Forbes Ave + Morewood Ave").assertIsDisplayed()
    }

    @Test
    fun stopPanel_shown_displaysStopNumber() {
        composeRule.setContent {
            StopDeparturesPanel(LIVE_STATE, onBack = {}, onDepartureClick = {})
        }

        composeRule.onNodeWithText("Stop #8312").assertIsDisplayed()
    }

    @Test
    fun stopPanel_liveDeparture_showsRouteDestinationMinutesAndLive() {
        composeRule.setContent {
            StopDeparturesPanel(LIVE_STATE, onBack = {}, onDepartureClick = {})
        }

        composeRule.onNodeWithText("61C").assertIsDisplayed()
        composeRule.onNodeWithText("To McKeesport").assertIsDisplayed()
        composeRule.onNodeWithText("5 min").assertIsDisplayed()
        composeRule.onNodeWithText("Live").assertIsDisplayed()
    }

    @Test
    fun stopPanel_liveDepartureClicked_reportsIt() {
        val clicked = mutableListOf<DepartureItem>()
        composeRule.setContent {
            StopDeparturesPanel(LIVE_STATE, onBack = {}, onDepartureClick = { clicked += it })
        }

        composeRule.onNodeWithText("To McKeesport").performClick()

        assertEquals(listOf(DEPARTURE), clicked)
    }

    @Test
    fun stopPanel_scheduledDeparture_isMarkedScheduled() {
        composeRule.setContent {
            StopDeparturesPanel(scheduledState(null), onBack = {}, onDepartureClick = {})
        }

        composeRule.onNodeWithText("Scheduled").assertIsDisplayed()
    }

    @Test
    fun stopPanel_scheduledDeparture_cannotBeOpened() {
        composeRule.setContent {
            StopDeparturesPanel(scheduledState(null), onBack = {}, onDepartureClick = {})
        }

        composeRule.onNodeWithText("To Downtown").assertHasNoClickAction()
    }

    @Test
    fun stopPanel_liveDeparture_canBeOpened() {
        composeRule.setContent {
            StopDeparturesPanel(LIVE_STATE, onBack = {}, onDepartureClick = {})
        }

        composeRule.onNodeWithText("To McKeesport").assertHasClickAction()
    }

    @Test
    fun stopPanel_scheduledForMissingKey_saysWhyThereAreNoLiveTimes() {
        composeRule.setContent {
            StopDeparturesPanel(
                scheduledState(TrueTimeError.MissingApiKey),
                onBack = {},
                onDepartureClick = {}
            )
        }

        composeRule.onNodeWithText(
            "No live times (no TrueTime API key). Times are from the timetable."
        ).assertIsDisplayed()
    }

    @Test
    fun stopPanel_scheduledForNetworkFailure_saysWhyThereAreNoLiveTimes() {
        composeRule.setContent {
            StopDeparturesPanel(
                scheduledState(TrueTimeError.Network(IOException("down"))),
                onBack = {},
                onDepartureClick = {}
            )
        }

        composeRule.onNodeWithText(
            "No live times (no internet connection). Times are from the timetable."
        ).assertIsDisplayed()
    }

    @Test
    fun stopPanel_scheduledWithoutPredictions_saysTrueTimeHasNone() {
        composeRule.setContent {
            StopDeparturesPanel(scheduledState(null), onBack = {}, onDepartureClick = {})
        }

        composeRule.onNodeWithText(
            "No live predictions for this stop right now. Times are from the timetable."
        ).assertIsDisplayed()
    }

    @Test
    fun stopPanel_loading_saysSo() {
        composeRule.setContent {
            StopDeparturesPanel(StopDeparturesUiState(STOP), onBack = {}, onDepartureClick = {})
        }

        composeRule.onNodeWithText("Loading buses at this stop…").assertIsDisplayed()
    }

    @Test
    fun stopPanel_nothingLeftToday_saysSo() {
        composeRule.setContent {
            StopDeparturesPanel(
                StopDeparturesUiState(STOP, emptyList(), StopTimesSource.Scheduled(null)),
                onBack = {},
                onDepartureClick = {}
            )
        }

        composeRule.onNodeWithText("No more buses from this stop in today's timetable.")
            .assertIsDisplayed()
    }

    private companion object {
        val STOP = StopMarker("8312", "FORBES AVE + MOREWOOD AVE", LatLng(40.444557, -79.942791))

        val DEPARTURE = DepartureItem(
            route = "61C",
            direction = "OUTBOUND",
            destination = "McKeesport",
            stopName = "FORBES AVE + MOREWOOD AVE",
            walkMinutes = 2,
            minutesUntilDeparture = 5,
            delayed = false,
            stopId = "8312",
            vehicleId = "5601"
        )

        val LIVE_STATE = StopDeparturesUiState(
            STOP,
            listOf(StopDeparture("61C", "McKeesport", 5, false, DEPARTURE)),
            StopTimesSource.Live
        )

        fun scheduledState(liveError: TrueTimeError?) = StopDeparturesUiState(
            STOP,
            listOf(StopDeparture("61C", "INBOUND-DOWNTOWN", 10, false, null)),
            StopTimesSource.Scheduled(liveError)
        )
    }
}
