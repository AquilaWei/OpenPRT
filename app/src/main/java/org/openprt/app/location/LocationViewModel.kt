package org.openprt.app.location

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.openprt.app.geo.LatLng

/** Where the location flow stands; every terminal state carries a usable position. */
sealed interface LocationUiState {
    /** The permission dialog has not been answered yet. */
    data object AwaitingPermission : LocationUiState

    /** Permission granted, waiting for a fix. Always ends in [Located] or [Failed]. */
    data object Loading : LocationUiState

    data class Located(val location: LatLng) : LocationUiState

    /** The user refused location access; [location] is the downtown fallback. */
    data class PermissionDenied(val location: LatLng = DOWNTOWN_PITTSBURGH) : LocationUiState

    /** Permission granted but no fix; [location] is the downtown fallback. */
    data class Failed(val error: LocationError, val location: LatLng = DOWNTOWN_PITTSBURGH) :
        LocationUiState
}

/**
 * Drives the permission → fix flow. The screen asks for the permission and reports the answer
 * through [onPermissionResult]; the fix is bounded by [timeout] so the state never stays in
 * [LocationUiState.Loading].
 */
class LocationViewModel(
    private val provider: LocationProvider,
    private val timeout: Duration = DEFAULT_TIMEOUT
) : ViewModel() {
    private val mutableState = MutableStateFlow<LocationUiState>(LocationUiState.AwaitingPermission)
    val state: StateFlow<LocationUiState> = mutableState.asStateFlow()

    private var locateJob: Job? = null

    fun onPermissionResult(granted: Boolean) {
        locateJob?.cancel()
        if (granted) {
            locate()
        } else {
            mutableState.value = LocationUiState.PermissionDenied()
        }
    }

    private fun locate() {
        mutableState.value = LocationUiState.Loading
        locateJob = viewModelScope.launch {
            val result = withTimeoutOrNull(timeout) { provider.currentLocation() }
            mutableState.value = when (result) {
                null -> LocationUiState.Failed(LocationError.Timeout)

                is LocationResult.Success -> LocationUiState.Located(result.location)

                is LocationResult.Failure -> when (result.error) {
                    LocationError.PermissionMissing -> LocationUiState.PermissionDenied()
                    else -> LocationUiState.Failed(result.error)
                }
            }
        }
    }

    companion object {
        val DEFAULT_TIMEOUT = 10.seconds
    }
}
