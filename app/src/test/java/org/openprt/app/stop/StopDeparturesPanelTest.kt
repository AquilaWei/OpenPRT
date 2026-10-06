package org.openprt.app.stop

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.openprt.app.data.gtfs.ScheduledRun
import org.openprt.app.data.gtfs.ScheduledStopTime
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
            StopDeparturesPanel(LIVE_STATE, onBack = {
            }, onDepartureClick = {}, onScheduledClick = {})
        }

        composeRule.onNodeWithText("Forbes Ave + Morewood Ave").assertIsDisplayed()
    }

    @Test
    fun stopPanel_shown_displaysStopNumber() {
        composeRule.setContent {
            StopDeparturesPanel(LIVE_STATE, onBack = {
            }, onDepartureClick = {}, onScheduledClick = {})
        }

        composeRule.onNodeWithText("Stop #8312").assertIsDisplayed()
    }

    @Test
    fun stopPanel_liveDeparture_showsRouteDestinationMinutesAndLive() {
        composeRule.setContent {
            StopDeparturesPanel(LIVE_STATE, onBack = {
            }, onDepartureClick = {}, onScheduledClick = {})
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
            StopDeparturesPanel(
                LIVE_STATE,
                onBack = {},
                onDepartureClick = { clicked += it },
                onScheduledClick = {}
            )
        }

        composeRule.onNodeWithText("To McKeesport").performClick()

        assertEquals(listOf(DEPARTURE), clicked)
    }

    @Test
    fun stopPanel_scheduledDeparture_isMarkedScheduled() {
        composeRule.setContent {
            StopDeparturesPanel(scheduledState(null), onBack = {
            }, onDepartureClick = {}, onScheduledClick = {})
        }

        composeRule.onNodeWithText("Scheduled").assertIsDisplayed()
    }

    @Test
    fun stopPanel_scheduledDepartureClicked_reportsIt() {
        val clicked = mutableListOf<StopDeparture>()
        composeRule.setContent {
            StopDeparturesPanel(
                scheduledState(null),
                onBack = {},
                onDepartureClick = {},
                onScheduledClick = { clicked += it }
            )
        }

        composeRule.onNodeWithText("To Downtown").performClick()

        assertEquals(listOf(SCHEDULED_ROW), clicked)
    }

    @Test
    fun stopPanel_scheduledDepartureWithoutRun_cannotBeOpened() {
        composeRule.setContent {
            StopDeparturesPanel(
                StopDeparturesUiState(
                    STOP,
                    listOf(StopDeparture("61C", "INBOUND-DOWNTOWN", 10, false, null)),
                    StopTimesSource.Scheduled(null)
                ),
                onBack = {},
                onDepartureClick = {},
                onScheduledClick = {}
            )
        }

        composeRule.onNodeWithText("To Downtown").assertHasNoClickAction()
    }

    @Test
    fun stopPanel_runOpen_listsItsStopsWithScheduledTimes() {
        composeRule.setContent {
            StopDeparturesPanel(
                runState(RUN_STOPS),
                onBack = {},
                onDepartureClick = {},
                onScheduledClick = {},
                zone = ZoneOffset.UTC
            )
        }

        composeRule.onNodeWithText("Steel Plaza Station").assertIsDisplayed()
        composeRule.onNodeWithText("12:30", substring = true).assertIsDisplayed()
    }

    @Test
    fun stopPanel_runOpen_saysTimesAreScheduled() {
        composeRule.setContent {
            StopDeparturesPanel(
                runState(RUN_STOPS),
                onBack = {},
                onDepartureClick = {},
                onScheduledClick = {}
            )
        }

        composeRule.onNodeWithText(
            "Timetabled run: there is no live position for this bus, so these are the " +
                "scheduled times."
        ).assertIsDisplayed()
    }

    @Test
    fun stopPanel_runLoading_saysSo() {
        composeRule.setContent {
            StopDeparturesPanel(
                runState(null),
                onBack = {},
                onDepartureClick = {},
                onScheduledClick = {}
            )
        }

        composeRule.onNodeWithText("Loading this run's stops…").assertIsDisplayed()
    }

    @Test
    fun stopPanel_runNoLongerInTimetable_saysSo() {
        composeRule.setContent {
            StopDeparturesPanel(
                runState(emptyList()),
                onBack = {},
                onDepartureClick = {},
                onScheduledClick = {}
            )
        }

        composeRule.onNodeWithText("This run is no longer in the timetable.").assertIsDisplayed()
    }

    @Test
    fun stopPanel_runBackClicked_callsOnBack() {
        var backs = 0
        composeRule.setContent {
            StopDeparturesPanel(
                runState(RUN_STOPS),
                onBack = { backs++ },
                onDepartureClick = {},
                onScheduledClick = {}
            )
        }

        composeRule.onNodeWithContentDescription("Back to this stop's buses").performClick()

        assertEquals(1, backs)
    }

    @Test
    fun stopPanel_liveDeparture_canBeOpened() {
        composeRule.setContent {
            StopDeparturesPanel(LIVE_STATE, onBack = {
            }, onDepartureClick = {}, onScheduledClick = {})
        }

        composeRule.onNodeWithText("To McKeesport").assertHasClickAction()
    }

    @Test
    fun stopPanel_scheduledForMissingKey_saysWhyThereAreNoLiveTimes() {
        composeRule.setContent {
            StopDeparturesPanel(
                scheduledState(TrueTimeError.MissingApiKey),
                onBack = {},
                onDepartureClick = {},
                onScheduledClick = {}
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
                onDepartureClick = {},
                onScheduledClick = {}
            )
        }

        composeRule.onNodeWithText(
            "No live times (no internet connection). Times are from the timetable."
        ).assertIsDisplayed()
    }

    @Test
    fun stopPanel_scheduledWithoutPredictions_saysTrueTimeHasNone() {
        composeRule.setContent {
            StopDeparturesPanel(scheduledState(null), onBack = {
            }, onDepartureClick = {}, onScheduledClick = {})
        }

        composeRule.onNodeWithText(
            "No live predictions for this stop right now. Times are from the timetable."
        ).assertIsDisplayed()
    }

    @Test
    fun stopPanel_loading_saysSo() {
        composeRule.setContent {
            StopDeparturesPanel(StopDeparturesUiState(STOP), onBack = {
            }, onDepartureClick = {}, onScheduledClick = {})
        }

        composeRule.onNodeWithText("Loading buses at this stop…").assertIsDisplayed()
    }

    @Test
    fun stopPanel_nothingLeftTodayOrTomorrow_saysSo() {
        composeRule.setContent {
            StopDeparturesPanel(
                StopDeparturesUiState(STOP, emptyList(), StopTimesSource.Scheduled(null)),
                onBack = {},
                onDepartureClick = {},
                onScheduledClick = {}
            )
        }

        composeRule.onNodeWithText(
            "No buses from this stop in the timetable for the rest of today or tomorrow."
        ).assertIsDisplayed()
    }

    // Sunday afternoon; Monday's first bus is 19 hours off.
    @Test
    fun stopPanel_scheduledHoursAwayOnAnotherDay_showsWeekdayAndClockTime() {
        composeRule.setContent {
            StopDeparturesPanel(
                StopDeparturesUiState(
                    STOP,
                    listOf(
                        StopDeparture(
                            "61C",
                            "INBOUND-DOWNTOWN",
                            1150,
                            false,
                            null,
                            ScheduledRun("T1", LocalDate.of(2026, 10, 5), 2),
                            Instant.parse("2026-10-05T11:10:00Z")
                        )
                    ),
                    StopTimesSource.Scheduled(null),
                    lastUpdated = Instant.parse("2026-10-04T16:00:00Z")
                ),
                onBack = {},
                onDepartureClick = {},
                onScheduledClick = {},
                zone = ZoneOffset.UTC
            )
        }

        composeRule.onNodeWithText("Mon 11:10", substring = true).assertIsDisplayed()
    }

    @Test
    fun stopPanel_scheduledHoursAwaySameDay_showsClockTimeWithoutWeekday() {
        composeRule.setContent {
            StopDeparturesPanel(
                StopDeparturesUiState(
                    STOP,
                    listOf(
                        StopDeparture(
                            "61C",
                            "INBOUND-DOWNTOWN",
                            250,
                            false,
                            null,
                            ScheduledRun("T2", LocalDate.of(2026, 10, 1), 2),
                            Instant.parse("2026-10-01T12:10:00Z")
                        )
                    ),
                    StopTimesSource.Scheduled(null),
                    lastUpdated = Instant.parse("2026-10-01T08:00:00Z")
                ),
                onBack = {},
                onDepartureClick = {},
                onScheduledClick = {},
                zone = ZoneOffset.UTC
            )
        }

        composeRule.onNodeWithText("12:10", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("Thu", substring = true).assertDoesNotExist()
    }

    @Test
    fun stopPanel_scheduledUnderAnHour_showsMinutes() {
        composeRule.setContent {
            StopDeparturesPanel(
                StopDeparturesUiState(
                    STOP,
                    listOf(
                        StopDeparture(
                            "61C",
                            "INBOUND-DOWNTOWN",
                            10,
                            false,
                            null,
                            ScheduledRun("T2", LocalDate.of(2026, 10, 1), 2),
                            Instant.parse("2026-10-01T12:10:00Z")
                        )
                    ),
                    StopTimesSource.Scheduled(null),
                    lastUpdated = Instant.parse("2026-10-01T12:00:00Z")
                ),
                onBack = {},
                onDepartureClick = {},
                onScheduledClick = {},
                zone = ZoneOffset.UTC
            )
        }

        composeRule.onNodeWithText("10 min").assertIsDisplayed()
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

        val SCHEDULED_ROW = StopDeparture(
            "61C",
            "INBOUND-DOWNTOWN",
            10,
            false,
            null,
            ScheduledRun("T2", LocalDate.of(2026, 10, 1), 1)
        )

        val RUN_STOPS = listOf(
            ScheduledStopTime("FORBES AVE + MOREWOOD AVE", Instant.parse("2026-10-01T12:00:00Z")),
            ScheduledStopTime("STEEL PLAZA STATION", Instant.parse("2026-10-01T12:30:00Z"))
        )

        fun scheduledState(liveError: TrueTimeError?) = StopDeparturesUiState(
            STOP,
            listOf(SCHEDULED_ROW),
            StopTimesSource.Scheduled(liveError)
        )

        fun runState(stops: List<ScheduledStopTime>?) = scheduledState(null)
            .copy(scheduledTrip = ScheduledTripUiState(SCHEDULED_ROW, stops))
    }
}
