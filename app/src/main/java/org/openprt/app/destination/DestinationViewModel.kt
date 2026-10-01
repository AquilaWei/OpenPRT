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

/** The end point of a trip to plan. */
data class Destination(
    /** The picked place's name; null for a spot pinned by long-pressing the map. */
    val name: String?,
    val location: LatLng
)

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

    /** Makes [place] the destination and closes the search. */
    fun selectPlace(place: Place)

    /** Makes the long-pressed spot the destination and closes the search. */
    fun onMapLongPress(location: LatLng)

    fun clearDestination()
}

data class DestinationUiState(
    val query: String = "",
    val search: SearchStatus = SearchStatus.Idle,
    val destination: Destination? = null
)

/**
 * Lets the user choose where to go, by searching for a place or long-pressing the map.
 *
 * Typing is debounced by [debounce], and a new keystroke cancels the search in flight, so only
 * the query the user paused on reaches the [geocoder]. Results outside [area] are dropped even if
 * the geocoder returns them, since there is no PRT service to plan a trip to.
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

    override fun selectPlace(place: Place) {
        setDestination(Destination(place.name, place.location))
    }

    override fun onMapLongPress(location: LatLng) {
        setDestination(Destination(name = null, location))
    }

    override fun clearDestination() {
        mutableState.update { it.copy(destination = null) }
    }

    private fun setDestination(destination: Destination) {
        searches.trySend(null)
        mutableState.value = DestinationUiState(destination = destination)
    }

    private suspend fun search(query: String) {
        val search = when (val result = geocoder.search(query, area)) {
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
