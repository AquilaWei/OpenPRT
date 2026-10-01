package org.openprt.app.data.truetime

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Wire shapes of BusTime v3 JSON. Field names follow the API; the mappers below turn them
// into the readable models in TrueTimeModels.kt. BusTime quotes some numbers (e.g. vehicle
// lat/lon) and not others, so the client's Json is lenient and accepts both forms.

@Serializable
internal class ErrorDto(val msg: String)

@Serializable
internal class RouteDto(val rt: String, val rtnm: String, val rtclr: String)

@Serializable
internal class DirectionDto(val id: String, val name: String)

@Serializable
internal class StopDto(val stpid: String, val stpnm: String, val lat: Double, val lon: Double)

@Serializable
internal class PredictionDto(
    val tmstmp: String,
    val typ: String,
    val stpid: String,
    val stpnm: String,
    val vid: String,
    val dstp: Int,
    val rt: String,
    val rtdir: String,
    val des: String,
    val prdtm: String,
    val dly: Boolean = false
)

@Serializable
internal class VehicleDto(
    val vid: String,
    val tmstmp: String,
    val lat: Double,
    val lon: Double,
    val hdg: Int,
    val pid: Int,
    val rt: String,
    val des: String,
    val pdist: Int,
    val dly: Boolean = false
)

@Serializable
internal class PatternDto(
    val pid: Int,
    @SerialName("ln") val length: Double,
    val rtdir: String,
    val pt: List<PatternPointDto>
)

@Serializable
internal class PatternPointDto(
    val seq: Int,
    val lat: Double,
    val lon: Double,
    val typ: String,
    val stpid: String? = null,
    val stpnm: String? = null,
    val pdist: Double? = null
)

/** TrueTime reports wall-clock times in the agency's zone without an offset. */
private val pittsburghZone: ZoneId = ZoneId.of("America/New_York")

/** `gettime` includes seconds, the other endpoints stop at minutes. */
private val trueTimeFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd HH:mm[:ss]")

/** @throws java.time.format.DateTimeParseException when [value] is not a TrueTime timestamp. */
internal fun parseTrueTime(value: String): Instant =
    LocalDateTime.parse(value, trueTimeFormat).atZone(pittsburghZone).toInstant()

internal fun RouteDto.toModel() = Route(id = rt, name = rtnm, color = rtclr)

internal fun DirectionDto.toModel() = Direction(id = id, name = name)

internal fun StopDto.toModel() = Stop(id = stpid, name = stpnm, latitude = lat, longitude = lon)

/** @throws IllegalArgumentException when [PredictionDto.typ] is neither "A" nor "D". */
internal fun PredictionDto.toModel() = Prediction(
    generatedAt = parseTrueTime(tmstmp),
    type =
        when (typ) {
            "A" -> PredictionType.ARRIVAL
            "D" -> PredictionType.DEPARTURE
            else -> throw IllegalArgumentException("Unknown prediction type: $typ")
        },
    stopId = stpid,
    stopName = stpnm,
    vehicleId = vid,
    distanceToStopFeet = dstp,
    route = rt,
    routeDirection = rtdir,
    destination = des,
    predictedTime = parseTrueTime(prdtm),
    delayed = dly
)

internal fun VehicleDto.toModel() = Vehicle(
    id = vid,
    reportedAt = parseTrueTime(tmstmp),
    latitude = lat,
    longitude = lon,
    headingDegrees = hdg,
    patternId = pid,
    route = rt,
    destination = des,
    distanceAlongPatternFeet = pdist,
    delayed = dly
)

internal fun PatternDto.toModel() = Pattern(
    id = pid,
    lengthFeet = length,
    routeDirection = rtdir,
    points = pt.map { it.toModel() }
)

/** @throws IllegalArgumentException when a stop point ("S") lacks its stop fields. */
private fun PatternPointDto.toModel(): PatternPoint {
    val stop =
        if (typ == "S") {
            PatternStop(
                id = requireNotNull(stpid) { "Stop point $seq has no stpid" },
                name = requireNotNull(stpnm) { "Stop point $seq has no stpnm" },
                distanceAlongPatternFeet = requireNotNull(pdist) { "Stop point $seq has no pdist" }
            )
        } else {
            null
        }
    return PatternPoint(sequence = seq, latitude = lat, longitude = lon, stop = stop)
}
