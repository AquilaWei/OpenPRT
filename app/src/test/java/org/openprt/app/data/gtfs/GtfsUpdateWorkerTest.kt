package org.openprt.app.data.gtfs

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.NetworkType
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.TimeUnit
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

@RunWith(AndroidJUnit4::class)
class GtfsUpdateWorkerTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val server = MockWebServer()
    private lateinit var database: GtfsDatabase
    private lateinit var log: GtfsImportLog
    private lateinit var updater: GtfsUpdater

    @Before
    fun setUp() {
        server.start()
        database = Room.inMemoryDatabaseBuilder(context, GtfsDatabase::class.java).build()
        log = GtfsImportLog(
            context.getSharedPreferences(GtfsImportLog.PREFS_NAME, Context.MODE_PRIVATE)
        )
        updater = GtfsUpdater(
            database.gtfsDao(),
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
    fun schedule_enqueuesADailyUpdate() {
        val workManager = testWorkManager()

        GtfsUpdateWorker.schedule(workManager)

        val work = workManager.getWorkInfosForUniqueWork(GtfsUpdateWorker.WORK_NAME).get().single()
        assertEquals(TimeUnit.DAYS.toMillis(1), work.periodicityInfo?.repeatIntervalMillis)
    }

    @Test
    fun schedule_requiresANetworkConnection() {
        val workManager = testWorkManager()

        GtfsUpdateWorker.schedule(workManager)

        val work = workManager.getWorkInfosForUniqueWork(GtfsUpdateWorker.WORK_NAME).get().single()
        assertEquals(NetworkType.CONNECTED, work.constraints.requiredNetworkType)
    }

    @Test
    fun schedule_calledTwice_keepsOneSchedule() {
        val workManager = testWorkManager()

        GtfsUpdateWorker.schedule(workManager)
        GtfsUpdateWorker.schedule(workManager)

        assertEquals(
            1,
            workManager.getWorkInfosForUniqueWork(GtfsUpdateWorker.WORK_NAME).get().size
        )
    }

    @Test
    fun doWork_dataEightDaysOld_downloadsTheFeedAndSucceeds() = runTest {
        log.record(NOW.minusSeconds(8 * DAY_SECONDS))
        server.enqueue(MockResponse.Builder().body(Buffer().write(zipOf(fixtureFeedFiles))).build())

        val result = worker().doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun doWork_dataOneDayOld_succeedsWithoutDownloading() = runTest {
        log.record(NOW.minusSeconds(DAY_SECONDS))

        val result = worker().doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun doWork_serverError_failsUntilTheNextDailyRun() = runTest {
        log.record(NOW.minusSeconds(8 * DAY_SECONDS))
        server.enqueue(MockResponse.Builder().code(503).build())

        val result = worker().doWork()

        assertEquals(ListenableWorker.Result.failure(), result)
    }

    @Test
    fun doWork_serverUnreachable_asksToRetry() = runTest {
        log.record(NOW.minusSeconds(8 * DAY_SECONDS))
        server.close()

        val result = worker().doWork()

        assertEquals(ListenableWorker.Result.retry(), result)
    }

    private fun worker(): GtfsUpdateWorker = TestListenableWorkerBuilder<GtfsUpdateWorker>(context)
        .setWorkerFactory(GtfsUpdateWorkerFactory { updater })
        .build()

    private fun testWorkManager(): WorkManager {
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder().setExecutor(SynchronousExecutor()).build()
        )
        return WorkManager.getInstance(context)
    }

    private companion object {
        val NOW: Instant = Instant.parse("2026-10-04T12:00:00Z")
        const val DAY_SECONDS = 24 * 60 * 60L
    }
}
