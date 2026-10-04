package org.openprt.app.data.gtfs

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ListenableWorker
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequest
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * Downloads the GTFS feed again in the background once the data is [GtfsUpdater.MAX_AGE] old.
 * Runs daily but only downloads when the data is that old, so the data is at most a day past
 * [GtfsUpdater.MAX_AGE] when the phone is online.
 */
class GtfsUpdateWorker(
    context: Context,
    params: WorkerParameters,
    private val updater: GtfsUpdater
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = when (val result = updater.updateIfOlderThan()) {
        null, is GtfsImportResult.Success -> Result.success()

        is GtfsImportResult.Failure -> when (result.error) {
            // The connection dropped mid-download: worth another try with WorkManager's backoff.
            GtfsImportError.Timeout, is GtfsImportError.Network -> Result.retry()

            // The server or the feed is broken; tomorrow's run tries again.
            is GtfsImportError.Http, is GtfsImportError.MalformedFeed -> Result.failure()
        }
    }

    companion object {
        /** The unique name the periodic update is scheduled under. */
        const val WORK_NAME = "gtfs-update"

        /** Daily, and only with a network connection, since every run may download ~22 MB. */
        fun request(): PeriodicWorkRequest = PeriodicWorkRequestBuilder<GtfsUpdateWorker>(
            1,
            TimeUnit.DAYS
        ).setConstraints(
            Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        ).build()

        /** Schedules the update; calling it again keeps the existing schedule. */
        fun schedule(workManager: WorkManager) {
            workManager.enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request()
            )
        }
    }
}

/** Creates [GtfsUpdateWorker]s with the app's [GtfsUpdater]; other workers are left to defaults. */
class GtfsUpdateWorkerFactory(private val updater: () -> GtfsUpdater) : WorkerFactory() {
    override fun createWorker(
        appContext: Context,
        workerClassName: String,
        workerParameters: WorkerParameters
    ): ListenableWorker? = if (workerClassName == GtfsUpdateWorker::class.java.name) {
        GtfsUpdateWorker(appContext, workerParameters, updater())
    } else {
        null
    }
}
