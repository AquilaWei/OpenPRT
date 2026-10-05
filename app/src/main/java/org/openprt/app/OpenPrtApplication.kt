package org.openprt.app

import android.app.Application
import android.content.Context
import androidx.work.Configuration
import org.openprt.app.data.gtfs.GtfsDao
import org.openprt.app.data.gtfs.GtfsDatabase
import org.openprt.app.data.gtfs.GtfsImportLog
import org.openprt.app.data.gtfs.GtfsImporter
import org.openprt.app.data.gtfs.GtfsUpdateWorkerFactory
import org.openprt.app.data.gtfs.GtfsUpdater
import org.openprt.app.data.gtfs.NearbyStopRepository
import org.openprt.app.data.gtfs.RideStopsSource
import org.openprt.app.data.gtfs.RoomRideStopsSource
import org.openprt.app.data.gtfs.RoomTransitNetworkSource
import org.openprt.app.data.gtfs.TripPlanRepository
import org.openprt.app.data.settings.ApiKeySettings
import org.openprt.app.data.settings.AppearanceSettings
import org.openprt.app.walk.CachingWalkRouter
import org.openprt.app.walk.ValhallaWalkRouter
import org.openprt.app.walk.WalkRouter

/**
 * Holds the app-wide singletons; Room wants one database instance per process. Also configures
 * WorkManager, which the manifest tells to initialize on first use instead of at startup, so
 * its workers get these singletons.
 */
class OpenPrtApplication :
    Application(),
    Configuration.Provider {
    private val gtfsDatabase: GtfsDatabase by lazy { GtfsDatabase.create(this) }

    val gtfsDao: GtfsDao get() = gtfsDatabase.gtfsDao()

    // App-wide, so the first import and the background update share its lock.
    val gtfsUpdater: GtfsUpdater by lazy {
        GtfsUpdater(
            gtfsDatabase.gtfsDao(),
            GtfsImporter(gtfsDatabase, cacheDir),
            GtfsImportLog(getSharedPreferences(GtfsImportLog.PREFS_NAME, Context.MODE_PRIVATE))
        )
    }

    val nearbyStopRepository: NearbyStopRepository by lazy {
        NearbyStopRepository(gtfsDatabase.gtfsDao(), gtfsUpdater)
    }

    val apiKeySettings: ApiKeySettings by lazy {
        ApiKeySettings(
            getSharedPreferences(ApiKeySettings.PREFS_NAME, Context.MODE_PRIVATE),
            builtInKey = BuildConfig.PRT_API_KEY
        )
    }

    val appearanceSettings: AppearanceSettings by lazy {
        AppearanceSettings(
            getSharedPreferences(AppearanceSettings.PREFS_NAME, Context.MODE_PRIVATE)
        )
    }

    val tripPlanRepository: TripPlanRepository by lazy {
        TripPlanRepository(
            RoomTransitNetworkSource(gtfsDatabase.gtfsDao()),
            feedVersion = { gtfsUpdater.lastImport.value },
            importFailed = { gtfsUpdater.lastImportFailed }
        )
    }

    val rideStops: RideStopsSource by lazy { RoomRideStopsSource(gtfsDatabase.gtfsDao()) }

    // App-wide, so its cache and its one-request-a-second spacing outlive any one screen.
    val walkRouter: WalkRouter by lazy {
        CachingWalkRouter(ValhallaWalkRouter(userAgent = "OpenPRT/${BuildConfig.VERSION_NAME}"))
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(GtfsUpdateWorkerFactory { gtfsUpdater })
            .build()
}
