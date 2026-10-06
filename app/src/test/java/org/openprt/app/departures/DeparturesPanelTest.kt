package org.openprt.app.departures

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.IOException
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.openprt.app.data.truetime.TrueTimeError
import org.openprt.app.ui.theme.OpenPrtTheme
import org.openprt.app.ui.theme.ThemeMode

@RunWith(AndroidJUnit4::class)
class DeparturesPanelTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun departuresPanel_twoDepartures_showsRouteNumbers() {
        composeRule.setContent {
            DeparturesPanel(
                DeparturesUiState(TWO_DEPARTURES, DeparturesStatus.Ready, UPDATED),
                onDepartureClick = {}
            )
        }

        composeRule.onNodeWithText("61C").assertIsDisplayed()
        composeRule.onNodeWithText("P1").assertIsDisplayed()
    }

    @Test
    fun departuresPanel_twoDepartures_showsMinutesUntilEachBus() {
        composeRule.setContent {
            DeparturesPanel(
                DeparturesUiState(TWO_DEPARTURES, DeparturesStatus.Ready, UPDATED),
                onDepartureClick = {}
            )
        }

        composeRule.onNodeWithText("5 min").assertIsDisplayed()
        composeRule.onNodeWithText("12 min").assertIsDisplayed()
    }

    @Test
    fun departuresPanel_departure_showsDirectionLabel() {
        composeRule.setContent {
            DeparturesPanel(
                DeparturesUiState(TWO_DEPARTURES, DeparturesStatus.Ready, UPDATED),
                onDepartureClick = {}
            )
        }

        composeRule.onNodeWithText("Outbound").assertIsDisplayed()
    }

    @Test
    fun departuresPanel_departure_showsStopAndWalkTime() {
        composeRule.setContent {
            DeparturesPanel(
                DeparturesUiState(listOf(TWO_DEPARTURES[0]), DeparturesStatus.Ready, UPDATED),
                onDepartureClick = {}
            )
        }

        composeRule.onNodeWithText("Forbes Ave at Morewood Ave").assertIsDisplayed()
        composeRule.onNodeWithText("2 min walk").assertIsDisplayed()
    }

    @Test
    fun departuresPanel_allCapsNames_showsThemTitleCased() {
        val shouting = TWO_DEPARTURES[0].copy(
            destination = "BRADDOCK HILLS SHOPPING CENTER",
            stopName = "FORBES AVE + MOREWOOD (CARNEGIE MELLON)"
        )
        composeRule.setContent {
            DeparturesPanel(
                DeparturesUiState(listOf(shouting), DeparturesStatus.Ready, UPDATED),
                onDepartureClick = {}
            )
        }

        composeRule.onNodeWithText("To Braddock Hills Shopping Center").assertIsDisplayed()
        composeRule.onNodeWithText("Forbes Ave + Morewood (Carnegie Mellon)").assertIsDisplayed()
    }

    @Test
    fun departuresPanel_bothDirectionsOfARoute_shareOneRouteBadge() {
        composeRule.setContent {
            DeparturesPanel(
                DeparturesUiState(P1_BOTH_WAYS, DeparturesStatus.Ready, UPDATED),
                onDepartureClick = {}
            )
        }

        composeRule.onAllNodesWithText("P1").assertCountEquals(1)
        composeRule.onNodeWithText("Inbound").assertIsDisplayed()
        composeRule.onNodeWithText("Outbound").assertIsDisplayed()
    }

    @Test
    fun departuresPanel_otherDirectionRowClicked_reportsThatDirection() {
        val clicked = mutableListOf<DepartureItem>()
        composeRule.setContent {
            DeparturesPanel(
                DeparturesUiState(P1_BOTH_WAYS, DeparturesStatus.Ready, UPDATED),
                onDepartureClick = { clicked.add(it) }
            )
        }

        composeRule.onNodeWithText("To Squirrel Hill").performClick()

        assertEquals(listOf(P1_BOTH_WAYS[1]), clicked)
    }

    @Test
    fun departuresPanel_onTimeDeparture_isMarkedLive() {
        composeRule.setContent {
            DeparturesPanel(
                DeparturesUiState(listOf(TWO_DEPARTURES[0]), DeparturesStatus.Ready, UPDATED),
                onDepartureClick = {}
            )
        }

        composeRule.onNodeWithText("Live").assertIsDisplayed()
    }

    @Test
    fun departuresPanel_darkTheme_showsRouteNumbers() {
        composeRule.setContent {
            OpenPrtTheme(ThemeMode.DARK) {
                DeparturesPanel(
                    DeparturesUiState(TWO_DEPARTURES, DeparturesStatus.Ready, UPDATED),
                    onDepartureClick = {}
                )
            }
        }

        composeRule.onNodeWithText("61C").assertIsDisplayed()
    }

    @Test
    fun departuresPanel_busUnderAMinuteAway_saysNow() {
        composeRule.setContent {
            DeparturesPanel(
                DeparturesUiState(
                    listOf(TWO_DEPARTURES[0].copy(minutesUntilDeparture = 0)),
                    DeparturesStatus.Ready,
                    UPDATED
                ),
                onDepartureClick = {}
            )
        }

        composeRule.onNodeWithText("Now").assertIsDisplayed()
    }

    @Test
    fun departuresPanel_delayedDeparture_showsDelayedLabel() {
        composeRule.setContent {
            DeparturesPanel(
                DeparturesUiState(TWO_DEPARTURES, DeparturesStatus.Ready, UPDATED),
                onDepartureClick = {}
            )
        }

        composeRule.onNodeWithText("Delayed").assertIsDisplayed()
    }

    @Test
    fun departuresPanel_rowClicked_reportsThatDeparture() {
        val clicked = mutableListOf<DepartureItem>()
        composeRule.setContent {
            DeparturesPanel(
                DeparturesUiState(TWO_DEPARTURES, DeparturesStatus.Ready, UPDATED),
                onDepartureClick = { clicked.add(it) }
            )
        }

        composeRule.onNodeWithText("To Downtown").performClick()

        assertEquals(listOf(TWO_DEPARTURES[1]), clicked)
    }

    @Test
    fun departuresPanel_readyWithNoDepartures_showsEmptyState() {
        composeRule.setContent {
            DeparturesPanel(
                DeparturesUiState(emptyList(), DeparturesStatus.Ready, UPDATED),
                onDepartureClick = {}
            )
        }

        composeRule
            .onNodeWithText("No buses you can catch", substring = true)
            .assertIsDisplayed()
    }

    @Test
    fun departuresPanel_loading_showsLoadingText() {
        composeRule.setContent { DeparturesPanel(DeparturesUiState(), onDepartureClick = {}) }

        composeRule.onNodeWithText("Loading departures…").assertIsDisplayed()
    }

    @Test
    fun departuresPanel_failedAfterSuccess_keepsDepartures() {
        composeRule.setContent {
            DeparturesPanel(
                DeparturesUiState(TWO_DEPARTURES, NETWORK_FAILURE, UPDATED),
                onDepartureClick = {},
                zone = PITTSBURGH
            )
        }

        composeRule.onNodeWithText("61C").assertIsDisplayed()
    }

    @Test
    fun departuresPanel_failedAfterSuccess_showsErrorWithLastUpdateTime() {
        composeRule.setContent {
            DeparturesPanel(
                DeparturesUiState(
                    TWO_DEPARTURES,
                    DeparturesStatus.Failed(TrueTimeError.Timeout),
                    UPDATED
                ),
                onDepartureClick = {},
                zone = PITTSBURGH
            )
        }

        // 12:40 UTC is 8:40 in Pittsburgh; the 12/24-hour style follows the device locale.
        composeRule
            .onNodeWithText("Couldn't update departures. Showing departures from", substring = true)
            .assertIsDisplayed()
        composeRule.onNodeWithText("8:40", substring = true).assertIsDisplayed()
    }

    @Test
    fun departuresPanel_offlineAfterSuccess_saysOfflineAndKeepsDepartures() {
        composeRule.setContent {
            DeparturesPanel(
                DeparturesUiState(TWO_DEPARTURES, NETWORK_FAILURE, UPDATED),
                onDepartureClick = {},
                zone = PITTSBURGH
            )
        }

        composeRule
            .onNodeWithText("You're offline. Showing departures from 8:40", substring = true)
            .assertIsDisplayed()
        composeRule.onNodeWithText("61C").assertIsDisplayed()
    }

    @Test
    fun departuresPanel_keyRejected_saysToChangeTheKey() {
        composeRule.setContent {
            DeparturesPanel(
                DeparturesUiState(
                    emptyList(),
                    DeparturesStatus.Failed(
                        TrueTimeError.Api(listOf("Invalid API access key supplied"))
                    ),
                    lastUpdated = null
                ),
                onDepartureClick = {}
            )
        }

        composeRule
            .onNodeWithText(
                "TrueTime didn't accept your API key. Tap the key icon at the top to change it."
            )
            .assertIsDisplayed()
    }

    @Test
    fun departuresPanel_dailyLimitExceeded_saysLiveTimesReturnTomorrow() {
        composeRule.setContent {
            DeparturesPanel(
                DeparturesUiState(
                    emptyList(),
                    DeparturesStatus.Failed(
                        TrueTimeError.Api(
                            listOf("Transaction limit for current day has been exceeded.")
                        )
                    ),
                    lastUpdated = null
                ),
                onDepartureClick = {}
            )
        }

        composeRule
            .onNodeWithText(
                "Your TrueTime API key has used up today's requests. " +
                    "Live departures come back tomorrow."
            )
            .assertIsDisplayed()
    }

    @Test
    fun departuresPanel_missingApiKey_explainsKeyIsNeeded() {
        composeRule.setContent {
            DeparturesPanel(
                DeparturesUiState(
                    emptyList(),
                    DeparturesStatus.Failed(TrueTimeError.MissingApiKey),
                    lastUpdated = null
                ),
                onDepartureClick = {}
            )
        }

        composeRule
            .onNodeWithText("Live departures need a TrueTime API key", substring = true)
            .assertIsDisplayed()
    }

    private companion object {
        val UPDATED: Instant = Instant.parse("2026-10-01T12:40:00Z")
        val PITTSBURGH: ZoneId = ZoneId.of("America/New_York")
        val NETWORK_FAILURE = DeparturesStatus.Failed(TrueTimeError.Network(IOException()))

        val TWO_DEPARTURES = listOf(
            DepartureItem(
                "61C",
                "OUTBOUND",
                "McKeesport",
                "Forbes Ave at Morewood Ave",
                2,
                5,
                false,
                "7117",
                "5601"
            ),
            DepartureItem(
                "P1",
                "INBOUND",
                "Downtown",
                "Forbes Ave at Morewood Ave",
                2,
                12,
                true,
                "7117",
                "3210"
            )
        )

        val P1_BOTH_WAYS = listOf(
            TWO_DEPARTURES[1],
            TWO_DEPARTURES[1].copy(
                direction = "OUTBOUND",
                destination = "Squirrel Hill",
                minutesUntilDeparture = 15,
                delayed = false,
                vehicleId = "3344"
            )
        )
    }
}
