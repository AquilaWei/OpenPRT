package org.openprt.app.destination

import java.io.IOException
import org.openprt.app.geo.BoundingBox
import org.openprt.app.geo.LatLng

/** A search result the user can pick as their destination. */
data class Place(
    /** The place's own name, or its street address when it has none. */
    val name: String,
    /** Where it is (street, neighborhood, city), to tell same-named places apart; may be empty. */
    val description: String,
    val location: LatLng,
    /**
     * The house number and street of a place that has its own [name], so a building name such
     * as "Cathedral of Learning" still shows which address it is; null when [name] is the
     * address already or there is no house number.
     */
    val address: String? = null
) {
    /** How the chosen destination is labeled: the name, then the address when there is one. */
    val label: String get() = listOfNotNull(name, address).joinToString(" · ")
}

/** Turns what the user typed into places; an interface so the ViewModel can use a fake. */
fun interface Geocoder {
    /**
     * Places matching [query], best match first. [bounds] is a hint for the service to search
     * that area; callers that need every result inside it still have to filter. Among places of
     * the same name, those nearer [near] rank higher.
     */
    suspend fun search(query: String, bounds: BoundingBox, near: LatLng): GeocodeResult
}

/** Outcome of a place search; failures are values so the screen can offer a retry. */
sealed interface GeocodeResult {
    data class Success(val places: List<Place>) : GeocodeResult

    data class Failure(val error: GeocodeError) : GeocodeResult
}

/** Why a place search produced no places. */
sealed interface GeocodeError {
    /** The server answered with a non-2xx HTTP status. */
    data class Http(val code: Int) : GeocodeError

    /** The request did not complete within the client's timeout. */
    data object Timeout : GeocodeError

    /** The request failed before a response arrived (no network, DNS, connection reset). */
    data class Network(val cause: IOException) : GeocodeError

    /** The response body was not the GeoJSON the service documents. */
    data class MalformedResponse(val cause: Exception) : GeocodeError
}
