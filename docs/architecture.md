# Architecture (Milestone 1 — provisional)

Status key: **Implemented** = code exists and is tested (environment named);
**Recommended** = developer proposal, not approved; **Open** = needs an
experiment or an owner decision. Decisions are recorded as ADRs in `docs/adr/`.
The enforcement layers below are **not settled**: Milestone 1 is open until
device and VM evidence exists (`docs/milestones.md`).

## Shape

```
┌──────────── Windows (C# / WinUI 3) ─────────────┐   ┌──────────── Android (Kotlin / Compose) ────────────┐
│ UI ─ onboarding, home, sessions, devices, recovery│   │ UI ─ same flows, Android conventions                │
│ Agent (per user) ─ window restriction, per-app    │   │ Detector ─ usage access (default) / a11y (optional) │
│   mute, explanations, tray, self-check            │   │ Cover ─ block screen + audio focus, self-check      │
│ Browser bridge ─ native messaging, heartbeat      │   │ Scheduler ─ alarms, foreground service              │
│ Service (SYSTEM, recommended, owner U1) ─ launch  │   │ Platform storage ─ Keystore                         │
│   PREVENTION via session-scoped App Control policy│   └─────────────────────────┬───────────────────────────┘
│   (if VM go), audio recovery, agent supervision,  │                             │ UniFFI (Kotlin)
│   browser/firewall settings + reconciliation      │                             │
└─────────────────────────┬─────────────────────────┘                             │
                          │ C ABI (C#)                                            │
                          └───────────────────────┬───────────────────────────────┘
                              focus-core (Rust) — no OS, network or clock access
          schedule · timeline (DST) · session state · override scopes · emergency recovery
          · release reasons · conflicts · protection status (Unknown for silent peers)
                 · (M5) sync protocol, pairing crypto · (M3) persistence/migrations
                                                  │ HTTPS, end-to-end encrypted payloads
                                         Relay (M5) — stores ciphertext only
```

## Layers and rules

| Layer | Owns | Must not |
|---|---|---|
| **focus-core** | What should happen and when; why nothing is restricted (`release_reason`); the truthful per-device status | Touch the OS, network or clock. `now` is passed in |
| **Enforcer** (native) | Making restriction happen; reporting what it observed (`Verified` / `Degraded` / `NotEnforcing`) | Decide policy; claim success without a self-check; call an app "paused" while it runs |
| **Windows service** (if approved) | Privileged actions only, behind an authenticated, ACL-restricted local IPC: launch prevention by policy, audio recovery, reconciliation of anything it applied (plus a separate scheduled cleanup task per policy) | Terminate real apps (R12); hold UI; accept unvalidated requests; leave OS settings behind |
| **Scheduler** (native) | Waking at `next_transition`, on resume, on clock or time-zone change and on sync; then calling `evaluate` | Count seconds to keep time |
| **UI** | Showing desired policy, acknowledgment and observed enforcement as separate facts; "Unknown" for silent peers | Show "protected" from desired policy alone |
| **Relay** (M5) | Moving encrypted, signed, versioned documents | Read schedules, or be needed for a local session to start or end |

## Decisions and their status

| # | Topic | Status |
|---|---|---|
| ADR-0001 | Shared Rust core | Engineering decision; implemented for scheduling/state/status |
| ADR-0002 | WinUI 3 and Kotlin/Compose | Engineering recommendation |
| ADR-0003 | Per-user window restriction | Superseded by ADR-0007 |
| ADR-0004 | Android enforcement | Open — after probe |
| ADR-0005 | No accounts, device-group pairing | Recommendation — owner decision U6 |
| ADR-0006 | Calendar via free/busy | Recommendation |
| ADR-0007 | Windows service + agent; prevention via App Control if VM evidence supports it | Proposed — evidence and owner decisions U1/U3 pending |
| ADR-0008 | Release reasons and Unknown peer status | Implemented in core |

## Timekeeping

- Occurrence starts are local wall-clock times resolved with RFC 5545 rules;
  phases are elapsed real time. *Implemented and tested.*
- Clients re-evaluate on timers, wake, resume, clock and time-zone change, and
  process start. No per-second counters.
- *Recommended, not implemented:* within a running session, track phase ends
  against the monotonic clock too, so a manual clock change can't shorten a session.
- Cross-device skew equals clock difference. Targets (unmeasured): ≤ 2 s with
  synced clocks; warn above 5 s.

## Safety invariants (enforced in core, tested on Linux)

- Restriction is only requested inside a focus phase of an accepted occurrence.
- Every session ends ≤ 12 h after it starts.
- Overrides are delayed (clamped ≤ 15 min), cancellable, and scoped to one
  focus period or the rest of the session; they never pre-authorize later periods.
- Emergency recovery is immediate, ends the session, and is distinguishable
  from an override and from a scheduled break.
- A device is "protected" only with a fresh, verified report of the current
  version; a silent device is "Unknown". Reports will travel as authenticated
  heartbeats (M5); no component is assumed able to announce its own crash.

## Remote changes

A paired device can propose a schedule or start a session for the group. The
receiving device always shows a notification and applies it only as an
accepted, versioned schedule. The sender shows "waiting for <device>" until
the acknowledgment arrives. Nothing activates silently. Offline devices keep
running, and ending, what they already accepted.
