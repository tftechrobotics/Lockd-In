# ADR-0007: Windows — privileged service plus per-user agent

**Status:** **Proposed — not approved; evidence pending** (probe R1–R6, W1–W7; VM 10, 11, 20, 30, 40, 98)
**Supersedes:** ADR-0003 · **Detail:** `docs/enforcement-comparison.md`

## Proposed decision
1. **Per-user agent:** window and audio restriction (minimize, mute) for
   anything not prevented; explanations, UI, tray. It records each mute
   before applying it so recovery is always possible.
2. **Lockd In service (LocalSystem):**
   - **launch prevention** through a session-scoped, unsigned App Control
     policy (allow-all base plus deny rules for installed non-approved apps),
     only if VM go criteria pass; AppLocker if App Control fails them;
   - restoring audio if the agent dies;
   - supervising the agent;
   - session-scoped browser policies and firewall rules;
   - reconciliation at every start, plus a separate scheduled cleanup task
     per policy;
   - authenticated, ACL-restricted local IPC with validated requests.
3. **Never terminate real apps.** Detect-and-terminate is not used in the
   product (requirement R12).
4. **Status to peers** comes from authenticated heartbeats. A device that
   stops reporting is **Unknown**. The service is not assumed able to report
   its own abrupt stop.

## Consequences
- Installing needs administrator consent (owner decision U1).
- App Control policies are machine-wide (owner decision U3).
- If no component survives to remove a policy, it stays in force (fail-closed).
  This is bounded by the scheduled cleanup task and measured in VM test 98.
- An administrator can still remove Lockd In; that is detected via heartbeat
  expiry, not prevented.

## Change log
- 2026-10-29 (first revision): created; replaced per-user-only ADR-0003.
- 2026-10-29 (second review):
  - "Launch gating" by detect-and-terminate removed. It is not prevention and
    can destroy state; termination is restricted to the harmless probe target.
  - App Control moved from "not for v1" to the leading prevention candidate:
    it is the only documented true prevention without a driver, rebootless
    removal covers all supported Windows 11 releases, and Home authoring is
    possible via an in-app compiler (MIT-licensed precedent).
  - The claim that the service would "report tamper events" was corrected to
    heartbeat expiry plus Unknown.
  - A recovery matrix was added.
