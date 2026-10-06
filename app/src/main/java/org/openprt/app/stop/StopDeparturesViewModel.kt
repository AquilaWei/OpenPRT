package org.openprt.app.stop

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
import kotlinx.coroutines.launch
import org.openprt.app.data.gtfs.ScheduledRun
import org.openprt.app.data.gtfs.ScheduledStopTime
import org.openprt.app.data.gtfs.ScheduledTripSource
import org.openprt.app.data.gtfs.StopScheduleEntry
import org.openprt.app.data.gtfs.StopScheduleSource
import org.openprt.app.data.truetime.Prediction
import org.openprt.app.data.truetime.TrueTimeError
import org.openprt.app.data.truetime.TrueTimeResult
import org.openprt.app.data.truetime.orEmptyWhenNoData
import org.openprt.app.departures.DepartureItem
import org.openprt.app.departures.DepartureRanker
import org.openprt.app.departures.PredictionSource
import org.openprt.app.geo.LatLng
import org.openprt.app.geo.haversineMeters
import org.openprt.app.map.StopMarker

/** Where the times in a stop's list come from. */
sealed interface StopTimesSource {
    /** Nothing has been fetched yet. */
    data object Loading : StopTimesSource

    /** TrueTime predictions. */
    data object Live : StopTimesSource

    /**
     * The GTFS timetable, because TrueTime failed with [liveError] (for instance there is no
     * key), or, when [liveError] is null, answered but predicts no bus at this stop.
     */
    data class Scheduled(val liveError: TrueTimeError?) : StopTimesSource
}

/**
 * One bus leaving the stop. Live rows carry [departure], which opens the departure details with
 * the bus on the map; timetabled rows have no vehicle to follow and carry [run] instead, which
 * opens the run's remaining stops and times.
 */
data class StopDeparture(
    val route: String,
    val destination: String,
    /** Rounded down and never below 0, like the departures list. */
    val minutes: Long,
    val delayed: Boolean,
    val departure: DepartureItem?,
    val run: ScheduledRun? = null,
    /** When a timetabled row's bus leaves; shown instead of [minutes] when it is hours away. */
    val time: Instant? = null
)

/** A timetabled run opened from the stop's list. */
data class ScheduledTripUiState(
    val row: StopDeparture,
    /** From the stop on; null while loading, empty when the timetable no longer has the run. */
    val stops: List<ScheduledStopTime>? = null
)

data class StopDeparturesUiState(
    val stop: StopMarker,
    val departures: List<StopDeparture> = emptyList(),
    val source: StopTimesSource = StopTimesSource.Loading,
    /** When [departures] were last fetched; null before the first refresh. */
    val lastUpdated: Instant? = null,
    /** The timetabled run shown over the list, if one is open. */
    val scheduledTrip: ScheduledTripUiState? = null
)

/**
 * The departures from one stop the user tapped on the map. [state] is null while no stop is
 * open; [select] opens a stop, [back] goes back. [autoRefresh] fetches its TrueTime predictions
 * right away and then every [refreshInterval]; when TrueTime fails or predicts nothing there,
 * the timetable from [schedule] is shown instead and marked as scheduled. A timetabled row opens
 * its run's remaining stops from [trips] through [openScheduledTrip].
 *
 * One API call per refresh, on top of the nearby list's, and only while a stop is open.
 */
class StopDeparturesViewModel(
    private val predictions: PredictionSource,
    private val schedule: StopScheduleSource,
    private val trips: ScheduledTripSource,
    private val clock: Clock,
    private val refreshInterval: Duration = DEFAULT_REFRESH_INTERVAL
) : ViewModel() {
    private val ranker = DepartureRanker(clock)

    private val mutableState = MutableStateFlow<StopDeparturesUiState?>(null)
    val state: StateFlow<StopDeparturesUiState?> = mutableState.asStateFlow()

    // Compared by identity, so reopening the same stop still restarts the refreshes.
    private class Selection(val stop: StopMarker, val walkMinutes: Long)

    private val selection = MutableStateFlow<Selection?>(null)

    /**
     * Shows [stop], whose ID must be its TrueTime stop ID. The walk to it is measured from
     * [from], the user's position; without one it counts as no walk.
     */
    fun select(stop: StopMarker, from: LatLng?) {
        val walk = from?.let { ranker.walkTime(haversineMeters(it, stop.position)) }
        mutableState.value = StopDeparturesUiState(stop)
        selection.value = Selection(stop, walk?.let { (it.seconds + 59) / 60 } ?: 0)
    }

    /** Back to whatever the stop covered; requests still running are abandoned. */
    fun close() {
        selection.value = null
        mutableState.value = null
    }

    /** Closes the open timetabled run if there is one, and the stop otherwise. */
    fun back() {
        if (mutableState.value?.scheduledTrip != null) {
            mutableState.update { it?.copy(scheduledTrip = null) }
        } else {
            close()
        }
    }

    /**
     * Shows where the timetabled [row] goes from this stop. Does nothing for a live row, whose
     * bus opens the departure details instead.
     */
    fun openScheduledTrip(row: StopDeparture) {
        val run = row.run ?: return
        val opened = ScheduledTripUiState(row)
        mutableState.update { it?.copy(scheduledTrip = opened) }
        viewModelScope.launch {
            val stops = trips.stopsFrom(run)
            mutableState.update { state ->
                // Identity: a run closed or replaced while loading must stay that way.
                if (state?.scheduledTrip === opened) {
                    state.copy(scheduledTrip = opened.copy(stops = stops))
                } else {
                    state
                }
            }
        }
    }

    /**
     * Refreshes the open stop until cancelled, starting over when another one is opened.
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
        val stopId = current.stop.stopId
        val live = predictions.predictions(listOf(stopId)).orEmptyWhenNoData()
        val now = clock.instant()
        val liveRows = (live as? TrueTimeResult.Success)?.value
            ?.filter { it.stopId == stopId && !it.predictedTime.isBefore(now) }
            ?.sortedBy { it.predictedTime }
            ?.take(MAX_ROWS)
            ?.map { it.toRow(current.walkMinutes, now) }
            .orEmpty()
        val (rows, source) = if (liveRows.isNotEmpty()) {
            liveRows to StopTimesSource.Live
        } else {
            val scheduled = schedule.departures(stopId, now, MAX_ROWS).map { it.toRow(now) }
            scheduled to StopTimesSource.Scheduled((live as? TrueTimeResult.Failure)?.error)
        }
        mutableState.update { state ->
            // A refresh that outlives its selection must not overwrite what replaced it.
            if (state != null && selection.value === current) {
                state.copy(departures = rows, source = source, lastUpdated = now)
            } else {
                state
            }
        }
    }

    companion object {
        val DEFAULT_REFRESH_INTERVAL = 30.seconds

        /** Enough to cover the next hour or so at a busy stop without a long scroll. */
        const val MAX_ROWS = 10
    }
}

private fun Prediction.toRow(walkMinutes: Long, now: Instant) = StopDeparture(
    route = route,
    destination = destination,
    minutes = minutesUntil(predictedTime, now),
    delayed = delayed,
    departure = DepartureItem(
        route = route,
        direction = routeDirection,
        destination = destination,
        stopName = stopName,
        walkMinutes = walkMinutes,
        minutesUntilDeparture = minutesUntil(predictedTime, now),
        delayed = delayed,
        stopId = stopId,
        vehicleId = vehicleId,
        feed = feed
    )
)

private fun StopScheduleEntry.toRow(now: Instant) = StopDeparture(
    route = route,
    destination = headsign.orEmpty(),
    minutes = minutesUntil(time, now),
    delayed = false,
    departure = null,
    run = run,
    time = time
)

private fun minutesUntil(time: Instant, now: Instant): Long =
    java.time.Duration.between(now, time).toMinutes().coerceAtLeast(0)
