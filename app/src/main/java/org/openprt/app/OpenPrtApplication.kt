package org.openprt.app

import android.app.Application
import org.openprt.app.data.gtfs.GtfsDatabase
import org.openprt.app.data.gtfs.GtfsImporter
import org.openprt.app.data.gtfs.NearbyStopRepository

/** Holds the app-wide singletons; Room wants one database instance per process. */
class OpenPrtApplication : Application() {
    val nearbyStopRepository: NearbyStopRepository by lazy {
        val dao = GtfsDatabase.create(this).gtfsDao()
        NearbyStopRepository(dao, GtfsImporter(dao))
    }
}
