package org.openprt.app.data.gtfs

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction

// The latitude index serves the bounding-box query in getStopsInBox; one column is enough because
// a 400 m latitude band across Pittsburgh holds only a few hundred stops.
@Entity(tableName = "stops", indices = [Index("latitude")])
data class StopEntity(
    @PrimaryKey val stopId: String,
    val code: String?,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val locationType: Int
)

@Entity(tableName = "routes")
data class RouteEntity(
    @PrimaryKey val routeId: String,
    val shortName: String?,
    val longName: String?,
    val type: Int,
    val color: String?
)

@Dao
interface GtfsDao {
    /**
     * Replaces all stops and routes in one transaction, so readers see either the old feed or
     * the new one, and stops dropped from the new feed disappear. If any insert fails the old
     * data stays.
     */
    @Transaction
    suspend fun replaceAll(stops: List<StopEntity>, routes: List<RouteEntity>) {
        deleteAllStops()
        deleteAllRoutes()
        insertStops(stops)
        insertRoutes(routes)
    }

    @Query("SELECT * FROM stops ORDER BY stopId")
    suspend fun getAllStops(): List<StopEntity>

    /**
     * Stops inside the given latitude / longitude ranges (inclusive), unordered. Runs in SQLite
     * on the latitude index, so it does not load the whole stops table.
     */
    @Query(
        "SELECT * FROM stops " +
            "WHERE latitude BETWEEN :minLatitude AND :maxLatitude " +
            "AND longitude BETWEEN :minLongitude AND :maxLongitude"
    )
    suspend fun getStopsInBox(
        minLatitude: Double,
        maxLatitude: Double,
        minLongitude: Double,
        maxLongitude: Double
    ): List<StopEntity>

    @Query("SELECT COUNT(*) FROM stops")
    suspend fun countStops(): Int

    @Query("SELECT * FROM routes ORDER BY routeId")
    suspend fun getAllRoutes(): List<RouteEntity>

    @Query("DELETE FROM stops")
    suspend fun deleteAllStops()

    @Query("DELETE FROM routes")
    suspend fun deleteAllRoutes()

    @Insert
    suspend fun insertStops(stops: List<StopEntity>)

    @Insert
    suspend fun insertRoutes(routes: List<RouteEntity>)
}

/** Static PRT GTFS data on the device. Rebuilt from the feed, so it holds nothing user-made. */
@Database(entities = [StopEntity::class, RouteEntity::class], version = 2)
abstract class GtfsDatabase : RoomDatabase() {
    abstract fun gtfsDao(): GtfsDao

    companion object {
        private const val FILE_NAME = "gtfs.db"

        /**
         * Opens the on-disk database; callers should keep one instance for the app's lifetime.
         * A schema change drops the old tables instead of migrating: everything here comes from
         * the GTFS feed, so the next import rebuilds it.
         */
        fun create(context: Context): GtfsDatabase =
            Room.databaseBuilder(context, GtfsDatabase::class.java, FILE_NAME)
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
    }
}
