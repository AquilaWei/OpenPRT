package org.openprt.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import java.time.Clock
import org.openprt.app.data.truetime.TrueTimeClient
import org.openprt.app.data.truetime.fromBuildConfig
import org.openprt.app.departures.NearbyDeparturesViewModel
import org.openprt.app.destination.DestinationViewModel
import org.openprt.app.destination.PhotonGeocoder
import org.openprt.app.details.DepartureDetailsViewModel
import org.openprt.app.details.asTripSource
import org.openprt.app.location.FusedLocationProvider
import org.openprt.app.location.LOCATION_PERMISSIONS
import org.openprt.app.location.LocationUiState
import org.openprt.app.location.LocationViewModel
import org.openprt.app.location.hasLocationPermission
import org.openprt.app.map.MapViewModel
import org.openprt.app.map.StopsStatus

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Lazy: after rotation the ViewModels already exist and need no new client.
        val trueTime by lazy { TrueTimeClient.fromBuildConfig() }
        val viewModelFactory = viewModelFactory {
            initializer { LocationViewModel(FusedLocationProvider(applicationContext)) }
            initializer {
                MapViewModel((application as OpenPrtApplication).nearbyStopRepository)
            }
            initializer { NearbyDeparturesViewModel(trueTime::getPredictions, Clock.systemUTC()) }
            initializer { DepartureDetailsViewModel(trueTime.asTripSource(), Clock.systemUTC()) }
            initializer {
                DestinationViewModel(
                    PhotonGeocoder(userAgent = "OpenPRT/${BuildConfig.VERSION_NAME}")
                )
            }
        }
        setContent {
            val locationViewModel: LocationViewModel = viewModel(factory = viewModelFactory)
            val mapViewModel: MapViewModel = viewModel(factory = viewModelFactory)
            val locationState by locationViewModel.state.collectAsStateWithLifecycle()
            val departuresViewModel: NearbyDeparturesViewModel =
                viewModel(factory = viewModelFactory)
            val mapState by mapViewModel.state.collectAsStateWithLifecycle()
            val departuresState by departuresViewModel.state.collectAsStateWithLifecycle()
            val detailsViewModel: DepartureDetailsViewModel = viewModel(factory = viewModelFactory)
            val detailsState by detailsViewModel.state.collectAsStateWithLifecycle()
            val destinationViewModel: DestinationViewModel = viewModel(factory = viewModelFactory)
            val destinationState by destinationViewModel.state.collectAsStateWithLifecycle()
            val permissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestMultiplePermissions()
            ) { grants -> locationViewModel.onPermissionResult(grants.values.any { it }) }

            // Runs once per ViewModel, or again after relocate(); after rotation the state is no
            // longer AwaitingPermission.
            LaunchedEffect(locationState) {
                if (locationState == LocationUiState.AwaitingPermission) {
                    if (hasLocationPermission(applicationContext)) {
                        locationViewModel.onPermissionResult(granted = true)
                    } else {
                        permissionLauncher.launch(LOCATION_PERMISSIONS)
                    }
                }
            }

            // Location updates only while the app is visible, to save battery.
            val lifecycleOwner = LocalLifecycleOwner.current
            LaunchedEffect(lifecycleOwner) {
                lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                    locationViewModel.followLocation()
                }
            }

            LaunchedEffect(locationState.location) {
                locationState.location?.let(mapViewModel::onLocationChanged)
            }

            // Departures refresh every 30 seconds, also only while the app is visible.
            LaunchedEffect(lifecycleOwner) {
                lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                    departuresViewModel.autoRefresh()
                }
            }

            // The open departure's bus is tracked every 15 seconds, also only while visible.
            LaunchedEffect(lifecycleOwner) {
                lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                    detailsViewModel.autoRefresh()
                }
            }

            // Before the first lookup succeeds the list is empty for want of stops, not of
            // buses, so only finished lookups are passed on.
            LaunchedEffect(mapState.walkableStops, mapState.stopsStatus) {
                if (mapState.stopsStatus == StopsStatus.Ready) {
                    departuresViewModel.onStopsChanged(mapState.walkableStops)
                }
            }

            MaterialTheme {
                HomeScreen(
                    locationState = locationState,
                    mapState = mapState,
                    departuresState = departuresState,
                    detailsState = detailsState,
                    destinationState = destinationState,
                    destinationActions = destinationViewModel,
                    onRelocate = locationViewModel::relocate,
                    onDepartureClick = detailsViewModel::open,
                    onCloseDetails = detailsViewModel::close
                )
            }
        }
    }
}
