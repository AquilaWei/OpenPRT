package org.openprt.app.walk

import java.util.concurrent.TimeUnit
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.openprt.app.geo.LatLng

class ValhallaWalkRouterTest {
    private val server = MockWebServer()
    private lateinit var router: ValhallaWalkRouter

    @Before
    fun setUp() {
        server.start()
        router = ValhallaWalkRouter(userAgent = "OpenPRT/test", baseUrl = server.url("/route"))
    }

    @After
    fun tearDown() {
        server.close()
    }

    private fun enqueueRecordedRoute() {
        // Recorded from valhalla1.openstreetmap.de on 2026-10-04: Forbes Ave by CMU to Craig St.
        val text = javaClass.getResource("/valhalla/route_cmu_to_craig.json")!!.readText()
        server.enqueue(MockResponse.Builder().body(text).build())
    }

    @Test
    fun route_recordedResponse_takesItsTimeRoundedUp() = runTest {
        enqueueRecordedRoute()

        val path = router.route(CMU, CRAIG) as WalkPath.Streets

        assertEquals(447L, path.seconds)
    }

    @Test
    fun route_recordedResponse_runsFromTheStartThroughTheStreetsToTheEnd() = runTest {
        enqueueRecordedRoute()

        val points = router.route(CMU, CRAIG).points

        // 38 decoded points, plus the exact start and end joined on.
        assertEquals(40, points.size)
        assertEquals(
            listOf(CMU, LatLng(40.444288, -79.943545), LatLng(40.444366, -79.943516)),
            points.take(3)
        )
        assertEquals(listOf(LatLng(40.444548, -79.948291), CRAIG), points.takeLast(2))
    }

    @Test
    fun route_asksForAPedestrianRouteBetweenTheEnds() = runTest {
        enqueueRecordedRoute()

        router.route(CMU, CRAIG)

        val url = server.takeRequest().url
        assertEquals("/route", url.encodedPath)
        assertEquals(
            """{"locations":[{"lat":40.4443,"lon":-79.9436},{"lat":40.4447,"lon":-79.9483}],""" +
                """"costing":"pedestrian","directions_type":"none"}""",
            url.queryParameter("json")
        )
    }

    @Test
    fun route_sendsTheUserAgent() = runTest {
        enqueueRecordedRoute()

        router.route(CMU, CRAIG)

        assertEquals("OpenPRT/test", server.takeRequest().headers["User-Agent"])
    }

    @Test
    fun route_httpError_isStraight() = runTest {
        server.enqueue(
            MockResponse.Builder()
                .code(400)
                .body("""{"error_code":171,"error":"No suitable edges near location"}""")
                .build()
        )

        assertEquals(WalkPath.Straight(CMU, CRAIG), router.route(CMU, CRAIG))
    }

    @Test
    fun route_malformedBody_isStraight() = runTest {
        server.enqueue(MockResponse.Builder().body("""{"trip":{"legs":[]}}""").build())

        assertEquals(WalkPath.Straight(CMU, CRAIG), router.route(CMU, CRAIG))
    }

    @Test
    fun route_cutOffShape_isStraight() = runTest {
        server.enqueue(
            MockResponse.Builder()
                .body("""{"trip":{"legs":[{"shape":"_wo"}],"summary":{"time":10.0}}}""")
                .build()
        )

        assertEquals(WalkPath.Straight(CMU, CRAIG), router.route(CMU, CRAIG))
    }

    @Test
    fun route_noAnswerInTime_isStraight() = runTest {
        server.enqueue(
            MockResponse.Builder().headersDelay(2, TimeUnit.SECONDS).body("{}").build()
        )
        val slowRouter = ValhallaWalkRouter(
            userAgent = "OpenPRT/test",
            httpClient = OkHttpClient.Builder().callTimeout(100, TimeUnit.MILLISECONDS).build(),
            baseUrl = server.url("/route")
        )

        assertEquals(WalkPath.Straight(CMU, CRAIG), slowRouter.route(CMU, CRAIG))
    }

    @Test
    fun defaultHttpClient_timesOutAfterFiveSeconds() {
        assertEquals(5_000, ValhallaWalkRouter.defaultHttpClient().callTimeoutMillis)
    }

    @Test
    fun route_twiceInARow_waitsASecondBeforeTheSecond() = runTest {
        enqueueRecordedRoute()
        enqueueRecordedRoute()
        val spacedRouter = ValhallaWalkRouter(
            userAgent = "OpenPRT/test",
            baseUrl = server.url("/route"),
            timeSource = testScheduler.timeSource
        )

        spacedRouter.route(CMU, CRAIG)
        spacedRouter.route(CRAIG, CMU)

        assertEquals(1_000L, testScheduler.currentTime)
    }

    @Test
    fun decodePolyline6_decodesEachPointAtSixDecimals() {
        // Google's documented example, read at Valhalla's precision.
        assertEquals(
            listOf(LatLng(3.85, -12.02), LatLng(4.07, -12.095), LatLng(4.3252, -12.6453)),
            decodePolyline6("_p~iF~ps|U_ulLnnqC_mqNvxq`@")
        )
    }

    private companion object {
        val CMU = LatLng(40.4443, -79.9436)
        val CRAIG = LatLng(40.4447, -79.9483)
    }
}
