package org.openprt.app.departures

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.IOException
import java.time.Instant
import java.time.ZoneId
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.openprt.app.data.truetime.TrueTimeError

@RunWith(AndroidJUnit4::class)
class DeparturesPanelTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun departuresPanel_twoDepartures_showsRouteNumbers() {
        composeRule.setContent {
            DeparturesPanel(DeparturesUiState(TWO_DEPARTURES, DeparturesStatus.Ready, UPDATED))
        }

        composeRule.onNodeWithText("61C").assertIsDisplayed()
        composeRule.onNodeWithText("P1").assertIsDisplayed()
    }

    @Test
    fun departuresPanel_twoDepartures_showsMinutesUntilEachBus() {
        composeRule.setContent {
            DeparturesPanel(DeparturesUiState(TWO_DEPARTURES, DeparturesStatus.Ready, UPDATED))
        }

        composeRule.onNodeWithText("5 min").assertIsDisplayed()
        composeRule.onNodeWithText("12 min").assertIsDisplayed()
    }

    @Test
    fun departuresPanel_departure_showsDirectionStopAndWalkTime() {
        composeRule.setContent {
            DeparturesPanel(DeparturesUiState(TWO_DEPARTURES, DeparturesStatus.Ready, UPDATED))
        }

        composeRule
            .onNodeWithText("OUTBOUND · Forbes Ave at Morewood Ave · 2 min walk")
            .assertIsDisplayed()
    }

    @Test
    fun departuresPanel_delayedDeparture_showsDelayedLabel() {
        composeRule.setContent {
            DeparturesPanel(DeparturesUiState(TWO_DEPARTURES, DeparturesStatus.Ready, UPDATED))
        }

        composeRule.onNodeWithText("Delayed").assertIsDisplayed()
    }

    @Test
    fun departuresPanel_readyWithNoDepartures_showsEmptyState() {
        composeRule.setContent {
            DeparturesPanel(DeparturesUiState(emptyList(), DeparturesStatus.Ready, UPDATED))
        }

        composeRule
            .onNodeWithText("No buses you can catch", substring = true)
            .assertIsDisplayed()
    }

    @Test
    fun departuresPanel_loading_showsLoadingText() {
        composeRule.setContent { DeparturesPanel(DeparturesUiState()) }

        composeRule.onNodeWithText("Loading departures…").assertIsDisplayed()
    }

    @Test
    fun departuresPanel_failedAfterSuccess_keepsDepartures() {
        composeRule.setContent {
            DeparturesPanel(
                DeparturesUiState(TWO_DEPARTURES, NETWORK_FAILURE, UPDATED),
                zone = PITTSBURGH
            )
        }

        composeRule.onNodeWithText("61C").assertIsDisplayed()
    }

    @Test
    fun departuresPanel_failedAfterSuccess_showsErrorWithLastUpdateTime() {
        composeRule.setContent {
            DeparturesPanel(
                DeparturesUiState(TWO_DEPARTURES, NETWORK_FAILURE, UPDATED),
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
    fun departuresPanel_missingApiKey_explainsKeyIsNeeded() {
        composeRule.setContent {
            DeparturesPanel(
                DeparturesUiState(
                    emptyList(),
                    DeparturesStatus.Failed(TrueTimeError.MissingApiKey),
                    lastUpdated = null
                )
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
                false
            ),
            DepartureItem("P1", "INBOUND", "Downtown", "Forbes Ave at Morewood Ave", 2, 12, true)
        )
    }
}
