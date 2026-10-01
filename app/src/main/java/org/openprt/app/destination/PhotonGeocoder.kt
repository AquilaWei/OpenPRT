package org.openprt.app.destination

import java.io.IOException
import java.io.InterruptedIOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.coroutines.executeAsync
import org.openprt.app.geo.BoundingBox
import org.openprt.app.geo.LatLng

/**
 * Place search backed by Photon (komoot's OSM geocoder): free, no API key, and built for
 * search-as-you-type, which Nominatim's usage policy forbids. Callers must still debounce input
 * to stay within Photon's fair-use limits.
 *
 * HTTP, network and parsing failures come back as [GeocodeResult.Failure], never exceptions.
 */
class PhotonGeocoder(
    /** Sent with every request so the service operator can tell where traffic comes from. */
    private val userAgent: String,
    private val httpClient: OkHttpClient = defaultHttpClient(),
    private val baseUrl: HttpUrl = DEFAULT_BASE_URL.toHttpUrl(),
    // Response bodies are read with blocking I/O.
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : Geocoder {
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun search(query: String, bounds: BoundingBox): GeocodeResult {
        val url = baseUrl
            .newBuilder()
            .addQueryParameter("q", query)
            .addQueryParameter("limit", MAX_RESULTS.toString())
            .addQueryParameter("lang", "en")
            // Photon's bbox order: min lon, min lat, max lon, max lat.
            .addQueryParameter(
                "bbox",
                with(bounds) { "$minLongitude,$minLatitude,$maxLongitude,$maxLatitude" }
            )
            .build()
        val request = Request.Builder().url(url).header("User-Agent", userAgent).build()

        return try {
            withContext(ioDispatcher) {
                httpClient.newCall(request).executeAsync().use { response ->
                    if (response.isSuccessful) {
                        decode(response.body.string())
                    } else {
                        GeocodeResult.Failure(GeocodeError.Http(response.code))
                    }
                }
            }
        } catch (e: InterruptedIOException) {
            // OkHttp signals both socket and call timeouts with InterruptedIOException.
            GeocodeResult.Failure(GeocodeError.Timeout)
        } catch (e: IOException) {
            GeocodeResult.Failure(GeocodeError.Network(e))
        }
    }

    private fun decode(text: String): GeocodeResult = try {
        val features = json.decodeFromString(FeatureCollectionDto.serializer(), text).features
        GeocodeResult.Success(features.mapNotNull { it.toPlace() })
    } catch (e: SerializationException) {
        GeocodeResult.Failure(GeocodeError.MalformedResponse(e))
    } catch (e: IllegalArgumentException) {
        // Also covers JSON that is not an object at all.
        GeocodeResult.Failure(GeocodeError.MalformedResponse(e))
    }

    companion object {
        const val DEFAULT_BASE_URL = "https://photon.komoot.io/api/"

        /** Enough to scroll through without making each keystroke's response large. */
        const val MAX_RESULTS = 10

        fun defaultHttpClient(): OkHttpClient = OkHttpClient
            .Builder()
            .callTimeout(10, TimeUnit.SECONDS)
            .build()
    }
}

@Serializable
private class FeatureCollectionDto(val features: List<FeatureDto>)

@Serializable
private class FeatureDto(val geometry: GeometryDto, val properties: PropertiesDto)

/** Photon only returns points; [coordinates] is longitude, latitude. */
@Serializable
private class GeometryDto(val coordinates: List<Double>)

@Serializable
private class PropertiesDto(
    val name: String? = null,
    @SerialName("housenumber") val houseNumber: String? = null,
    val street: String? = null,
    val locality: String? = null,
    val city: String? = null
)

/** Null for features with nothing to call them by, which the user could not recognize. */
private fun FeatureDto.toPlace(): Place? {
    require(geometry.coordinates.size >= 2) { "point needs 2 coordinates: ${geometry.coordinates}" }
    val address = listOfNotNull(properties.houseNumber, properties.street)
        .joinToString(" ")
        .ifEmpty { null }
    val name = properties.name ?: address ?: return null
    val description = listOfNotNull(
        address.takeIf { properties.name != null },
        properties.locality,
        properties.city
    ).distinct().joinToString(", ")
    return Place(name, description, LatLng(geometry.coordinates[1], geometry.coordinates[0]))
}
