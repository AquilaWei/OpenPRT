package org.openprt.app.details

import org.openprt.app.data.truetime.Pattern
import org.openprt.app.data.truetime.PatternPoint
import org.openprt.app.geo.LatLng
import org.openprt.app.map.StopMarker

/**
 * What the map draws for one bus's route: [line] through every pattern point in order, the
 * [stops] along it, and the user's [boardingStop], which is null when the pattern does not
 * serve that stop (e.g. the stop ID schemes of GTFS and TrueTime turn out not to match).
 *
 * [boardingDistanceFeet] is how far along the pattern the boarding stop lies, comparable with
 * a vehicle's distance along the pattern to tell whether the bus has passed it.
 */
data class RouteShape(
    val line: List<LatLng>,
    val stops: List<StopMarker>,
    val boardingStop: StopMarker?,
    val boardingDistanceFeet: Double?
)

/**
 * Turns this pattern into a [RouteShape], ordered by point sequence. Stops are part of the line
 * too, since BusTime places them on the road. A loop route that visits [boardingStopId] twice
 * boards at its first visit.
 */
fun Pattern.toRouteShape(boardingStopId: String): RouteShape {
    val ordered = points.sortedBy { it.sequence }
    val boardingPoint = ordered.firstOrNull { it.stop?.id == boardingStopId }
    return RouteShape(
        line = ordered.map { LatLng(it.latitude, it.longitude) },
        stops = ordered.mapNotNull { it.toStopMarker() },
        boardingStop = boardingPoint?.toStopMarker(),
        boardingDistanceFeet = boardingPoint?.stop?.distanceAlongPatternFeet
    )
}

private fun PatternPoint.toStopMarker(): StopMarker? =
    stop?.let { StopMarker(it.id, it.name, LatLng(latitude, longitude)) }
