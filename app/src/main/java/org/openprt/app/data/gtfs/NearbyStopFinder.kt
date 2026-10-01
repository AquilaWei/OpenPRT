package org.openprt.app.data.gtfs

import org.openprt.app.geo.BoundingBox
import org.openprt.app.geo.LatLng
import org.openprt.app.geo.haversineMeters

/** A stop and its straight-line distance from the query point. */
data class NearbyStop(val stop: StopEntity, val distanceMeters: Double)

/** Finds imported GTFS stops near a point. */
class NearbyStopFinder(private val dao: GtfsDao) {
    /**
     * Stops within [radiusMeters] of [center], nearest first (ties broken by stop ID so the
     * order is stable). Empty when no stops have been imported or none are in range.
     * Distance is straight-line, not walking-route distance.
     *
     * @throws IllegalArgumentException if [radiusMeters] is negative.
     */
    suspend fun findNearby(center: LatLng, radiusMeters: Double): List<NearbyStop> {
        val box = BoundingBox.around(center, radiusMeters)
        // The box is a pre-filter; its corners lie outside the circle, so check exact distance.
        return dao
            .getStopsInBox(box.minLatitude, box.maxLatitude, box.minLongitude, box.maxLongitude)
            .map { NearbyStop(it, haversineMeters(center, LatLng(it.latitude, it.longitude))) }
            .filter { it.distanceMeters <= radiusMeters }
            .sortedWith(compareBy({ it.distanceMeters }, { it.stop.stopId }))
    }
}
