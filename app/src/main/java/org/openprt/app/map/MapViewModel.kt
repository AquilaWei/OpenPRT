package org.openprt.app.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.time.Clock
import java.time.LocalDate
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import org.openprt.app.data.gtfs.GtfsImportError
import org.openprt.app.data.gtfs.NearbyStopSource
import org.openprt.app.data.gtfs.NearbyStopsResult
import org.openprt.app.data.gtfs.PRT_TIME_ZONE
import org.openprt.app.data.gtfs.TimetableDatesSource
import org.openprt.app.data.gtfs.trueTimeStopId
import org.openprt.app.departures.WalkableStop
import org.openprt.app.geo.LatLng
import org.openprt.app.geo.haversineMeters

/**
 * A stop to draw on the map. [stopId] is its TrueTime stop ID (the number on the stop sign),
 * so a tapped stop can be asked about straight away, whether it came from GTFS or a pattern.
 */
data class StopMarker(val stopId: String, val name: String, val position: LatLng)

/** Whether the stop markers reflect the latest queried position. */
sealed interface StopsStatus {
    /** No position yet, or a lookup (possibly including the first GTFS download) is running. */
    data object Loading : StopsStatus

    data object Ready : StopsStatus

    /** The lookup failed; the markers from the previous success are kept. */
    data class Failed(val error: GtfsImportError) : StopsStatus
}

data class MapUiState(
    val stopMarkers: List<StopMarker> = emptyList(),
    val stopsStatus: StopsStatus = StopsStatus.Loading,
    /** The same stops as [stopMarkers], keyed by TrueTime stop ID, for the departures list. */
    val walkableStops: List<WalkableStop> = emptyList(),
    /**
     * The last day of the imported timetable when that day is already past, so its times may no
     * longer match the buses; null while the timetable is current or there is none.
     */
    val timetableEndedOn: LocalDate? = null
)

/**
 * Keeps the nearby-stop markers in step with the map's position. Small moves reuse the current
 * markers: stops are re-queried only once the position is at least [requeryDistanceMeters] away
 * from where they were last queried.
 *
 * Also tells whether the timetable has run out, from [timetableDates] and [clock], after every
 * stop lookup (which may have downloaded the first timetable) and whenever [timetableUpdates]
 * emits, i.e. a newer timetable was imported.
 */
class MapViewModel(
    private val stopSource: NearbyStopSource,
    private val radiusMeters: Double = DEFAULT_RADIUS_METERS,
    private val requeryDistanceMeters: Double = DEFAULT_REQUERY_DISTANCE_METERS,
    private val timetableDates: TimetableDatesSource = TimetableDatesSource { null },
    private val clock: Clock = Clock.systemUTC(),
    timetableUpdates: Flow<Any?> = emptyFlow()
) : ViewModel() {
    private val mutableState = MutableStateFlow(MapUiState())
    val state: StateFlow<MapUiState> = mutableState.asStateFlow()

    // Center of the last lookup that was started; null forces the next position to query.
    private var lastQueried: LatLng? = null

    // Conflated: a lookup in progress is not cancelled (it may be mid-download); positions that
    // arrive meanwhile collapse into the latest one, which is queried next.
    private val queries = Channel<LatLng>(Channel.CONFLATED)

    init {
        viewModelScope.launch {
            queries.receiveAsFlow().collect { load(it) }
        }
        viewModelScope.launch {
            timetableUpdates.collect { checkTimetableEnd() }
        }
    }

    /** Reports the position the map is centered on (a device fix or the downtown fallback). */
    fun onLocationChanged(location: LatLng) {
        val last = lastQueried
        if (last != null && haversineMeters(last, location) < requeryDistanceMeters) return
        lastQueried = location
        queries.trySend(location)
    }

    private suspend fun load(center: LatLng) {
        mutableState.value = mutableState.value.copy(stopsStatus = StopsStatus.Loading)
        mutableState.value = when (val result = stopSource.nearbyStops(center, radiusMeters)) {
            is NearbyStopsResult.Success -> MapUiState(
                stopMarkers = result.stops.map {
                    StopMarker(
                        stopId = it.stop.trueTimeStopId,
                        name = it.stop.name,
                        position = LatLng(it.stop.latitude, it.stop.longitude)
                    )
                },
                stopsStatus = StopsStatus.Ready,
                walkableStops = result.stops.map {
                    WalkableStop(it.stop.trueTimeStopId, it.distanceMeters)
                }
            )

            is NearbyStopsResult.Failure -> {
                // Retry on the next position update instead of waiting for a 100 m move.
                lastQueried = null
                mutableState.value.copy(stopsStatus = StopsStatus.Failed(result.error))
            }
        }
        checkTimetableEnd()
    }

    private suspend fun checkTimetableEnd() {
        val last = timetableDates.dates()?.endInclusive
        val today = LocalDate.now(clock.withZone(PRT_TIME_ZONE))
        val endedOn = last?.takeIf { it < today }
        mutableState.value = mutableState.value.copy(timetableEndedOn = endedOn)
    }

    companion object {
        /** A five-minute walk; F7 ranks departures from stops within this radius. */
        const val DEFAULT_RADIUS_METERS = 400.0
        const val DEFAULT_REQUERY_DISTANCE_METERS = 100.0
    }
}
