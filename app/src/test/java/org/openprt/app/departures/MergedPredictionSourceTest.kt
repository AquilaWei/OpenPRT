package org.openprt.app.departures

import java.io.IOException
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.openprt.app.data.truetime.DataFeed
import org.openprt.app.data.truetime.Prediction
import org.openprt.app.data.truetime.PredictionType
import org.openprt.app.data.truetime.TrueTimeError
import org.openprt.app.data.truetime.TrueTimeResult

class MergedPredictionSourceTest {
    @Test
    fun predictions_bothFeedsAnswer_returnsBothFeedsPredictions() = runTest {
        val merged = MergedPredictionSource(
            listOf(
                PredictionSource { TrueTimeResult.Success(listOf(BUS_61C)) },
                PredictionSource { TrueTimeResult.Success(listOf(RAIL_RED)) }
            )
        )

        assertEquals(
            TrueTimeResult.Success(listOf(BUS_61C, RAIL_RED)),
            merged.predictions(listOf("7117", "99994"))
        )
    }

    @Test
    fun predictions_askEveryFeedForTheSameStops() = runTest {
        val asked = mutableListOf<List<String>>()
        val recording = PredictionSource { stopIds ->
            asked.add(stopIds)
            TrueTimeResult.Success(emptyList())
        }
        val merged = MergedPredictionSource(listOf(recording, recording))

        merged.predictions(listOf("7117", "99994"))

        assertEquals(listOf(listOf("7117", "99994"), listOf("7117", "99994")), asked)
    }

    @Test
    fun predictions_busFeedFails_stillReturnsLightRailPredictions() = runTest {
        val merged = MergedPredictionSource(
            listOf(
                PredictionSource { TrueTimeResult.Failure(TrueTimeError.Timeout) },
                PredictionSource { TrueTimeResult.Success(listOf(RAIL_RED)) }
            )
        )

        assertEquals(TrueTimeResult.Success(listOf(RAIL_RED)), merged.predictions(listOf("99994")))
    }

    @Test
    fun predictions_lightRailFeedFails_stillReturnsBusPredictions() = runTest {
        val merged = MergedPredictionSource(
            listOf(
                PredictionSource { TrueTimeResult.Success(listOf(BUS_61C)) },
                PredictionSource { TrueTimeResult.Failure(TrueTimeError.Http(500)) }
            )
        )

        assertEquals(TrueTimeResult.Success(listOf(BUS_61C)), merged.predictions(listOf("7117")))
    }

    @Test
    fun predictions_lightRailFeedHasNoData_stillReturnsBusPredictions() = runTest {
        val merged = MergedPredictionSource(
            listOf(
                PredictionSource { TrueTimeResult.Success(listOf(BUS_61C)) },
                PredictionSource { NO_DATA }
            )
        )

        assertEquals(TrueTimeResult.Success(listOf(BUS_61C)), merged.predictions(listOf("7117")))
    }

    @Test
    fun predictions_busFeedHasNoData_stillReturnsLightRailPredictions() = runTest {
        val merged = MergedPredictionSource(
            listOf(
                PredictionSource { NO_DATA },
                PredictionSource { TrueTimeResult.Success(listOf(RAIL_RED)) }
            )
        )

        assertEquals(TrueTimeResult.Success(listOf(RAIL_RED)), merged.predictions(listOf("99994")))
    }

    @Test
    fun predictions_bothFeedsHaveNoData_isEmptySuccess() = runTest {
        val merged = MergedPredictionSource(
            listOf(PredictionSource { NO_DATA }, PredictionSource { NO_DATA })
        )

        assertEquals(
            TrueTimeResult.Success(emptyList<Prediction>()),
            merged.predictions(listOf("7117"))
        )
    }

    @Test
    fun predictions_oneFeedFailsAndOtherHasNoData_reportsTheFailure() = runTest {
        val offline = TrueTimeError.Network(IOException("no network"))
        val merged = MergedPredictionSource(
            listOf(
                PredictionSource { TrueTimeResult.Failure(offline) },
                PredictionSource { NO_DATA }
            )
        )

        assertEquals(TrueTimeResult.Failure(offline), merged.predictions(listOf("7117")))
    }

    @Test
    fun predictions_bothFeedsFail_reportsTheFirstFeedsError() = runTest {
        val merged = MergedPredictionSource(
            listOf(
                PredictionSource { TrueTimeResult.Failure(TrueTimeError.MissingApiKey) },
                PredictionSource { TrueTimeResult.Failure(TrueTimeError.Timeout) }
            )
        )

        assertEquals(
            TrueTimeResult.Failure(TrueTimeError.MissingApiKey),
            merged.predictions(listOf("7117"))
        )
    }

    private companion object {
        val NOW: Instant = Instant.parse("2026-10-01T12:40:00Z")

        val NO_DATA = TrueTimeResult.Failure(
            TrueTimeError.Api(listOf("No data found for parameter"))
        )

        val BUS_61C = Prediction(
            generatedAt = NOW,
            type = PredictionType.ARRIVAL,
            stopId = "7117",
            stopName = "Forbes Ave at Morewood Ave",
            vehicleId = "5601",
            distanceToStopFeet = 4210,
            route = "61C",
            routeDirection = "OUTBOUND",
            destination = "McKeesport",
            predictedTime = NOW.plusSeconds(300),
            delayed = false
        )

        val RAIL_RED = Prediction(
            generatedAt = NOW,
            type = PredictionType.ARRIVAL,
            stopId = "99994",
            stopName = "Steel Plaza Station",
            vehicleId = "4301",
            distanceToStopFeet = 2500,
            route = "RED",
            routeDirection = "INBOUND",
            destination = "Downtown",
            predictedTime = NOW.plusSeconds(480),
            delayed = false,
            feed = DataFeed.LIGHT_RAIL
        )
    }
}
