package org.openprt.app.data.gtfs

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith

/** Queries the fixture feed after a real import; see test resources `gtfs/feed/`. */
@RunWith(AndroidJUnit4::class)
class GtfsTimetableTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val server = MockWebServer()
    private lateinit var database: GtfsDatabase
    private lateinit var timetable: GtfsTimetable

    // A Thursday, so weekday service WK runs.
    private val thursday = LocalDate.of(2026, 10, 1)

    // Labor Day: calendar_dates removes WK and adds SA.
    private val laborDay = LocalDate.of(2026, 9, 7)

    @Before
    fun setUp() = runTest {
        server.start()
        database = Room
            .inMemoryDatabaseBuilder(
                ApplicationProvider.getApplicationContext(),
                GtfsDatabase::class.java
            )
            .build()
        timetable = GtfsTimetable(database)
        server.enqueue(MockResponse.Builder().body(Buffer().write(zipOf(fixtureFeedFiles))).build())
        importer().import()
    }

    @After
    fun tearDown() {
        database.close()
        server.close()
    }

    // calendar.txt runs 2026-06-28 to 2026-10-24; calendar_dates' Labor Day is inside that.
    @Test
    fun timetableDates_importedFeed_spansTheCalendar() = runTest {
        val dates = RoomTimetableDatesSource(database).dates()

        assertEquals(LocalDate.of(2026, 6, 28)..LocalDate.of(2026, 10, 24), dates)
    }

    @Test
    fun timetableDates_nothingImported_isNull() = runTest {
        val empty = Room
            .inMemoryDatabaseBuilder(
                ApplicationProvider.getApplicationContext(),
                GtfsDatabase::class.java
            )
            .build()

        val dates = RoomTimetableDatesSource(empty).dates()

        empty.close()
        assertEquals(null, dates)
    }

    // The new feed runs a week later on both ends: 2026-07-05 to 2026-11-21.
    @Test
    fun timetableDates_whileAnImportCommitsBetweenReads_spansTheCalendarItStartedReading() =
        runTest {
            val dao = ImportingAfterFirstServiceDateDao(database.gtfsDao())

            val dates = RoomTimetableDatesSource(database, dao).dates()
            dao.import!!.await()

            assertEquals(LocalDate.of(2026, 6, 28)..LocalDate.of(2026, 10, 24), dates)
        }

    // 06:30:00; the new feed moves T1 here from 07:10 to 06:10, so it would offer T2 first.
    @Test
    fun departuresAfter_whileAnImportCommitsBetweenReads_listsTheTimetableItStartedReading() =
        runTest {
            val dao = ImportingAfterCalendarsDao(database.gtfsDao())

            val departures = GtfsTimetable(database, dao)
                .departuresAfter("2635", thursday, afterSeconds = 23_400, limit = 1)
            dao.import!!.await()

            assertEquals(
                listOf(ScheduledDeparture("T1", "61C", "INBOUND-DOWNTOWN", 2, thursday, 25_800)),
                departures
            )
        }

    @Test
    fun departuresAfter_weekdayMorning_returnsLaterTripsEarliestFirst() = runTest {
        // 07:15:00, just after T1 leaves this stop at 07:10.
        val departures = timetable.departuresAfter("2635", thursday, afterSeconds = 26_100)

        assertEquals(
            listOf(
                ScheduledDeparture("T2", "61C", "INBOUND-DOWNTOWN", 2, thursday, 29_400),
                ScheduledDeparture("T4", "61C", "INBOUND-DOWNTOWN", 2, thursday, 89_400)
            ),
            departures
        )
    }

    @Test
    fun departuresAfter_departureExactlyAtQueryTime_isIncluded() = runTest {
        val departures = timetable.departuresAfter("2635", thursday, afterSeconds = 29_400)

        assertEquals(listOf("T2", "T4"), departures.map { it.tripId })
    }

    @Test
    fun departuresAfter_withLimit_returnsOnlyThatMany() = runTest {
        val departures = timetable.departuresAfter("2635", thursday, afterSeconds = 0, limit = 1)

        assertEquals(listOf("T1"), departures.map { it.tripId })
    }

    @Test
    fun departuresAfter_holidayWithCalendarExceptions_runsAddedServiceInsteadOfRemoved() = runTest {
        val departures = timetable.departuresAfter("2635", laborDay, afterSeconds = 0)

        assertEquals(
            listOf(ScheduledDeparture("T3", "61C", "INBOUND-DOWNTOWN", 2, laborDay, 33_000)),
            departures
        )
    }

    @Test
    fun departuresAfter_lastStopWhereNobodyBoards_returnsNothing() = runTest {
        val departures = timetable.departuresAfter("10", thursday, afterSeconds = 0)

        assertEquals(emptyList<ScheduledDeparture>(), departures)
    }

    @Test
    fun departuresAfter_dateAfterCalendarEnds_returnsNothing() = runTest {
        val departures = timetable.departuresAfter("2635", LocalDate.of(2026, 10, 29), 0)

        assertEquals(emptyList<ScheduledDeparture>(), departures)
    }

    private fun importer() = GtfsImporter(
        database,
        downloadDir = temporaryFolder.root,
        feedUrl = server.url("/GTFS.zip")
    )

    /**
     * Starts importing [feed] and gives it a second to commit before returning. Reads sharing a
     * transaction hold the import back until they finish.
     */
    private suspend fun startImport(feed: Map<String, String>): Deferred<GtfsImportResult> {
        server.enqueue(MockResponse.Builder().body(Buffer().write(zipOf(feed))).build())
        val import = CoroutineScope(Dispatchers.IO).async { importer().import() }
        withContext(Dispatchers.IO) { withTimeoutOrNull(1_000) { import.await() } }
        return import
    }

    /** Imports [laterCalendarFeed] right after the first service date is read. */
    private inner class ImportingAfterFirstServiceDateDao(private val real: GtfsDao) :
        GtfsDao by real {
        var import: Deferred<GtfsImportResult>? = null

        override suspend fun getFirstServiceDate(): LocalDate? = real.getFirstServiceDate().also {
            if (import == null) import = startImport(laterCalendarFeed)
        }
    }

    /** Imports [earlierT1Feed] right after the day's calendars are read. */
    private inner class ImportingAfterCalendarsDao(private val real: GtfsDao) : GtfsDao by real {
        var import: Deferred<GtfsImportResult>? = null

        override suspend fun getCalendarsCovering(date: LocalDate): List<ServiceCalendarEntity> =
            real.getCalendarsCovering(date).also {
                if (import == null) import = startImport(earlierT1Feed)
            }
    }

    private companion object {
        val laterCalendarFeed = fixtureFeedFiles + (
            "calendar.txt" to fixtureFeedFiles.getValue("calendar.txt")
                .replace("20260628", "20260705")
                .replace("20261024", "20261121")
            )

        val earlierT1Feed = fixtureFeedFiles + (
            "stop_times.txt" to fixtureFeedFiles.getValue("stop_times.txt")
                .replace(",07:", ",06:")
            )
    }
}
