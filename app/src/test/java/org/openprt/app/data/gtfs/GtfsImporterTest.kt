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
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GtfsImporterTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

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
        importer = GtfsImporter(
            database,
            downloadDir = temporaryFolder.root,
            feedUrl = server.url("/GTFS.zip")
        )
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

        assertEquals(
            GtfsImportResult.Success(
                stopCount = 3,
                routeCount = 3,
                tripCount = 4,
                stopTimeCount = 12
            ),
            result
        )
    }

    @Test
    fun import_withFixture_storesStopTimesWithPastMidnightTimesAndBoardingRules() = runTest {
        enqueueZip(fixtureFeedFiles)

        importer.import()

        assertEquals(
            listOf(
                StopTimeEntity("T4", 1, "8312", 88_800, 88_800, true, false),
                StopTimeEntity("T4", 2, "2635", 89_400, 89_400, true, true),
                StopTimeEntity("T4", 3, "10", 90_600, 90_600, false, true)
            ),
            dao.getStopTimesOfTrip("T4")
        )
    }

    @Test
    fun import_afterSuccess_deletesDownloadedZip() = runTest {
        enqueueZip(fixtureFeedFiles)

        importer.import()

        assertEquals(emptyList<String>(), temporaryFolder.root.list()!!.toList())
    }

    @Test
    fun import_whenStopTimesHaveABadTime_returnsMalformedFeedAndKeepsExistingTimetable() = runTest {
        enqueueZip(fixtureFeedFiles)
        importer.import()
        enqueueZip(
            fixtureFeedFiles + (
                "stop_times.txt" to
                    "trip_id,arrival_time,departure_time,stop_id,stop_sequence\n" +
                    "T1,07:00:00,07:00:00,8312,1\n" +
                    "T1,7 am,7 am,2635,2\n"
                )
        )

        val result = importer.import()

        assertTrue(
            result is GtfsImportResult.Failure && result.error is GtfsImportError.MalformedFeed
        )
        assertEquals(12, dao.countStopTimes())
        assertEquals(fixtureStops, dao.getAllStops())
    }

    @Test
    fun import_withoutTripsFile_returnsMalformedFeedAndStoresNothing() = runTest {
        enqueueZip(fixtureFeedFiles - "trips.txt")

        val result = importer.import()

        assertTrue(
            result is GtfsImportResult.Failure && result.error is GtfsImportError.MalformedFeed
        )
        assertEquals(emptyList<StopEntity>(), dao.getAllStops())
    }

    @Test
    fun import_withoutEitherCalendarFile_returnsMalformedFeed() = runTest {
        enqueueZip(fixtureFeedFiles - "calendar.txt" - "calendar_dates.txt")

        val result = importer.import()

        assertTrue(
            result is GtfsImportResult.Failure && result.error is GtfsImportError.MalformedFeed
        )
    }

    @Test
    fun import_whenStopTimesHasOnlyItsHeader_returnsMalformedFeedAndKeepsExistingTimetable() =
        runTest {
            enqueueZip(fixtureFeedFiles)
            importer.import()
            enqueueZip(
                fixtureFeedFiles + (
                    "stop_times.txt" to
                        "trip_id,arrival_time,departure_time,stop_id,stop_sequence\n"
                    )
            )

            val result = importer.import()

            assertTrue(
                result is GtfsImportResult.Failure && result.error is GtfsImportError.MalformedFeed
            )
            assertEquals(12, dao.countStopTimes())
            assertEquals(fixtureStops, dao.getAllStops())
        }

    @Test
    fun import_whenEveryFileHasOnlyItsHeader_returnsMalformedFeedAndKeepsExistingTimetable() =
        runTest {
            enqueueZip(fixtureFeedFiles)
            importer.import()
            enqueueZip(fixtureFeedFiles.mapValues { (_, text) -> text.lines().first() + "\n" })

            val result = importer.import()

            assertTrue(
                result is GtfsImportResult.Failure && result.error is GtfsImportError.MalformedFeed
            )
            assertEquals(fixtureStops, dao.getAllStops())
            assertEquals(fixtureRoutes, dao.getAllRoutes())
            assertEquals(12, dao.countStopTimes())
        }

    @Test
    fun import_whenBothCalendarFilesHaveOnlyTheirHeaders_returnsMalformedFeed() = runTest {
        enqueueZip(
            fixtureFeedFiles + mapOf(
                "calendar.txt" to
                    "service_id,monday,tuesday,wednesday,thursday,friday,saturday,sunday," +
                    "start_date,end_date\n",
                "calendar_dates.txt" to "service_id,date,exception_type\n"
            )
        )

        val result = importer.import()

        assertTrue(
            result is GtfsImportResult.Failure && result.error is GtfsImportError.MalformedFeed
        )
    }

    @Test
    fun import_withOnlyCalendarDates_succeeds() = runTest {
        enqueueZip(fixtureFeedFiles - "calendar.txt")

        val result = importer.import()

        assertTrue(result is GtfsImportResult.Success)
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
            database,
            downloadDir = temporaryFolder.root,
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

    private fun importerWithFeedPage() = GtfsImporter(
        database,
        downloadDir = temporaryFolder.root,
        feedUrl = server.url("/fallback/GTFS.zip"),
        feedPageUrl = server.url("/developer-resources/")
    )

    private fun enqueuePage(html: String) {
        server.enqueue(MockResponse.Builder().body(html).build())
    }

    @Test
    fun import_whenFeedPageLinksAZip_downloadsTheLinkedZip() = runTest {
        enqueuePage("""<a href="/contentassets/abc123/gtfs.zip">GTFS</a>""")
        enqueueZip(fixtureFeedFiles)

        importerWithFeedPage().import()

        // Timeouts so a missing request fails the test instead of hanging it.
        server.takeRequest(1, TimeUnit.SECONDS)
        assertEquals(
            "/contentassets/abc123/gtfs.zip",
            server.takeRequest(1, TimeUnit.SECONDS)?.url?.encodedPath
        )
    }

    @Test
    fun import_whenFeedPageLinksAZip_storesItsStops() = runTest {
        enqueuePage("""<a href="/contentassets/abc123/gtfs.zip">GTFS</a>""")
        enqueueZip(fixtureFeedFiles)

        importerWithFeedPage().import()

        assertEquals(fixtureStops, dao.getAllStops())
    }

    @Test
    fun import_whenFeedPageHasNoZipLink_downloadsTheFallbackUrl() = runTest {
        enqueuePage("""<a href="/about-us/">About</a>""")
        enqueueZip(fixtureFeedFiles)

        importerWithFeedPage().import()

        // Timeouts so a missing request fails the test instead of hanging it.
        server.takeRequest(1, TimeUnit.SECONDS)
        assertEquals(
            "/fallback/GTFS.zip",
            server.takeRequest(1, TimeUnit.SECONDS)?.url?.encodedPath
        )
    }

    @Test
    fun import_whenFeedPageIsMissing_downloadsTheFallbackUrl() = runTest {
        server.enqueue(MockResponse.Builder().code(404).build())
        enqueueZip(fixtureFeedFiles)

        importerWithFeedPage().import()

        // Timeouts so a missing request fails the test instead of hanging it.
        server.takeRequest(1, TimeUnit.SECONDS)
        assertEquals(
            "/fallback/GTFS.zip",
            server.takeRequest(1, TimeUnit.SECONDS)?.url?.encodedPath
        )
    }
}
