package org.openprt.app.planner

import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Test
import org.openprt.app.geo.LatLng

// The fixture stops sit on one meridian, about 2.2 km apart, so only C and C2 (222 m apart) are
// within transfer walking distance of each other.
class RoutePlannerTest {
    @Test
    fun plan_directTrip_returnsOneRideBetweenTheNearestStops() {
        val planner = plannerFor(
            trip("T1", "R1", "A" to "08:00", "B" to "08:10", "C" to "08:20")
        )

        val result = planner.plan(A.location, C.location, at("07:50"))

        assertEquals(
            listOf(ride("T1", "R1", A, C, "08:00", "08:20")),
            itineraries(result).single().rides
        )
    }

    @Test
    fun plan_directTrip_walksFromTheOriginToLeaveAsTheBusDeparts() {
        val planner = plannerFor(
            trip("T1", "R1", "A" to "08:00", "B" to "08:10", "C" to "08:20")
        )
        // 0.001 degrees of latitude is 111.195 m, a 93 s walk at 1.2 m/s.
        val origin = LatLng(40.401, -80.0)

        val result = planner.plan(origin, C.location, at("07:50"))

        assertEquals(at("07:58:27"), itineraries(result).single().departureSeconds)
    }

    @Test
    fun plan_directTrip_arrivesAfterTheWalkFromTheLastStop() {
        val planner = plannerFor(
            trip("T1", "R1", "A" to "08:00", "B" to "08:10", "C" to "08:20")
        )
        val destination = LatLng(40.441, -80.0)

        val result = planner.plan(A.location, destination, at("07:50"))

        assertEquals(at("08:21:33"), itineraries(result).single().arrivalSeconds)
    }

    @Test
    fun plan_busLeavesBeforeRiderReachesStop_takesTheNextTrip() {
        val planner = plannerFor(
            trip("T1", "R1", "A" to "08:00", "C" to "08:20"),
            trip("T2", "R1", "A" to "08:30", "C" to "08:50")
        )

        val result = planner.plan(A.location, C.location, at("08:01"))

        assertEquals(
            listOf(ride("T2", "R1", A, C, "08:30", "08:50")),
            itineraries(result).single().rides
        )
    }

    @Test
    fun plan_oneTransferNeeded_returnsTwoRidesChangingAtTheSharedStop() {
        val planner = plannerFor(
            trip("T1", "R1", "A" to "08:00", "B" to "08:10", "C" to "08:20"),
            trip("T2", "R2", "C" to "08:30", "D" to "08:40")
        )

        val result = planner.plan(A.location, D.location, at("07:50"))

        assertEquals(
            listOf(
                ride("T1", "R1", A, C, "08:00", "08:20"),
                ride("T2", "R2", C, D, "08:30", "08:40")
            ),
            itineraries(result).single().rides
        )
    }

    @Test
    fun plan_transferBetweenNearbyStops_walksBetweenThem() {
        val planner = plannerFor(
            trip("T1", "R1", "A" to "08:00", "C" to "08:20"),
            trip("T2", "R2", "C2" to "08:30", "D" to "08:40")
        )

        val result = planner.plan(A.location, D.location, at("07:50"))

        // C to C2 is 222.390 m, a 186 s walk.
        assertEquals(
            WalkLeg(C, C2, 222.39016, at("08:20"), at("08:23:06")),
            itineraries(result).single().legs[2].roundedDistance()
        )
    }

    @Test
    fun plan_directAndTransferArriveTogether_returnsOnlyTheDirectTrip() {
        val planner = plannerFor(
            trip("T1", "R1", "A" to "08:00", "C" to "08:20"),
            trip("T2", "R2", "C" to "08:30", "D" to "08:40"),
            trip("T3", "R3", "A" to "08:05", "D" to "08:40")
        )

        val result = planner.plan(A.location, D.location, at("07:50"))

        assertEquals(
            listOf(listOf(ride("T3", "R3", A, D, "08:05", "08:40"))),
            itineraries(result).map { it.rides }
        )
    }

    @Test
    fun plan_transferArrivesEarlier_returnsBothDirectAndTransferTrips() {
        val planner = plannerFor(
            trip("T1", "R1", "A" to "08:00", "C" to "08:20"),
            trip("T2", "R2", "C" to "08:30", "D" to "08:40"),
            trip("T3", "R3", "A" to "08:05", "D" to "08:50")
        )

        val result = planner.plan(A.location, D.location, at("07:50"))

        assertEquals(
            listOf(
                listOf(ride("T3", "R3", A, D, "08:05", "08:50")),
                listOf(
                    ride("T1", "R1", A, C, "08:00", "08:20"),
                    ride("T2", "R2", C, D, "08:30", "08:40")
                )
            ),
            itineraries(result).map { it.rides }
        )
    }

    @Test
    fun plan_nextBusLeavesUnderTheTransferBufferAfterArrival_takesALaterOne() {
        val planner = plannerFor(
            trip("T1", "R1", "A" to "08:00", "C" to "08:20"),
            trip("T2", "R2", "C" to "08:20:30", "D" to "08:30"),
            trip("T3", "R3", "C" to "08:25", "D" to "08:35")
        )

        val result = planner.plan(A.location, D.location, at("07:50"))

        assertEquals(
            listOf(
                ride("T1", "R1", A, C, "08:00", "08:20"),
                ride("T3", "R3", C, D, "08:25", "08:35")
            ),
            itineraries(result).single().rides
        )
    }

    @Test
    fun plan_nextBusLeavesExactlyTheTransferBufferAfterArrival_takesIt() {
        val planner = plannerFor(
            trip("T1", "R1", "A" to "08:00", "C" to "08:20"),
            trip("T2", "R2", "C" to "08:21", "D" to "08:30")
        )

        val result = planner.plan(A.location, D.location, at("07:50"))

        assertEquals(
            listOf(
                ride("T1", "R1", A, C, "08:00", "08:20"),
                ride("T2", "R2", C, D, "08:21", "08:30")
            ),
            itineraries(result).single().rides
        )
    }

    @Test
    fun plan_busLeavesTheOriginStopAtTheRequestedTime_boardsItWithoutABuffer() {
        val planner = plannerFor(trip("T1", "R1", "A" to "08:00", "C" to "08:20"))

        val result = planner.plan(A.location, C.location, at("08:00"))

        assertEquals(
            listOf(ride("T1", "R1", A, C, "08:00", "08:20")),
            itineraries(result).single().rides
        )
    }

    @Test
    fun plan_cannotBoardWherePickupIsNotAllowed_findsNoConnection() {
        val planner = plannerFor(
            trip(
                "T1",
                "R1",
                "A" to "08:00",
                "C" to "08:20",
                pickupAllowedAtFirst = false
            )
        )

        val result = planner.plan(A.location, C.location, at("07:50"))

        assertEquals(PlanResult.NoRoute(NoRouteReason.NO_CONNECTION), result)
    }

    @Test
    fun plan_noStopWithinWalkOfOrigin_returnsNoRoute() {
        val planner = plannerFor(trip("T1", "R1", "A" to "08:00", "C" to "08:20"))
        // About 1.1 km south of A, the southernmost stop.
        val origin = LatLng(40.39, -80.0)

        val result = planner.plan(origin, C.location, at("07:50"))

        assertEquals(PlanResult.NoRoute(NoRouteReason.NO_STOP_NEAR_ORIGIN), result)
    }

    @Test
    fun plan_noStopWithinWalkOfDestination_returnsNoRoute() {
        val planner = plannerFor(trip("T1", "R1", "A" to "08:00", "C" to "08:20"))
        // About 1.1 km north of D, the northernmost stop.
        val destination = LatLng(40.47, -80.0)

        val result = planner.plan(A.location, destination, at("07:50"))

        assertEquals(PlanResult.NoRoute(NoRouteReason.NO_STOP_NEAR_DESTINATION), result)
    }

    @Test
    fun plan_tripsOnlyRunTheOtherWay_returnsNoConnection() {
        val planner = plannerFor(trip("T1", "R1", "A" to "08:00", "C" to "08:20"))

        val result = planner.plan(C.location, A.location, at("07:50"))

        assertEquals(PlanResult.NoRoute(NoRouteReason.NO_CONNECTION), result)
    }

    @Test
    fun plan_lastTripAlreadyLeft_returnsNoConnection() {
        val planner = plannerFor(trip("T1", "R1", "A" to "08:00", "C" to "08:20"))

        val result = planner.plan(A.location, C.location, at("08:01"))

        assertEquals(PlanResult.NoRoute(NoRouteReason.NO_CONNECTION), result)
    }

    @Test
    fun plan_transferWouldExceedTheRideLimit_returnsNoConnection() {
        val network = network(
            trip("T1", "R1", "A" to "08:00", "C" to "08:20"),
            trip("T2", "R2", "C" to "08:30", "D" to "08:40")
        )
        val planner = RoutePlanner(network, maxRides = 1)

        val result = planner.plan(A.location, D.location, at("07:50"))

        assertEquals(PlanResult.NoRoute(NoRouteReason.NO_CONNECTION), result)
    }

    @Test
    fun planArrivingBy_severalTripsInTime_ridesTheLatestOneThatArrivesByTheDeadline() {
        val planner = plannerFor(
            trip("T1", "R1", "A" to "08:00", "C" to "08:20"),
            trip("T2", "R1", "A" to "08:30", "C" to "08:50"),
            trip("T3", "R1", "A" to "09:00", "C" to "09:20")
        )

        val result = planner.planArrivingBy(A.location, C.location, at("09:00"))

        assertEquals(
            listOf(ride("T2", "R1", A, C, "08:30", "08:50")),
            itineraries(result).single().rides
        )
    }

    @Test
    fun planArrivingBy_tripArrivesExactlyAtTheDeadline_ridesIt() {
        val planner = plannerFor(
            trip("T1", "R1", "A" to "08:00", "C" to "08:20"),
            trip("T2", "R1", "A" to "08:30", "C" to "08:50")
        )

        val result = planner.planArrivingBy(A.location, C.location, at("08:50"))

        assertEquals(
            listOf(ride("T2", "R1", A, C, "08:30", "08:50")),
            itineraries(result).single().rides
        )
    }

    @Test
    fun planArrivingBy_noTripArrivesBeforeTheDeadline_returnsNoConnection() {
        val planner = plannerFor(trip("T1", "R1", "A" to "08:00", "C" to "08:20"))

        val result = planner.planArrivingBy(A.location, C.location, at("08:10"))

        assertEquals(PlanResult.NoRoute(NoRouteReason.NO_CONNECTION), result)
    }

    @Test
    fun planArrivingBy_laterFeederArrivesUnderTheTransferBuffer_ridesAnEarlierOne() {
        val planner = plannerFor(
            trip("T0", "R1", "A" to "07:50", "C" to "08:10"),
            trip("T1", "R1", "A" to "08:00", "C" to "08:20"),
            trip("T2", "R2", "C" to "08:20:30", "D" to "08:30")
        )

        val result = planner.planArrivingBy(A.location, D.location, at("08:30"))

        assertEquals(
            listOf(
                ride("T0", "R1", A, C, "07:50", "08:10"),
                ride("T2", "R2", C, D, "08:20:30", "08:30")
            ),
            itineraries(result).single().rides
        )
    }

    @Test
    fun planArrivingBy_walkFromTheLastStopWouldEndLate_ridesAnEarlierTrip() {
        val planner = plannerFor(
            trip("T0", "R1", "A" to "07:50", "C" to "08:10"),
            trip("T1", "R1", "A" to "08:00", "C" to "08:20")
        )
        // 111.195 m north of C, a 93 s walk: T1 would arrive at 08:21:33.
        val destination = LatLng(40.441, -80.0)

        val result = planner.planArrivingBy(A.location, destination, at("08:21"))

        assertEquals(
            listOf(ride("T0", "R1", A, C, "07:50", "08:10")),
            itineraries(result).single().rides
        )
    }

    @Test
    fun planArrivingBy_walkFromTheLastStop_startsAsTheBusArrives() {
        val planner = plannerFor(trip("T1", "R1", "A" to "08:00", "C" to "08:20"))
        val destination = LatLng(40.441, -80.0)

        val result = planner.planArrivingBy(A.location, destination, at("08:30"))

        assertEquals(at("08:21:33"), itineraries(result).single().arrivalSeconds)
    }

    @Test
    fun planArrivingBy_walkToTheFirstStop_endsAsTheBusLeaves() {
        val planner = plannerFor(trip("T1", "R1", "A" to "08:00", "C" to "08:20"))
        // 111.195 m north of A, a 93 s walk.
        val origin = LatLng(40.401, -80.0)

        val result = planner.planArrivingBy(origin, C.location, at("08:30"))

        assertEquals(at("07:58:27"), itineraries(result).single().departureSeconds)
    }

    @Test
    fun planArrivingBy_transferLeavesLater_keepsBothDirectAndTransferTrips() {
        val planner = plannerFor(
            trip("T3", "R3", "A" to "08:00", "D" to "08:40"),
            trip("T1", "R1", "A" to "08:10", "C" to "08:20"),
            trip("T2", "R2", "C" to "08:30", "D" to "08:40")
        )

        val result = planner.planArrivingBy(A.location, D.location, at("08:45"))

        assertEquals(
            listOf(
                listOf(ride("T3", "R3", A, D, "08:00", "08:40")),
                listOf(
                    ride("T1", "R1", A, C, "08:10", "08:20"),
                    ride("T2", "R2", C, D, "08:30", "08:40")
                )
            ),
            itineraries(result).map { it.rides }
        )
    }

    @Test
    fun planArrivingBy_transferLeavesNoLater_returnsOnlyTheDirectTrip() {
        val planner = plannerFor(
            trip("T3", "R3", "A" to "08:10", "D" to "08:40"),
            trip("T1", "R1", "A" to "08:10", "C" to "08:20"),
            trip("T2", "R2", "C" to "08:30", "D" to "08:40")
        )

        val result = planner.planArrivingBy(A.location, D.location, at("08:45"))

        assertEquals(
            listOf(listOf(ride("T3", "R3", A, D, "08:10", "08:40"))),
            itineraries(result).map { it.rides }
        )
    }

    @Test
    fun planArrivingBy_transferBetweenNearbyStops_walksBetweenThemAfterTheFirstRide() {
        val planner = plannerFor(
            trip("T1", "R1", "A" to "08:00", "C" to "08:20"),
            trip("T2", "R2", "C2" to "08:30", "D" to "08:40")
        )

        val result = planner.planArrivingBy(A.location, D.location, at("08:45"))

        // C to C2 is 222.390 m, a 186 s walk, timed to reach C2 as T2 leaves.
        assertEquals(
            WalkLeg(C, C2, 222.39016, at("08:26:54"), at("08:30")),
            itineraries(result).single().legs[2].roundedDistance()
        )
    }

    @Test
    fun planArrivingBy_cannotGetOffWhereDropOffIsNotAllowed_findsNoConnection() {
        val planner = plannerFor(
            ScheduledTrip(
                "T1",
                "R1",
                null,
                listOf(
                    TripStop("A", at("08:00"), at("08:00")),
                    TripStop("C", at("08:20"), at("08:20"), dropOffAllowed = false)
                )
            )
        )

        val result = planner.planArrivingBy(A.location, C.location, at("08:30"))

        assertEquals(PlanResult.NoRoute(NoRouteReason.NO_CONNECTION), result)
    }

    @Test
    fun planArrivingBy_noStopWithinWalkOfDestination_returnsNoRoute() {
        val planner = plannerFor(trip("T1", "R1", "A" to "08:00", "C" to "08:20"))

        val result = planner.planArrivingBy(A.location, LatLng(40.47, -80.0), at("08:30"))

        assertEquals(PlanResult.NoRoute(NoRouteReason.NO_STOP_NEAR_DESTINATION), result)
    }

    @Test(expected = IllegalArgumentException::class)
    fun network_tripVisitsUnknownStop_throws() {
        network(trip("T1", "R1", "A" to "08:00", "Z" to "08:20"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun constructor_zeroRides_throws() {
        RoutePlanner(network(), maxRides = 0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun constructor_negativeTransferBuffer_throws() {
        RoutePlanner(network(), minTransferSeconds = -1)
    }

    private companion object {
        val A = TransitStop("A", "Stop A", LatLng(40.40, -80.0))
        val B = TransitStop("B", "Stop B", LatLng(40.42, -80.0))
        val C = TransitStop("C", "Stop C", LatLng(40.44, -80.0))
        val C2 = TransitStop("C2", "Stop C2", LatLng(40.442, -80.0))
        val D = TransitStop("D", "Stop D", LatLng(40.46, -80.0))

        fun network(vararg trips: ScheduledTrip) =
            TransitNetwork(listOf(A, B, C, C2, D), trips.toList())

        fun plannerFor(vararg trips: ScheduledTrip) = RoutePlanner(network(*trips))

        fun trip(
            tripId: String,
            routeId: String,
            vararg stops: Pair<String, String>,
            pickupAllowedAtFirst: Boolean = true
        ) = ScheduledTrip(
            tripId = tripId,
            routeId = routeId,
            headsign = null,
            stops = stops.mapIndexed { index, (stopId, time) ->
                TripStop(
                    stopId = stopId,
                    arrivalSeconds = at(time),
                    departureSeconds = at(time),
                    pickupAllowed = index > 0 || pickupAllowedAtFirst
                )
            }
        )

        fun ride(
            tripId: String,
            routeId: String,
            from: TransitStop,
            to: TransitStop,
            start: String,
            end: String
        ) = RideLeg(tripId, routeId, null, from, to, at(start), at(end))

        fun at(time: String): Int = LocalTime.parse(time).toSecondOfDay()

        fun itineraries(result: PlanResult): List<Itinerary> =
            (result as PlanResult.Found).itineraries

        // Distances are doubles from haversine; five decimals (0.01 mm) is enough to compare.
        fun Leg.roundedDistance(): Leg = when (this) {
            is WalkLeg -> copy(distanceMeters = Math.round(distanceMeters * 100_000) / 100_000.0)
            is RideLeg -> this
        }
    }
}
