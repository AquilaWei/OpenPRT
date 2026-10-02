package org.openprt.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.openprt.app.departures.DepartureItem
import org.openprt.app.departures.DeparturesPanel
import org.openprt.app.departures.DeparturesUiState
import org.openprt.app.departures.oppositeDirectionOf
import org.openprt.app.destination.DestinationActions
import org.openprt.app.destination.DestinationSearch
import org.openprt.app.destination.DestinationUiState
import org.openprt.app.destination.Place
import org.openprt.app.details.DepartureDetailsPanel
import org.openprt.app.details.DepartureDetailsUiState
import org.openprt.app.details.RouteStatus
import org.openprt.app.geo.LatLng
import org.openprt.app.location.LocationError
import org.openprt.app.location.LocationUiState
import org.openprt.app.map.MapUiState
import org.openprt.app.map.StopMap
import org.openprt.app.map.StopsStatus
import org.openprt.app.map.mapPalette
import org.openprt.app.planner.RideLeg
import org.openprt.app.trip.TripOption
import org.openprt.app.trip.TripPlanActions
import org.openprt.app.trip.TripPlanUiState
import org.openprt.app.trip.TripPlansPanel
import org.openprt.app.ui.theme.LocalOpenPrtColors
import org.openprt.app.ui.theme.OpenPrtTheme
import org.openprt.app.ui.theme.ThemeMode

/** Test tag of the box that holds the map, whichever map implementation fills it. */
const val MAP_CONTAINER_TAG = "map"

/** How much of the departures sheet shows while collapsed: the title and about two rows. */
private val SHEET_PEEK_HEIGHT = 240.dp

/**
 * Taller while a departure's details or a chosen trip are shown, so the collapsed sheet shows
 * more than the header card: the arrival time, or the first leg.
 */
private val DETAILS_SHEET_PEEK_HEIGHT = 300.dp

/**
 * Home screen: a map of the stops around the user, centered on the current location (or the
 * downtown fallback), with status messages on top, a button to re-center, and the nearby
 * departures in a bottom sheet that can be dragged up.
 *
 * While [detailsState] is set, the sheet shows that departure instead and the map shows its
 * route in place of the nearby stops; the back button and system back call [onCloseDetails].
 *
 * The destination search floats at the top of the map; long-pressing the map also picks a
 * destination. Both go to [destinationActions]. While [tripPlanState] is set (a destination is
 * chosen), the sheet lists the ways there instead of the nearby departures. Tapping one shows it
 * leg by leg and on the map; those requests go to [tripPlanActions], and system back returns to
 * the list.
 *
 * The key button in the top bar calls [onOpenApiKey] to change the TrueTime key; the theme
 * button offers System / Light / Dark, marks [themeMode] and reports a pick to [onThemeModeChange].
 *
 * [mapContent] draws the map inside the container; tests replace it because the real MapLibre
 * map needs native code that Robolectric cannot load.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    locationState: LocationUiState,
    mapState: MapUiState,
    departuresState: DeparturesUiState,
    detailsState: DepartureDetailsUiState?,
    destinationState: DestinationUiState,
    destinationActions: DestinationActions,
    tripPlanState: TripPlanUiState?,
    tripPlanActions: TripPlanActions,
    onRelocate: () -> Unit,
    onDepartureClick: (DepartureItem) -> Unit,
    onCloseDetails: () -> Unit,
    onOpenApiKey: () -> Unit,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    modifier: Modifier = Modifier,
    mapContent: @Composable (Modifier, PaddingValues) -> Unit = { mapModifier, overlayPadding ->
        val trip = (tripPlanState as? TripPlanUiState.Results)?.selected?.map
        StopMap(
            center = locationState.location,
            userLocation = (locationState as? LocationUiState.Located)?.location,
            // A route's or trip's own stops replace the nearby ones so its stops stand out.
            stops = if (detailsState == null && trip == null) mapState.stopMarkers else emptyList(),
            route = (detailsState?.route as? RouteStatus.Ready)?.shape,
            bus = detailsState?.bus?.position,
            destination = destinationState.destination?.location,
            onLongPress = destinationActions::onMapLongPress,
            palette = mapPalette(dark = LocalOpenPrtColors.current.isDark),
            modifier = mapModifier,
            trip = trip,
            overlayPadding = overlayPadding
        )
    }
) {
    val tripSelected = (tripPlanState as? TripPlanUiState.Results)?.selected != null
    BackHandler(enabled = detailsState != null, onBack = onCloseDetails)
    BackHandler(
        enabled = detailsState == null && tripSelected,
        onBack = tripPlanActions::closeSelection
    )
    val peekHeight = if (detailsState != null || tripSelected) {
        DETAILS_SHEET_PEEK_HEIGHT
    } else {
        SHEET_PEEK_HEIGHT
    }
    // The search box floats over the top of the map; camera fits keep clear of it.
    var searchHeight by remember { mutableStateOf(0.dp) }
    val density = LocalDensity.current
    BottomSheetScaffold(
        modifier = modifier,
        sheetPeekHeight = peekHeight,
        topBar = {
            val brand = LocalOpenPrtColors.current
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = brand.appBar,
                    titleContentColor = brand.onAppBar,
                    actionIconContentColor = brand.onAppBar
                ),
                actions = {
                    ThemeMenu(themeMode, onThemeModeChange)
                    IconButton(onClick = onOpenApiKey) {
                        Icon(
                            painter = painterResource(R.drawable.ic_key),
                            contentDescription = stringResource(R.string.api_key_open)
                        )
                    }
                }
            )
        },
        sheetContent = {
            when {
                detailsState != null -> DepartureDetailsPanel(
                    detailsState,
                    onBack = onCloseDetails,
                    otherDirection = oppositeDirectionOf(
                        detailsState.departure,
                        departuresState.departures
                    ),
                    onSwitchDirection = onDepartureClick
                )

                tripPlanState != null -> TripPlansPanel(tripPlanState, tripPlanActions)

                else -> DeparturesPanel(departuresState, onDepartureClick)
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            Box(modifier = Modifier.fillMaxSize().testTag(MAP_CONTAINER_TAG)) {
                mapContent(
                    Modifier.fillMaxSize(),
                    // The map ends at the sheet's top edge, so only the search box covers it.
                    PaddingValues(top = searchHeight)
                )
            }
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .onSizeChanged { searchHeight = with(density) { it.height.toDp() } }
            ) {
                DestinationSearch(
                    state = destinationState,
                    onQueryChanged = destinationActions::onQueryChanged,
                    onPlaceSelected = destinationActions::selectPlace,
                    onRetry = destinationActions::retry,
                    onClearDestination = destinationActions::clearDestination
                )
                StatusMessages(
                    messages = listOfNotNull(
                        locationStatusText(locationState),
                        stopsStatusText(locationState, mapState.stopsStatus)
                    )
                )
            }
            FloatingActionButton(
                onClick = onRelocate,
                containerColor = LocalOpenPrtColors.current.accent,
                contentColor = LocalOpenPrtColors.current.onAccent,
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_my_location),
                    contentDescription = stringResource(R.string.map_relocate)
                )
            }
        }
    }
}

/** The top bar's theme button and its System / Light / Dark menu. */
@Composable
private fun ThemeMenu(themeMode: ThemeMode, onThemeModeChange: (ThemeMode) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                painter = painterResource(R.drawable.ic_contrast),
                contentDescription = stringResource(R.string.theme_menu)
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            ThemeMode.entries.forEach { mode ->
                DropdownMenuItem(
                    text = { Text(stringResource(mode.labelRes())) },
                    onClick = {
                        expanded = false
                        onThemeModeChange(mode)
                    },
                    trailingIcon = {
                        if (mode == themeMode) {
                            Icon(
                                painter = painterResource(R.drawable.ic_check),
                                contentDescription = stringResource(R.string.theme_selected)
                            )
                        }
                    }
                )
            }
        }
    }
}

private fun ThemeMode.labelRes(): Int = when (this) {
    ThemeMode.SYSTEM -> R.string.theme_system
    ThemeMode.LIGHT -> R.string.theme_light
    ThemeMode.DARK -> R.string.theme_dark
}

@Composable
private fun StatusMessages(messages: List<String>, modifier: Modifier = Modifier) {
    if (messages.isEmpty()) return
    Surface(
        modifier = modifier.fillMaxWidth().padding(12.dp),
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 3.dp,
        shadowElevation = 3.dp
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            messages.forEach { Text(it) }
        }
    }
}

/** Null when the device location is known, since the map itself shows it. */
@Composable
private fun locationStatusText(state: LocationUiState): String? = when (state) {
    LocationUiState.AwaitingPermission, LocationUiState.Loading ->
        stringResource(R.string.location_locating)

    is LocationUiState.Located -> null

    is LocationUiState.PermissionDenied -> stringResource(R.string.location_permission_denied)

    is LocationUiState.Failed -> stringResource(
        R.string.location_failed,
        stringResource(
            when (state.error) {
                LocationError.Timeout -> R.string.location_error_timeout

                LocationError.Unavailable -> R.string.location_error_unavailable

                LocationError.PermissionMissing,
                is LocationError.Failed -> R.string.location_error_failed
            }
        )
    )
}

/** Stop loading is only worth mentioning once there is a position to load stops for. */
@Composable
private fun stopsStatusText(locationState: LocationUiState, status: StopsStatus): String? = when {
    locationState.location == null -> null
    status == StopsStatus.Loading -> stringResource(R.string.stops_loading)
    status is StopsStatus.Failed -> stringResource(R.string.stops_failed)
    else -> null
}

@Preview
@Composable
private fun HomeScreenPreview() {
    OpenPrtTheme {
        HomeScreenPreviewContent()
    }
}

@Preview
@Composable
private fun HomeScreenDarkPreview() {
    OpenPrtTheme(ThemeMode.DARK) {
        HomeScreenPreviewContent()
    }
}

@Composable
private fun HomeScreenPreviewContent() {
    HomeScreen(
        locationState = LocationUiState.PermissionDenied(),
        mapState = MapUiState(stopsStatus = StopsStatus.Ready),
        departuresState = DeparturesUiState(),
        detailsState = null,
        destinationState = DestinationUiState(),
        destinationActions = PreviewDestinationActions,
        tripPlanState = null,
        tripPlanActions = NoTripPlanActions,
        onRelocate = {},
        onDepartureClick = {},
        onCloseDetails = {},
        onOpenApiKey = {},
        themeMode = ThemeMode.SYSTEM,
        onThemeModeChange = {},
        mapContent = { mapModifier, _ ->
            Surface(mapModifier, color = MaterialTheme.colorScheme.surfaceVariant) {}
        }
    )
}

private object PreviewDestinationActions : DestinationActions {
    override fun onQueryChanged(query: String) = Unit

    override fun retry() = Unit

    override fun selectPlace(place: Place) = Unit

    override fun onMapLongPress(location: LatLng) = Unit

    override fun clearDestination() = Unit
}

/** Trip actions that do nothing, for previews and screens without a trip. */
internal object NoTripPlanActions : TripPlanActions {
    override fun retry() = Unit

    override fun select(option: TripOption) = Unit

    override fun closeSelection() = Unit

    override fun openRide(ride: RideLeg) = Unit

    override fun onRideOpened() = Unit
}
