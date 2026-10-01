package org.openprt.app.details

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.openprt.app.data.truetime.Pattern
import org.openprt.app.data.truetime.TrueTimeClient
import org.openprt.app.data.truetime.TrueTimeError
import org.openprt.app.data.truetime.TrueTimeResult
import org.openprt.app.data.truetime.Vehicle
import org.openprt.app.data.truetime.orEmptyWhenNoData
import org.openprt.app.departures.DepartureItem

/** The TrueTime calls the details screen needs; an interface so tests can use a fake. */
interface TripSource {
    suspend fun vehicles(vehicleIds: List<String>): TrueTimeResult<List<Vehicle>>

    suspend fun patterns(patternId: Int): TrueTimeResult<List<Pattern>>
}

fun TrueTimeClient.asTripSource(): TripSource = object : TripSource {
    override suspend fun vehicles(vehicleIds: List<String>) = getVehicles(vehicleIds)

    override suspend fun patterns(patternId: Int) = getPatterns(patternId)
}

/** Progress of loading the route drawn for the selected departure. */
sealed interface RouteStatus {
    data object Loading : RouteStatus

    data class Ready(val shape: RouteShape) : RouteStatus

    /** TrueTime no longer reports the bus or its pattern, typically because the trip ended. */
    data object NotFound : RouteStatus

    data class Failed(val error: TrueTimeError) : RouteStatus
}

data class DepartureDetailsUiState(val departure: DepartureItem, val route: RouteStatus)

/**
 * Which departure, if any, is shown in detail. [state] is null while the nearby list is shown;
 * [open] switches to the details of a departure and loads its route, [close] goes back.
 *
 * Predictions carry no pattern ID, so the route takes two calls: the bus's position report
 * (which names its pattern), then the pattern itself.
 */
class DepartureDetailsViewModel(private val source: TripSource) : ViewModel() {
    private val mutableState = MutableStateFlow<DepartureDetailsUiState?>(null)
    val state: StateFlow<DepartureDetailsUiState?> = mutableState.asStateFlow()

    private var loadJob: Job? = null

    fun open(departure: DepartureItem) {
        loadJob?.cancel()
        mutableState.value = DepartureDetailsUiState(departure, RouteStatus.Loading)
        loadJob = viewModelScope.launch {
            mutableState.value = DepartureDetailsUiState(departure, loadRoute(departure))
        }
    }

    /** Back to the nearby list; a route still loading is abandoned. */
    fun close() {
        loadJob?.cancel()
        mutableState.value = null
    }

    private suspend fun loadRoute(departure: DepartureItem): RouteStatus {
        val vehicles = source.vehicles(listOf(departure.vehicleId)).orEmptyWhenNoData()
        val vehicle = when (vehicles) {
            is TrueTimeResult.Failure -> return RouteStatus.Failed(vehicles.error)
            is TrueTimeResult.Success -> vehicles.value.firstOrNull { it.id == departure.vehicleId }
        } ?: return RouteStatus.NotFound

        val patterns = source.patterns(vehicle.patternId).orEmptyWhenNoData()
        val pattern = when (patterns) {
            is TrueTimeResult.Failure -> return RouteStatus.Failed(patterns.error)
            is TrueTimeResult.Success -> patterns.value.firstOrNull { it.id == vehicle.patternId }
        } ?: return RouteStatus.NotFound

        return RouteStatus.Ready(pattern.toRouteShape(departure.stopId))
    }
}
