package org.openprt.app.data.gtfs

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.openprt.app.geo.LatLng

@RunWith(AndroidJUnit4::class)
class NearbyStopFinderTest {
    private lateinit var database: GtfsDatabase
    private lateinit var dao: GtfsDao
    private lateinit var finder: NearbyStopFinder

    private val executedQueries = CopyOnWriteArrayList<Pair<String, List<Any?>>>()

    private val center = LatLng(40.444000, -79.945000)

    // Distances from center: G 14 m, A 100 m, B 212 m, C 344 m, E 405 m and D 418 m (inside the
    // 400 m bounding box but outside the circle), F 667 m (outside the box).
    private val stops = listOf(
        StopEntity("A", "A", "STOP A", 40.444900, -79.945000, 0),
        StopEntity("B", "B", "STOP B", 40.444000, -79.947500, 0),
        StopEntity("C", "C", "STOP C", 40.441000, -79.944000, 0),
        StopEntity("D", "D", "STOP D", 40.446800, -79.948300, 0),
        StopEntity("E", "E", "STOP E", 40.446000, -79.941000, 0),
        StopEntity("F", "F", "STOP F", 40.450000, -79.945000, 0),
        StopEntity("G", "G", "STOP G", 40.444100, -79.945100, 0)
    )

    @Before
    fun setUp() {
        database = Room
            .inMemoryDatabaseBuilder(
                ApplicationProvider.getApplicationContext(),
                GtfsDatabase::class.java
            )
            .setQueryCallback(
                { sql, args -> executedQueries.add(sql to args) },
                Runnable::run
            )
            .build()
        dao = database.gtfsDao()
        finder = NearbyStopFinder(dao)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun findNearby_within400m_returnsStopsNearestFirst() = runTest {
        dao.replaceAll(stops, emptyList())

        val result = finder.findNearby(center, 400.0)

        assertEquals(listOf("G", "A", "B", "C"), result.map { it.stop.stopId })
    }

    @Test
    fun findNearby_within400m_reportsHaversineDistances() = runTest {
        dao.replaceAll(stops, emptyList())

        val roundedDistances = finder.findNearby(center, 400.0)
            .map { Math.round(it.distanceMeters * 10) / 10.0 }

        assertEquals(listOf(14.0, 100.1, 211.6, 344.2), roundedDistances)
    }

    @Test
    fun findNearby_stopsAtSameCoordinates_areOrderedByStopId() = runTest {
        dao.replaceAll(
            listOf(
                StopEntity("Z", "Z", "INBOUND", 40.444900, -79.945000, 0),
                StopEntity("Y", "Y", "OUTBOUND", 40.444900, -79.945000, 0)
            ),
            emptyList()
        )

        val result = finder.findNearby(center, 400.0)

        assertEquals(listOf("Y", "Z"), result.map { it.stop.stopId })
    }

    @Test
    fun findNearby_noStopsInRadius_returnsEmptyList() = runTest {
        dao.replaceAll(stops, emptyList())

        val result = finder.findNearby(LatLng(40.500000, -80.100000), 400.0)

        assertEquals(emptyList<NearbyStop>(), result)
    }

    @Test
    fun findNearby_beforeAnyImport_returnsEmptyList() = runTest {
        val result = finder.findNearby(center, 400.0)

        assertEquals(emptyList<NearbyStop>(), result)
    }

    @Test
    fun getStopsInBox_returnsOnlyStopsInsideBox() = runTest {
        dao.replaceAll(stops, emptyList())

        val result = dao.getStopsInBox(40.4404, 40.4476, -79.9498, -79.9402)

        assertEquals(setOf("A", "B", "C", "D", "E", "G"), result.map { it.stopId }.toSet())
    }

    @Test
    fun getStopsInBox_queryPlan_searchesLatitudeIndexInsteadOfScanningTable() = runTest {
        dao.getStopsInBox(40.4, 40.5, -80.0, -79.9)
        val (sql, args) = executedQueries.single { it.first.contains("FROM stops WHERE") }

        val plan = database.openHelper.readableDatabase
            .query("EXPLAIN QUERY PLAN $sql", args.toTypedArray())
            .use { cursor ->
                cursor.moveToFirst()
                cursor.getString(cursor.getColumnIndexOrThrow("detail"))
            }

        assertTrue(plan, plan.contains("USING INDEX index_stops_latitude"))
    }
}
