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
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import java.time.LocalDate

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

/**
 * The ID TrueTime uses for this stop: the GTFS `stop_code` (the number printed on PRT stop
 * signs), falling back to `stop_id` when the feed has no code. Checked against real TrueTime
 * responses on 2026-10-01.
 */
val StopEntity.trueTimeStopId: String get() = code ?: stopId

@Entity(tableName = "routes")
data class RouteEntity(
    @PrimaryKey val routeId: String,
    val shortName: String?,
    val longName: String?,
    val type: Int,
    val color: String?
)

/** A row of trips.txt: one run of a vehicle along a route, on the days [serviceId] runs. */
@Entity(tableName = "trips")
data class TripEntity(
    @PrimaryKey val tripId: String,
    val routeId: String,
    val serviceId: String,
    val headsign: String?,
    /** GTFS direction_id, 0 or 1; its meaning (inbound / outbound) differs per route. */
    val directionId: Int?
)

/**
 * A row of stop_times.txt. Times are seconds after the start of the trip's service day and can
 * pass 24 hours for trips that run past midnight; [serviceTime] turns them into instants.
 * [pickupAllowed] is false where riders cannot board (usually the last stop of a trip) and
 * [dropOffAllowed] is false where they cannot get off (usually the first).
 */
// The primary key also serves "the stops of a trip, in order"; the stop index serves
// "departures from a stop after a time".
@Entity(
    tableName = "stop_times",
    primaryKeys = ["tripId", "stopSequence"],
    indices = [Index("stopId", "departureSeconds")]
)
data class StopTimeEntity(
    val tripId: String,
    val stopSequence: Int,
    val stopId: String,
    val arrivalSeconds: Int,
    val departureSeconds: Int,
    val pickupAllowed: Boolean,
    val dropOffAllowed: Boolean
)

/** A row of calendar.txt: the weekdays a service runs between two dates (both inclusive). */
@Entity(tableName = "calendar")
data class ServiceCalendarEntity(
    @PrimaryKey val serviceId: String,
    val monday: Boolean,
    val tuesday: Boolean,
    val wednesday: Boolean,
    val thursday: Boolean,
    val friday: Boolean,
    val saturday: Boolean,
    val sunday: Boolean,
    val startDate: LocalDate,
    val endDate: LocalDate
)

/**
 * A row of calendar_dates.txt: on [date] the service runs ([added] true, GTFS exception_type 1)
 * or does not ([added] false, exception_type 2), whatever calendar.txt says.
 */
@Entity(tableName = "calendar_dates", primaryKeys = ["serviceId", "date"])
data class CalendarDateEntity(val serviceId: String, val date: LocalDate, val added: Boolean)

/** One scheduled stop of a trip where riders can board, joined with its trip. */
data class StopDepartureRow(
    val tripId: String,
    val routeId: String,
    val headsign: String?,
    val stopSequence: Int,
    val departureSeconds: Int
)

/** One stop of a trip, joined with the stop's name. */
data class TripStopTimeRow(
    val stopSequence: Int,
    val stopName: String,
    val arrivalSeconds: Int,
    val departureSeconds: Int
)

@Dao
interface GtfsDao {
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

    /**
     * The stops TrueTime knows as [trueTimeStopId] (see [trueTimeStopId]). Scans the stops table,
     * which is a few thousand rows, so it suits one lookup per tap, not a loop.
     */
    @Query(
        "SELECT * FROM stops WHERE code = :trueTimeStopId " +
            "OR (code IS NULL AND stopId = :trueTimeStopId) ORDER BY stopId"
    )
    suspend fun getStopsByTrueTimeId(trueTimeStopId: String): List<StopEntity>

    @Query("SELECT * FROM routes ORDER BY routeId")
    suspend fun getAllRoutes(): List<RouteEntity>

    @Query("SELECT * FROM routes WHERE routeId IN (:routeIds)")
    suspend fun getRoutes(routeIds: Collection<String>): List<RouteEntity>

    @Query("SELECT COUNT(*) FROM stop_times")
    suspend fun countStopTimes(): Int

    /** The stops of [tripId] in travel order; runs on the primary key. */
    @Query("SELECT * FROM stop_times WHERE tripId = :tripId ORDER BY stopSequence")
    suspend fun getStopTimesOfTrip(tripId: String): List<StopTimeEntity>

    /** The stops [tripId] serves, in travel order; a loop trip lists a stop once per visit. */
    @Query(
        "SELECT s.* FROM stop_times st JOIN stops s ON s.stopId = st.stopId " +
            "WHERE st.tripId = :tripId ORDER BY st.stopSequence"
    )
    suspend fun getStopsOfTrip(tripId: String): List<StopEntity>

    /**
     * The stops of [tripId] from [fromSequence] on, with their names, in travel order; runs on
     * the primary key.
     */
    @Query(
        "SELECT st.stopSequence, s.name AS stopName, st.arrivalSeconds, st.departureSeconds " +
            "FROM stop_times st JOIN stops s ON s.stopId = st.stopId " +
            "WHERE st.tripId = :tripId AND st.stopSequence >= :fromSequence " +
            "ORDER BY st.stopSequence"
    )
    suspend fun getTripStopTimesFrom(tripId: String, fromSequence: Int): List<TripStopTimeRow>

    /** Calendar rows whose date range contains [date], whatever their weekdays. */
    @Query("SELECT * FROM calendar WHERE :date BETWEEN startDate AND endDate")
    suspend fun getCalendarsCovering(date: LocalDate): List<ServiceCalendarEntity>

    @Query("SELECT * FROM calendar_dates WHERE date = :date")
    suspend fun getCalendarDatesOn(date: LocalDate): List<CalendarDateEntity>

    /**
     * Boardable stop times at [stopId] at or after [afterSeconds] on trips of [serviceIds],
     * earliest first. Runs on the (stopId, departureSeconds) index.
     */
    @Query(
        "SELECT st.tripId, t.routeId, t.headsign, st.stopSequence, st.departureSeconds " +
            "FROM stop_times st JOIN trips t ON t.tripId = st.tripId " +
            "WHERE st.stopId = :stopId AND st.departureSeconds >= :afterSeconds " +
            "AND st.pickupAllowed AND t.serviceId IN (:serviceIds) " +
            "ORDER BY st.departureSeconds, t.routeId, st.tripId LIMIT :limit"
    )
    suspend fun getDeparturesAfter(
        stopId: String,
        afterSeconds: Int,
        serviceIds: Collection<String>,
        limit: Int
    ): List<StopDepartureRow>

    @Query("SELECT * FROM trips WHERE serviceId IN (:serviceIds)")
    suspend fun getTripsOfServices(serviceIds: Collection<String>): List<TripEntity>

    /**
     * Every stop time of the trips of [serviceIds], grouped by trip and in travel order within
     * each trip. Loads a whole service day (about 300,000 rows for PRT), so keep the result
     * rather than asking again.
     */
    // The subquery lets SQLite look the trips up on the primary key; a join made it scan all of
    // stop_times instead, five times slower on the real feed.
    @Query(
        "SELECT * FROM stop_times " +
            "WHERE tripId IN (SELECT tripId FROM trips WHERE serviceId IN (:serviceIds)) " +
            "ORDER BY tripId, stopSequence"
    )
    suspend fun getStopTimesOfServices(serviceIds: Collection<String>): List<StopTimeEntity>

    /** Empties every table; the importer calls it inside the transaction that refills them. */
    suspend fun deleteAll() {
        deleteAllStops()
        deleteAllRoutes()
        deleteAllTrips()
        deleteAllStopTimes()
        deleteAllCalendars()
        deleteAllCalendarDates()
    }

    @Query("DELETE FROM stops")
    suspend fun deleteAllStops()

    @Query("DELETE FROM routes")
    suspend fun deleteAllRoutes()

    @Query("DELETE FROM trips")
    suspend fun deleteAllTrips()

    @Query("DELETE FROM stop_times")
    suspend fun deleteAllStopTimes()

    @Query("DELETE FROM calendar")
    suspend fun deleteAllCalendars()

    @Query("DELETE FROM calendar_dates")
    suspend fun deleteAllCalendarDates()

    @Insert
    suspend fun insertStops(stops: List<StopEntity>)

    @Insert
    suspend fun insertRoutes(routes: List<RouteEntity>)

    @Insert
    suspend fun insertTrips(trips: List<TripEntity>)

    @Insert
    suspend fun insertStopTimes(stopTimes: List<StopTimeEntity>)

    @Insert
    suspend fun insertCalendars(calendars: List<ServiceCalendarEntity>)

    @Insert
    suspend fun insertCalendarDates(calendarDates: List<CalendarDateEntity>)
}

/** Stores dates as epoch days, so SQL comparisons on them follow calendar order. */
class GtfsConverters {
    @TypeConverter
    fun fromEpochDay(epochDay: Long): LocalDate = LocalDate.ofEpochDay(epochDay)

    @TypeConverter
    fun toEpochDay(date: LocalDate): Long = date.toEpochDay()
}

/** Static PRT GTFS data on the device. Rebuilt from the feed, so it holds nothing user-made. */
@Database(
    entities = [
        StopEntity::class,
        RouteEntity::class,
        TripEntity::class,
        StopTimeEntity::class,
        ServiceCalendarEntity::class,
        CalendarDateEntity::class
    ],
    version = 3
)
@TypeConverters(GtfsConverters::class)
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
