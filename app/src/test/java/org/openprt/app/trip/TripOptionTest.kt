package org.openprt.app.trip

import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.openprt.app.data.gtfs.TripPlan
import org.openprt.app.data.truetime.Prediction
import org.openprt.app.data.truetime.PredictionType
import org.openprt.app.geo.LatLng
import org.openprt.app.planner.Itinerary
import org.openprt.app.planner.RideLeg
import org.openprt.app.planner.TransitStop
import org.openprt.app.planner.WalkLeg

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

    private companion object {
        val NOW: Instant = Instant.parse("2026-10-01T10:50:00Z")
        val DATE: LocalDate = LocalDate.of(2026, 10, 1)

        val CMU = TransitStop("s8312", "Forbes Ave at Morewood", LatLng(40.4443, -79.9532), "8312")
        val FIFTH = TransitStop("s2635", "Fifth Ave at Craig", LatLng(40.4460, -79.9490), "2635")
        val FIFTH_OPPOSITE =
            TransitStop("s2636", "Fifth Ave opp Craig", LatLng(40.4462, -79.9492), "2636")
        val STEEL_PLAZA = TransitStop("s10", "Steel Plaza", LatLng(40.4406, -79.9959), "10")

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
