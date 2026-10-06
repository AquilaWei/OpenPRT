package org.openprt.app.trip

import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.openprt.app.data.gtfs.TripPlan
import org.openprt.app.data.truetime.Prediction
import org.openprt.app.data.truetime.PredictionType
import org.openprt.app.geo.LatLng
import org.openprt.app.planner.Itinerary
import org.openprt.app.planner.RideLeg
import org.openprt.app.planner.TransitStop
import org.openprt.app.planner.WalkLeg
import org.openprt.app.walk.WalkPath

/**
 * The plan is on Thursday 2026-10-01 (EDT, UTC-4): walk 200 s, 61C CMU 07:00 (11:00Z) → Fifth
 * Ave 07:13:20, walk 120 s, 71B 07:20 → Steel Plaza 07:26:40, walk 100 s to arrive 07:28:20.
 */
class TripOptionTest {
    @Test
    fun toOption_transferPlan_summarizesLegsInOrder() {
        val option = TRANSFER_PLAN.toOption(emptyList(), NOW)

        assertEquals(
            listOf(
                LegSummary.Walk(4),
                LegSummary.Ride("61C"),
                LegSummary.Walk(2),
                LegSummary.Ride("71B"),
                LegSummary.Walk(2)
            ),
            option.legs
        )
    }

    @Test
    fun toOption_transferPlan_countsOneTransfer() {
        val option = TRANSFER_PLAN.toOption(emptyList(), NOW)

        assertEquals(1, option.transfers)
    }

    @Test
    fun toOption_noPredictions_totalMinutesRunFromSettingOffToArrivalRoundedUp() {
        // 06:56:40 → 07:28:20 is 31 min 40 s.
        val option = TRANSFER_PLAN.toOption(emptyList(), NOW)

        assertEquals(32, option.totalMinutes)
    }

    @Test
    fun toOption_noPredictions_boardsAtTimetableTime() {
        val option = TRANSFER_PLAN.toOption(emptyList(), NOW)

        assertEquals(Instant.parse("2026-10-01T11:00:00Z"), option.boardingTime)
        assertFalse(option.live)
    }

    @Test
    fun toOption_zeroLengthWalk_isLeftOut() {
        val plan = TripPlan(
            DATE,
            Itinerary(
                listOf(
                    WalkLeg(null, CMU, 0.0, 25_200, 25_200),
                    RideLeg("T1", "61C", null, CMU, FIFTH, 25_200, 26_000),
                    WalkLeg(FIFTH, null, 0.0, 26_000, 26_000)
                )
            )
        )

        val option = plan.toOption(emptyList(), NOW)

        assertEquals(listOf(LegSummary.Ride("61C")), option.legs)
    }

    @Test
    fun toOption_predictionOfFirstBus_boardsAtPredictedTime() {
        val option = TRANSFER_PLAN.toOption(
            listOf(prediction("61C", "8312", "2026-10-01T11:04:00Z")),
            NOW
        )

        assertEquals(Instant.parse("2026-10-01T11:04:00Z"), option.boardingTime)
    }

    @Test
    fun toOption_predictionOfFirstBus_keepsTimetableArrival() {
        val option = TRANSFER_PLAN.toOption(
            listOf(prediction("61C", "8312", "2026-10-01T11:04:00Z")),
            NOW
        )

        assertEquals(Instant.parse("2026-10-01T11:28:20Z"), option.arrivalTime)
    }

    @Test
    fun toOption_firstBusLaterThanTransferWait_pushesArrivalBackByTheRest() {
        // 61C 10 min late (600 s); the wait for 71B is 280 s, so arrival moves 320 s.
        val option = TRANSFER_PLAN.toOption(
            listOf(prediction("61C", "8312", "2026-10-01T11:10:00Z")),
            NOW
        )

        assertEquals(Instant.parse("2026-10-01T11:33:40Z"), option.arrivalTime)
    }

    @Test
    fun toOption_directBusRunsLate_arrivesThatMuchLater() {
        // Regression: a 14-minute-late bus showed arrival before departure (-3 min).
        val option = DIRECT_PLAN.toOption(
            listOf(prediction("61C", "8312", "2026-10-01T11:14:00Z")),
            NOW
        )

        assertEquals(Instant.parse("2026-10-01T11:45:40Z"), option.arrivalTime)
    }

    @Test
    fun toOption_directBusRunsLate_totalMinutesStayTheTripLength() {
        val option = DIRECT_PLAN.toOption(
            listOf(prediction("61C", "8312", "2026-10-01T11:14:00Z")),
            NOW
        )

        // 240 s walk + 1800 s ride + 100 s walk.
        assertEquals(36L, option.totalMinutes)
    }

    @Test
    fun toOption_firstBusEarly_keepsTimetableArrival() {
        val option = DIRECT_PLAN.toOption(
            listOf(prediction("61C", "8312", "2026-10-01T10:58:00Z")),
            NOW
        )

        assertEquals(Instant.parse("2026-10-01T11:31:40Z"), option.arrivalTime)
    }

    @Test
    fun toOption_predictionOfOtherRoute_isNotLive() {
        val option = TRANSFER_PLAN.toOption(
            listOf(prediction("71B", "8312", "2026-10-01T11:04:00Z")),
            NOW
        )

        assertFalse(option.live)
    }

    @Test
    fun toOption_predictionAtOtherStop_isNotLive() {
        val option = TRANSFER_PLAN.toOption(
            listOf(prediction("61C", "2635", "2026-10-01T11:04:00Z")),
            NOW
        )

        assertFalse(option.live)
    }

    @Test
    fun toOption_predictionMoreThan15MinutesFromTimetable_isNotLive() {
        val option = TRANSFER_PLAN.toOption(
            listOf(prediction("61C", "8312", "2026-10-01T11:16:00Z")),
            NOW
        )

        assertFalse(option.live)
    }

    @Test
    fun toOption_predictedBusLeavesBeforeUserCanWalkThere_isNotUsed() {
        // At 10:58 the 200 s walk reaches the stop at 11:01:20, after the 11:00:30 bus.
        val option = TRANSFER_PLAN.toOption(
            listOf(prediction("61C", "8312", "2026-10-01T11:00:30Z")),
            Instant.parse("2026-10-01T10:58:00Z")
        )

        assertFalse(option.live)
    }

    @Test
    fun toOption_twoPredictionsOfFirstBus_takesTheOneClosestToTimetable() {
        val option = TRANSFER_PLAN.toOption(
            listOf(
                prediction("61C", "8312", "2026-10-01T10:56:00Z"),
                prediction("61C", "8312", "2026-10-01T11:02:00Z"),
                prediction("61C", "8312", "2026-10-01T11:12:00Z")
            ),
            NOW
        )

        assertEquals(Instant.parse("2026-10-01T11:02:00Z"), option.boardingTime)
    }

    @Test
    fun withWalks_firstWalkLongerAlongStreets_setsOffEarlier() {
        // 300 s instead of 200 s before the 11:00Z bus.
        val option = TRANSFER_PLAN.toOption(emptyList(), NOW)
            .withWalks(listOf(streets(300), straight(), straight()), NOW)

        assertEquals(Instant.parse("2026-10-01T10:55:00Z"), option.departureTime)
    }

    @Test
    fun withWalks_firstWalkLongerAlongStreets_countsItInTheTotal() {
        // 10:55:00 → 11:28:20 is 33 min 20 s.
        val option = TRANSFER_PLAN.toOption(emptyList(), NOW)
            .withWalks(listOf(streets(300), straight(), straight()), NOW)

        assertEquals(34, option.totalMinutes)
    }

    @Test
    fun withWalks_firstWalkLongerAlongStreets_showsItsMinutesInTheLegs() {
        val option = TRANSFER_PLAN.toOption(emptyList(), NOW)
            .withWalks(listOf(streets(300), straight(), straight()), NOW)

        assertEquals(LegSummary.Walk(5), option.legs.first())
    }

    @Test
    fun withWalks_firstWalkTooLongToSetOffInTime_missesTheBus() {
        // 700 s before 11:00Z means setting off at 10:48:20Z, before NOW.
        val option = TRANSFER_PLAN.toOption(emptyList(), NOW)
            .withWalks(listOf(streets(700), straight(), straight()), NOW)

        assertTrue(option.missesBus)
    }

    @Test
    fun withWalks_firstWalkLongerButStillInTime_doesNotMissTheBus() {
        val option = TRANSFER_PLAN.toOption(emptyList(), NOW)
            .withWalks(listOf(streets(300), straight(), straight()), NOW)

        assertFalse(option.missesBus)
    }

    @Test
    fun withWalks_transferWalkLongerThanTheWait_missesTheBus() {
        // 500 s instead of 120 s; the wait for 71B is 280 s.
        val option = TRANSFER_PLAN.toOption(emptyList(), NOW)
            .withWalks(listOf(straight(), streets(500), straight()), NOW)

        assertTrue(option.missesBus)
    }

    @Test
    fun withWalks_transferWalkLongerThanTheWait_pushesArrivalBackByTheRest() {
        // 380 s longer, 280 s of it absorbed by the wait: arrival moves 100 s.
        val option = TRANSFER_PLAN.toOption(emptyList(), NOW)
            .withWalks(listOf(straight(), streets(500), straight()), NOW)

        assertEquals(Instant.parse("2026-10-01T11:30:00Z"), option.arrivalTime)
    }

    @Test
    fun withWalks_transferWalkLongerWithinTheWait_keepsArrivalAndCatchesTheBus() {
        // 300 s instead of 120 s fits in the 280 s wait.
        val option = TRANSFER_PLAN.toOption(emptyList(), NOW)
            .withWalks(listOf(straight(), streets(300), straight()), NOW)

        assertEquals(Instant.parse("2026-10-01T11:28:20Z"), option.arrivalTime)
        assertFalse(option.missesBus)
    }

    @Test
    fun withWalks_lastWalkLongerAlongStreets_arrivesThatMuchLater() {
        val option = TRANSFER_PLAN.toOption(emptyList(), NOW)
            .withWalks(listOf(straight(), straight(), streets(400)), NOW)

        assertEquals(Instant.parse("2026-10-01T11:33:20Z"), option.arrivalTime)
    }

    @Test
    fun withWalks_lastWalkLongerPastTheDeadline_isLateBecauseOfWalking() {
        val deadline = Instant.parse("2026-10-01T11:30:00Z")
        val option = TRANSFER_PLAN.toOption(emptyList(), NOW, deadline)
            .withWalks(listOf(straight(), straight(), streets(400)), NOW)

        assertTrue(option.late)
        assertTrue(option.walksLonger)
    }

    @Test
    fun withWalks_shorterAlongStreets_isNotWalksLonger() {
        val option = TRANSFER_PLAN.toOption(emptyList(), NOW)
            .withWalks(listOf(streets(100), straight(), straight()), NOW)

        assertFalse(option.walksLonger)
    }

    @Test
    fun withWalks_appliedTwice_givesTheSameOption() {
        val walks = listOf(streets(300), streets(500), streets(400))
        val once = TRANSFER_PLAN.toOption(emptyList(), NOW).withWalks(walks, NOW)

        assertEquals(once, once.withWalks(walks, NOW))
    }

    @Test
    fun withWalks_liveFirstBus_keepsItsPredictedBoarding() {
        val option = TRANSFER_PLAN.toOption(
            listOf(prediction("61C", "8312", "2026-10-01T11:03:00Z")),
            NOW
        ).withWalks(listOf(streets(300), straight(), straight()), NOW)

        assertEquals(Instant.parse("2026-10-01T10:58:00Z"), option.departureTime)
    }

    private companion object {
        val NOW: Instant = Instant.parse("2026-10-01T10:50:00Z")
        val DATE: LocalDate = LocalDate.of(2026, 10, 1)

        val CMU = TransitStop("s8312", "Forbes Ave at Morewood", LatLng(40.4443, -79.9532), "8312")
        val FIFTH = TransitStop("s2635", "Fifth Ave at Craig", LatLng(40.4460, -79.9490), "2635")
        val FIFTH_OPPOSITE =
            TransitStop("s2636", "Fifth Ave opp Craig", LatLng(40.4462, -79.9492), "2636")
        val STEEL_PLAZA = TransitStop("s10", "Steel Plaza", LatLng(40.4406, -79.9959), "10")

        /** Walk 4 min to 61C at 07:00 local (11:00Z), ride 30 min, walk 100 s: arrive 11:31:40Z. */
        val DIRECT_PLAN = TripPlan(
            DATE,
            Itinerary(
                listOf(
                    WalkLeg(null, CMU, 240.0, 24_960, 25_200),
                    RideLeg("T1", "61C", "DOWNTOWN", CMU, STEEL_PLAZA, 25_200, 27_000),
                    WalkLeg(STEEL_PLAZA, null, 120.0, 27_000, 27_100)
                )
            )
        )

        val TRANSFER_PLAN = TripPlan(
            DATE,
            Itinerary(
                listOf(
                    WalkLeg(null, CMU, 240.0, 25_000, 25_200),
                    RideLeg("T1", "61C", "DOWNTOWN", CMU, FIFTH, 25_200, 26_000),
                    WalkLeg(FIFTH, FIFTH_OPPOSITE, 140.0, 26_000, 26_120),
                    RideLeg("T9", "71B", "DOWNTOWN", FIFTH_OPPOSITE, STEEL_PLAZA, 26_400, 26_800),
                    WalkLeg(STEEL_PLAZA, null, 120.0, 26_800, 26_900)
                )
            )
        )

        /** A walk routed along the streets; its line plays no part in the times. */
        fun streets(seconds: Long) = WalkPath.Streets(emptyList(), seconds)

        fun straight() = WalkPath.Straight(CMU.location, CMU.location)

        fun prediction(route: String, stopId: String, time: String) = Prediction(
            generatedAt = NOW,
            type = PredictionType.DEPARTURE,
            stopId = stopId,
            stopName = "Forbes Ave at Morewood",
            vehicleId = "5501",
            distanceToStopFeet = 1000,
            route = route,
            routeDirection = "INBOUND",
            destination = "Downtown",
            predictedTime = Instant.parse(time),
            delayed = false
        )
    }
}
