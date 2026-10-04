package org.openprt.app.walk

import org.openprt.app.geo.LatLng

/** How a walk is drawn and timed. */
sealed interface WalkPath {
    /** The line to draw, starting at the walk's start and ending at its end. */
    val points: List<LatLng>

    /** A route along streets and footpaths, taking [seconds] at the router's walking pace. */
    data class Streets(override val points: List<LatLng>, val seconds: Long) : WalkPath {
        /** Rounded up, so the user never gets less time than shown. */
        val minutes: Long get() = (seconds + 59) / 60
    }

    /**
     * No street route is known, so the walk is drawn straight from [from] to [to] and keeps
     * the planner's own straight-line estimate.
     */
    data class Straight(val from: LatLng, val to: LatLng) : WalkPath {
        override val points: List<LatLng> get() = listOf(from, to)
    }
}

/** Finds the way a pedestrian would walk; an interface so the ViewModel can use a fake. */
fun interface WalkRouter {
    /**
     * The walk from [from] to [to]. Never fails: when no street route can be had (no network,
     * timeout, service error) the result is [WalkPath.Straight], so a plan is always drawable.
     */
    suspend fun route(from: LatLng, to: LatLng): WalkPath
}

/**
 * Remembers the street routes [delegate] finds, so the same walk (same start and end) is asked
 * for once, also when the user goes back and forth between options. Straight results are not
 * kept, so a walk that failed is tried again next time. Holds at most [capacity] routes, dropping
 * the least recently used.
 */
class CachingWalkRouter(
    private val delegate: WalkRouter,
    private val capacity: Int = DEFAULT_CAPACITY
) : WalkRouter {
    private val routes = object : LinkedHashMap<Pair<LatLng, LatLng>, WalkPath.Streets>(
        16,
        0.75f,
        true
    ) {
        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<Pair<LatLng, LatLng>, WalkPath.Streets>
        ): Boolean = size > capacity
    }

    override suspend fun route(from: LatLng, to: LatLng): WalkPath {
        val key = from to to
        // Locked only around the map; the request itself runs unlocked.
        synchronized(routes) { routes[key] }?.let { return it }
        val path = delegate.route(from, to)
        if (path is WalkPath.Streets) synchronized(routes) { routes[key] = path }
        return path
    }

    companion object {
        /** Several trips' worth of walks; each route is a few dozen points. */
        const val DEFAULT_CAPACITY = 64
    }
}
