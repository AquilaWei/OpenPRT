package org.openprt.app.trip

import org.openprt.app.geo.LatLng
import org.openprt.app.planner.Itinerary
import org.openprt.app.planner.WalkLeg

/**
 * What the map draws for a chosen trip: [walks] as dashed straight lines, [rides] as solid
 * lines through the stops each bus serves, and the stops where each bus is boarded and left.
 */
data class TripMapLayers(
    val walks: List<List<LatLng>>,
    val rides: List<List<LatLng>>,
    val boardingStops: List<LatLng>,
    val alightingStops: List<LatLng>
) {
    /** Every point drawn, for fitting the camera to the whole trip. */
    val allPoints: List<LatLng> get() = walks.flatten() + rides.flatten()
}

/**
 * The map layers of this itinerary from [origin] to [destination]. [rideStops] gives the stops
 * between boarding and getting off for each ride, in the order of [Itinerary.rides]; a ride
 * with fewer than two of them is drawn straight from its boarding to its alighting stop.
 * Walks of no length are left out.
 */
fun Itinerary.toMapLayers(
    origin: LatLng,
    destination: LatLng,
    rideStops: List<List<LatLng>>
): TripMapLayers {
    val walks = legs.filterIsInstance<WalkLeg>()
        .map { listOf(it.from?.location ?: origin, it.to?.location ?: destination) }
        .filter { (start, end) -> start != end }
    val rides = rides.mapIndexed { index, ride ->
        rideStops.getOrNull(index)?.takeIf { it.size >= 2 }
            ?: listOf(ride.from.location, ride.to.location)
    }
    return TripMapLayers(
        walks = walks,
        rides = rides,
        boardingStops = this.rides.map { it.from.location },
        alightingStops = this.rides.map { it.to.location }
    )
}
