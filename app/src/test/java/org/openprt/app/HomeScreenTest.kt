package org.openprt.app

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.openprt.app.data.gtfs.GtfsImportError
import org.openprt.app.departures.DeparturesUiState
import org.openprt.app.geo.LatLng
import org.openprt.app.location.LocationError
import org.openprt.app.location.LocationUiState
import org.openprt.app.map.MapUiState
import org.openprt.app.map.StopsStatus

@RunWith(AndroidJUnit4::class)
class HomeScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    // The real MapLibre map needs native code that Robolectric cannot load.
    private val stubMap: @Composable (Modifier) -> Unit = { Box(it) }

    @Test
    fun homeScreen_whenShown_displaysAppTitle() {
        composeRule.setContent {
            HomeScreen(
                LocationUiState.Loading,
                MapUiState(),
                DeparturesUiState(),
                onRelocate = {},
                mapContent = stubMap
            )
        }

        composeRule.onNodeWithText("OpenPRT").assertIsDisplayed()
    }

    @Test
    fun homeScreen_whenShown_hasMapContainer() {
        composeRule.setContent {
            HomeScreen(
                LocationUiState.Loading,
                MapUiState(),
                DeparturesUiState(),
                onRelocate = {},
                mapContent = stubMap
            )
        }

        composeRule.onNodeWithTag("map").assertIsDisplayed()
    }

    @Test
    fun homeScreen_whenShown_hasRelocateButton() {
        composeRule.setContent {
            HomeScreen(
                LocationUiState.Loading,
                MapUiState(),
                DeparturesUiState(),
                onRelocate = {},
                mapContent = stubMap
            )
        }

        composeRule.onNodeWithContentDescription("Re-center on my location").assertIsDisplayed()
    }

    @Test
    fun homeScreen_relocateClicked_callsOnRelocate() {
        var relocations = 0
        composeRule.setContent {
            HomeScreen(
                LocationUiState.Located(LatLng(40.4443, -79.9532)),
                MapUiState(stopsStatus = StopsStatus.Ready),
                DeparturesUiState(),
                onRelocate = { relocations++ },
                mapContent = stubMap
            )
        }

        composeRule.onNodeWithContentDescription("Re-center on my location").performClick()

        assertEquals(1, relocations)
    }

    @Test
    fun homeScreen_permissionDenied_tellsUserDowntownIsShown() {
        composeRule.setContent {
            HomeScreen(
                LocationUiState.PermissionDenied(),
                MapUiState(stopsStatus = StopsStatus.Ready),
                DeparturesUiState(),
                onRelocate = {},
                mapContent = stubMap
            )
        }

        composeRule
            .onNodeWithText("Location permission denied", substring = true)
            .assertIsDisplayed()
    }

    @Test
    fun homeScreen_timedOut_showsTimeoutMessage() {
        composeRule.setContent {
            HomeScreen(
                LocationUiState.Failed(LocationError.Timeout),
                MapUiState(stopsStatus = StopsStatus.Ready),
                DeparturesUiState(),
                onRelocate = {},
                mapContent = stubMap
            )
        }

        composeRule
            .onNodeWithText("Couldn't get your location (timed out)", substring = true)
            .assertIsDisplayed()
    }

    @Test
    fun homeScreen_locatedAndStopsLoading_showsStopsLoadingMessage() {
        composeRule.setContent {
            HomeScreen(
                LocationUiState.Located(LatLng(40.4443, -79.9532)),
                MapUiState(stopsStatus = StopsStatus.Loading),
                DeparturesUiState(),
                onRelocate = {},
                mapContent = stubMap
            )
        }

        composeRule.onNodeWithText("Loading nearby stops…").assertIsDisplayed()
    }

    @Test
    fun homeScreen_stopsFailed_showsStopsErrorMessage() {
        composeRule.setContent {
            HomeScreen(
                LocationUiState.Located(LatLng(40.4443, -79.9532)),
                MapUiState(
                    stopsStatus = StopsStatus.Failed(GtfsImportError.Network(IOException()))
                ),
                DeparturesUiState(),
                onRelocate = {},
                mapContent = stubMap
            )
        }

        composeRule
            .onNodeWithText("Couldn't load bus stops", substring = true)
            .assertIsDisplayed()
    }

    @Test
    fun homeScreen_whenShown_showsDeparturesSheet() {
        composeRule.setContent {
            HomeScreen(
                LocationUiState.Located(LatLng(40.4443, -79.9532)),
                MapUiState(stopsStatus = StopsStatus.Ready),
                DeparturesUiState(),
                onRelocate = {},
                mapContent = stubMap
            )
        }

        composeRule.onNodeWithText("Nearby departures").assertIsDisplayed()
    }
}
