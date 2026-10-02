package org.openprt.app.details

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.openprt.app.data.truetime.TrueTimeError
import org.openprt.app.departures.DepartureItem
import org.openprt.app.geo.LatLng
import org.openprt.app.map.StopMarker
import org.openprt.app.ui.theme.OpenPrtTheme
import org.openprt.app.ui.theme.ThemeMode
import org.robolectric.annotation.Config

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
        composeRule.onNodeWithText("Board at Forbes Ave at Morewood").assertIsDisplayed()
        composeRule.onNodeWithText("2 min walk").assertIsDisplayed()
    }

    @Test
    fun detailsPanel_darkTheme_showsRoute() {
        composeRule.setContent {
            OpenPrtTheme(ThemeMode.DARK) {
                DepartureDetailsPanel(
                    DepartureDetailsUiState(DEPARTURE, RouteStatus.Ready(SHAPE)),
                    onBack = {}
                )
            }
        }

        composeRule.onNodeWithText("61C").assertIsDisplayed()
    }

    @Test
    fun detailsPanel_otherDirectionNearby_switchingReportsIt() {
        val switched = mutableListOf<DepartureItem>()
        composeRule.setContent {
            DepartureDetailsPanel(
                DepartureDetailsUiState(DEPARTURE, RouteStatus.Loading),
                onBack = {},
                otherDirection = INBOUND,
                onSwitchDirection = { switched.add(it) }
            )
        }

        composeRule.onNodeWithText("Inbound").performClick()

        assertEquals(listOf(INBOUND), switched)
    }

    @Test
    fun detailsPanel_currentDirection_isSelected() {
        composeRule.setContent {
            DepartureDetailsPanel(
                DepartureDetailsUiState(DEPARTURE, RouteStatus.Loading),
                onBack = {},
                otherDirection = INBOUND
            )
        }

        composeRule.onNode(hasText("Outbound") and hasClickAction()).assertIsSelected()
    }

    @Test
    fun detailsPanel_noOtherDirectionNearby_otherDirectionIsDisabled() {
        composeRule.setContent {
            DepartureDetailsPanel(
                DepartureDetailsUiState(DEPARTURE, RouteStatus.Loading),
                onBack = {}
            )
        }

        composeRule.onNode(hasText("Inbound") and hasClickAction()).assertIsNotEnabled()
    }

    @Test
    fun detailsPanel_noOtherDirectionNearby_saysSo() {
        composeRule.setContent {
            DepartureDetailsPanel(
                DepartureDetailsUiState(DEPARTURE, RouteStatus.Loading),
                onBack = {}
            )
        }

        composeRule
            .onNodeWithText("No Inbound buses you can catch nearby right now.")
            .assertIsDisplayed()
    }

    @Test
    fun detailsPanel_busOnRoute_marksItOnTheTimeline() {
        composeRule.setContent {
            DepartureDetailsPanel(
                DepartureDetailsUiState(
                    DEPARTURE,
                    RouteStatus.Ready(SHAPE),
                    LiveBus(progress = BusProgress(passedStops = 1, stopsAway = 1))
                ),
                onBack = {}
            )
        }

        composeRule.onNodeWithText("Your bus is here").assertIsDisplayed()
    }

    @Test
    fun detailsPanel_busPastFirstStop_marksThatStopPassed() {
        composeRule.setContent {
            DepartureDetailsPanel(
                DepartureDetailsUiState(
                    DEPARTURE,
                    RouteStatus.Ready(SHAPE),
                    LiveBus(progress = BusProgress(passedStops = 1, stopsAway = 1))
                ),
                onBack = {}
            )
        }

        composeRule
            .onNode(hasText("Fifth Ave at Wood St") and hasStateDescription("Passed"))
            .assertExists()
    }

    @Test
    fun detailsPanel_busApproaching_saysHowManyStopsAway() {
        composeRule.setContent {
            DepartureDetailsPanel(
                DepartureDetailsUiState(
                    DEPARTURE,
                    RouteStatus.Loading,
                    LiveBus(
                        progress = BusProgress(passedStops = 1, stopsAway = 3),
                        arrival = Arrival.Expected(7, delayed = false)
                    )
                ),
                onBack = {}
            )
        }

        composeRule.onNodeWithText("3 stops away").assertIsDisplayed()
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

    // Tall enough for the header and arrival cards plus the whole three-stop timeline.
    @Config(qualifiers = "w411dp-h900dp")
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

    @Test
    fun detailsPanel_busExpected_showsMinutesUntilArrival() {
        composeRule.setContent {
            DepartureDetailsPanel(
                DepartureDetailsUiState(
                    DEPARTURE,
                    RouteStatus.Loading,
                    LiveBus(arrival = Arrival.Expected(7, delayed = false))
                ),
                onBack = {}
            )
        }

        composeRule.onNodeWithText("Arrives at your stop in").assertIsDisplayed()
        composeRule.onNodeWithText("7 min").assertIsDisplayed()
    }

    @Test
    fun detailsPanel_busExpectedDelayed_showsDelayed() {
        composeRule.setContent {
            DepartureDetailsPanel(
                DepartureDetailsUiState(
                    DEPARTURE,
                    RouteStatus.Loading,
                    LiveBus(arrival = Arrival.Expected(7, delayed = true))
                ),
                onBack = {}
            )
        }

        composeRule.onNodeWithText("Delayed").assertIsDisplayed()
    }

    @Test
    fun detailsPanel_busDeparted_saysItLeft() {
        composeRule.setContent {
            DepartureDetailsPanel(
                DepartureDetailsUiState(
                    DEPARTURE,
                    RouteStatus.Loading,
                    LiveBus(arrival = Arrival.Departed)
                ),
                onBack = {}
            )
        }

        composeRule.onNodeWithText("This bus has left your stop.").assertIsDisplayed()
    }

    @Test
    fun detailsPanel_busUpdated_showsUpdateTime() {
        composeRule.setContent {
            DepartureDetailsPanel(
                DepartureDetailsUiState(
                    DEPARTURE,
                    RouteStatus.Loading,
                    LiveBus(
                        arrival = Arrival.Expected(7, delayed = false),
                        lastUpdated = Instant.parse("2026-10-01T16:40:15Z")
                    )
                ),
                onBack = {},
                zone = PITTSBURGH
            )
        }

        composeRule.onNodeWithText("Updated 12:40:15", substring = true).assertIsDisplayed()
    }

    @Test
    fun detailsPanel_busUpdateFailed_showsErrorWithTimeOfLastData() {
        composeRule.setContent {
            DepartureDetailsPanel(
                DepartureDetailsUiState(
                    DEPARTURE,
                    RouteStatus.Loading,
                    LiveBus(
                        arrival = Arrival.Expected(7, delayed = false),
                        lastUpdated = Instant.parse("2026-10-01T16:40:15Z"),
                        error = TrueTimeError.Timeout
                    )
                ),
                onBack = {},
                zone = PITTSBURGH
            )
        }

        composeRule
            .onNodeWithText("Couldn't update the bus. Showing data from 12:40:15", substring = true)
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

        val INBOUND = DEPARTURE.copy(
            direction = "INBOUND",
            destination = "Downtown",
            stopName = "Forbes Ave opp Morewood",
            vehicleId = "5702"
        )

        val BOARDING = StopMarker("7117", "Forbes Ave at Morewood", LatLng(40.4445, -79.9429))

        val SHAPE = RouteShape(
            line = listOf(LatLng(40.4409, -79.9991), LatLng(40.4445, -79.9429)),
            stops = listOf(
                StopMarker("20690", "Fifth Ave at Wood St", LatLng(40.4409, -79.9991)),
                BOARDING,
                StopMarker("2635", "Fifth Ave at Craig St", LatLng(40.4447, -79.9483))
            ),
            boardingStop = BOARDING,
            boardingDistanceFeet = 18620.0
        )

        val PITTSBURGH: ZoneId = ZoneId.of("America/New_York")
    }
}

private fun hasStateDescription(description: String) =
    SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, description)
