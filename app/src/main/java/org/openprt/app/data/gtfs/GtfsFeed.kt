package org.openprt.app.data.gtfs

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

// Row parsers for the GTFS tables the app stores. Each throws GtfsFormatException for a row that
// breaks the spec, so the importer can reject the whole feed.

/**
 * A row of stops.txt, or `null` for generic nodes (location_type 3) and boarding areas (4), which
 * have no required position and where nobody boards.
 */
fun GtfsRow.toStopEntity(): StopEntity? {
    val locationType = optionalInt("location_type") ?: 0
    if (locationType > 2) return null
    return StopEntity(
        stopId = required("stop_id"),
        code = this["stop_code"],
        name = required("stop_name"),
        latitude = requiredDouble("stop_lat"),
        longitude = requiredDouble("stop_lon"),
        locationType = locationType
    )
}

fun GtfsRow.toRouteEntity(): RouteEntity = RouteEntity(
    routeId = required("route_id"),
    shortName = this["route_short_name"],
    longName = this["route_long_name"],
    type = optionalInt("route_type")
        ?: throw GtfsFormatException("Row $rowNumber: missing route_type"),
    color = this["route_color"]
)

fun GtfsRow.toTripEntity(): TripEntity = TripEntity(
    tripId = required("trip_id"),
    routeId = required("route_id"),
    serviceId = required("service_id"),
    headsign = this["trip_headsign"],
    directionId = optionalInt("direction_id")
)

/**
 * A row of stop_times.txt. Requires both times on every row: GTFS lets feeds leave them out
 * between timepoints, but PRT's feed fills them all in, so the app does not interpolate.
 */
fun GtfsRow.toStopTimeEntity(): StopTimeEntity = StopTimeEntity(
    tripId = required("trip_id"),
    stopSequence = optionalInt("stop_sequence")
        ?: throw GtfsFormatException("Row $rowNumber: missing stop_sequence"),
    stopId = required("stop_id"),
    arrivalSeconds = parseGtfsTime(required("arrival_time"), rowNumber),
    departureSeconds = parseGtfsTime(required("departure_time"), rowNumber),
    // 1 means "no pickup / drop off"; 2 and 3 (call the agency / ask the driver) still allow it.
    pickupAllowed = optionalInt("pickup_type") != 1,
    dropOffAllowed = optionalInt("drop_off_type") != 1
)

fun GtfsRow.toServiceCalendarEntity(): ServiceCalendarEntity = ServiceCalendarEntity(
    serviceId = required("service_id"),
    monday = requiredFlag("monday"),
    tuesday = requiredFlag("tuesday"),
    wednesday = requiredFlag("wednesday"),
    thursday = requiredFlag("thursday"),
    friday = requiredFlag("friday"),
    saturday = requiredFlag("saturday"),
    sunday = requiredFlag("sunday"),
    startDate = parseGtfsDate(required("start_date"), rowNumber),
    endDate = parseGtfsDate(required("end_date"), rowNumber)
)

fun GtfsRow.toCalendarDateEntity(): CalendarDateEntity = CalendarDateEntity(
    serviceId = required("service_id"),
    date = parseGtfsDate(required("date"), rowNumber),
    added = when (required("exception_type")) {
        "1" -> true
        "2" -> false
        else -> throw GtfsFormatException("Row $rowNumber: exception_type must be 1 or 2")
    }
)

/**
 * Seconds after the start of the service day for a GTFS time `H:MM:SS` or `HH:MM:SS`. Hours can
 * be 24 or more for trips that run past midnight, e.g. `25:10:00` is 1:10 the next morning.
 *
 * @throws GtfsFormatException if [text] is not in that form.
 */
fun parseGtfsTime(text: String, rowNumber: Int): Int {
    val parts = text.split(':')
    val hours = parts.getOrNull(0)?.toIntOrNull()
    val minutes = parts.getOrNull(1)?.takeIf { it.length == 2 }?.toIntOrNull()
    val seconds = parts.getOrNull(2)?.takeIf { it.length == 2 }?.toIntOrNull()
    if (parts.size != 3 || hours == null || hours < 0 || minutes == null || minutes > 59 ||
        seconds == null || seconds > 59
    ) {
        throw GtfsFormatException("Row $rowNumber: \"$text\" is not a GTFS time")
    }
    return hours * 3600 + minutes * 60 + seconds
}

/** @throws GtfsFormatException if [text] is not a `YYYYMMDD` date. */
fun parseGtfsDate(text: String, rowNumber: Int): LocalDate = try {
    LocalDate.parse(text, DateTimeFormatter.BASIC_ISO_DATE)
} catch (e: DateTimeParseException) {
    throw GtfsFormatException("Row $rowNumber: \"$text\" is not a GTFS date")
}

private fun GtfsRow.requiredFlag(column: String): Boolean = when (required(column)) {
    "1" -> true
    "0" -> false
    else -> throw GtfsFormatException("Row $rowNumber: $column must be 0 or 1")
}
