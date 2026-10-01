package org.openprt.app.departures

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Test
import org.openprt.app.data.truetime.Prediction
import org.openprt.app.data.truetime.PredictionType

class DepartureRankerTest {
    private val ranker = DepartureRanker(clockAt(NOW))

    @Test
    fun walkTime_atDefaultSpeed_isDistanceOverOnePointTwoMetersPerSecond() {
        assertEquals(Duration.ofSeconds(100), ranker.walkTime(120.0))
    }

    @Test
    fun walkTime_fractionalSeconds_roundsUp() {
        assertEquals(Duration.ofSeconds(1), ranker.walkTime(1.0))
    }

    @Test
    fun walkTime_withCustomSpeed_usesThatSpeed() {
        val fastWalker = DepartureRanker(clockAt(NOW), walkingSpeedMetersPerSecond = 2.0)

        assertEquals(Duration.ofSeconds(50), fastWalker.walkTime(100.0))
    }

    @Test(expected = IllegalArgumentException::class)
    fun constructor_zeroWalkingSpeed_throws() {
        DepartureRanker(clockAt(NOW), walkingSpeedMetersPerSecond = 0.0)
    }

    @Test
    fun rank_catchableBus_reportsWalkTimeUntilDepartureAndSpareTime() {
        val ranked = ranker.rank(
            listOf(WalkableStop("A", 120.0)),
            listOf(prediction("61C", "INBOUND", "A", secondsFromNow = 300))
        )

        assertEquals(1, ranked.size)
        assertEquals(Duration.ofSeconds(100), ranked[0].walkTime)
        assertEquals(Duration.ofSeconds(300), ranked[0].timeUntilDeparture)
        assertEquals(Duration.ofSeconds(200), ranked[0].spareTime)
    }

    @Test
    fun rank_busArrivesBeforeUserCanWalkToStop_isExcluded() {
        val ranked = ranker.rank(
            listOf(WalkableStop("A", 240.0)),
            listOf(prediction("61C", "INBOUND", "A", secondsFromNow = 150))
        )

        assertEquals(emptyList<RankedDeparture>(), ranked)
    }

    @Test
    fun rank_busArrivesExactlyWhenUserReachesStop_isKept() {
        val ranked = ranker.rank(
            listOf(WalkableStop("A", 240.0)),
            listOf(prediction("61C", "INBOUND", "A", secondsFromNow = 200))
        )

        assertEquals(listOf("A"), ranked.map { it.stop.stopId })
    }

    @Test
    fun rank_busAlreadyPast_isExcluded() {
        val ranked = ranker.rank(
            listOf(WalkableStop("A", 0.0)),
            listOf(prediction("61C", "INBOUND", "A", secondsFromNow = -30))
        )

        assertEquals(emptyList<RankedDeparture>(), ranked)
    }

    @Test
    fun rank_laterClock_excludesBusThatWasCatchableEarlier() {
        val later = DepartureRanker(clockAt(NOW.plusSeconds(120)))

        val ranked = later.rank(
            listOf(WalkableStop("A", 120.0)),
            listOf(prediction("61C", "INBOUND", "A", secondsFromNow = 200))
        )

        assertEquals(emptyList<RankedDeparture>(), ranked)
    }

    @Test
    fun rank_sameRouteAndDirectionAtTwoStops_keepsOnlyEarliestBoarding() {
        val ranked = ranker.rank(
            listOf(WalkableStop("NEAR", 60.0), WalkableStop("FAR", 300.0)),
            listOf(
                prediction("61C", "INBOUND", "NEAR", secondsFromNow = 400),
                prediction("61C", "INBOUND", "FAR", secondsFromNow = 330)
            )
        )

        assertEquals(listOf("FAR"), ranked.map { it.stop.stopId })
    }

    @Test
    fun rank_sameRouteAndDirectionAtSameTime_keepsShorterWalk() {
        val ranked = ranker.rank(
            listOf(WalkableStop("NEAR", 60.0), WalkableStop("FAR", 300.0)),
            listOf(
                prediction("61C", "INBOUND", "FAR", secondsFromNow = 400),
                prediction("61C", "INBOUND", "NEAR", secondsFromNow = 400)
            )
        )

        assertEquals(listOf("NEAR"), ranked.map { it.stop.stopId })
    }

    @Test
    fun rank_sameRouteAndDirectionWhenEarlierStopUnreachable_keepsCatchableStop() {
        val ranked = ranker.rank(
            listOf(WalkableStop("NEAR", 60.0), WalkableStop("FAR", 360.0)),
            listOf(
                prediction("61C", "INBOUND", "FAR", secondsFromNow = 200),
                prediction("61C", "INBOUND", "NEAR", secondsFromNow = 500)
            )
        )

        assertEquals(listOf("NEAR"), ranked.map { it.stop.stopId })
    }

    @Test
    fun rank_sameRouteOppositeDirections_keepsBoth() {
        val ranked = ranker.rank(
            listOf(WalkableStop("A", 60.0), WalkableStop("B", 60.0)),
            listOf(
                prediction("61C", "INBOUND", "A", secondsFromNow = 300),
                prediction("61C", "OUTBOUND", "B", secondsFromNow = 400)
            )
        )

        assertEquals(listOf("INBOUND", "OUTBOUND"), ranked.map { it.prediction.routeDirection })
    }

    @Test
    fun rank_predictionAtStopNotInList_isIgnored() {
        val ranked = ranker.rank(
            listOf(WalkableStop("A", 60.0)),
            listOf(prediction("61C", "INBOUND", "ELSEWHERE", secondsFromNow = 300))
        )

        assertEquals(emptyList<RankedDeparture>(), ranked)
    }

    @Test
    fun rank_fixedInput_producesFixedOrder() {
        val stops = listOf(
            WalkableStop("S1", 50.0),
            WalkableStop("S2", 150.0),
            WalkableStop("S3", 400.0)
        )
        val predictions = listOf(
            prediction("71A", "INBOUND", "S2", secondsFromNow = 600),
            prediction("61C", "INBOUND", "S1", secondsFromNow = 240),
            prediction("61C", "INBOUND", "S2", secondsFromNow = 180),
            prediction("P1", "OUTBOUND", "S3", secondsFromNow = 240),
            prediction("28X", "OUTBOUND", "S3", secondsFromNow = 900),
            prediction("71A", "OUTBOUND", "S1", secondsFromNow = 240),
            prediction("54", "INBOUND", "S2", secondsFromNow = 60),
            prediction("61C", "OUTBOUND", "S1", secondsFromNow = 1200)
        )

        val ranked = ranker.rank(stops, predictions)

        // Walks: S1 42 s, S2 125 s, S3 334 s. 54 (60 s) and P1 (240 s) are not catchable, and
        // 61C INBOUND keeps S2 at 180 s over S1 at 240 s.
        assertEquals(
            listOf(
                "61C INBOUND S2",
                "71A OUTBOUND S1",
                "71A INBOUND S2",
                "28X OUTBOUND S3",
                "61C OUTBOUND S1"
            ),
            ranked.map {
                "${it.prediction.route} ${it.prediction.routeDirection} ${it.stop.stopId}"
            }
        )
    }

    private fun prediction(route: String, direction: String, stopId: String, secondsFromNow: Long) =
        Prediction(
            generatedAt = NOW,
            type = PredictionType.ARRIVAL,
            stopId = stopId,
            stopName = "Stop $stopId",
            vehicleId = "$route-$direction-$stopId",
            distanceToStopFeet = 0,
            route = route,
            routeDirection = direction,
            destination = "Downtown",
            predictedTime = NOW.plusSeconds(secondsFromNow),
            delayed = false
        )

    private companion object {
        val NOW: Instant = Instant.parse("2026-10-01T16:00:00Z")

        fun clockAt(instant: Instant): Clock = Clock.fixed(instant, ZoneOffset.UTC)
    }
}
