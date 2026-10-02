package org.openprt.app.trip

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.time.Clock
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.openprt.app.data.gtfs.TripPlan
import org.openprt.app.data.gtfs.TripPlanResult
import org.openprt.app.data.gtfs.TripPlanSource
import org.openprt.app.data.truetime.Prediction
import org.openprt.app.data.truetime.TrueTimeResult
import org.openprt.app.data.truetime.orEmptyWhenNoData
import org.openprt.app.departures.PredictionSource
import org.openprt.app.geo.LatLng
import org.openprt.app.planner.NoRouteReason
import org.openprt.app.planner.RideLeg
import org.openprt.app.planner.WalkLeg

/** A leg as the plan list shows it. */
sealed interface LegSummary {
    /** Rounded up, so the user never gets less time than shown. */
    data class Walk(val minutes: Long) : LegSummary

    data class Ride(val route: String) : LegSummary
}

/**
 * One row of the plan list. When TrueTime predicts the first bus, [departureTime] and
 * [boardingTime] follow the prediction and [live] is true; [arrivalTime] always comes from the
 * timetable, since later legs have no live data.
 */
data class TripOption(
    /** When to set off from the origin to reach the first bus. */
    val departureTime: Instant,
    val arrivalTime: Instant,
    /** From [departureTime] to [arrivalTime], rounded up. */
    val totalMinutes: Long,
    val transfers: Int,
    /** Walks of no length, e.g. when the origin is at the stop, are left out. */
    val legs: List<LegSummary>,
    val firstRoute: String,
    val boardingStopName: String,
    /** When the first bus leaves the boarding stop. */
    val boardingTime: Instant,
    val live: Boolean,
    /** The plan behind this row, for showing it on the map. */
    val plan: TripPlan
)

/** What the plan list shows while a destination is set. */
sealed interface TripPlanUiState {
    /** Searching, or waiting for the first location fix to search from. */
    data object Planning : TripPlanUiState

    /** At least one option, fewest rides first. */
    data class Results(val options: List<TripOption>) : TripPlanUiState

    data class NoRoute(val reason: NoRouteReason) : TripPlanUiState

    /** The timetable has not been downloaded yet; [TripPlanViewModel.retry] tries again. */
    data object NoTimetable : TripPlanUiState
}

/**
 * Plans a trip from the user's location to the chosen destination. A new destination replaces
 * the plans, and clearing it cancels any search in flight; `state` is null while there is no
 * destination, so the screen shows the nearby departures instead.
 *
 * Location updates do not plan again, so the list stays put while the user walks; only the first
 * known location (also after re-centering) starts a search that was waiting for one.
 *
 * The first bus of each option is looked up in TrueTime once, when the plans arrive; if that
 * fails the options keep their timetable times rather than failing the whole search.
 */
class TripPlanViewModel(
    private val source: TripPlanSource,
    private val predictions: PredictionSource,
    private val clock: Clock
) : ViewModel() {
    private val mutableState = MutableStateFlow<TripPlanUiState?>(null)
    val state: StateFlow<TripPlanUiState?> = mutableState.asStateFlow()

    private var origin: LatLng? = null
    private var destination: LatLng? = null
    private var planning: Job? = null

    /** Reports the user's location; null while it is being looked up again. */
    fun onLocationChanged(location: LatLng?) {
        val waitingForLocation = origin == null
        origin = location
        if (waitingForLocation && location != null && destination != null) startPlanning()
    }

    /** Reports the destination; null clears it and returns to the nearby departures. */
    fun onDestinationChanged(location: LatLng?) {
        if (location == destination) return
        destination = location
        startPlanning()
    }

    /** Searches again for the current destination, e.g. after the timetable has downloaded. */
    fun retry() {
        if (destination != null) startPlanning()
    }

    private fun startPlanning() {
        planning?.cancel()
        planning = null
        val to = destination
        if (to == null) {
            mutableState.value = null
            return
        }
        mutableState.value = TripPlanUiState.Planning
        val from = origin ?: return
        // Cancelled before it can write if the destination changes, since both run on Main.
        planning = viewModelScope.launch { mutableState.value = plan(from, to) }
    }

    private suspend fun plan(from: LatLng, to: LatLng): TripPlanUiState {
        val now = clock.instant()
        return when (val result = source.plan(from, to, now)) {
            is TripPlanResult.Found -> TripPlanUiState.Results(options(result.plans, now))
            is TripPlanResult.NoRoute -> TripPlanUiState.NoRoute(result.reason)
            TripPlanResult.NoTimetable -> TripPlanUiState.NoTimetable
        }
    }

    private suspend fun options(plans: List<TripPlan>, now: Instant): List<TripOption> {
        val stopIds = plans.map { it.itinerary.rides.first().from.trueTimeStopId }.distinct()
        val live = when (val result = predictions.predictions(stopIds).orEmptyWhenNoData()) {
            is TrueTimeResult.Success -> result.value

            // The timetable alone still gives usable plans.
            is TrueTimeResult.Failure -> emptyList()
        }
        return plans.map { it.toOption(live, now) }
    }
}

/** How far a prediction may be from the scheduled boarding time to count as the same bus. */
private val MAX_LIVE_OFFSET: Duration = Duration.ofMinutes(15)

/**
 * This plan as a list row. The first bus takes the time of the [predictions] entry for its route
 * at its boarding stop that the user can still walk to by [now] and that is closest to the
 * timetable, within [MAX_LIVE_OFFSET]; without one the timetable time stays.
 */
internal fun TripPlan.toOption(predictions: List<Prediction>, now: Instant): TripOption {
    val firstRide = itinerary.rides.first()
    val scheduledBoarding = timeOf(firstRide.startSeconds)
    val walkToBus = Duration.ofSeconds(
        (firstRide.startSeconds - itinerary.departureSeconds).toLong()
    )
    val catchableFrom = now + walkToBus
    val live = predictions
        .filter {
            it.route == firstRide.routeId &&
                it.stopId == firstRide.from.trueTimeStopId &&
                it.predictedTime >= catchableFrom
        }
        .map { it to Duration.between(scheduledBoarding, it.predictedTime).abs() }
        .filter { (_, offset) -> offset <= MAX_LIVE_OFFSET }
        .minByOrNull { (_, offset) -> offset }
        ?.first
    val boardingTime = live?.predictedTime ?: scheduledBoarding
    val departureTime = boardingTime - walkToBus
    return TripOption(
        departureTime = departureTime,
        arrivalTime = arrivalTime,
        totalMinutes = Duration.between(departureTime, arrivalTime).roundedUpMinutes(),
        transfers = itinerary.transfers,
        legs = itinerary.legs.mapNotNull { leg ->
            when (leg) {
                is RideLeg -> LegSummary.Ride(leg.routeId)

                is WalkLeg -> {
                    val minutes = Duration.ofSeconds(leg.durationSeconds()).roundedUpMinutes()
                    if (minutes > 0) LegSummary.Walk(minutes) else null
                }
            }
        },
        firstRoute = firstRide.routeId,
        boardingStopName = firstRide.from.name,
        boardingTime = boardingTime,
        live = live != null,
        plan = this
    )
}

private fun WalkLeg.durationSeconds(): Long = (endSeconds - startSeconds).toLong()

private fun Duration.roundedUpMinutes(): Long = (seconds + 59) / 60
