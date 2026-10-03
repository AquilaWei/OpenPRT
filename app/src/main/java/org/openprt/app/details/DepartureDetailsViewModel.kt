package org.openprt.app.details

import androidx.lifecycle.ViewModel
import java.time.Clock
import java.time.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import org.openprt.app.data.truetime.Pattern
import org.openprt.app.data.truetime.Prediction
import org.openprt.app.data.truetime.TrueTimeClient
import org.openprt.app.data.truetime.TrueTimeError
import org.openprt.app.data.truetime.TrueTimeResult
import org.openprt.app.data.truetime.Vehicle
import org.openprt.app.data.truetime.orEmptyWhenNoData
import org.openprt.app.departures.DepartureItem
import org.openprt.app.geo.LatLng

/** The TrueTime calls the details screen needs; an interface so tests can use a fake. */
interface TripSource {
    suspend fun vehicles(vehicleIds: List<String>): TrueTimeResult<List<Vehicle>>

    suspend fun patterns(patternId: Int): TrueTimeResult<List<Pattern>>

    suspend fun predictions(stopIds: List<String>): TrueTimeResult<List<Prediction>>
}

fun TrueTimeClient.asTripSource(): TripSource = object : TripSource {
    override suspend fun vehicles(vehicleIds: List<String>) = getVehicles(vehicleIds)

    override suspend fun patterns(patternId: Int) = getPatterns(patternId)

    override suspend fun predictions(stopIds: List<String>) = getPredictions(stopIds)
}

/** Progress of loading the route drawn for the selected departure. */
sealed interface RouteStatus {
    data object Loading : RouteStatus

    data class Ready(val shape: RouteShape) : RouteStatus

    /** TrueTime no longer reports the bus or its pattern, typically because the trip ended. */
    data object NotFound : RouteStatus

    data class Failed(val error: TrueTimeError) : RouteStatus
}

/** Where the bus was last reported, for the map marker. */
data class BusPosition(val location: LatLng, val headingDegrees: Int)

/** When the selected bus reaches the user's boarding stop. */
sealed interface Arrival {
    /** Nothing has been fetched yet. */
    data object Loading : Arrival

    /** Rounded down, like the departures list and PRT's own signs. */
    data class Expected(val minutes: Long, val delayed: Boolean) : Arrival

    /** The bus is past the boarding stop, or TrueTime no longer predicts it there. */
    data object Departed : Arrival
}

/**
 * The live state of the selected bus. A failed refresh sets [error] and keeps the position and
 * arrival from earlier refreshes; [lastUpdated] is when both calls last succeeded.
 */
data class LiveBus(
    val position: BusPosition? = null,
    /** Where the bus is along the route; null until both the bus and the route are known. */
    val progress: BusProgress? = null,
    val arrival: Arrival = Arrival.Loading,
    val lastUpdated: Instant? = null,
    val error: TrueTimeError? = null
)

data class DepartureDetailsUiState(
    val departure: DepartureItem,
    val route: RouteStatus,
    val bus: LiveBus = LiveBus()
)

/**
 * Which departure, if any, is shown in detail. [state] is null while the nearby list is shown;
 * [open] switches to the details of a departure, [close] goes back. [autoRefresh] does the
 * loading: the route once, and the bus's position and arrival every [refreshInterval].
 *
 * Predictions carry no pattern ID, so the route takes two calls: the bus's position report
 * (which names its pattern), then the pattern itself. A route that failed to load is retried
 * on the next refresh.
 */
class DepartureDetailsViewModel(
    private val source: TripSource,
    private val clock: Clock,
    private val refreshInterval: Duration = DEFAULT_REFRESH_INTERVAL
) : ViewModel() {
    private val mutableState = MutableStateFlow<DepartureDetailsUiState?>(null)
    val state: StateFlow<DepartureDetailsUiState?> = mutableState.asStateFlow()

    // Compared by identity, so reopening the same departure still restarts the refreshes.
    private class Selection(val departure: DepartureItem)

    private val selection = MutableStateFlow<Selection?>(null)

    fun open(departure: DepartureItem) {
        mutableState.value = DepartureDetailsUiState(departure, RouteStatus.Loading)
        selection.value = Selection(departure)
    }

    /** Back to the nearby list; requests still running are abandoned. */
    fun close() {
        selection.value = null
        mutableState.value = null
    }

    /**
     * Refreshes the open departure until cancelled, starting over when another one is opened.
     * Callers run it only while the screen is visible (STARTED), like the departures list.
     */
    suspend fun autoRefresh() {
        selection.collectLatest { current ->
            if (current == null) return@collectLatest
            while (true) {
                refresh(current)
                delay(refreshInterval)
            }
        }
    }

    private suspend fun refresh(current: Selection) {
        val departure = current.departure
        val vehicles = source.vehicles(listOf(departure.vehicleId)).orEmptyWhenNoData()
        if (mutableState.value?.route !is RouteStatus.Ready) {
            val route = loadRoute(departure, vehicles)
            updateIfOpen(current) { it.copy(route = route) }
        }
        val predictions = source.predictions(listOf(departure.stopId)).orEmptyWhenNoData()
        val now = clock.instant()
        updateIfOpen(current) {
            it.copy(bus = it.bus.next(departure, it.route, vehicles, predictions, now))
        }
    }

    // A refresh that outlives its selection must not overwrite what replaced it.
    private fun updateIfOpen(
        current: Selection,
        transform: (DepartureDetailsUiState) -> DepartureDetailsUiState
    ) {
        mutableState.update { state ->
            if (state != null && selection.value === current) transform(state) else state
        }
    }

    private suspend fun loadRoute(
        departure: DepartureItem,
        vehicles: TrueTimeResult<List<Vehicle>>
    ): RouteStatus {
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

    companion object {
        val DEFAULT_REFRESH_INTERVAL = 15.seconds
    }
}

/**
 * This bus after one refresh. Each part falls back to its previous value when its call failed.
 * A bus no longer reported has no position; whether it has left is decided by its distance
 * along the pattern when known, otherwise by whether TrueTime still predicts it at the stop.
 */
private fun LiveBus.next(
    departure: DepartureItem,
    route: RouteStatus,
    vehicles: TrueTimeResult<List<Vehicle>>,
    predictions: TrueTimeResult<List<Prediction>>,
    now: Instant
): LiveBus {
    val vehicle = (vehicles as? TrueTimeResult.Success)?.value
        ?.firstOrNull { it.id == departure.vehicleId }
    val boardingFeet = (route as? RouteStatus.Ready)?.shape?.boardingDistanceFeet
    val passedStop =
        vehicle != null && boardingFeet != null && vehicle.distanceAlongPatternFeet > boardingFeet
    val newArrival = when {
        passedStop -> Arrival.Departed

        predictions is TrueTimeResult.Success ->
            predictions.value
                .filter { it.vehicleId == departure.vehicleId && it.stopId == departure.stopId }
                .minByOrNull { it.predictedTime }
                ?.let { Arrival.Expected(minutesUntil(it.predictedTime, now), it.delayed) }
                ?: Arrival.Departed

        else -> arrival
    }
    val error = (vehicles as? TrueTimeResult.Failure)?.error
        ?: (predictions as? TrueTimeResult.Failure)?.error
    val shape = (route as? RouteStatus.Ready)?.shape
    return LiveBus(
        position = when (vehicles) {
            is TrueTimeResult.Success ->
                vehicle?.let { BusPosition(LatLng(it.latitude, it.longitude), it.headingDegrees) }

            is TrueTimeResult.Failure -> position
        },
        progress = when (vehicles) {
            is TrueTimeResult.Success ->
                vehicle?.let { shape?.progressOf(it.distanceAlongPatternFeet.toDouble()) }

            is TrueTimeResult.Failure -> progress
        },
        arrival = newArrival,
        lastUpdated = if (error == null) now else lastUpdated,
        error = error
    )
}

private fun minutesUntil(time: Instant, now: Instant): Long =
    java.time.Duration.between(now, time).toMinutes().coerceAtLeast(0)
