package org.openprt.app.data.truetime

import java.time.Instant
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

class TrueTimeClientTest {
    private val server = MockWebServer()
    private lateinit var client: TrueTimeClient

    @Before
    fun setUp() {
        server.start()
        client = TrueTimeClient(apiKey = "test-key", baseUrl = server.url("/bustime/api/v3/"))
    }

    @After
    fun tearDown() {
        server.close()
    }

    private fun enqueueFixture(name: String) {
        val text = javaClass.getResource("/truetime/$name")!!.readText()
        server.enqueue(MockResponse.Builder().body(text).build())
    }

    private fun <T> TrueTimeResult<T>.valueOrFail(): T = (this as? TrueTimeResult.Success)?.value
        ?: throw AssertionError("Expected success, got $this")

    @Test
    fun getRoutes_withFixture_parsesRoutes() = runTest {
        enqueueFixture("getroutes.json")

        val routes = client.getRoutes().valueOrFail()

        assertEquals(
            listOf(
                Route(id = "61C", name = "McKeesport-Homestead", color = "#cc00cc"),
                Route(id = "P1", name = "East Busway-All Stops", color = "#3366ff")
            ),
            routes
        )
    }

    @Test
    fun getRoutes_sendsKeyAndJsonFormatToEndpoint() = runTest {
        enqueueFixture("getroutes.json")

        client.getRoutes()

        val url = server.takeRequest().url
        assertEquals("/bustime/api/v3/getroutes", url.encodedPath)
        assertEquals("test-key", url.queryParameter("key"))
        assertEquals("json", url.queryParameter("format"))
    }

    @Test
    fun getDirections_withFixture_parsesDirections() = runTest {
        enqueueFixture("getdirections.json")

        val directions = client.getDirections("61C").valueOrFail()

        assertEquals(
            listOf(
                Direction(id = "INBOUND", name = "INBOUND"),
                Direction(id = "OUTBOUND", name = "OUTBOUND")
            ),
            directions
        )
    }

    @Test
    fun getDirections_sendsRouteParameter() = runTest {
        enqueueFixture("getdirections.json")

        client.getDirections("61C")

        assertEquals("61C", server.takeRequest().url.queryParameter("rt"))
    }

    @Test
    fun getStops_withFixture_parsesStops() = runTest {
        enqueueFixture("getstops.json")

        val stops = client.getStops("61C", "OUTBOUND").valueOrFail()

        assertEquals(
            listOf(
                Stop(
                    id = "7117",
                    name = "Forbes Ave at Morewood Ave (Carnegie Mellon)",
                    latitude = 40.444567,
                    longitude = -79.942862
                ),
                Stop(
                    id = "20690",
                    name = "Fifth Ave at Wood St",
                    latitude = 40.440878,
                    longitude = -79.999141
                )
            ),
            stops
        )
    }

    @Test
    fun getStops_sendsRouteAndDirectionParameters() = runTest {
        enqueueFixture("getstops.json")

        client.getStops("61C", "OUTBOUND")

        val url = server.takeRequest().url
        assertEquals("61C", url.queryParameter("rt"))
        assertEquals("OUTBOUND", url.queryParameter("dir"))
    }

    @Test
    fun getPredictions_withFixture_parsesPredictionsInPittsburghTime() = runTest {
        enqueueFixture("getpredictions.json")

        val predictions = client.getPredictions(listOf("7117")).valueOrFail()

        assertEquals(
            listOf(
                Prediction(
                    generatedAt = Instant.parse("2026-10-01T12:40:00Z"),
                    type = PredictionType.ARRIVAL,
                    stopId = "7117",
                    stopName = "Forbes Ave at Morewood Ave (Carnegie Mellon)",
                    vehicleId = "5601",
                    distanceToStopFeet = 4210,
                    route = "61C",
                    routeDirection = "OUTBOUND",
                    destination = "McKeesport",
                    predictedTime = Instant.parse("2026-10-01T12:52:00Z"),
                    delayed = false
                ),
                Prediction(
                    generatedAt = Instant.parse("2026-10-01T12:40:00Z"),
                    type = PredictionType.DEPARTURE,
                    stopId = "7117",
                    stopName = "Forbes Ave at Morewood Ave (Carnegie Mellon)",
                    vehicleId = "3210",
                    distanceToStopFeet = 9050,
                    route = "P1",
                    routeDirection = "INBOUND",
                    destination = "Downtown",
                    predictedTime = Instant.parse("2026-10-01T13:05:00Z"),
                    delayed = true
                )
            ),
            predictions
        )
    }

    @Test
    fun getPredictions_withSeveralStops_sendsCommaSeparatedStopIds() = runTest {
        enqueueFixture("getpredictions.json")

        client.getPredictions(listOf("7117", "20690"))

        assertEquals("7117,20690", server.takeRequest().url.queryParameter("stpid"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun getPredictions_withMoreThanTenStops_throws() = runTest {
        client.getPredictions(listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11"))
    }

    @Test
    fun getPredictions_whenSomeStopsHaveNoData_returnsPredictionsForTheOthers() = runTest {
        enqueueFixture("partial_error.json")

        val predictions = client.getPredictions(listOf("7117", "99999")).valueOrFail()

        assertEquals(listOf("7117"), predictions.map { it.stopId })
    }

    @Test
    fun getVehicles_withQuotedNumbers_parsesVehicle() = runTest {
        enqueueFixture("getvehicles.json")

        val vehicles = client.getVehicles(listOf("5601")).valueOrFail()

        assertEquals(
            listOf(
                Vehicle(
                    id = "5601",
                    reportedAt = Instant.parse("2026-10-01T12:40:15Z"),
                    latitude = 40.43851,
                    longitude = -79.92284,
                    headingDegrees = 145,
                    patternId = 4512,
                    route = "61C",
                    destination = "McKeesport",
                    distanceAlongPatternFeet = 12345,
                    delayed = false
                )
            ),
            vehicles
        )
    }

    @Test
    fun getVehicles_sendsVehicleIds() = runTest {
        enqueueFixture("getvehicles.json")

        client.getVehicles(listOf("5601", "3210"))

        assertEquals("5601,3210", server.takeRequest().url.queryParameter("vid"))
    }

    @Test
    fun getPatterns_withFixture_parsesStopsAndWaypoints() = runTest {
        enqueueFixture("getpatterns.json")

        val patterns = client.getPatterns(4512).valueOrFail()

        assertEquals(
            listOf(
                Pattern(
                    id = 4512,
                    lengthFeet = 85432.0,
                    routeDirection = "OUTBOUND",
                    points =
                        listOf(
                            PatternPoint(
                                1,
                                40.440878,
                                -79.999141,
                                PatternStop("20690", "Fifth Ave at Wood St", 0.0)
                            ),
                            PatternPoint(2, 40.44121, -79.99502, null),
                            PatternPoint(
                                3,
                                40.444567,
                                -79.942862,
                                PatternStop(
                                    "7117",
                                    "Forbes Ave at Morewood Ave (Carnegie Mellon)",
                                    18620.0
                                )
                            )
                        )
                )
            ),
            patterns
        )
    }

    @Test
    fun getPatterns_sendsPatternId() = runTest {
        enqueueFixture("getpatterns.json")

        client.getPatterns(4512)

        assertEquals("4512", server.takeRequest().url.queryParameter("pid"))
    }

    @Test
    fun getPredictions_whenApiReportsErrorWithoutData_returnsApiError() = runTest {
        enqueueFixture("error_no_data.json")

        val result = client.getPredictions(listOf("99999"))

        assertEquals(
            TrueTimeResult.Failure(TrueTimeError.Api(listOf("No data found for parameter"))),
            result
        )
    }

    @Test
    fun getRoutes_whenServerReturns500_returnsHttpError() = runTest {
        server.enqueue(MockResponse.Builder().code(500).body("oops").build())

        val result = client.getRoutes()

        assertEquals(TrueTimeResult.Failure(TrueTimeError.Http(500)), result)
    }

    @Test
    fun getRoutes_whenServerIsTooSlow_returnsTimeout() = runTest {
        val impatientClient =
            TrueTimeClient(
                apiKey = "test-key",
                httpClient = OkHttpClient.Builder().readTimeout(
                    100,
                    TimeUnit.MILLISECONDS
                ).build(),
                baseUrl = server.url("/bustime/api/v3/")
            )
        server.enqueue(
            MockResponse.Builder().headersDelay(2, TimeUnit.SECONDS).body("{}").build()
        )

        val result = impatientClient.getRoutes()

        assertEquals(TrueTimeResult.Failure(TrueTimeError.Timeout), result)
    }

    @Test
    fun getRoutes_whenBodyIsNotBusTimeJson_returnsMalformedResponse() = runTest {
        server.enqueue(MockResponse.Builder().body("<html>maintenance</html>").build())

        val result = client.getRoutes()

        assertTrue(
            result is TrueTimeResult.Failure && result.error is TrueTimeError.MalformedResponse
        )
    }

    @Test
    fun getRoutes_withoutApiKey_returnsMissingApiKey() = runTest {
        val keylessClient =
            TrueTimeClient(apiKey = "", baseUrl = server.url("/bustime/api/v3/"))

        val result = keylessClient.getRoutes()

        assertEquals(TrueTimeResult.Failure(TrueTimeError.MissingApiKey), result)
    }

    @Test
    fun getRoutes_withoutApiKey_sendsNoRequest() = runTest {
        val keylessClient =
            TrueTimeClient(apiKey = "", baseUrl = server.url("/bustime/api/v3/"))

        keylessClient.getRoutes()

        assertEquals(0, server.requestCount)
    }
}
