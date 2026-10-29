# ADR-0008: Separate release reasons and "unknown" peer status

**Status:** Implemented in `focus-core` (tested on Linux, 21 tests). Product
defaults (delays, freshness window) are recommendations awaiting owner decision U4/U5.

## Decision
"Not restricting" always carries one reason, and the four kinds of release
are never merged:

| Situation | Model | Behavior |
|---|---|---|
| Scheduled break | Plan phase | Release at break start; warn before resume |
| Delayed intentional override | `OverrideRecord` with scope *current focus period* or *rest of session* | Countdown (recommended 90 s, clamped ≤ 15 min), cancellable; never pre-authorizes later periods |
| Immediate emergency recovery | `EmergencyRecovery` | No countdown; ends the session; recorded and reported |
| Enforcement failure | Device status (`Degraded` / `NotEnforcing`) | Does **not** change desired state; the device reports it is failing to deliver it |

Peer status:
- **Protected** requires a fresh report from the device that it applied the
  current version and passed its self-check.
- A device with no fresh report is **Unknown** — never assumed protected or
  stopped. Its last report is kept as history.
- The freshness window (120 s) and clock tolerance (5 s) are **targets until
  measured**.

## Change log
- 2026-10-29: `EarlyEnd` replaced by override scopes plus `EmergencyRecovery`.
- 2026-10-29: `Stale` replaced by `Unknown`, which now also covers old
  "degraded" or "not enforcing" reports.
- 2026-10-29: `release_reason` added to every snapshot.
