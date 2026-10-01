package org.openprt.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.openprt.app.location.LocationError
import org.openprt.app.location.LocationUiState
import org.openprt.app.map.MapUiState
import org.openprt.app.map.StopMap
import org.openprt.app.map.StopsStatus

/** Test tag of the box that holds the map, whichever map implementation fills it. */
const val MAP_CONTAINER_TAG = "map"

/**
 * Home screen: a map of the stops around the user, centered on the current location (or the
 * downtown fallback), with status messages on top and a button to re-center.
 *
 * [mapContent] draws the map inside the container; tests replace it because the real MapLibre
 * map needs native code that Robolectric cannot load.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    locationState: LocationUiState,
    mapState: MapUiState,
    onRelocate: () -> Unit,
    modifier: Modifier = Modifier,
    mapContent: @Composable (Modifier) -> Unit = { mapModifier ->
        StopMap(
            center = locationState.location,
            userLocation = (locationState as? LocationUiState.Located)?.location,
            stops = mapState.stopMarkers,
            modifier = mapModifier
        )
    }
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            CenterAlignedTopAppBar(title = { Text(stringResource(R.string.app_name)) })
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onRelocate) {
                Icon(
                    painter = painterResource(R.drawable.ic_my_location),
                    contentDescription = stringResource(R.string.map_relocate)
                )
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            Box(modifier = Modifier.fillMaxSize().testTag(MAP_CONTAINER_TAG)) {
                mapContent(Modifier.fillMaxSize())
            }
            StatusMessages(
                messages = listOfNotNull(
                    locationStatusText(locationState),
                    stopsStatusText(locationState, mapState.stopsStatus)
                ),
                modifier = Modifier.align(Alignment.TopCenter)
            )
        }
    }
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
    HomeScreen(
        locationState = LocationUiState.PermissionDenied(),
        mapState = MapUiState(stopsStatus = StopsStatus.Ready),
        onRelocate = {},
        mapContent = { Surface(it, color = MaterialTheme.colorScheme.surfaceVariant) {} }
    )
}
