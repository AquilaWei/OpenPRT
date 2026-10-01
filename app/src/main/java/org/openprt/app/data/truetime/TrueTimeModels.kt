package org.openprt.app.data.truetime

import java.time.Instant

/** A PRT route as listed by `getroutes`. [color] is the API's hex string, e.g. `#cc00cc`. */
data class Route(val id: String, val name: String, val color: String)

/** A travel direction of a route; [id] is what `getstops` expects as `dir`. */
data class Direction(val id: String, val name: String)

data class Stop(val id: String, val name: String, val latitude: Double, val longitude: Double)

enum class PredictionType {
    ARRIVAL,
    DEPARTURE
}

/** A predicted arrival or departure of one vehicle at one stop. */
data class Prediction(
    val generatedAt: Instant,
    val type: PredictionType,
    val stopId: String,
    val stopName: String,
    val vehicleId: String,
    val distanceToStopFeet: Int,
    val route: String,
    val routeDirection: String,
    val destination: String,
    val predictedTime: Instant,
    val delayed: Boolean
)

/** The last reported position of a vehicle on pattern [patternId]. */
data class Vehicle(
    val id: String,
    val reportedAt: Instant,
    val latitude: Double,
    val longitude: Double,
    val headingDegrees: Int,
    val patternId: Int,
    val route: String,
    val destination: String,
    val distanceAlongPatternFeet: Int,
    val delayed: Boolean
)

/** The ordered shape of one route variant, mixing stops and plain waypoints. */
data class Pattern(
    val id: Int,
    val lengthFeet: Double,
    val routeDirection: String,
    val points: List<PatternPoint>
)

/** A point of a [Pattern]; [stop] is null for waypoints that only shape the line. */
data class PatternPoint(
    val sequence: Int,
    val latitude: Double,
    val longitude: Double,
    val stop: PatternStop?
)

data class PatternStop(val id: String, val name: String, val distanceAlongPatternFeet: Double)
