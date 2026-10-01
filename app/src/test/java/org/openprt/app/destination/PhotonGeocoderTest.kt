package org.openprt.app.destination

import java.util.concurrent.TimeUnit
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.openprt.app.geo.LatLng
import org.openprt.app.geo.PITTSBURGH_AREA

class PhotonGeocoderTest {
    private val server = MockWebServer()
    private lateinit var geocoder: PhotonGeocoder

    @Before
    fun setUp() {
        server.start()
        geocoder = PhotonGeocoder(userAgent = "OpenPRT/test", baseUrl = server.url("/api/"))
    }

    @After
    fun tearDown() {
        server.close()
    }

    private fun enqueueFixture(name: String) {
        val text = javaClass.getResource("/photon/$name")!!.readText()
        server.enqueue(MockResponse.Builder().body(text).build())
    }

    @Test
    fun search_withRecordedResponse_parsesPlaces() = runTest {
        // Recorded from photon.komoot.io on 2026-10-01 for "carnegie mellon".
        enqueueFixture("search_carnegie_mellon.json")

        val result = geocoder.search("carnegie mellon", PITTSBURGH_AREA)

        assertEquals(
            GeocodeResult.Success(
                listOf(
                    Place(
                        "Carnegie Mellon University",
                        "Forbes Bike lane Westbound, North Oakland, Pittsburgh",
                        LatLng(40.4439193, -79.9428267)
                    ),
                    Place(
                        "Carnegie Mellon Café",
                        "5000 Forbes Avenue, Squirrel Hill North, Pittsburgh",
                        LatLng(40.4424191, -79.9397388)
                    ),
                    Place(
                        "Carnegie Mellon University Bookstore",
                        "5032 Forbes Avenue, Squirrel Hill North, Pittsburgh",
                        LatLng(40.4437584, -79.9423308)
                    )
                )
            ),
            result
        )
    }

    @Test
    fun search_sendsQueryAndAreaAsPhotonBbox() = runTest {
        enqueueFixture("search_5000_forbes.json")

        geocoder.search("5000 forbes ave", PITTSBURGH_AREA)

        val url = server.takeRequest().url
        assertEquals("/api/", url.encodedPath)
        assertEquals("5000 forbes ave", url.queryParameter("q"))
        assertEquals("-80.37,40.19,-79.68,40.68", url.queryParameter("bbox"))
    }

    @Test
    fun search_sendsUserAgent() = runTest {
        enqueueFixture("search_5000_forbes.json")

        geocoder.search("5000 forbes ave", PITTSBURGH_AREA)

        assertEquals("OpenPRT/test", server.takeRequest().headers["User-Agent"])
    }

    @Test
    fun search_featureWithoutName_usesStreetAddressAsName() = runTest {
        server.enqueue(
            MockResponse.Builder().body(
                """{"type":"FeatureCollection","features":[{"type":"Feature",
                "geometry":{"type":"Point","coordinates":[-79.95,40.44]},
                "properties":{"housenumber":"4400","street":"Forbes Avenue","city":"Pittsburgh"}}]}"""
            ).build()
        )

        val result = geocoder.search("4400 forbes", PITTSBURGH_AREA)

        assertEquals(
            GeocodeResult.Success(
                listOf(Place("4400 Forbes Avenue", "Pittsburgh", LatLng(40.44, -79.95)))
            ),
            result
        )
    }

    @Test
    fun search_featureWithoutNameOrStreet_isSkipped() = runTest {
        server.enqueue(
            MockResponse.Builder().body(
                """{"type":"FeatureCollection","features":[{"type":"Feature",
                "geometry":{"type":"Point","coordinates":[-79.95,40.44]},
                "properties":{"city":"Pittsburgh"}}]}"""
            ).build()
        )

        val result = geocoder.search("pittsburgh", PITTSBURGH_AREA)

        assertEquals(GeocodeResult.Success(emptyList()), result)
    }

    @Test
    fun search_whenServerReturns500_returnsHttpError() = runTest {
        server.enqueue(MockResponse.Builder().code(500).body("oops").build())

        val result = geocoder.search("cmu", PITTSBURGH_AREA)

        assertEquals(GeocodeResult.Failure(GeocodeError.Http(500)), result)
    }

    @Test
    fun search_whenServerIsTooSlow_returnsTimeout() = runTest {
        val impatient = PhotonGeocoder(
            userAgent = "OpenPRT/test",
            httpClient = OkHttpClient.Builder().readTimeout(100, TimeUnit.MILLISECONDS).build(),
            baseUrl = server.url("/api/")
        )
        server.enqueue(
            MockResponse.Builder().headersDelay(2, TimeUnit.SECONDS).body("{}").build()
        )

        val result = impatient.search("cmu", PITTSBURGH_AREA)

        assertEquals(GeocodeResult.Failure(GeocodeError.Timeout), result)
    }

    @Test
    fun search_whenServerIsUnreachable_returnsNetworkError() = runTest {
        val url = server.url("/api/")
        server.close()
        val unreachable = PhotonGeocoder(userAgent = "OpenPRT/test", baseUrl = url)

        val result = unreachable.search("cmu", PITTSBURGH_AREA)

        assertTrue(result is GeocodeResult.Failure && result.error is GeocodeError.Network)
    }

    @Test
    fun search_whenBodyIsNotGeoJson_returnsMalformedResponse() = runTest {
        server.enqueue(MockResponse.Builder().body("<html>maintenance</html>").build())

        val result = geocoder.search("cmu", PITTSBURGH_AREA)

        assertTrue(
            result is GeocodeResult.Failure && result.error is GeocodeError.MalformedResponse
        )
    }
}
