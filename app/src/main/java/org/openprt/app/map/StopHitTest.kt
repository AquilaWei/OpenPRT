package org.openprt.app.map

import org.openprt.app.geo.LatLng

/** A point on the screen in pixels, as the map projects it. */
data class ScreenPoint(val x: Float, val y: Float)

/**
 * The stop a tap at [tap] was meant for: the one among [stops] nearest to it on screen, if it
 * lies within [radiusPx]; null otherwise, so the tap falls through to the map. [toScreen]
 * projects a stop's position onto the screen. Equally near stops go to the first in [stops].
 */
fun stopAt(
    tap: ScreenPoint,
    stops: List<StopMarker>,
    radiusPx: Float,
    toScreen: (LatLng) -> ScreenPoint
): StopMarker? {
    val radiusSquared = radiusPx * radiusPx
    return stops
        .map { stop ->
            val point = toScreen(stop.position)
            val dx = point.x - tap.x
            val dy = point.y - tap.y
            stop to dx * dx + dy * dy
        }
        .filter { (_, distanceSquared) -> distanceSquared <= radiusSquared }
        .minByOrNull { (_, distanceSquared) -> distanceSquared }
        ?.first
}
