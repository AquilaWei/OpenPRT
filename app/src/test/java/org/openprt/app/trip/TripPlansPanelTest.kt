package org.openprt.app.trip

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
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
            TripPlansPanel(TripPlanUiState.Results(TWO_OPTIONS), onRetry = {}, zone = UTC)
        }

        composeRule.onNodeWithText("61C").assertIsDisplayed()
        composeRule.onNodeWithText("P1").assertIsDisplayed()
        composeRule.onNodeWithText("71B").assertIsDisplayed()
    }

    @Test
    fun tripPlansPanel_darkTheme_showsRouteNumbers() {
        composeRule.setContent {
            OpenPrtTheme(ThemeMode.DARK) {
                TripPlansPanel(TripPlanUiState.Results(TWO_OPTIONS), onRetry = {}, zone = UTC)
            }
        }

        composeRule.onNodeWithText("61C").assertIsDisplayed()
    }

    @Test
    fun tripPlansPanel_twoOptions_showsTransferCounts() {
        composeRule.setContent {
            TripPlansPanel(TripPlanUiState.Results(TWO_OPTIONS), onRetry = {}, zone = UTC)
        }

        composeRule.onNodeWithText("No transfers").assertIsDisplayed()
        composeRule.onNodeWithText("1 transfer").assertIsDisplayed()
    }

    @Test
    fun tripPlansPanel_twoOptions_showsTotalMinutes() {
        composeRule.setContent {
            TripPlansPanel(TripPlanUiState.Results(TWO_OPTIONS), onRetry = {}, zone = UTC)
        }

        composeRule.onNodeWithText("35 min").assertIsDisplayed()
        composeRule.onNodeWithText("28 min").assertIsDisplayed()
    }

    @Test
    fun tripPlansPanel_liveFirstBus_saysLive() {
        composeRule.setContent {
            TripPlansPanel(
                TripPlanUiState.Results(listOf(DIRECT.copy(live = true))),
                onRetry = {},
                zone = UTC
            )
        }

        composeRule
            // The clock time's spacing depends on the JDK's locale data, so it is left out.
            .onNodeWithText("61C leaves Forbes Ave at Morewood at", substring = true)
            .assertIsDisplayed()
        composeRule
            .onNodeWithText("· Live", substring = true)
            .assertIsDisplayed()
    }

    @Test
    fun tripPlansPanel_scheduledFirstBus_saysScheduled() {
        composeRule.setContent {
            TripPlansPanel(TripPlanUiState.Results(listOf(DIRECT)), onRetry = {}, zone = UTC)
        }

        composeRule
            .onNodeWithText("(scheduled)", substring = true)
            .assertIsDisplayed()
    }

    @Test
    fun tripPlansPanel_planning_saysPlanning() {
        composeRule.setContent {
            TripPlansPanel(TripPlanUiState.Planning, onRetry = {})
        }

        composeRule.onNodeWithText("Planning your trip…").assertIsDisplayed()
    }

    @Test
    fun tripPlansPanel_noStopNearOrigin_saysSo() {
        composeRule.setContent {
            TripPlansPanel(
                TripPlanUiState.NoRoute(NoRouteReason.NO_STOP_NEAR_ORIGIN),
                onRetry = {}
            )
        }

        composeRule
            .onNodeWithText("No bus stop within walking distance (800 m) of you.")
            .assertIsDisplayed()
    }

    @Test
    fun tripPlansPanel_noStopNearDestination_saysSo() {
        composeRule.setContent {
            TripPlansPanel(
                TripPlanUiState.NoRoute(NoRouteReason.NO_STOP_NEAR_DESTINATION),
                onRetry = {}
            )
        }

        composeRule
            .onNodeWithText("No bus stop within walking distance (800 m) of the destination.")
            .assertIsDisplayed()
    }

    @Test
    fun tripPlansPanel_noConnection_saysSo() {
        composeRule.setContent {
            TripPlansPanel(TripPlanUiState.NoRoute(NoRouteReason.NO_CONNECTION), onRetry = {})
        }

        composeRule
            .onNodeWithText("No buses connect these places for the rest of today's timetable.")
            .assertIsDisplayed()
    }

    @Test
    fun tripPlansPanel_noTimetableRetryClicked_reportsRetry() {
        var retries = 0
        composeRule.setContent {
            TripPlansPanel(TripPlanUiState.NoTimetable, onRetry = { retries++ })
        }

        composeRule.onNodeWithText("Try again").performClick()

        assertEquals(1, retries)
    }

    private companion object {
        val UTC: ZoneId = ZoneId.of("UTC")
        val CMU = TransitStop("s8312", "Forbes Ave at Morewood", LatLng(40.4443, -79.9532), "8312")
        val STEEL_PLAZA = TransitStop("s10", "Steel Plaza", LatLng(40.4406, -79.9959), "10")

        // The panel only reads the summary fields; the plan is there for the map.
        val PLAN = TripPlan(
            LocalDate.of(2026, 10, 1),
            Itinerary(
                listOf(
                    WalkLeg(null, CMU, 0.0, 25_200, 25_200),
                    RideLeg("T1", "61C", null, CMU, STEEL_PLAZA, 25_200, 27_000)
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
    }
}
