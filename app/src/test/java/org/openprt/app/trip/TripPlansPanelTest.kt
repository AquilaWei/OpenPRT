package org.openprt.app.trip

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.openprt.app.data.gtfs.TripPlan
import org.openprt.app.geo.LatLng
import org.openprt.app.planner.Itinerary
import org.openprt.app.planner.NoRouteReason
import org.openprt.app.planner.RideLeg
import org.openprt.app.planner.TransitStop
import org.openprt.app.planner.WalkLeg
import org.openprt.app.ui.theme.OpenPrtTheme
import org.openprt.app.ui.theme.ThemeMode

@RunWith(AndroidJUnit4::class)
class TripPlansPanelTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun tripPlansPanel_twoOptions_showsRouteNumbersOfEachLeg() {
        composeRule.setContent {
            TripPlansPanel(
                TripPlanUiState.Results(TWO_OPTIONS),
                actions = RecordingActions(),
                zone = UTC
            )
        }

        composeRule.onNodeWithText("61C").assertIsDisplayed()
        composeRule.onNodeWithText("P1").assertIsDisplayed()
        composeRule.onNodeWithText("71B").assertIsDisplayed()
    }

    @Test
    fun tripPlansPanel_darkTheme_showsRouteNumbers() {
        composeRule.setContent {
            OpenPrtTheme(ThemeMode.DARK) {
                TripPlansPanel(
                    TripPlanUiState.Results(TWO_OPTIONS),
                    actions = RecordingActions(),
                    zone = UTC
                )
            }
        }

        composeRule.onNodeWithText("61C").assertIsDisplayed()
    }

    @Test
    fun tripPlansPanel_twoOptions_showsTransferCounts() {
        composeRule.setContent {
            TripPlansPanel(
                TripPlanUiState.Results(TWO_OPTIONS),
                actions = RecordingActions(),
                zone = UTC
            )
        }

        composeRule.onNodeWithText("No transfers").assertIsDisplayed()
        composeRule.onNodeWithText("1 transfer").assertIsDisplayed()
    }

    @Test
    fun tripPlansPanel_twoOptions_showsTotalMinutes() {
        composeRule.setContent {
            TripPlansPanel(
                TripPlanUiState.Results(TWO_OPTIONS),
                actions = RecordingActions(),
                zone = UTC
            )
        }

        composeRule.onNodeWithText("35 min trip").assertIsDisplayed()
        composeRule.onNodeWithText("28 min trip").assertIsDisplayed()
    }

    @Test
    fun tripPlansPanel_liveFirstBus_saysLive() {
        composeRule.setContent {
            TripPlansPanel(
                TripPlanUiState.Results(listOf(DIRECT.copy(live = true))),
                actions = RecordingActions(),
                zone = UTC
            )
        }

        composeRule
            // The clock time's spacing depends on the JDK's locale data, so it is left out.
            .onNodeWithText("61C leaves Forbes Ave at Morewood at", substring = true)
            .assertIsDisplayed()
        composeRule.onNodeWithText("Live").assertIsDisplayed()
    }

    @Test
    fun tripPlansPanel_scheduledFirstBus_saysScheduled() {
        composeRule.setContent {
            TripPlansPanel(
                TripPlanUiState.Results(listOf(DIRECT)),
                actions = RecordingActions(),
                zone = UTC
            )
        }

        composeRule.onNodeWithText("Scheduled").assertIsDisplayed()
    }

    @Test
    fun tripPlansPanel_planning_saysPlanning() {
        composeRule.setContent {
            TripPlansPanel(TripPlanUiState.Planning, actions = RecordingActions())
        }

        composeRule.onNodeWithText("Planning your trip…").assertIsDisplayed()
    }

    @Test
    fun tripPlansPanel_noStopNearOrigin_saysSo() {
        composeRule.setContent {
            TripPlansPanel(
                TripPlanUiState.NoRoute(NoRouteReason.NO_STOP_NEAR_ORIGIN),
                actions = RecordingActions()
            )
        }

        composeRule
            .onNodeWithText("No bus stop within walking distance (800 m) of the starting point.")
            .assertIsDisplayed()
    }

    @Test
    fun tripPlansPanel_noStopNearDestination_saysSo() {
        composeRule.setContent {
            TripPlansPanel(
                TripPlanUiState.NoRoute(NoRouteReason.NO_STOP_NEAR_DESTINATION),
                actions = RecordingActions()
            )
        }

        composeRule
            .onNodeWithText("No bus stop within walking distance (800 m) of the destination.")
            .assertIsDisplayed()
    }

    @Test
    fun tripPlansPanel_noConnection_saysSo() {
        composeRule.setContent {
            TripPlansPanel(
                TripPlanUiState.NoRoute(NoRouteReason.NO_CONNECTION),
                actions = RecordingActions()
            )
        }

        composeRule
            .onNodeWithText("No buses in the timetable connect these places at this time.")
            .assertIsDisplayed()
    }

    @Test
    fun tripPlansPanel_noTimetableRetryClicked_reportsRetry() {
        val actions = RecordingActions()
        composeRule.setContent {
            TripPlansPanel(TripPlanUiState.NoTimetable, actions)
        }

        composeRule.onNodeWithText("Try again").performClick()

        assertEquals(listOf("retry"), actions.calls)
    }

    @Test
    fun tripPlansPanel_twoOptions_eachShowsItOpens() {
        composeRule.setContent {
            TripPlansPanel(TripPlanUiState.Results(TWO_OPTIONS), RecordingActions(), zone = UTC)
        }

        composeRule
            .onAllNodesWithContentDescription("Show this way on the map")
            .assertCountEquals(2)
    }

    @Test
    fun tripPlansPanel_optionClicked_selectsIt() {
        val actions = RecordingActions()
        composeRule.setContent {
            TripPlansPanel(TripPlanUiState.Results(TWO_OPTIONS), actions, zone = UTC)
        }

        composeRule.onNodeWithText("35 min trip").performClick()

        assertEquals(listOf("select 61C"), actions.calls)
    }

    @Test
    fun tripPlansPanel_optionSelected_listsEachLeg() {
        composeRule.setContent {
            TripPlansPanel(SELECTED, RecordingActions(), zone = UTC)
        }

        composeRule.onNodeWithText("Walk 4 min to Forbes Ave at Morewood").assertIsDisplayed()
        composeRule.onNodeWithText("Toward Downtown").assertIsDisplayed()
        composeRule.onNodeWithText("Walk 2 min to your destination").assertIsDisplayed()
    }

    @Test
    fun tripPlansPanel_optionSelected_showsWhereToBoardAndGetOff() {
        composeRule.setContent {
            TripPlansPanel(SELECTED, RecordingActions(), zone = UTC)
        }

        composeRule.onNodeWithText("Board at Forbes Ave at Morewood").assertIsDisplayed()
        composeRule.onNodeWithText("Get off at Steel Plaza").assertIsDisplayed()
    }

    @Test
    fun tripPlansPanel_optionSelected_showsLiveBusAboveTheStops() {
        composeRule.setContent { TripPlansPanel(SELECTED, RecordingActions(), zone = UTC) }

        val liveBus = composeRule.onNodeWithText("Live bus").getUnclippedBoundsInRoot()
        val boardAt = composeRule
            .onNodeWithText("Board at Forbes Ave at Morewood")
            .getUnclippedBoundsInRoot()
        assertTrue(liveBus.bottom <= boardAt.top)
    }

    @Test
    fun tripPlansPanel_liveBusClicked_opensThatRide() {
        val actions = RecordingActions()
        composeRule.setContent { TripPlansPanel(SELECTED, actions, zone = UTC) }

        composeRule.onNodeWithText("Live bus").performClick()

        assertEquals(listOf("openRide 61C"), actions.calls)
    }

    @Test
    fun tripPlansPanel_rideHasNoLiveData_saysTimesAreScheduled() {
        val selected = SELECTED.copy(
            selected = SELECTED.selected!!.copy(ride = RideLookup.ScheduledOnly(RIDE))
        )
        composeRule.setContent { TripPlansPanel(selected, RecordingActions(), zone = UTC) }

        composeRule
            .onNodeWithText("No live data for this bus right now", substring = true)
            .assertIsDisplayed()
    }

    @Test
    fun tripPlansPanel_backFromSelectedOption_closesIt() {
        val actions = RecordingActions()
        composeRule.setContent { TripPlansPanel(SELECTED, actions, zone = UTC) }

        composeRule.onNodeWithContentDescription("Back to ways to get there").performClick()

        assertEquals(listOf("closeSelection"), actions.calls)
    }

    @Test
    fun tripPlansPanel_arriveByClicked_reportsTheMode() {
        val actions = RecordingActions()
        composeRule.setContent {
            TripPlansPanel(TripPlanUiState.Results(TWO_OPTIONS), actions, zone = UTC)
        }

        composeRule.onNodeWithText("Arrive by").performClick()

        assertEquals(listOf("setTimeMode ARRIVE_BY"), actions.calls)
    }

    @Test
    fun tripPlansPanel_leaveNow_hasNoDateOrTimeButtons() {
        composeRule.setContent {
            TripPlansPanel(TripPlanUiState.Results(TWO_OPTIONS), RecordingActions(), zone = UTC)
        }

        composeRule.onNodeWithContentDescription("Change the date").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Change the time").assertDoesNotExist()
    }

    @Test
    fun tripPlansPanel_departAt_showsTheChosenDateAndTime() {
        composeRule.setContent {
            TripPlansPanel(
                TripPlanUiState.Planning,
                RecordingActions(),
                time = DEPART_AT_NOON,
                zone = UTC
            )
        }

        composeRule.onNodeWithText("Oct 2, 2026").assertIsDisplayed()
        composeRule.onNodeWithText("12:30", substring = true).assertIsDisplayed()
    }

    @Test
    fun tripPlansPanel_noRoute_stillOffersTheTimeChoice() {
        composeRule.setContent {
            TripPlansPanel(
                TripPlanUiState.NoRoute(NoRouteReason.NO_CONNECTION),
                RecordingActions(),
                time = DEPART_AT_NOON,
                zone = UTC
            )
        }

        composeRule.onNodeWithContentDescription("Change the time").assertIsDisplayed()
    }

    @Test
    fun tripPlansPanel_arriveByOption_saysWhenToLeave() {
        composeRule.setContent {
            TripPlansPanel(
                TripPlanUiState.Results(listOf(ARRIVE_BY_OPTION)),
                RecordingActions(),
                zone = UTC
            )
        }

        composeRule.onNodeWithText("Leave by 10:56", substring = true).assertIsDisplayed()
    }

    @Test
    fun tripPlansPanel_arriveByOptionOnTime_hasNoLateWarning() {
        composeRule.setContent {
            TripPlansPanel(
                TripPlanUiState.Results(listOf(ARRIVE_BY_OPTION)),
                RecordingActions(),
                zone = UTC
            )
        }

        composeRule.onNodeWithText("running late", substring = true).assertDoesNotExist()
    }

    @Test
    fun tripPlansPanel_arriveByOptionLate_warnsItMayMissTheDeadline() {
        val late = ARRIVE_BY_OPTION.copy(arrivalTime = Instant.parse("2026-10-01T11:36:00Z"))
        composeRule.setContent {
            TripPlansPanel(TripPlanUiState.Results(listOf(late)), RecordingActions(), zone = UTC)
        }

        composeRule
            .onNodeWithText(
                "The first bus is running late, so you may arrive after 11:32",
                substring = true
            )
            .assertIsDisplayed()
    }

    private class RecordingActions : TripPlanActions {
        val calls = mutableListOf<String>()

        override fun retry() {
            calls += "retry"
        }

        override fun select(option: TripOption) {
            calls += "select ${option.firstRoute}"
        }

        override fun closeSelection() {
            calls += "closeSelection"
        }

        override fun openRide(ride: RideLeg) {
            calls += "openRide ${ride.routeId}"
        }

        override fun onRideOpened() {
            calls += "onRideOpened"
        }

        override fun setTimeMode(mode: TripTimeMode) {
            calls += "setTimeMode $mode"
        }

        override fun setTime(at: Instant) {
            calls += "setTime $at"
        }
    }

    private companion object {
        val UTC: ZoneId = ZoneId.of("UTC")
        val CMU = TransitStop("s8312", "Forbes Ave at Morewood", LatLng(40.4443, -79.9532), "8312")
        val STEEL_PLAZA = TransitStop("s10", "Steel Plaza", LatLng(40.4406, -79.9959), "10")

        val RIDE = RideLeg("T1", "61C", "INBOUND-DOWNTOWN", CMU, STEEL_PLAZA, 25_200, 27_000)

        // The option list reads only the summary fields; the selected option lists these legs.
        val PLAN = TripPlan(
            LocalDate.of(2026, 10, 1),
            Itinerary(
                listOf(
                    WalkLeg(null, CMU, 288.0, 24_960, 25_200),
                    RIDE,
                    WalkLeg(STEEL_PLAZA, null, 120.0, 27_000, 27_100)
                )
            )
        )

        val DIRECT = TripOption(
            departureTime = Instant.parse("2026-10-01T10:56:00Z"),
            arrivalTime = Instant.parse("2026-10-01T11:31:00Z"),
            totalMinutes = 35,
            transfers = 0,
            legs = listOf(LegSummary.Walk(4), LegSummary.Ride("61C"), LegSummary.Walk(2)),
            firstRoute = "61C",
            boardingStopName = "Forbes Ave at Morewood",
            boardingTime = Instant.parse("2026-10-01T11:00:00Z"),
            live = false,
            plan = PLAN
        )

        val WITH_TRANSFER = TripOption(
            departureTime = Instant.parse("2026-10-01T11:01:00Z"),
            arrivalTime = Instant.parse("2026-10-01T11:29:00Z"),
            totalMinutes = 28,
            transfers = 1,
            legs = listOf(
                LegSummary.Walk(3),
                LegSummary.Ride("P1"),
                LegSummary.Walk(1),
                LegSummary.Ride("71B")
            ),
            firstRoute = "P1",
            boardingStopName = "Fifth Ave at Craig",
            boardingTime = Instant.parse("2026-10-01T11:04:00Z"),
            live = false,
            plan = PLAN
        )

        val TWO_OPTIONS = listOf(DIRECT, WITH_TRANSFER)

        val ARRIVE_BY_OPTION = DIRECT.copy(deadline = Instant.parse("2026-10-01T11:32:00Z"))

        val DEPART_AT_NOON = TripTimeUiState(
            mode = TripTimeMode.DEPART_AT,
            at = Instant.parse("2026-10-02T12:30:00Z")
        )

        val SELECTED = TripPlanUiState.Results(
            TWO_OPTIONS,
            SelectedTrip(
                DIRECT,
                TripMapLayers(emptyList(), emptyList(), emptyList(), emptyList())
            )
        )
    }
}
