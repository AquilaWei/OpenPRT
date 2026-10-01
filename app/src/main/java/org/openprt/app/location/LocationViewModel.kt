package org.openprt.app.location

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.openprt.app.geo.LatLng

/** Where the location flow stands; every terminal state carries a usable position. */
sealed interface LocationUiState {
    /** Where the map should center; null until the flow reaches a terminal state. */
    val location: LatLng? get() = null

    /** The permission dialog has not been answered yet. */
    data object AwaitingPermission : LocationUiState

    /** Permission granted, waiting for a fix. Always ends in [Located] or [Failed]. */
    data object Loading : LocationUiState

    data class Located(override val location: LatLng) : LocationUiState

    /** The user refused location access; [location] is the downtown fallback. */
    data class PermissionDenied(override val location: LatLng = DOWNTOWN_PITTSBURGH) :
        LocationUiState

    /** Permission granted but no fix; [location] is the downtown fallback. */
    data class Failed(
        val error: LocationError,
        override val location: LatLng = DOWNTOWN_PITTSBURGH
    ) : LocationUiState
}

/**
 * Drives the permission → fix flow. The screen asks for the permission and reports the answer
 * through [onPermissionResult]; the fix is bounded by [timeout] so the state never stays in
 * [LocationUiState.Loading]. While [followLocation] runs, later fixes keep the state current.
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

    /**
     * Starts over from [LocationUiState.AwaitingPermission], so the screen re-checks the
     * permission (asking again if needed) and takes a fresh fix. Used by the re-center button.
     */
    fun relocate() {
        locateJob?.cancel()
        mutableState.value = LocationUiState.AwaitingPermission
    }

    /**
     * Applies continuous location updates until cancelled. Callers run it only while the screen
     * is visible, so updates stop in the background. Updates are listened to only after the
     * first fix attempt has finished (located or failed), never while the permission is missing
     * or a single fix is in flight; a later update turns [LocationUiState.Failed] into
     * [LocationUiState.Located].
     */
    suspend fun followLocation() {
        state
            .map { it is LocationUiState.Located || it is LocationUiState.Failed }
            .distinctUntilChanged()
            .collectLatest { tracking ->
                if (tracking) {
                    provider.locationUpdates().collect {
                        mutableState.value = LocationUiState.Located(it)
                    }
                }
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
