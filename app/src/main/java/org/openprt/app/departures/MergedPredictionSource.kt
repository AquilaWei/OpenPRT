package org.openprt.app.departures

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.openprt.app.data.truetime.Prediction
import org.openprt.app.data.truetime.TrueTimeResult
import org.openprt.app.data.truetime.orEmptyWhenNoData

/**
 * Asks every one of [sources] (one per TrueTime feed, e.g. buses and light rail) about the same
 * stops at once and combines their predictions, so the caller sees one list.
 *
 * A feed answering "No data found" counts as an empty list. A feed that fails does not hide the
 * others' predictions; the result is a failure (the first source's error) only when no feed
 * returned any prediction, so that, for example, a missing key or no network still shows as
 * such instead of as an empty list.
 *
 * Every call makes one request per source, so the TrueTime quota is used [sources] times as fast.
 */
class MergedPredictionSource(private val sources: List<PredictionSource>) : PredictionSource {
    init {
        require(sources.isNotEmpty()) { "MergedPredictionSource needs at least one source" }
    }

    override suspend fun predictions(stopIds: List<String>): TrueTimeResult<List<Prediction>> {
        val results = coroutineScope {
            sources
                .map { source -> async { source.predictions(stopIds).orEmptyWhenNoData() } }
                .awaitAll()
        }
        val predictions = results.filterIsInstance<TrueTimeResult.Success<List<Prediction>>>()
            .flatMap { it.value }
        val firstFailure = results.filterIsInstance<TrueTimeResult.Failure>().firstOrNull()
        return if (predictions.isEmpty() && firstFailure != null) {
            firstFailure
        } else {
            TrueTimeResult.Success(predictions)
        }
    }
}
