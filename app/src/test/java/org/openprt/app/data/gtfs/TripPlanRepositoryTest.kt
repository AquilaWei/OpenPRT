package org.openprt.app.data.gtfs

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.Instant
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
import org.openprt.app.geo.LatLng
import org.openprt.app.planner.Itinerary
import org.openprt.app.planner.NoRouteReason
import org.openprt.app.planner.RideLeg
import org.openprt.app.planner.TransitNetwork
import org.openprt.app.planner.TransitStop
import org.openprt.app.planner.WalkLeg

/**
 * Plans on the fixture feed after a real import; see test resources `gtfs/feed/`. Route 61C
 * runs CMU (8312) → Fifth Ave (2635) → Steel Plaza (10): T1 07:00, T2 08:00 and late-night T4
 * 24:40 on weekdays.
 */
@RunWith(AndroidJUnit4::class)
class TripPlanRepositoryTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val server = MockWebServer()
    private lateinit var database: GtfsDatabase
    private lateinit var source: CountingNetworkSource
    private lateinit var repository: TripPlanRepository

    @Before
    fun setUp() = runTest {
        server.start()
        database = inMemoryDatabase()
        server.enqueue(MockResponse.Builder().body(Buffer().write(zipOf(fixtureFeedFiles))).build())
        GtfsImporter(
            database,
            downloadDir = temporaryFolder.root,
            feedUrl = server.url("/GTFS.zip")
        ).import()
        source = CountingNetworkSource(RoomTransitNetworkSource(database.gtfsDao()))
        repository = TripPlanRepository(source)
    }

    @After
    fun tearDown() {
        database.close()
        server.close()
    }

    @Test
    fun plan_weekdayMorningCmuToSteelPlaza_ridesTheNextTripBetweenThem() = runTest {
        val result = repository.plan(
            CMU.location,
            STEEL_PLAZA.location,
            TripTime.DepartAt(THURSDAY_06_50)
        )

        assertEquals(
            TripPlanResult.Found(
                listOf(
                    TripPlan(
                        THURSDAY,
                        Itinerary(
                            listOf(
                                WalkLeg(null, CMU, 0.0, 25_200, 25_200),
                                RideLeg(
                                    "T1",
                                    "61C",
                                    "INBOUND-DOWNTOWN",
                                    CMU,
                                    STEEL_PLAZA,
                                    25_200,
                                    27_000
                                ),
                                WalkLeg(STEEL_PLAZA, null, 0.0, 27_000, 27_000)
                            )
                        )
                    )
                )
            ),
            result
        )
    }

    @Test
    fun plan_weekdayMorning_arrivalTimeIsOnTheServiceDay() = runTest {
        val result = repository.plan(
            CMU.location,
            STEEL_PLAZA.location,
            TripTime.DepartAt(THURSDAY_06_50)
        )

        assertEquals(Instant.parse("2026-10-01T11:30:00Z"), plans(result).single().arrivalTime)
    }

    @Test
    fun plan_twiceOnTheSameServiceDay_buildsTheNetworkOnce() = runTest {
        repository.plan(CMU.location, STEEL_PLAZA.location, TripTime.DepartAt(THURSDAY_06_50))
        repository.plan(
            CMU.location,
            STEEL_PLAZA.location,
            TripTime.DepartAt(Instant.parse("2026-10-01T12:00:00Z"))
        )

        assertEquals(listOf(THURSDAY), source.builtDays)
    }

    @Test
    fun plan_onTheNextServiceDay_buildsItsNetwork() = runTest {
        repository.plan(CMU.location, STEEL_PLAZA.location, TripTime.DepartAt(THURSDAY_06_50))
        repository.plan(
            CMU.location,
            STEEL_PLAZA.location,
            TripTime.DepartAt(Instant.parse("2026-10-02T11:00:00Z"))
        )

        assertEquals(listOf(THURSDAY, FRIDAY), source.builtDays)
    }

    @Test
    fun plan_afterMidnight_ridesThePreviousServiceDaysLateTrip() = runTest {
        val result = repository.plan(
            CMU.location,
            STEEL_PLAZA.location,
            TripTime.DepartAt(FRIDAY_00_30)
        )

        assertEquals(
            listOf(RideLeg("T4", "61C", "INBOUND-DOWNTOWN", CMU, STEEL_PLAZA, 88_800, 90_600)),
            plans(result).single().itinerary.rides
        )
    }

    @Test
    fun plan_afterMidnight_planBelongsToThePreviousServiceDay() = runTest {
        val result = repository.plan(
            CMU.location,
            STEEL_PLAZA.location,
            TripTime.DepartAt(FRIDAY_00_30)
        )

        assertEquals(THURSDAY, plans(result).single().serviceDate)
    }

    @Test
    fun plan_afterMidnight_arrivesAt0110OnTheCalendarDay() = runTest {
        val result = repository.plan(
            CMU.location,
            STEEL_PLAZA.location,
            TripTime.DepartAt(FRIDAY_00_30)
        )

        assertEquals(Instant.parse("2026-10-02T05:10:00Z"), plans(result).single().arrivalTime)
    }

    @Test
    fun plan_arriveByMorning_ridesTheLatestTripThatArrivesInTime() = runTest {
        val result = repository.plan(
            CMU.location,
            STEEL_PLAZA.location,
            TripTime.ArriveBy(THURSDAY_08_45)
        )

        assertEquals(
            listOf(RideLeg("T2", "61C", "INBOUND-DOWNTOWN", CMU, STEEL_PLAZA, 28_800, 30_600)),
            plans(result).single().itinerary.rides
        )
    }

    @Test
    fun plan_arriveByAfterMidnight_ridesThePreviousServiceDaysLastTrip() = runTest {
        val result = repository.plan(
            CMU.location,
            STEEL_PLAZA.location,
            TripTime.ArriveBy(FRIDAY_01_30)
        )

        assertEquals(
            listOf(RideLeg("T4", "61C", "INBOUND-DOWNTOWN", CMU, STEEL_PLAZA, 88_800, 90_600)),
            plans(result).single().itinerary.rides
        )
    }

    @Test
    fun plan_arriveByAfterMidnight_planBelongsToThePreviousServiceDay() = runTest {
        val result = repository.plan(
            CMU.location,
            STEEL_PLAZA.location,
            TripTime.ArriveBy(FRIDAY_01_30)
        )

        assertEquals(THURSDAY, plans(result).single().serviceDate)
    }

    @Test
    fun plan_arriveByBeforeTheFirstTrip_returnsNoConnection() = runTest {
        // Monday 06:00 EDT, before T1 reaches Steel Plaza; nothing runs on Sunday night.
        val arriveBy = Instant.parse("2026-10-05T10:00:00Z")

        val result = repository.plan(
            CMU.location,
            STEEL_PLAZA.location,
            TripTime.ArriveBy(arriveBy)
        )

        assertEquals(TripPlanResult.NoRoute(NoRouteReason.NO_CONNECTION), result)
    }

    @Test
    fun plan_departAtThenArriveByOnTheSameDay_buildsTheNetworkOnce() = runTest {
        repository.plan(CMU.location, STEEL_PLAZA.location, TripTime.DepartAt(THURSDAY_06_50))
        repository.plan(CMU.location, STEEL_PLAZA.location, TripTime.ArriveBy(THURSDAY_08_45))

        assertEquals(listOf(THURSDAY), source.builtDays)
    }

    @Test
    fun plan_arriveByOnAnotherDayTwice_buildsThatDaysNetworkOnce() = runTest {
        val fridayMorning = Instant.parse("2026-10-02T12:45:00Z")

        repository.plan(CMU.location, STEEL_PLAZA.location, TripTime.ArriveBy(THURSDAY_08_45))
        repository.plan(CMU.location, STEEL_PLAZA.location, TripTime.ArriveBy(fridayMorning))
        repository.plan(CMU.location, STEEL_PLAZA.location, TripTime.ArriveBy(fridayMorning))

        assertEquals(listOf(THURSDAY, FRIDAY), source.builtDays)
    }

    @Test
    fun plan_destinationFarFromEveryStop_returnsNoRoute() = runTest {
        val result = repository.plan(
            CMU.location,
            LatLng(40.6, -80.2),
            TripTime.DepartAt(THURSDAY_06_50)
        )

        assertEquals(TripPlanResult.NoRoute(NoRouteReason.NO_STOP_NEAR_DESTINATION), result)
    }

    @Test
    fun plan_dayAfterTheCalendarEnds_returnsNoConnection() = runTest {
        // Thursday 2026-10-29, after calendar.txt's last date, 2026-10-24.
        val departAt = Instant.parse("2026-10-29T10:50:00Z")

        val result = repository.plan(
            CMU.location,
            STEEL_PLAZA.location,
            TripTime.DepartAt(departAt)
        )

        assertEquals(TripPlanResult.NoRoute(NoRouteReason.NO_CONNECTION), result)
    }

    @Test
    fun plan_databaseWithoutGtfsData_returnsNoTimetable() = runTest {
        val empty = inMemoryDatabase()
        val emptyRepository = TripPlanRepository(RoomTransitNetworkSource(empty.gtfsDao()))

        val result = emptyRepository.plan(
            CMU.location,
            STEEL_PLAZA.location,
            TripTime.DepartAt(THURSDAY_06_50)
        )

        empty.close()
        assertEquals(TripPlanResult.NoTimetable, result)
    }

    private fun inMemoryDatabase(): GtfsDatabase = Room
        .inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            GtfsDatabase::class.java
        )
        .build()

    private class CountingNetworkSource(private val real: TransitNetworkSource) :
        TransitNetworkSource {
        val builtDays = mutableListOf<LocalDate>()

        override suspend fun network(serviceDate: LocalDate): TransitNetwork? {
            builtDays += serviceDate
            return real.network(serviceDate)
        }
    }

    private companion object {
        val THURSDAY: LocalDate = LocalDate.of(2026, 10, 1)
        val FRIDAY: LocalDate = LocalDate.of(2026, 10, 2)

        // New York time (EDT, UTC-4).
        val THURSDAY_06_50: Instant = Instant.parse("2026-10-01T10:50:00Z")
        val FRIDAY_00_30: Instant = Instant.parse("2026-10-02T04:30:00Z")
        val THURSDAY_08_45: Instant = Instant.parse("2026-10-01T12:45:00Z")
        val FRIDAY_01_30: Instant = Instant.parse("2026-10-02T05:30:00Z")

        val CMU = TransitStop(
            "8312",
            "FORBES AVE + MOREWOOD AVE \"CMU\"",
            LatLng(40.444557, -79.942791),
            "8312"
        )
        val STEEL_PLAZA = TransitStop(
            "10",
            "STEEL PLAZA STATION",
            LatLng(40.439562, -79.995332),
            "99994"
        )

        fun plans(result: TripPlanResult): List<TripPlan> = (result as TripPlanResult.Found).plans
    }
}
