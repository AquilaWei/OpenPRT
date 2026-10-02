package org.openprt.app.data.gtfs

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
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
        timetable = GtfsTimetable(database.gtfsDao())
        server.enqueue(MockResponse.Builder().body(Buffer().write(zipOf(fixtureFeedFiles))).build())
        GtfsImporter(
            database,
            downloadDir = temporaryFolder.root,
            feedUrl = server.url("/GTFS.zip")
        ).import()
    }

    @After
    fun tearDown() {
        database.close()
        server.close()
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
}
