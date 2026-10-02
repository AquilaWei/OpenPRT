package org.openprt.app

import android.app.Application
import android.content.Context
import org.openprt.app.data.gtfs.GtfsDatabase
import org.openprt.app.data.gtfs.GtfsImporter
import org.openprt.app.data.gtfs.NearbyStopRepository
import org.openprt.app.data.gtfs.RoomTransitNetworkSource
import org.openprt.app.data.gtfs.TripPlanRepository
import org.openprt.app.data.settings.ApiKeySettings

/** Holds the app-wide singletons; Room wants one database instance per process. */
class OpenPrtApplication : Application() {
    private val gtfsDatabase: GtfsDatabase by lazy { GtfsDatabase.create(this) }

    val nearbyStopRepository: NearbyStopRepository by lazy {
        NearbyStopRepository(gtfsDatabase.gtfsDao(), GtfsImporter(gtfsDatabase, cacheDir))
    }

    val apiKeySettings: ApiKeySettings by lazy {
        ApiKeySettings(
            getSharedPreferences(ApiKeySettings.PREFS_NAME, Context.MODE_PRIVATE),
            builtInKey = BuildConfig.PRT_API_KEY
        )
    }

    val tripPlanRepository: TripPlanRepository by lazy {
        TripPlanRepository(RoomTransitNetworkSource(gtfsDatabase.gtfsDao()))
    }
}
