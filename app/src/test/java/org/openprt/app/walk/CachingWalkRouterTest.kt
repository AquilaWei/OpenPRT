package org.openprt.app.walk

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.openprt.app.geo.LatLng

class CachingWalkRouterTest {
    @Test
    fun route_sameWalkTwice_asksOnce() = runTest {
        val delegate = CountingRouter(STREETS)
        val router = CachingWalkRouter(delegate)

        router.route(A, B)
        router.route(A, B)

        assertEquals(1, delegate.calls)
    }

    @Test
    fun route_sameWalkTwice_givesTheFirstAnswerAgain() = runTest {
        val router = CachingWalkRouter(CountingRouter(STREETS))
        router.route(A, B)

        assertEquals(STREETS, router.route(A, B))
    }

    @Test
    fun route_otherDirection_asksAgain() = runTest {
        val delegate = CountingRouter(STREETS)
        val router = CachingWalkRouter(delegate)

        router.route(A, B)
        router.route(B, A)

        assertEquals(2, delegate.calls)
    }

    @Test
    fun route_straightAnswer_isAskedAgainNextTime() = runTest {
        val delegate = CountingRouter(WalkPath.Straight(A, B))
        val router = CachingWalkRouter(delegate)

        router.route(A, B)
        router.route(A, B)

        assertEquals(2, delegate.calls)
    }

    @Test
    fun route_pastCapacity_forgetsTheLeastRecentlyUsed() = runTest {
        val delegate = CountingRouter(STREETS)
        val router = CachingWalkRouter(delegate, capacity = 2)
        router.route(A, B)
        router.route(B, A)
        router.route(A, B)
        router.route(A, C)

        router.route(B, A)

        assertEquals(4, delegate.calls)
    }

    private class CountingRouter(val path: WalkPath) : WalkRouter {
        var calls = 0

        override suspend fun route(from: LatLng, to: LatLng): WalkPath {
            calls++
            return path
        }
    }

    private companion object {
        val A = LatLng(40.4443, -79.9436)
        val B = LatLng(40.4447, -79.9483)
        val C = LatLng(40.4406, -79.9959)
        val STREETS = WalkPath.Streets(listOf(A, LatLng(40.4445, -79.9460), B), 447)
    }
}
