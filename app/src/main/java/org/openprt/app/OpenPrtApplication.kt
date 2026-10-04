package org.openprt.app

import android.app.Application
import android.content.Context
import org.openprt.app.data.gtfs.GtfsDao
import org.openprt.app.data.gtfs.GtfsDatabase
import org.openprt.app.data.gtfs.GtfsImporter
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

/** Holds the app-wide singletons; Room wants one database instance per process. */
class OpenPrtApplication : Application() {
    private val gtfsDatabase: GtfsDatabase by lazy { GtfsDatabase.create(this) }

    val gtfsDao: GtfsDao get() = gtfsDatabase.gtfsDao()

    val nearbyStopRepository: NearbyStopRepository by lazy {
        NearbyStopRepository(gtfsDatabase.gtfsDao(), GtfsImporter(gtfsDatabase, cacheDir))
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
        TripPlanRepository(RoomTransitNetworkSource(gtfsDatabase.gtfsDao()))
    }

    val rideStops: RideStopsSource by lazy { RoomRideStopsSource(gtfsDatabase.gtfsDao()) }

    // App-wide, so its cache and its one-request-a-second spacing outlive any one screen.
    val walkRouter: WalkRouter by lazy {
        CachingWalkRouter(ValhallaWalkRouter(userAgent = "OpenPRT/${BuildConfig.VERSION_NAME}"))
    }
}
