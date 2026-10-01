package org.openprt.app.details

import org.openprt.app.data.truetime.Pattern
import org.openprt.app.geo.LatLng
import org.openprt.app.map.StopMarker

/**
 * What the map draws for one bus's route: [line] through every pattern point in order, the
 * [stops] along it, and the user's [boardingStop], which is null when the pattern does not
 * serve that stop (e.g. the stop ID schemes of GTFS and TrueTime turn out not to match).
 */
data class RouteShape(
    val line: List<LatLng>,
    val stops: List<StopMarker>,
    val boardingStop: StopMarker?
)

/**
 * Turns this pattern into a [RouteShape], ordered by point sequence. Stops are part of the line
 * too, since BusTime places them on the road. A loop route that visits [boardingStopId] twice
 * boards at its first visit.
 */
fun Pattern.toRouteShape(boardingStopId: String): RouteShape {
    val ordered = points.sortedBy { it.sequence }
    val stops = ordered.mapNotNull { point ->
        point.stop?.let { StopMarker(it.id, it.name, LatLng(point.latitude, point.longitude)) }
    }
    return RouteShape(
        line = ordered.map { LatLng(it.latitude, it.longitude) },
        stops = stops,
        boardingStop = stops.firstOrNull { it.stopId == boardingStopId }
    )
}
