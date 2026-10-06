package org.openprt.app.destination

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.openprt.app.geo.BoundingBox
import org.openprt.app.geo.LatLng
import org.openprt.app.geo.PITTSBURGH_AREA
import org.openprt.app.location.DOWNTOWN_PITTSBURGH

/** An end of a trip to plan: where to go, or a starting point other than the user's location. */
data class Destination(
    /** The picked place's name; null for a spot pinned by long-pressing the map. */
    val name: String?,
    val location: LatLng,
    /** Where the user was when the ends were swapped; it stays put when they move on. */
    val wasUserLocation: Boolean = false
)

/** Which end of the trip the search and a long-press fill in. */
enum class Endpoint { ORIGIN, DESTINATION }

/** Where the place search for [DestinationUiState.query] stands. */
sealed interface SearchStatus {
    /** Nothing to show: the query is empty, or a destination was just picked. */
    data object Idle : SearchStatus

    /** Waiting for the user to pause typing, or for the geocoder to answer. */
    data object Searching : SearchStatus

    /** Matches inside the service area; may be empty. */
    data class Results(val places: List<Place>) : SearchStatus

    /** The search failed; [DestinationViewModel.retry] runs it again. */
    data class Failed(val error: GeocodeError) : SearchStatus
}

/** What the screen can ask of the destination choice; [DestinationViewModel] implements it. */
interface DestinationActions {
    /** Reports the text in the search field; blank text clears the results. */
    fun onQueryChanged(query: String)

    /** Runs the failed search again right away. */
    fun retry()

    /** Makes [place] the end being searched for (see [editOrigin]) and closes the search. */
    fun selectPlace(place: Place)

    /** Makes the long-pressed spot the end being searched for and closes the search. */
    fun onMapLongPress(location: LatLng)

    /**
     * Starts over: the destination goes and the start is the user's location again, so no trip
     * is planned. Removing either end means the user is done with that trip (user feedback,
     * 2026-10-05).
     */
    fun clearDestination()

    /** Starts choosing the starting point: the search and a long-press now fill it in. */
    fun editOrigin()

    /** Stops choosing the starting point without changing it. */
    fun cancelOriginEdit()

    /**
     * Starts over like [clearDestination]: from the user's location, with no destination, rather
     * than planning the same trip again from where the user is.
     */
    fun clearOrigin()

    /**
     * Exchanges the two ends. Starting from the user's location, the destination becomes where
     * they are now, fixed; does nothing while that is unknown or there is no destination.
     */
    fun swapEndpoints()
}

data class DestinationUiState(
    val query: String = "",
    val search: SearchStatus = SearchStatus.Idle,
    val destination: Destination? = null,
    /** The chosen starting point; null starts from the user's location. */
    val origin: Destination? = null,
    /** The end [query] searches for. */
    val editing: Endpoint = Endpoint.DESTINATION
)

/**
 * Lets the user choose where to go, by searching for a place or long-pressing the map, and
 * optionally where to start from the same way (see [editOrigin]); the start is the user's
 * location until then.
 *
 * Typing is debounced by [debounce], and a new keystroke cancels the search in flight, so only
 * the query the user paused on reaches the [geocoder]. Results outside [area] are dropped even if
 * the geocoder returns them, since there is no PRT service to plan a trip to. Places near the
 * user, reported through [onLocationChanged], rank first; until then, places near Downtown.
 */
class DestinationViewModel(
    private val geocoder: Geocoder,
    private val area: BoundingBox = PITTSBURGH_AREA,
    private val debounce: Duration = DEFAULT_DEBOUNCE
) : ViewModel(),
    DestinationActions {
    private val mutableState = MutableStateFlow(DestinationUiState())
    val state: StateFlow<DestinationUiState> = mutableState.asStateFlow()

    // Null stops the current search without starting another. collectLatest cancels the previous
    // request (or its debounce) whenever a new one arrives.
    private val searches = Channel<SearchRequest?>(Channel.CONFLATED)

    private var near: LatLng = DOWNTOWN_PITTSBURGH
    private var userLocation: LatLng? = null

    init {
        viewModelScope.launch {
            searches.receiveAsFlow().collectLatest { request ->
                if (request == null) return@collectLatest
                if (request.debounced) delay(debounce)
                search(request.query)
            }
        }
    }

    override fun onQueryChanged(query: String) {
        if (query.isBlank()) {
            mutableState.update { it.copy(query = query, search = SearchStatus.Idle) }
            searches.trySend(null)
            return
        }
        mutableState.update { it.copy(query = query, search = SearchStatus.Searching) }
        searches.trySend(SearchRequest(query.trim(), debounced = true))
    }

    // Not debounced: the user already waited once.
    override fun retry() {
        val query = mutableState.value.query
        if (query.isBlank()) return
        mutableState.update { it.copy(search = SearchStatus.Searching) }
        searches.trySend(SearchRequest(query.trim(), debounced = false))
    }

    /** Reports the user's location, so searches favor places near it; null keeps the last one. */
    fun onLocationChanged(location: LatLng?) {
        if (location != null) {
            near = location
            userLocation = location
        }
    }

    override fun selectPlace(place: Place) {
        setEnd(Destination(place.label, place.location))
    }

    override fun onMapLongPress(location: LatLng) {
        setEnd(Destination(name = null, location))
    }

    override fun clearDestination() = startOver()

    override fun editOrigin() {
        searches.trySend(null)
        mutableState.update {
            it.copy(query = "", search = SearchStatus.Idle, editing = Endpoint.ORIGIN)
        }
    }

    override fun cancelOriginEdit() {
        searches.trySend(null)
        mutableState.update {
            it.copy(query = "", search = SearchStatus.Idle, editing = Endpoint.DESTINATION)
        }
    }

    override fun clearOrigin() = startOver()

    private fun startOver() {
        searches.trySend(null)
        mutableState.value = DestinationUiState()
    }

    override fun swapEndpoints() {
        val state = mutableState.value
        val destination = state.destination ?: return
        val newDestination = state.origin
            ?: userLocation?.let { Destination(null, it, wasUserLocation = true) }
            ?: return
        // Swapping back to where the user was starts from their live location again.
        val newOrigin = destination.takeUnless { it.wasUserLocation }
        mutableState.update { it.copy(origin = newOrigin, destination = newDestination) }
    }

    // The other end stays; the search closes and goes back to looking for destinations.
    private fun setEnd(end: Destination) {
        searches.trySend(null)
        mutableState.update {
            val origin = if (it.editing == Endpoint.ORIGIN) end else it.origin
            val destination = if (it.editing == Endpoint.DESTINATION) end else it.destination
            DestinationUiState(destination = destination, origin = origin)
        }
    }

    private suspend fun search(query: String) {
        val search = when (val result = geocoder.search(query, area, near)) {
            is GeocodeResult.Success ->
                SearchStatus.Results(result.places.filter { it.location in area })

            is GeocodeResult.Failure -> SearchStatus.Failed(result.error)
        }
        // The cancellation sent by a newer action may not have reached this coroutine yet; only
        // the search the screen is still waiting for may write its result.
        mutableState.update {
            val stillWaiting = it.search == SearchStatus.Searching && it.query.trim() == query
            if (stillWaiting) it.copy(search = search) else it
        }
    }

    private class SearchRequest(val query: String, val debounced: Boolean)

    companion object {
        val DEFAULT_DEBOUNCE = 300.milliseconds
    }
}
