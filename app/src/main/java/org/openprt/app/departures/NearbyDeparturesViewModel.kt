package org.openprt.app.departures

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
import org.openprt.app.data.truetime.DataFeed
import org.openprt.app.data.truetime.Prediction
import org.openprt.app.data.truetime.TrueTimeClient
import org.openprt.app.data.truetime.TrueTimeError
import org.openprt.app.data.truetime.TrueTimeResult
import org.openprt.app.data.truetime.orEmptyWhenNoData

/** Where departures come from; an interface so the ViewModel can be tested with a fake. */
fun interface PredictionSource {
    /** Predictions at 1..[TrueTimeClient.MAX_IDS_PER_CALL] TrueTime stop IDs. */
    suspend fun predictions(stopIds: List<String>): TrueTimeResult<List<Prediction>>
}

/** One row of the departures list, with times already turned into whole minutes. */
data class DepartureItem(
    val route: String,
    val direction: String,
    val destination: String,
    val stopName: String,
    /** Rounded up, so the user never gets less time than shown. */
    val walkMinutes: Long,
    /** Rounded down, matching how PRT signs count down. */
    val minutesUntilDeparture: Long,
    val delayed: Boolean,
    /** TrueTime ID of the stop the bus is boarded at. */
    val stopId: String,
    /** The bus itself; its pattern (route shape) is looked up from it. */
    val vehicleId: String,
    /** The TrueTime feed that tracks [vehicleId]; light rail cars are not in the bus feed. */
    val feed: DataFeed = DataFeed.BUS
)

/** Whether [DeparturesUiState.departures] reflects the latest request. */
sealed interface DeparturesStatus {
    /** Nothing has been fetched yet. */
    data object Loading : DeparturesStatus

    data object Ready : DeparturesStatus

    /** The latest refresh failed; the departures from the last success are kept. */
    data class Failed(val error: TrueTimeError) : DeparturesStatus
}

data class DeparturesUiState(
    val departures: List<DepartureItem> = emptyList(),
    val status: DeparturesStatus = DeparturesStatus.Loading,
    /** When [departures] were last fetched successfully; null before the first success. */
    val lastUpdated: Instant? = null
)

/**
 * Keeps the list of catchable departures near the user current. The nearby stops come from
 * [onStopsChanged]; [autoRefresh] fetches predictions for them right away and then every
 * [refreshInterval], and starts over immediately when the stops change.
 *
 * Only the [TrueTimeClient.MAX_IDS_PER_CALL] nearest stops are asked about, so each refresh is a
 * single [source] call: one request per feed when [source] is a [MergedPredictionSource]. At two
 * requests every 30 seconds the daily TrueTime quota still lasts all day.
 */
class NearbyDeparturesViewModel(
    private val source: PredictionSource,
    private val clock: Clock,
    private val refreshInterval: Duration = DEFAULT_REFRESH_INTERVAL
) : ViewModel() {
    private val ranker = DepartureRanker(clock)

    private val mutableState = MutableStateFlow(DeparturesUiState())
    val state: StateFlow<DeparturesUiState> = mutableState.asStateFlow()

    // Null until the map has looked up stops for the first time.
    private val stops = MutableStateFlow<List<WalkableStop>?>(null)

    /** Reports the stops around the user; their IDs must be TrueTime stop IDs. */
    fun onStopsChanged(walkableStops: List<WalkableStop>) {
        stops.value = walkableStops
    }

    /**
     * Refreshes until cancelled. Callers run it only while the screen is visible (STARTED), so
     * no requests are made in the background; it refreshes again as soon as it is restarted.
     */
    suspend fun autoRefresh() {
        stops.collectLatest { current ->
            if (current == null) return@collectLatest
            while (true) {
                refresh(current)
                delay(refreshInterval)
            }
        }
    }

    private suspend fun refresh(walkableStops: List<WalkableStop>) {
        val asked = walkableStops
            .sortedBy { it.distanceMeters }
            .take(TrueTimeClient.MAX_IDS_PER_CALL)
        if (asked.isEmpty()) {
            mutableState.value =
                DeparturesUiState(emptyList(), DeparturesStatus.Ready, clock.instant())
            return
        }
        val result = source.predictions(asked.map { it.stopId })
        mutableState.value = when (val predictions = result.orEmptyWhenNoData()) {
            is TrueTimeResult.Success -> DeparturesUiState(
                departures = ranker.rank(asked, predictions.value).map { it.toItem() },
                status = DeparturesStatus.Ready,
                lastUpdated = clock.instant()
            )

            is TrueTimeResult.Failure ->
                mutableState.value.copy(status = DeparturesStatus.Failed(predictions.error))
        }
    }

    companion object {
        val DEFAULT_REFRESH_INTERVAL = 30.seconds
    }
}

private fun RankedDeparture.toItem() = DepartureItem(
    route = prediction.route,
    direction = prediction.routeDirection,
    destination = prediction.destination,
    stopName = prediction.stopName,
    walkMinutes = (walkTime.seconds + 59) / 60,
    minutesUntilDeparture = timeUntilDeparture.toMinutes(),
    delayed = prediction.delayed,
    stopId = prediction.stopId,
    vehicleId = prediction.vehicleId,
    feed = prediction.feed
)
