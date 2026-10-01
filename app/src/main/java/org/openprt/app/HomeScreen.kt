package org.openprt.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import java.util.Locale
import org.openprt.app.location.LocationError
import org.openprt.app.location.LocationUiState

/** Home screen; for now it only reports the location state. The map arrives with F6. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(locationState: LocationUiState, modifier: Modifier = Modifier) {
    Scaffold(
        modifier = modifier,
        topBar = {
            CenterAlignedTopAppBar(title = { Text(stringResource(R.string.app_name)) })
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(innerPadding).padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(locationStatusText(locationState))
        }
    }
}

@Composable
private fun locationStatusText(state: LocationUiState): String = when (state) {
    LocationUiState.AwaitingPermission, LocationUiState.Loading ->
        stringResource(R.string.location_locating)

    is LocationUiState.Located -> stringResource(
        R.string.location_located,
        // Fixed locale so coordinates always use a decimal point.
        String.format(Locale.ROOT, "%.5f, %.5f", state.location.latitude, state.location.longitude)
    )

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

@Preview
@Composable
private fun HomeScreenPreview() {
    HomeScreen(LocationUiState.PermissionDenied())
}
