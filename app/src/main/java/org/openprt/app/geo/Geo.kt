package org.openprt.app.geo

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Mean Earth radius (IUGG). The sphere model is within about 0.5% of WGS84 at city scale. */
const val EARTH_RADIUS_METERS = 6_371_008.8

/** A WGS84 coordinate in degrees. */
data class LatLng(val latitude: Double, val longitude: Double)

/** Straight-line (great-circle) distance between two points, in meters. */
fun haversineMeters(from: LatLng, to: LatLng): Double {
    val lat1 = Math.toRadians(from.latitude)
    val lat2 = Math.toRadians(to.latitude)
    val halfDLat = (lat2 - lat1) / 2
    val halfDLon = Math.toRadians(to.longitude - from.longitude) / 2
    val a = sin(halfDLat) * sin(halfDLat) + cos(lat1) * cos(lat2) * sin(halfDLon) * sin(halfDLon)
    return 2 * EARTH_RADIUS_METERS * asin(sqrt(a.coerceAtMost(1.0)))
}

/** Latitude / longitude ranges, in degrees, used as a cheap index-friendly pre-filter. */
data class BoundingBox(
    val minLatitude: Double,
    val maxLatitude: Double,
    val minLongitude: Double,
    val maxLongitude: Double
) {
    companion object {
        /**
         * The smallest box that contains every point within [radiusMeters] of [center] on the
         * sphere, so filtering by it never drops a point the haversine check would keep.
         *
         * Does not handle boxes that cross a pole or the antimeridian; the service area is
         * Pittsburgh, so those never occur. [radiusMeters] must be non-negative.
         */
        fun around(center: LatLng, radiusMeters: Double): BoundingBox {
            require(radiusMeters >= 0) { "radiusMeters must be non-negative: $radiusMeters" }
            val angular = radiusMeters / EARTH_RADIUS_METERS
            val dLat = Math.toDegrees(angular)
            // Exact longitude half-width of a spherical cap; wider than angular / cos(lat).
            val dLon = Math.toDegrees(asin(sin(angular) / cos(Math.toRadians(center.latitude))))
            return BoundingBox(
                minLatitude = center.latitude - dLat,
                maxLatitude = center.latitude + dLat,
                minLongitude = center.longitude - dLon,
                maxLongitude = center.longitude + dLon
            )
        }
    }
}
