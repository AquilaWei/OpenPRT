package org.openprt.app.data.gtfs

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GtfsImporterTest {
    private val server = MockWebServer()
    private lateinit var database: GtfsDatabase
    private lateinit var dao: GtfsDao
    private lateinit var importer: GtfsImporter

    private val fixtureStops = listOf(
        StopEntity("10", "99994", "STEEL PLAZA STATION", 40.439562, -79.995332, 1),
        StopEntity("2635", "2635", "FIFTH AVE + BELLEFIELD, OPP", 40.445770, -79.951416, 0),
        StopEntity("8312", "8312", "FORBES AVE + MOREWOOD AVE \"CMU\"", 40.444557, -79.942791, 0)
    )

    private val fixtureRoutes = listOf(
        RouteEntity("61C", "61C", "MCKEESPORT-HOMESTEAD", 3, "CC00CC"),
        RouteEntity("BLUE", "BLUE", null, 2, "00E9FF"),
        RouteEntity("DQI", "DQI", "DUQUESNE INCLINE", 7, null)
    )

    @Before
    fun setUp() {
        server.start()
        database = Room
            .inMemoryDatabaseBuilder(
                ApplicationProvider.getApplicationContext(),
                GtfsDatabase::class.java
            )
            .build()
        dao = database.gtfsDao()
        importer = GtfsImporter(dao, feedUrl = server.url("/GTFS.zip"))
    }

    @After
    fun tearDown() {
        database.close()
        server.close()
    }

    private fun enqueueZip(files: Map<String, String>) {
        server.enqueue(MockResponse.Builder().body(Buffer().write(zipOf(files))).build())
    }

    @Test
    fun import_withFixture_storesStops() = runTest {
        enqueueZip(fixtureFeedFiles)

        importer.import()

        assertEquals(fixtureStops, dao.getAllStops())
    }

    @Test
    fun import_withFixture_storesRoutes() = runTest {
        enqueueZip(fixtureFeedFiles)

        importer.import()

        assertEquals(fixtureRoutes, dao.getAllRoutes())
    }

    @Test
    fun import_withFixture_reportsCounts() = runTest {
        enqueueZip(fixtureFeedFiles)

        val result = importer.import()

        assertEquals(GtfsImportResult.Success(stopCount = 3, routeCount = 3), result)
    }

    @Test
    fun import_sameFeedTwice_keepsOneRowPerStopAndRoute() = runTest {
        enqueueZip(fixtureFeedFiles)
        enqueueZip(fixtureFeedFiles)

        importer.import()
        importer.import()

        assertEquals(fixtureStops, dao.getAllStops())
        assertEquals(fixtureRoutes, dao.getAllRoutes())
    }

    @Test
    fun import_newerFeedWithoutAStop_removesThatStop() = runTest {
        enqueueZip(fixtureFeedFiles)
        enqueueZip(
            fixtureFeedFiles + (
                "stops.txt" to
                    "stop_id,stop_name,stop_lat,stop_lon\n10,STEEL PLAZA STATION,40.439562,-79.995332\n"
                )
        )

        importer.import()
        importer.import()

        assertEquals(listOf("10"), dao.getAllStops().map { it.stopId })
    }

    @Test
    fun import_whenServerErrors_returnsHttpErrorAndKeepsExistingData() = runTest {
        enqueueZip(fixtureFeedFiles)
        importer.import()
        server.enqueue(MockResponse.Builder().code(503).build())

        val result = importer.import()

        assertEquals(GtfsImportResult.Failure(GtfsImportError.Http(503)), result)
        assertEquals(fixtureStops, dao.getAllStops())
        assertEquals(fixtureRoutes, dao.getAllRoutes())
    }

    @Test
    fun import_whenDownloadTimesOut_returnsTimeoutAndKeepsExistingData() = runTest {
        enqueueZip(fixtureFeedFiles)
        importer.import()
        val impatientImporter = GtfsImporter(
            dao,
            httpClient = OkHttpClient.Builder().readTimeout(100, TimeUnit.MILLISECONDS).build(),
            feedUrl = server.url("/GTFS.zip")
        )
        server.enqueue(MockResponse.Builder().headersDelay(2, TimeUnit.SECONDS).build())

        val result = impatientImporter.import()

        assertEquals(GtfsImportResult.Failure(GtfsImportError.Timeout), result)
        assertEquals(fixtureStops, dao.getAllStops())
    }

    @Test
    fun import_whenBodyIsNotAZip_returnsMalformedFeedAndKeepsExistingData() = runTest {
        enqueueZip(fixtureFeedFiles)
        importer.import()
        server.enqueue(MockResponse.Builder().body("<html>maintenance</html>").build())

        val result = importer.import()

        assertTrue(
            result is GtfsImportResult.Failure && result.error is GtfsImportError.MalformedFeed
        )
        assertEquals(fixtureStops, dao.getAllStops())
    }

    @Test
    fun import_whenFeedRepeatsAStopId_returnsMalformedFeedAndKeepsExistingData() = runTest {
        enqueueZip(fixtureFeedFiles)
        importer.import()
        enqueueZip(
            fixtureFeedFiles + (
                "stops.txt" to
                    "stop_id,stop_name,stop_lat,stop_lon\n1,A,40.4,-79.9\n1,B,40.5,-79.8\n"
                )
        )

        val result = importer.import()

        assertTrue(
            result is GtfsImportResult.Failure && result.error is GtfsImportError.MalformedFeed
        )
        assertEquals(fixtureStops, dao.getAllStops())
        assertEquals(fixtureRoutes, dao.getAllRoutes())
    }
}
