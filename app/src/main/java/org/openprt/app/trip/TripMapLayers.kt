package org.openprt.app.trip

import org.openprt.app.geo.LatLng
import org.openprt.app.planner.Itinerary
import org.openprt.app.planner.WalkLeg

/**
 * What the map draws for a chosen trip: [walks] as dashed lines, [rides] as solid
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
 * [walkPaths] gives the line along the streets for each walk, in the order of the walks; a walk
 * without one is drawn straight. Walks of no length are left out.
 */
fun Itinerary.toMapLayers(
    origin: LatLng,
    destination: LatLng,
    rideStops: List<List<LatLng>>,
    walkPaths: List<List<LatLng>>
): TripMapLayers {
    val walks = walkEnds(origin, destination).mapIndexedNotNull { index, (start, end) ->
        if (start == end) {
            null
        } else {
            walkPaths.getOrNull(index)?.takeIf { it.size >= 2 } ?: listOf(start, end)
        }
    }
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

/** Where each walk starts and ends, in order; the first and last may be [origin] and [destination]. */
fun Itinerary.walkEnds(origin: LatLng, destination: LatLng): List<Pair<LatLng, LatLng>> =
    legs.filterIsInstance<WalkLeg>()
        .map { (it.from?.location ?: origin) to (it.to?.location ?: destination) }
