package org.openprt.app.data.gtfs

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
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

@RunWith(AndroidJUnit4::class)
class NearbyStopRepositoryTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val server = MockWebServer()
    private lateinit var database: GtfsDatabase
    private lateinit var dao: GtfsDao
    private lateinit var repository: NearbyStopRepository

    // Fixture stop 2635 is about 110 m from here; the other fixture stops are kilometers away.
    private val nearFifthAndBellefield = LatLng(40.445770, -79.952716)

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
        repository = NearbyStopRepository(
            dao,
            GtfsImporter(
                database,
                downloadDir = temporaryFolder.root,
                feedUrl = server.url("/GTFS.zip")
            )
        )
    }

    @After
    fun tearDown() {
        database.close()
        server.close()
    }

    @Test
    fun nearbyStops_emptyDatabase_importsFeedThenReturnsStops() = runTest {
        server.enqueue(MockResponse.Builder().body(Buffer().write(zipOf(fixtureFeedFiles))).build())

        val result = repository.nearbyStops(nearFifthAndBellefield, 400.0)

        assertEquals(
            listOf("2635"),
            (result as NearbyStopsResult.Success).stops.map { it.stop.stopId }
        )
    }

    @Test
    fun nearbyStops_databaseHasStops_doesNotDownload() = runTest {
        dao.insertStops(listOf(StopEntity("2635", "2635", "FIFTH AVE", 40.445770, -79.951416, 0)))

        repository.nearbyStops(nearFifthAndBellefield, 400.0)

        assertEquals(0, server.requestCount)
    }

    @Test
    fun nearbyStops_emptyDatabaseAndDownloadFails_returnsImportError() = runTest {
        server.enqueue(MockResponse.Builder().code(503).build())

        val result = repository.nearbyStops(nearFifthAndBellefield, 400.0)

        assertEquals(NearbyStopsResult.Failure(GtfsImportError.Http(503)), result)
    }
}
