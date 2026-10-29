# Architecture (proposed, Milestone 1)

Status key: **Implemented** = code exists and is tested (environment named);
**Proposed** = decided direction, no code yet; **Open** = needs an experiment
or an owner decision. Individual decisions are recorded as ADRs in `docs/adr/`.

## Shape

```
┌──────────────── Windows client (C# / WinUI 3) ───────────────┐   ┌──────────── Android client (Kotlin / Compose) ────────────┐
│ UI ─ onboarding, home, sessions, profiles, devices, recovery │   │ UI ─ same flows, Android conventions                     │
│ Enforcer ─ foreground hook, minimize + explanation, self-check│   │ Enforcer ─ UsageEvents/a11y detector, overlay, self-check │
│ Browser bridge ─ native-messaging host for the extension     │   │ Scheduler ─ alarms, foreground service                   │
│ Scheduler ─ timers, wake/clock-change handling               │   │ Platform storage ─ Keystore                              │
│ Platform storage ─ DPAPI / Credential Locker                 │   └───────────────────────┬──────────────────────────────────┘
└───────────────────────┬──────────────────────────────────────┘                           │ UniFFI (Kotlin)
                        │ C ABI (C#)                                                         │
                        └───────────────────────┬────────────────────────────────────────────┘
                                   focus-core (Rust) — no OS access
            schedule · timeline (DST) · session state · overrides · conflicts · protection status
                     · (M5) sync protocol, policy versions, pairing crypto · (M3) persistence/migrations
                                                │ HTTPS, end-to-end encrypted payloads
                                       Relay (M5) — stores ciphertext only
```

## Layers and rules

| Layer | Owns | Must not |
|---|---|---|
| **focus-core** (Rust) | What should happen and when: schedules, DST-correct timelines, desired restriction state, overrides, conflicts, the truthful protection summary; later sync and persistence | Touch the OS, network or clock directly. `now` is always passed in |
| **Enforcer** (native, per platform) | Making restriction happen; reporting what it actually observed (`Enforcement::Verified/Degraded/Unavailable`) | Decide policy; claim success without a self-check |
| **Scheduler** (native) | Waking at `Snapshot.next_transition`, on resume, on clock/time-zone change, and on sync messages; then calling `evaluate` | Count seconds to keep time |
| **UI** (native) | Presenting desired policy, acknowledgment and observed enforcement as **three separate facts** | Show "protected" from desired policy alone |
| **Relay** (M5) | Moving encrypted, signed, versioned documents between paired devices | Read schedules, or be required for a local session to start or end |

## Key decisions (see ADRs)

1. **Shared Rust core** (ADR-0001) — *Implemented* for scheduling/state/
   protection; tested on Linux.
2. **Native clients**: WinUI 3 on .NET 10 for Windows; Kotlin + Jetpack Compose
   for Android (ADR-0002) — *Proposed*.
3. **Windows enforcement**: per-user window restriction, no admin, never closes
   apps (ADR-0003) — *Proposed, probe written*.
4. **Android enforcement**: UsageEvents default, optional accessibility,
   overlay cover, `specialUse` foreground service (ADR-0004) — *Open until probe*.
5. **No Focus accounts; local-first; device-group pairing** (ADR-0005) —
   *Proposed*. A single device works fully offline with no sign-up.
6. **Calendar with free/busy-only scopes** (ADR-0006) — *Proposed*.

## Timekeeping

- Occurrence starts are local wall-clock times resolved with RFC 5545 rules;
  phases are elapsed real time. *Implemented and tested* (spring-forward gap,
  fall-back overlap, five zones including a 30-minute-DST zone).
- Clients set one timer for `next_transition` and re-evaluate on wake, resume,
  clock change, time-zone change and process start. No per-second counters.
- Cross-device timing: both devices compute phases from the same absolute
  instants, so skew equals clock difference. Target **≤ 2 s** with
  network-synced clocks; a device whose clock differs from the relay by
  > 5 s shows a warning (M5).

## Safety invariants (enforced in core, tested)

- Restriction is only requested inside a focus phase of an accepted occurrence.
- Every session ends ≤ 12 h after it starts; no indefinite state exists.
- Overrides lift only the current focus period; delay is clamped to ≤ 15 min.
- Emergency release is immediate, ends the session, and is recorded.
- "All protected" requires every device to have a fresh acknowledgment of the
  current version **and** a fresh verified self-check.

## Remote changes

A paired device can propose a schedule or start a session for the group. The
receiving device always shows a notification and applies it only as an
**accepted, versioned** schedule; the sender shows "waiting for <device>"
until the acknowledgment arrives. Nothing activates silently, and if the
relay is unreachable each device keeps running (and ending) the schedules it
already accepted.
