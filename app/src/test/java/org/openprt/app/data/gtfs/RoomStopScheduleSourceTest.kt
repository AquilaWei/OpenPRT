package org.openprt.app.data.gtfs

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.Instant
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
class RoomStopScheduleSourceTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val server = MockWebServer()
    private lateinit var database: GtfsDatabase
    private lateinit var source: RoomStopScheduleSource

    @Before
    fun setUp() = runTest {
        server.start()
        database = Room
            .inMemoryDatabaseBuilder(
                ApplicationProvider.getApplicationContext(),
                GtfsDatabase::class.java
            )
            .build()
        source = RoomStopScheduleSource(database.gtfsDao())
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

    // Thursday 07:15 in Pittsburgh: T2 at 08:10, then T4 at 24:50 (00:50 Friday).
    @Test
    fun departures_weekdayMorning_listsLaterTripsWithRouteName() = runTest {
        val departures = source.departures("2635", Instant.parse("2026-10-01T11:15:00Z"), 10)

        assertEquals(
            listOf(
                StopScheduleEntry("61C", "INBOUND-DOWNTOWN", Instant.parse("2026-10-01T12:10:00Z")),
                StopScheduleEntry("61C", "INBOUND-DOWNTOWN", Instant.parse("2026-10-02T04:50:00Z"))
            ),
            departures
        )
    }

    // Friday 00:45: Thursday's T4 still runs, at 24:50 on Thursday's service day.
    @Test
    fun departures_afterMidnight_includesPreviousServiceDaysLateTrip() = runTest {
        val departures = source.departures("2635", Instant.parse("2026-10-02T04:45:00Z"), 1)

        assertEquals(
            listOf(
                StopScheduleEntry("61C", "INBOUND-DOWNTOWN", Instant.parse("2026-10-02T04:50:00Z"))
            ),
            departures
        )
    }

    // The TrueTime ID is the stop code; stop 10 is coded 99994 and nobody boards there.
    @Test
    fun departures_stopCodeOfLastStop_isEmpty() = runTest {
        val departures = source.departures("99994", Instant.parse("2026-10-01T11:15:00Z"), 10)

        assertEquals(emptyList<StopScheduleEntry>(), departures)
    }

    @Test
    fun departures_unknownStop_isEmpty() = runTest {
        val departures = source.departures("424242", Instant.parse("2026-10-01T11:15:00Z"), 10)

        assertEquals(emptyList<StopScheduleEntry>(), departures)
    }

    // GTFS stop_id 10 is not a TrueTime ID: TrueTime calls that stop 99994.
    @Test
    fun getStopsByTrueTimeId_stopIdOfCodedStop_findsNothing() = runTest {
        assertEquals(emptyList<StopEntity>(), database.gtfsDao().getStopsByTrueTimeId("10"))
    }
}
