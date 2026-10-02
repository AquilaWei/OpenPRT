package org.openprt.app.departures

/**
 * The departures of one [route], one row per direction, so both directions of a route are seen
 * together on one card.
 */
data class DepartureGroup(val route: String, val rows: List<DepartureItem>)

/**
 * Groups [departures] by route. Groups keep the order of their first departure, so with a list
 * ranked earliest first the route leaving soonest comes first; rows within a group are ordered
 * by direction, so each direction stays in the same place when the times change.
 */
fun groupByRoute(departures: List<DepartureItem>): List<DepartureGroup> = departures
    .groupBy { it.route }
    .map { (route, rows) -> DepartureGroup(route, rows.sortedBy { it.direction }) }

/**
 * The departure on the same route as [departure] going the other way, among [departures];
 * null when no bus that way can be caught nearby.
 */
fun oppositeDirectionOf(departure: DepartureItem, departures: List<DepartureItem>): DepartureItem? =
    departures.firstOrNull { it.route == departure.route && it.direction != departure.direction }

private val OPPOSITES = mapOf(
    "INBOUND" to "OUTBOUND",
    "NORTHBOUND" to "SOUTHBOUND",
    "EASTBOUND" to "WESTBOUND"
).let { pairs -> pairs + pairs.entries.associate { (a, b) -> b to a } }

/**
 * The name of the other direction of travel for a TrueTime direction such as `INBOUND`, or null
 * for a name this app does not know the opposite of.
 */
fun oppositeDirectionName(direction: String): String? = OPPOSITES[direction.uppercase()]

/** A TrueTime direction such as `INBOUND` as people read it: `Inbound`. */
fun directionLabel(direction: String): String =
    direction.lowercase().replaceFirstChar { it.uppercase() }
