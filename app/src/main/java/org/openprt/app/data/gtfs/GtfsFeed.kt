package org.openprt.app.data.gtfs

import java.io.InputStream
import java.util.zip.ZipInputStream

/** The parts of a GTFS feed the app stores; more tables are added as features need them. */
data class GtfsFeed(val stops: List<GtfsStop>, val routes: List<GtfsRoute>)

/**
 * A row of stops.txt. [code] is the rider-facing number printed on the sign, which may differ
 * from [id]. [locationType] is 0 for a stop or platform and 1 for a station.
 */
data class GtfsStop(
    val id: String,
    val code: String?,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val locationType: Int
)

/** A row of routes.txt. [type] is the GTFS route_type, e.g. 3 for bus; [color] is hex without `#`. */
data class GtfsRoute(
    val id: String,
    val shortName: String?,
    val longName: String?,
    val type: Int,
    val color: String?
)

/**
 * Reads stops.txt and routes.txt from a GTFS zip, skipping every other entry.
 *
 * Does not close [zip].
 *
 * @throws GtfsFormatException if either file is missing or has an invalid row.
 * @throws java.io.IOException if [zip] cannot be read or is not a zip archive.
 */
fun readGtfsFeed(zip: InputStream): GtfsFeed {
    var stops: List<GtfsStop>? = null
    var routes: List<GtfsRoute>? = null
    // Not closed: that would close the caller's stream.
    val entries = ZipInputStream(zip)
    while (true) {
        val entry = entries.nextEntry ?: break
        // Each entry reads until the zip stream reports the end of that entry.
        val reader = entries.reader(Charsets.UTF_8)
        when (entry.name) {
            "stops.txt" -> stops = readGtfsTable(reader).mapNotNull(::toStop).toList()
            "routes.txt" -> routes = readGtfsTable(reader).map(::toRoute).toList()
        }
    }
    return GtfsFeed(
        stops = stops ?: throw GtfsFormatException("stops.txt missing"),
        routes = routes ?: throw GtfsFormatException("routes.txt missing")
    )
}

/** Generic nodes (3) and boarding areas (4) have no required position and nobody boards there. */
private fun toStop(row: GtfsRow): GtfsStop? {
    val locationType = row.optionalInt("location_type") ?: 0
    if (locationType > 2) return null
    return GtfsStop(
        id = row.required("stop_id"),
        code = row["stop_code"],
        name = row.required("stop_name"),
        latitude = row.requiredDouble("stop_lat"),
        longitude = row.requiredDouble("stop_lon"),
        locationType = locationType
    )
}

private fun toRoute(row: GtfsRow): GtfsRoute = GtfsRoute(
    id = row.required("route_id"),
    shortName = row["route_short_name"],
    longName = row["route_long_name"],
    type = row.optionalInt("route_type")
        ?: throw GtfsFormatException("Row ${row.rowNumber}: missing route_type"),
    color = row["route_color"]
)
