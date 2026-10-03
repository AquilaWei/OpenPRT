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
import org.openprt.app.data.gtfs.RideStopsSource
import org.openprt.app.data.gtfs.TripPlan
import org.openprt.app.data.gtfs.TripPlanResult
import org.openprt.app.data.gtfs.TripPlanSource
import org.openprt.app.data.truetime.Prediction
import org.openprt.app.data.truetime.TrueTimeResult
import org.openprt.app.data.truetime.orEmptyWhenNoData
import org.openprt.app.departures.DepartureItem
import org.openprt.app.departures.PredictionSource
import org.openprt.app.geo.LatLng
import org.openprt.app.planner.Itinerary
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
 * [boardingTime] follow the prediction and [live] is true. Later legs have no live data, so
 * [arrivalTime] is the timetable's, pushed back only by as much of a late first bus as the
 * waits at transfers cannot absorb.
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

/** Looking up the live bus of a ride the user tapped in a chosen trip. */
sealed interface RideLookup {
    data object Idle : RideLookup

    data class Looking(val ride: RideLeg) : RideLookup

    /**
     * TrueTime predicts this bus at the ride's boarding stop; the screen opens its live details
     * and then calls [TripPlanActions.onRideOpened].
     */
    data class Live(val departure: DepartureItem) : RideLookup

    /** TrueTime has no prediction for this bus, or could not be asked; only its timetable is known. */
    data class ScheduledOnly(val ride: RideLeg) : RideLookup
}

/**
 * The option the user chose, shown leg by leg and on the map. [map] draws rides straight from
 * stop to stop until the stops they pass have been read from the timetable.
 */
data class SelectedTrip(
    val option: TripOption,
    val map: TripMapLayers,
    val ride: RideLookup = RideLookup.Idle
)

/** What the plan list shows while a destination is set. */
sealed interface TripPlanUiState {
    /** Searching, or waiting for the first location fix to search from. */
    data object Planning : TripPlanUiState

    /** At least one option, fewest rides first; [selected] is the one being looked at, if any. */
    data class Results(val options: List<TripOption>, val selected: SelectedTrip? = null) :
        TripPlanUiState

    data class NoRoute(val reason: NoRouteReason) : TripPlanUiState

    /** The timetable has not been downloaded yet; [TripPlanViewModel.retry] tries again. */
    data object NoTimetable : TripPlanUiState
}

/** What the trip panels can ask for; [TripPlanViewModel] does them. */
interface TripPlanActions {
    /** Searches again for the current destination, e.g. after the timetable has downloaded. */
    fun retry()

    /** Shows [option] leg by leg and on the map. */
    fun select(option: TripOption)

    /** Back from a chosen option to the list, without planning again. */
    fun closeSelection()

    /** Looks for the live bus of [ride] in the chosen option. */
    fun openRide(ride: RideLeg)

    /** The screen has opened the live bus found by [openRide]. */
    fun onRideOpened()
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
 *
 * A chosen option is drawn from the origin the plans were made from, not where the user is now.
 * Its rides are traced through the stops [rideStops] reads from the timetable.
 */
class TripPlanViewModel(
    private val source: TripPlanSource,
    private val predictions: PredictionSource,
    private val rideStops: RideStopsSource,
    private val clock: Clock
) : ViewModel(),
    TripPlanActions {
    private val mutableState = MutableStateFlow<TripPlanUiState?>(null)
    val state: StateFlow<TripPlanUiState?> = mutableState.asStateFlow()

    private var origin: LatLng? = null
    private var destination: LatLng? = null
    private var plannedFrom: LatLng? = null
    private var planning: Job? = null

    // Loads for the chosen option; cancelled when the choice or the plans change.
    private var selection: Job? = null
    private var rideLookup: Job? = null

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

    override fun retry() {
        if (destination != null) startPlanning()
    }

    override fun select(option: TripOption) {
        val results = mutableState.value as? TripPlanUiState.Results ?: return
        val from = plannedFrom ?: return
        val to = destination ?: return
        val itinerary = option.plan.itinerary
        cancelSelectionLoads()
        mutableState.value = results.copy(
            selected = SelectedTrip(option, itinerary.toMapLayers(from, to, emptyList()))
        )
        selection = viewModelScope.launch {
            val stops = itinerary.rides.map {
                rideStops.stopsBetween(it.tripId, it.from.stopId, it.to.stopId)
            }
            val layers = itinerary.toMapLayers(from, to, stops)
            updateSelected(option) { it.copy(map = layers) }
        }
    }

    override fun closeSelection() {
        cancelSelectionLoads()
        val results = mutableState.value as? TripPlanUiState.Results ?: return
        mutableState.value = results.copy(selected = null)
    }

    override fun openRide(ride: RideLeg) {
        val option = (mutableState.value as? TripPlanUiState.Results)?.selected?.option ?: return
        updateSelected(option) { it.copy(ride = RideLookup.Looking(ride)) }
        rideLookup?.cancel()
        rideLookup = viewModelScope.launch {
            val found = findLiveBus(option, ride)
            updateSelected(option) {
                it.copy(ride = found?.let(RideLookup::Live) ?: RideLookup.ScheduledOnly(ride))
            }
        }
    }

    override fun onRideOpened() {
        val option = (mutableState.value as? TripPlanUiState.Results)?.selected?.option ?: return
        updateSelected(option) { it.copy(ride = RideLookup.Idle) }
    }

    private fun cancelSelectionLoads() {
        selection?.cancel()
        rideLookup?.cancel()
    }

    // A load that outlives its option must not change what replaced it.
    private fun updateSelected(option: TripOption, transform: (SelectedTrip) -> SelectedTrip) {
        val results = mutableState.value as? TripPlanUiState.Results ?: return
        val selected = results.selected?.takeIf { it.option == option } ?: return
        mutableState.value = results.copy(selected = transform(selected))
    }

    private suspend fun findLiveBus(option: TripOption, ride: RideLeg): DepartureItem? {
        val now = clock.instant()
        val stopId = ride.from.trueTimeStopId
        val result = predictions.predictions(listOf(stopId)).orEmptyWhenNoData()
        val live = (result as? TrueTimeResult.Success)?.value.orEmpty()
        val plan = option.plan
        val prediction = closestPrediction(
            live,
            ride,
            scheduled = plan.timeOf(ride.startSeconds),
            catchableFrom = now
        ) ?: return null
        return DepartureItem(
            route = prediction.route,
            direction = prediction.routeDirection,
            destination = prediction.destination,
            stopName = ride.from.name,
            walkMinutes = plan.itinerary.walkBefore(ride),
            minutesUntilDeparture = Duration.between(now, prediction.predictedTime).toMinutes(),
            delayed = prediction.delayed,
            stopId = prediction.stopId,
            vehicleId = prediction.vehicleId
        )
    }

    private fun startPlanning() {
        planning?.cancel()
        planning = null
        cancelSelectionLoads()
        val to = destination
        if (to == null) {
            mutableState.value = null
            return
        }
        mutableState.value = TripPlanUiState.Planning
        val from = origin ?: return
        plannedFrom = from
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
    val live = closestPrediction(predictions, firstRide, scheduledBoarding, now + walkToBus)
    val boardingTime = live?.predictedTime ?: scheduledBoarding
    val departureTime = boardingTime - walkToBus
    // An early first bus does not make the later legs leave earlier, so only lateness counts.
    val lateSeconds = Duration.between(scheduledBoarding, boardingTime).seconds.coerceAtLeast(0)
    val expectedArrival = arrivalTime.plusSeconds(itinerary.delayAtEnd(lateSeconds))
    return TripOption(
        departureTime = departureTime,
        arrivalTime = expectedArrival,
        totalMinutes = Duration.between(departureTime, expectedArrival).roundedUpMinutes(),
        transfers = itinerary.transfers,
        legs = itinerary.legs.mapNotNull { leg ->
            when (leg) {
                is RideLeg -> LegSummary.Ride(leg.routeId)

                is WalkLeg -> {
                    val minutes = leg.minutes()
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

/**
 * The entry of [predictions] for [ride]'s route at its boarding stop that leaves no earlier than
 * [catchableFrom] and closest to the [scheduled] time, within [MAX_LIVE_OFFSET]; null if none.
 */
private fun closestPrediction(
    predictions: List<Prediction>,
    ride: RideLeg,
    scheduled: Instant,
    catchableFrom: Instant
): Prediction? = predictions
    .filter {
        it.route == ride.routeId &&
            it.stopId == ride.from.trueTimeStopId &&
            it.predictedTime >= catchableFrom
    }
    .map { it to Duration.between(scheduled, it.predictedTime).abs() }
    .filter { (_, offset) -> offset <= MAX_LIVE_OFFSET }
    .minByOrNull { (_, offset) -> offset }
    ?.first

/**
 * How late the trip ends when its first bus leaves [lateSeconds] late: each later ride starts
 * on time if the delay fits in the wait before it, and otherwise the rest of the delay carries
 * on, as if a later bus of that route ran the same timetable shifted back.
 */
private fun Itinerary.delayAtEnd(lateSeconds: Long): Long {
    var delay = lateSeconds
    legs.zipWithNext().forEach { (previous, next) ->
        if (next is RideLeg && next != rides.first()) {
            val wait = (next.startSeconds - previous.endSeconds).coerceAtLeast(0)
            delay = (delay - wait).coerceAtLeast(0)
        }
    }
    return delay
}

/** Minutes of walking right before [ride], rounded up; 0 after a ride or a wait. */
private fun Itinerary.walkBefore(ride: RideLeg): Long {
    val walk = legs.getOrNull(legs.indexOf(ride) - 1) as? WalkLeg ?: return 0
    return walk.minutes()
}

/** How long this walk takes, rounded up so the user never gets less time than shown. */
internal fun WalkLeg.minutes(): Long =
    Duration.ofSeconds((endSeconds - startSeconds).toLong()).roundedUpMinutes()

private fun Duration.roundedUpMinutes(): Long = (seconds + 59) / 60
