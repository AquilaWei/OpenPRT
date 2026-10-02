package org.openprt.app

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import org.openprt.app.departures.DepartureItem
import org.openprt.app.departures.DeparturesStatus
import org.openprt.app.departures.DeparturesUiState
import org.openprt.app.destination.DestinationActions
import org.openprt.app.destination.DestinationUiState
import org.openprt.app.destination.Place
import org.openprt.app.details.DepartureDetailsUiState
import org.openprt.app.details.RouteStatus
import org.openprt.app.geo.LatLng
import org.openprt.app.location.LocationError
import org.openprt.app.location.LocationUiState
import org.openprt.app.map.MapUiState
import org.openprt.app.map.StopsStatus
import org.openprt.app.trip.TripPlanUiState
import org.openprt.app.ui.theme.OpenPrtTheme
import org.openprt.app.ui.theme.ThemeMode

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
                detailsState = null,
                destinationState = DestinationUiState(),
                destinationActions = NoDestinationActions,
                tripPlanState = null,
                onRetryPlan = {},
                onRelocate = {},
                onDepartureClick = {},
                onCloseDetails = {},
                onOpenApiKey = {},
                themeMode = ThemeMode.SYSTEM,
                onThemeModeChange = {},
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
                detailsState = null,
                destinationState = DestinationUiState(),
                destinationActions = NoDestinationActions,
                tripPlanState = null,
                onRetryPlan = {},
                onRelocate = {},
                onDepartureClick = {},
                onCloseDetails = {},
                onOpenApiKey = {},
                themeMode = ThemeMode.SYSTEM,
                onThemeModeChange = {},
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
                detailsState = null,
                destinationState = DestinationUiState(),
                destinationActions = NoDestinationActions,
                tripPlanState = null,
                onRetryPlan = {},
                onRelocate = {},
                onDepartureClick = {},
                onCloseDetails = {},
                onOpenApiKey = {},
                themeMode = ThemeMode.SYSTEM,
                onThemeModeChange = {},
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
                detailsState = null,
                destinationState = DestinationUiState(),
                destinationActions = NoDestinationActions,
                tripPlanState = null,
                onRetryPlan = {},
                onRelocate = { relocations++ },
                onDepartureClick = {},
                onCloseDetails = {},
                onOpenApiKey = {},
                themeMode = ThemeMode.SYSTEM,
                onThemeModeChange = {},
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
                detailsState = null,
                destinationState = DestinationUiState(),
                destinationActions = NoDestinationActions,
                tripPlanState = null,
                onRetryPlan = {},
                onRelocate = {},
                onDepartureClick = {},
                onCloseDetails = {},
                onOpenApiKey = {},
                themeMode = ThemeMode.SYSTEM,
                onThemeModeChange = {},
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
                detailsState = null,
                destinationState = DestinationUiState(),
                destinationActions = NoDestinationActions,
                tripPlanState = null,
                onRetryPlan = {},
                onRelocate = {},
                onDepartureClick = {},
                onCloseDetails = {},
                onOpenApiKey = {},
                themeMode = ThemeMode.SYSTEM,
                onThemeModeChange = {},
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
                detailsState = null,
                destinationState = DestinationUiState(),
                destinationActions = NoDestinationActions,
                tripPlanState = null,
                onRetryPlan = {},
                onRelocate = {},
                onDepartureClick = {},
                onCloseDetails = {},
                onOpenApiKey = {},
                themeMode = ThemeMode.SYSTEM,
                onThemeModeChange = {},
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
                detailsState = null,
                destinationState = DestinationUiState(),
                destinationActions = NoDestinationActions,
                tripPlanState = null,
                onRetryPlan = {},
                onRelocate = {},
                onDepartureClick = {},
                onCloseDetails = {},
                onOpenApiKey = {},
                themeMode = ThemeMode.SYSTEM,
                onThemeModeChange = {},
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
                detailsState = null,
                destinationState = DestinationUiState(),
                destinationActions = NoDestinationActions,
                tripPlanState = null,
                onRetryPlan = {},
                onRelocate = {},
                onDepartureClick = {},
                onCloseDetails = {},
                onOpenApiKey = {},
                themeMode = ThemeMode.SYSTEM,
                onThemeModeChange = {},
                mapContent = stubMap
            )
        }

        composeRule.onNodeWithText("Nearby departures").assertIsDisplayed()
    }

    @Test
    fun homeScreen_themeMenuDarkPicked_reportsDark() {
        val picked = mutableListOf<ThemeMode>()
        composeRule.setContent {
            HomeScreen(
                LocationUiState.Located(LatLng(40.4443, -79.9532)),
                MapUiState(stopsStatus = StopsStatus.Ready),
                DeparturesUiState(),
                detailsState = null,
                destinationState = DestinationUiState(),
                destinationActions = NoDestinationActions,
                tripPlanState = null,
                onRetryPlan = {},
                onRelocate = {},
                onDepartureClick = {},
                onCloseDetails = {},
                onOpenApiKey = {},
                themeMode = ThemeMode.SYSTEM,
                onThemeModeChange = { picked += it },
                mapContent = stubMap
            )
        }

        composeRule.onNodeWithContentDescription("Theme").performClick()
        composeRule.onNodeWithText("Dark").performClick()

        assertEquals(listOf(ThemeMode.DARK), picked)
    }

    @Test
    fun homeScreen_darkTheme_showsDeparturesSheet() {
        composeRule.setContent {
            OpenPrtTheme(ThemeMode.DARK) {
                HomeScreen(
                    LocationUiState.Located(LatLng(40.4443, -79.9532)),
                    MapUiState(stopsStatus = StopsStatus.Ready),
                    DeparturesUiState(),
                    detailsState = null,
                    destinationState = DestinationUiState(),
                    destinationActions = NoDestinationActions,
                    tripPlanState = null,
                    onRetryPlan = {},
                    onRelocate = {},
                    onDepartureClick = {},
                    onCloseDetails = {},
                    onOpenApiKey = {},
                    themeMode = ThemeMode.DARK,
                    onThemeModeChange = {},
                    mapContent = stubMap
                )
            }
        }

        composeRule.onNodeWithText("Nearby departures").assertIsDisplayed()
    }

    @Test
    fun homeScreen_keyButtonClicked_opensApiKeySettings() {
        var opened = 0
        composeRule.setContent {
            HomeScreen(
                LocationUiState.Located(LatLng(40.4443, -79.9532)),
                MapUiState(stopsStatus = StopsStatus.Ready),
                DeparturesUiState(),
                detailsState = null,
                destinationState = DestinationUiState(),
                destinationActions = NoDestinationActions,
                tripPlanState = null,
                onRetryPlan = {},
                onRelocate = {},
                onDepartureClick = {},
                onCloseDetails = {},
                onOpenApiKey = { opened++ },
                themeMode = ThemeMode.SYSTEM,
                onThemeModeChange = {},
                mapContent = stubMap
            )
        }

        composeRule.onNodeWithContentDescription("TrueTime API key").performClick()

        assertEquals(1, opened)
    }

    @Test
    fun homeScreen_tripPlanSet_showsPlansInsteadOfDepartures() {
        composeRule.setContent {
            HomeScreen(
                LocationUiState.Located(LatLng(40.4443, -79.9532)),
                MapUiState(stopsStatus = StopsStatus.Ready),
                DeparturesUiState(),
                detailsState = null,
                destinationState = DestinationUiState(),
                destinationActions = NoDestinationActions,
                tripPlanState = TripPlanUiState.Planning,
                onRetryPlan = {},
                onRelocate = {},
                onDepartureClick = {},
                onCloseDetails = {},
                onOpenApiKey = {},
                themeMode = ThemeMode.SYSTEM,
                onThemeModeChange = {},
                mapContent = stubMap
            )
        }

        composeRule.onNodeWithText("Planning your trip…").assertIsDisplayed()
        composeRule.onNodeWithText("Nearby departures").assertDoesNotExist()
    }

    @Test
    fun homeScreen_departureClicked_showsItsDetails() {
        composeRule.setContent { NavigableHomeScreen(onRelocate = {}) }

        composeRule.onNodeWithText("To McKeesport").performClick()

        composeRule.onNodeWithText("Locating the bus…").assertIsDisplayed()
    }

    @Test
    fun homeScreen_backFromDetails_showsNearbyListAgain() {
        composeRule.setContent { NavigableHomeScreen(onRelocate = {}) }
        composeRule.onNodeWithText("To McKeesport").performClick()

        composeRule.onNodeWithContentDescription("Back to nearby departures").performClick()

        composeRule.onNodeWithText("Nearby departures").assertIsDisplayed()
    }

    @Test
    fun homeScreen_openAndCloseDetails_doesNotRequestLocationAgain() {
        // Relocating is the screen's only way to ask for a new fix (see MainActivity).
        var relocations = 0
        composeRule.setContent { NavigableHomeScreen(onRelocate = { relocations++ }) }
        composeRule.onNodeWithText("To McKeesport").performClick()

        composeRule.onNodeWithContentDescription("Back to nearby departures").performClick()

        assertEquals(0, relocations)
    }

    /** HomeScreen with details state held the way MainActivity's ViewModel holds it. */
    @Composable
    private fun NavigableHomeScreen(onRelocate: () -> Unit) {
        var details by remember { mutableStateOf<DepartureDetailsUiState?>(null) }
        HomeScreen(
            LocationUiState.Located(LatLng(40.4443, -79.9532)),
            MapUiState(stopsStatus = StopsStatus.Ready),
            DeparturesUiState(listOf(DEPARTURE), DeparturesStatus.Ready),
            detailsState = details,
            destinationState = DestinationUiState(),
            destinationActions = NoDestinationActions,
            tripPlanState = null,
            onRetryPlan = {},
            onRelocate = onRelocate,
            onDepartureClick = { details = DepartureDetailsUiState(it, RouteStatus.Loading) },
            onCloseDetails = { details = null },
            onOpenApiKey = {},
            themeMode = ThemeMode.SYSTEM,
            onThemeModeChange = {},
            mapContent = stubMap
        )
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
    }

    private object NoDestinationActions : DestinationActions {
        override fun onQueryChanged(query: String) = Unit

        override fun retry() = Unit

        override fun selectPlace(place: Place) = Unit

        override fun onMapLongPress(location: LatLng) = Unit

        override fun clearDestination() = Unit
    }
}
