# 027: Board the first bus at the nearest stop it passes

## Goal

Trip search sometimes sends the user on a long walk to a far stop when the same bus also passes a
nearer one. The plan should still reach the destination as early as possible, but it should get
on that bus at the stop with the shortest walk, so the user leaves home later.

## Requirements

Decisions made with the user:

- **Earliest arrival at the destination stays the goal.** The user explicitly rejected "always
  use the nearest stop": a faster bus from a farther stop still wins.
- **Same bus, several stops:** when the first bus can be boarded at more than one access stop,
  board at the one with the shortest walk from the origin (latest time leaving home).
  Arrival at the destination does not change.
- **Equal arrival, different buses:** the plan that lets the user leave home later wins (leave
  home = first bus departure − access walk). This is usually the closer stop.
- **Only the origin side of "depart at" changes.** The user reports the problem at the start
  point, not at the end. "Arrive by" already ranks by latest time leaving home, so its origin
  side should already pick the nearer stop. Add a check for that, but don't change its code.
- **Transfers are unchanged:** from the second ride on, boarding and transfer stops are chosen
  exactly as before.

Root cause (`RoutePlanner.RaptorSearch.scanPattern`): a pattern is scanned from its earliest
marked stop. The search only switches trips when a candidate departs **strictly** earlier
(`<`). So when the same trip passes a nearer access stop later on the pattern, the search keeps
the far upstream boarding point.

## Design

- **`app/src/main/java/org/openprt/app/planner/RoutePlanner.kt`**
  - `scanPattern`: in **round 1 only**, when the stop being scanned can board the trip already on
    board (same trip), move `board` to this stop. Rounds ≥ 2 keep the strict `<` rule.
  - `recordDestination`: when two candidates arrive at the same time, prefer the later
    leave-home time. Keep `improve`'s pruning (`seconds >= minOf(best[stop], bestAtDestination)`).
  - Update the class KDoc to state the tie-break rule.
- **`app/src/main/java/org/openprt/app/data/gtfs/TripPlanRepository.kt`**: in `paretoFront`
  (the merge across service days), when arrivals are equal, prefer the later departure.
- **Interfaces that must not change:** `PlanResult`, `NoRouteReason`, `TripPlanResult`,
  `TripPlanSource`, and the ViewModel/UI.
- **Risk:** `itinerary()` traces back through `latestLabel(boardStop, k - 1)`. After moving the
  boarding point, the nearer stop must have its own round-0 `Label.Access`, or the first walk
  leg will use the wrong distance. Tests must assert the first walk's distance and start time.
- Per coding-standards: add a regression test that fails on the old code, bump PATCH, and update
  CHANGELOG and progress.md. Device-test locations never go into tests, CHANGELOG, progress.md
  or commits.

## Out of scope

- Choosing the stop at the destination end, in either mode.
- How the transfer walk between rides is chosen.
- The 800 m access radius (`DEFAULT_MAX_ACCESS_WALK_METERS`).
- Real walking distance from Valhalla. Stop selection stays straight-line.
- UI changes.

## Acceptance criteria

- New test, same bus: stop A (700 m, upstream) and stop B (150 m) are on the same trip. `plan` boards at B. The first walk is about 150 m, and the time to leave home is later than in the A plan.
- New test, unchanged arrival: in the test above, arrival time at the destination is the same as before the fix.
- New test, a faster bus still wins: the bus at far stop C reaches the destination earlier than the bus at near stop B. The plan still starts from C.
- New test, same arrival on different buses: B and C reach the destination at the same time. The plan picks the one that lets you leave home later, which is usually the closer stop.
- New test, transfers unchanged: after the first bus, how the transfer stop is chosen stays the same as before the fix.
- `./gradlew testDebugUnitTest` all pass. Existing `RoutePlannerTest` and `TripPlanRepositoryTest` expectations are updated only where they asserted the far upstream stop.
- Phone check on the S23 (`installDebug`): the start point that showed the problem now boards the same bus at the nearer stop, and the plan says to leave home later. I'd record it in progress.md without any address.
