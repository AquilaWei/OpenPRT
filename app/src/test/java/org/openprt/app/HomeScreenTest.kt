package org.openprt.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.ui.test.performScrollTo
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
import org.openprt.app.map.StopMarker
import org.openprt.app.map.StopsStatus
import org.openprt.app.stop.StopDeparture
import org.openprt.app.stop.StopDeparturesUiState
import org.openprt.app.stop.StopTimesSource
import org.openprt.app.trip.TripPlanUiState
import org.openprt.app.ui.theme.OpenPrtTheme
import org.openprt.app.ui.theme.ThemeMode

@RunWith(AndroidJUnit4::class)
class HomeScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    // The real MapLibre map needs native code that Robolectric cannot load.
    private val stubMap: @Composable (
        Modifier,
        PaddingValues
    ) -> Unit = { modifier, _ -> Box(modifier) }

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
                tripPlanActions = NoTripPlanActions,
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
                tripPlanActions = NoTripPlanActions,
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
                tripPlanActions = NoTripPlanActions,
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
                tripPlanActions = NoTripPlanActions,
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
                tripPlanActions = NoTripPlanActions,
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
                tripPlanActions = NoTripPlanActions,
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
                tripPlanActions = NoTripPlanActions,
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
                tripPlanActions = NoTripPlanActions,
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
                tripPlanActions = NoTripPlanActions,
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
                tripPlanActions = NoTripPlanActions,
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
                    tripPlanActions = NoTripPlanActions,
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
                tripPlanActions = NoTripPlanActions,
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
                tripPlanActions = NoTripPlanActions,
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

    @Test
    fun homeScreen_stopSelected_showsStopBusesInsteadOfNearbyDepartures() {
        composeRule.setContent { NavigableStopHomeScreen(onStopBack = {}) }

        composeRule.onNodeWithText("Stop #7117").assertIsDisplayed()
        composeRule.onNodeWithText("Nearby departures").assertDoesNotExist()
    }

    @Test
    fun homeScreen_departureClickedInStopPanel_showsItsDetails() {
        composeRule.setContent { NavigableStopHomeScreen(onStopBack = {}) }

        composeRule.onNodeWithText("To McKeesport").performClick()

        composeRule.onNodeWithText("Locating the bus…").assertIsDisplayed()
    }

    @Test
    fun homeScreen_backFromDetailsOpenedFromStop_showsStopBusesAgain() {
        composeRule.setContent { NavigableStopHomeScreen(onStopBack = {}) }
        composeRule.onNodeWithText("To McKeesport").performClick()

        composeRule.onNodeWithContentDescription("Back to nearby departures").performClick()

        composeRule.onNodeWithText("Stop #7117").assertIsDisplayed()
    }

    @Test
    fun homeScreen_stopPanelBackClicked_callsOnStopBack() {
        var closed = 0
        composeRule.setContent { NavigableStopHomeScreen(onStopBack = { closed++ }) }

        composeRule.onNodeWithContentDescription("Close this stop").performClick()

        assertEquals(1, closed)
    }

    @Test
    fun homeScreen_legendButtonClicked_explainsEveryMapMarker() {
        composeRule.setContent { NavigableHomeScreen(onRelocate = {}) }

        composeRule.onNodeWithContentDescription("Map legend").performClick()

        // The list scrolls on Robolectric's small screen, so each row is scrolled to first.
        composeRule.onNodeWithText("You").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Bus stop near you. Tap one to see its buses.")
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithText("Stop to board at, or the stop you tapped")
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithText("Stop on the bus's route").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Bus route").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Walk").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("The bus you're following").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Destination").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun homeScreen_legendClosed_hidesLegend() {
        composeRule.setContent { NavigableHomeScreen(onRelocate = {}) }
        composeRule.onNodeWithContentDescription("Map legend").performClick()

        composeRule.onNodeWithText("Close").performClick()

        composeRule.onNodeWithText("Bus route").assertDoesNotExist()
    }

    /** HomeScreen with a tapped stop open, and details held like MainActivity holds them. */
    @Composable
    private fun NavigableStopHomeScreen(onStopBack: () -> Unit) {
        var details by remember { mutableStateOf<DepartureDetailsUiState?>(null) }
        HomeScreen(
            LocationUiState.Located(LatLng(40.4443, -79.9532)),
            MapUiState(stopsStatus = StopsStatus.Ready),
            DeparturesUiState(emptyList(), DeparturesStatus.Ready),
            detailsState = details,
            destinationState = DestinationUiState(),
            destinationActions = NoDestinationActions,
            tripPlanState = null,
            tripPlanActions = NoTripPlanActions,
            onRelocate = {},
            onDepartureClick = { details = DepartureDetailsUiState(it, RouteStatus.Loading) },
            onCloseDetails = { details = null },
            onOpenApiKey = {},
            themeMode = ThemeMode.SYSTEM,
            onThemeModeChange = {},
            stopState = StopDeparturesUiState(
                stop = StopMarker("7117", "Forbes Ave at Morewood", LatLng(40.4446, -79.9428)),
                departures = listOf(StopDeparture("61C", "McKeesport", 5, false, DEPARTURE)),
                source = StopTimesSource.Live
            ),
            onStopBack = onStopBack,
            mapContent = stubMap
        )
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
            tripPlanActions = NoTripPlanActions,
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

        override fun editOrigin() = Unit

        override fun cancelOriginEdit() = Unit

        override fun clearOrigin() = Unit

        override fun swapEndpoints() = Unit
    }
}
