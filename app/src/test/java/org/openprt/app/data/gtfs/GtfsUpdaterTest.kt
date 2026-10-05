package org.openprt.app.data.gtfs

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GtfsUpdaterTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val server = MockWebServer()
    private lateinit var database: GtfsDatabase
    private lateinit var dao: GtfsDao
    private lateinit var log: GtfsImportLog
    private lateinit var updater: GtfsUpdater

    @Before
    fun setUp() {
        server.start()
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, GtfsDatabase::class.java).build()
        dao = database.gtfsDao()
        log = GtfsImportLog(
            context.getSharedPreferences(GtfsImportLog.PREFS_NAME, Context.MODE_PRIVATE)
        )
        updater = GtfsUpdater(
            dao,
            GtfsImporter(database, temporaryFolder.root, feedUrl = server.url("/GTFS.zip")),
            log,
            Clock.fixed(NOW, ZoneOffset.UTC)
        )
    }

    @After
    fun tearDown() {
        database.close()
        server.close()
    }

    @Test
    fun importIfEmpty_emptyDatabase_recordsTheImportTime() = runTest {
        enqueueFeed()

        updater.importIfEmpty()

        assertEquals(NOW, log.lastImport)
    }

    @Test
    fun importIfEmpty_downloadFails_recordsNoImportTime() = runTest {
        server.enqueue(MockResponse.Builder().code(503).build())

        updater.importIfEmpty()

        assertNull(log.lastImport)
    }

    @Test
    fun updateIfOlderThan_importedSixDaysAgo_doesNotDownload() = runTest {
        log.record(NOW.minusSeconds(6 * DAY_SECONDS))

        val result = updater.updateIfOlderThan()

        assertNull(result)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun updateIfOlderThan_importedSevenDaysAgo_downloadsAndRecordsTheNewTime() = runTest {
        log.record(NOW.minusSeconds(7 * DAY_SECONDS))
        enqueueFeed()

        updater.updateIfOlderThan()

        assertEquals(1, server.requestCount)
        assertEquals(NOW, log.lastImport)
    }

    @Test
    fun updateIfOlderThan_importNeverRecorded_downloads() = runTest {
        dao.insertStops(listOf(StopEntity("2635", "2635", "FIFTH AVE", 40.445770, -79.951416, 0)))
        enqueueFeed()

        updater.updateIfOlderThan()

        assertEquals(1, server.requestCount)
    }

    @Test
    fun updateIfOlderThan_newFeedIsBroken_keepsTheDataAndItsImportTime() = runTest {
        enqueueFeed()
        updater.importIfEmpty()
        val stopsBefore = dao.getAllStops()
        val imported = NOW.minusSeconds(8 * DAY_SECONDS)
        log.record(imported)
        server.enqueue(MockResponse.Builder().body("not a zip").build())

        val result = updater.updateIfOlderThan()

        assertEquals(
            GtfsImportError.MalformedFeed::class,
            ((result as GtfsImportResult.Failure).error)::class
        )
        assertEquals(stopsBefore, dao.getAllStops())
        assertEquals(12, dao.countStopTimes())
        assertEquals(imported, log.lastImport)
    }

    @Test
    fun updateIfOlderThan_newFeedHasOnlyHeaders_keepsTheDataAndItsImportTime() = runTest {
        enqueueFeed()
        updater.importIfEmpty()
        val stopsBefore = dao.getAllStops()
        val imported = NOW.minusSeconds(8 * DAY_SECONDS)
        log.record(imported)
        val headersOnly = fixtureFeedFiles.mapValues { (_, text) -> text.lines().first() + "\n" }
        server.enqueue(MockResponse.Builder().body(Buffer().write(zipOf(headersOnly))).build())

        val result = updater.updateIfOlderThan()

        assertEquals(
            GtfsImportError.MalformedFeed::class,
            ((result as GtfsImportResult.Failure).error)::class
        )
        assertEquals(stopsBefore, dao.getAllStops())
        assertEquals(12, dao.countStopTimes())
        assertEquals(imported, log.lastImport)
    }

    @Test
    fun importIfEmpty_downloadFails_reportsTheImportFailed() = runTest {
        server.enqueue(MockResponse.Builder().code(503).build())

        updater.importIfEmpty()

        assertTrue(updater.lastImportFailed)
    }

    @Test
    fun importIfEmpty_succeedsAfterAFailure_noLongerReportsAFailure() = runTest {
        server.enqueue(MockResponse.Builder().code(503).build())
        updater.importIfEmpty()
        enqueueFeed()

        updater.importIfEmpty()

        assertFalse(updater.lastImportFailed)
    }

    @Test
    fun updateIfOlderThan_succeeds_publishesTheNewImportTime() = runTest {
        log.record(NOW.minusSeconds(7 * DAY_SECONDS))
        val openUpdater = GtfsUpdater(
            dao,
            GtfsImporter(database, temporaryFolder.root, feedUrl = server.url("/GTFS.zip")),
            log,
            Clock.fixed(NOW, ZoneOffset.UTC)
        )
        enqueueFeed()

        openUpdater.updateIfOlderThan()

        assertEquals(NOW, openUpdater.lastImport.value)
    }

    @Test
    fun firstImportAndBackgroundUpdateTogether_downloadTheFeedOnce() = runTest {
        enqueueFeed()
        enqueueFeed()

        val firstImport = launch { updater.importIfEmpty() }
        val backgroundUpdate = launch { updater.updateIfOlderThan() }
        firstImport.join()
        backgroundUpdate.join()

        assertEquals(1, server.requestCount)
    }

    private fun enqueueFeed() {
        server.enqueue(MockResponse.Builder().body(Buffer().write(zipOf(fixtureFeedFiles))).build())
    }

    private companion object {
        val NOW: Instant = Instant.parse("2026-10-04T12:00:00Z")
        const val DAY_SECONDS = 24 * 60 * 60L
    }
}
