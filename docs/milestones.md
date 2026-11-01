# Milestones, acceptance criteria and evidence

Replanned 2026-10-31 (R37) when functional development began. See
`docs/audit-2026-11.md` for the inventory behind it. The earlier M2–M7
plan is superseded. Its design-system criteria are met by the approved UI
(R34–R36); its remaining criteria are folded into the milestones below.

A milestone is complete only when **every** criterion has **recorded
evidence** from the named environment. Compiling, unit tests, screenshots
and emulator runs are not evidence that restriction works on a person's
device. Numeric thresholds are **targets until measured**; record the
measured value beside each.

Environments: **E-emu** = CI Android emulator (API 35); **E-phone** =
physical phone via Play internal testing (owner's S24+ plus at least one
more); **E-win-ci** = GitHub windows-2025 runner; **E-pc** = Windows 11 PC
with a signed build; **E-vm** = disposable Windows VM.

## M1 — Reliable single-device focus · **IN PROGRESS**

**Sessions (all platforms)**
- M1.1 Start now, or start at a set time today. Focus length 1–240 min, break 1–120 min, rounds 1–12. The total length and finish time are shown before starting and match the core within 1 min (unit + UI tests).
- M1.2 Profiles Study / Writing / Coding / Custom can each be edited and saved per device: Block list or Approved only, plus a list of apps. The session snapshots its rules at start; editing a profile does not change a running session (unit test).
- M1.3 Commitment level is chosen at start:
  - Flexible: end immediately.
  - Delayed: countdown, default 90 s, cancellable.
  - Locked: no early end; emergency recovery only.
  - Emergency recovery is always one deliberate action away, ends the session immediately and is recorded separately from an ordinary end (core + UI tests).
- M1.4 State is persisted after every change. Data from v1 migrates to v2 without loss (migration unit tests with v1 fixtures). Corrupt data falls back safely and never leaves a session stuck restricting (test).

**Android enforcement (E-emu first, then E-phone)**
- M1.5 During focus, opening a restricted app brings up the Lockd In block screen. The block screen shows the task, the time to the next break, Return to work, and the session-options route.
  - E-emu: 20/20 trials.
  - E-phone: ≥ 49/50, detection p95 ≤ 1.5 s.
- M1.6 Always reachable during any session: phone/dialer, emergency calls, Settings, the launcher, the keyboard and Lockd In (unit test of the decision table + E-emu check of Settings).
- M1.7 Restrictions lift for a break, return at the next focus period, and lift at the end. A restricted app opens normally ≤ 2 s after each release (E-emu, then E-phone).
- M1.8 Blocking keeps working after the app UI is swiped away, after the process is killed, after the screen is off for 10 min, and after a reboot mid-session. The service resumes from persisted state and the session still ends on time (E-emu for process kill and reboot; E-phone for all).
- M1.9 Permission loss (usage access or display-over-apps) is detected within 10 s. Status changes to **Permission needed** and a notification explains how to fix it. The lock never shows closed without verified restriction (E-emu via `appops`).
- M1.10 Apps are never closed or killed by Lockd In (code review + test that no kill APIs are used).

**Windows enforcement (E-win-ci first, then E-pc / E-vm)**
- M1.11 A per-user agent runs separately from the window. Closing the window does not end blocking; quitting from the tray asks first.
- M1.12 During focus, windows of restricted apps are minimized and their audio sessions muted. p95 ≤ 500 ms after the window appears. This is named "minimize and mute", not launch prevention.
- M1.13 Release restores mute state at breaks and at the end. A crash or reboot recovers audio via the ledger (R1–R5).
- M1.14 Launch prevention is offered only if the VM go criteria in §F pass and U1/U3 are answered yes. Otherwise the UI states that apps can open and will be minimized.

**Product essentials for M1**
- M1.15 Onboarding requests each permission with a one-sentence reason, re-checks it on resume, and shows a denied state with a Settings shortcut.
- M1.16 Signed Windows installer and Play internal-testing build install, update and uninstall cleanly, leaving no policy or service behind. Needs the owner's accounts (audit §5).

### M1 evidence so far (2026-11-01)

| Criterion | Evidence | Environment | Status |
|---|---|---|---|
| M1.2 rules snapshot, M1.3 commitment, M1.4 v1→v2 migration | `EnforcementRulesTest` (10 JVM tests); web `restriction.test.ts`; Compose UI tests for the Locked level, the app editor and the timer-only fallback | JVM, Chromium, API 35 emulator | Logic **met**; device UI not checked |
| M1.5 block screen | 20/20 trials. The fix to re-read an overlapping usage-events window removed a missed cover (one run had 19/20). On-device "app in front → block requested": p50 1.4 s, p95 3.5 s (n=27). **Above the 1.5 s p95 target on the emulator**, because usage events reach the app late there. Must be measured on phones | E-emu (`enforcement-e2e.sh`, run 41277248635, all 16 checks green) | Emulator **pass, slow detection**; phone **not tested** |
| M1.6 essentials | Settings opened during focus, not covered; decision table unit-tested | E-emu, JVM | Emulator **pass** |
| M1.7 break / resume / end | Target opened 3/3 in the break; blocked again in round 2; opens after the end; service and notification gone | E-emu | Emulator **pass** |
| M1.8 process kill, reboot | Back 12 s after `kill -9` (sticky restart); back 119 s after `adb reboot`, including boot | E-emu | Emulator **pass**; swipe-away and screen-off not yet scripted |
| M1.9 permission loss | "Blocking paused" notification 2–5 s after usage access was revoked; blocking back about 2–4 s after re-grant | E-emu (3 runs) | Emulator **pass** |
| M1.10 never closes apps | Target alive after 20 covers; no kill APIs in the app | E-emu | Emulator **pass** |
| M1.11–M1.13 Windows agent | Separate agent process with no app window. It minimized an already-open blocked window in 88 ms and re-minimized 10/10 restores (p50 25 ms). It reported "protecting", never closed the target, released and exited at the end, and the window could be restored afterwards. The app launch test with the agent bundled passes. Audio mute is not exercised (the target ran silent). No elevated app was tested | E-win-ci (`agent-e2e.ps1`, run 41276045247) | Runner **pass**; PC **not tested** (signing) |

**M1 closes when** M1.1–M1.16 have evidence, with M1.5–M1.9 on E-phone and M1.11–M1.13 on E-pc.

## M2 — Connected devices
- M2.1 Pairing by QR/code between Windows, Android and the web. Devices can be named, removed and revoked; a revoked device can no longer read new schedules (test).
- M2.2 A session or schedule started on one device reaches each paired device. Each device shows **received** and **activated** separately. Different restrictions per device are honored.
- M2.3 Per-device status is Protecting / Permission needed / Offline / Unknown / Ready. A device that is silent past its heartbeat expiry shows Unknown within 2 min. It is never shown as Protecting (test).
- M2.4 Offline devices run schedules they already received. Conflicting edits resolve by the documented rule and are shown, never silently dropped (tests: duplicate, delayed, out-of-order and conflicting messages).
- M2.5 The web app never shows itself as protecting. A recovery path is documented and tested for "all devices lost" and "browser storage cleared".
- M2.6 Measured timing skew is reported (target ≤ 2 s with synced clocks).

## M3 — Everyday planning
- M3.1 Recurring schedules start on their own at the scheduled time:
  - Android: exact alarm when granted, else within a 10 min window, with that stated in the UI.
  - Windows: scheduled wake of the agent.
- M3.2 Reminders: upcoming session, start task, break start and end, and missed session with a reschedule action. Quiet hours, snooze and frequency limits apply. One alert across paired devices.
- M3.3 Task presets: subject, task, first action, duration, profile and resources, with "Open my study resources" and a short-start option.
- M3.4 Google Calendar:
  - Connect and disconnect.
  - Free/busy only by default; event details only with separate consent.
  - Suggestions to accept, edit or dismiss; optional creation of accepted sessions.
  - Correct across time zones, DST, recurrence, cancellations, token expiry and duplicates.
  - Nothing is created or moved without the user's action.

## M4 — Useful insights
- M4.1 Opt-in usage analytics stored locally. Every metric is labelled measured, user-reported or estimated. Foreground, idle and timer time are kept separate. Device-hours are kept separate from elapsed time. Gaps in data are shown.
- M4.2 Session history: completed, interrupted, overridden, emergency.
- M4.3 Time by subject, task and profile, and blocked-attempt counts.
- M4.4 Retention control, export and deletion work (tests). No keystrokes, screenshots or contents. No scores.

## M5 — Optional support
- M5.1 Personalized block-screen messages per app or category.
- M5.2 Manual question timing.
- M5.3 Inactivity check-ins. Paper work is a valid answer; a check-in never adds restrictions.
- M5.4 Location routines: opt-in, arrival reminder first, evaluated on device.

**Later research:** SMS coaching (consent and cost write-up first), tutoring, LMS integrations, iOS.

## F — Feasibility evidence (formerly M1) · **OPEN**, feeds the new M1

### Evidence tracker

Each row records evidence separately: **implemented** (code exists),
**compiled/checked** (where), **tested on device/VM** (where), or
**proposed**. Thresholds are targets until measured.

| Area | Criterion (target) | Evidence so far | Status |
|---|---|---|---|
| **1. Schedule correctness** | Timelines, DST, override scopes, emergency recovery, conflicts, release reasons | 21 tests incl. 512-case property test — Ubuntu 24.04, Rust 1.97.0 | Logic **met**. On-device boundary accuracy not measured (M3/M4) |
| **2a. Restriction — Windows (per-user)** | Smoke S1–S3; minimize p95 ≤ 500 ms over ≥ 20 trials; off-focus window ≤ 2.5 s; mute ≤ 2 s; elevated-app failure reported | Probe implemented; self-contained x64/ARM64 builds in CI; headless runner sanity run passed (not a desktop, not evidence of restriction) | **Not tested** on a desktop |
| **Device finding 2026-10-29 (owner S24+)** | — | Samsung **Auto Blocker blocked installing the sideloaded probe APK** ("unknown app blocked"). Per R18 it stays on; Android testing moves to the Google Play internal testing track. | **Blocked** (Play account) |
| **Device finding 2026-10-29 (owner PC)** | — | Smart App Control **blocked the unsigned LockdInTarget.exe** ("could not verify its publisher") on first launch. No restriction test possible on this PC until the binaries are signed by a trusted CA or tested on a PC/VM without Smart App Control. | **Blocked** (signing) |
| **2b. Restriction — Windows (prevention, VM)** | App Control / AppLocker block the target; time to apply/remove; running copy unaffected; Home deploy (test 11) | Scripts implemented; parse + PSScriptAnalyzer clean (PowerShell 7.5.4, Linux); manifest logic unit-tested | **Not tested** |
| **2c. Restriction — Android** | Smoke S1–S3; detection p95 ≤ 1.5 s; ≤ 1/50 missed; block screen ≥ 49/50; audio-after-cover per method; PiP/split recorded | Probe implemented; APKs built in CI. **Emulator only** (API 35 google_apis x86_64, GitHub-hosted runner, workflow `Android emulator demo`, commit 0233321): session notification shown, block screen covered the target, target opened normally after End now — all pass. Not timing data, not a phone. | **Not tested on a phone** |
| **3. Safe release and recovery — Windows audio** | R1, R2 immediate; R3 guardian ≤ 2 s; R4, R5 restored by `--recover`; R6 reboot behavior recorded; no data loss | Guardian + ledger implemented; compiles | **Not tested** |
| **3. Safe release and recovery — policies (VM)** | Normal removal rebootless ≤ 5 s; `-SimulateCrash` + reboot: persistence measured; 99-Cleanup removes only owned items and reports conflicts | Implemented; ownership manifest unit-tested off Windows | **Not tested** |
| **3. Safe release — Android** | Release on time 100%; "End now" immediate; overlay/block screen gone at end | Implemented | **Not tested** |
| **4. Device lifecycle** | Windows sleep/resume, sign-out; Android screen-off 10 min, swipe-away, reboot, permission revocation ≤ 10 s, SAW revoked (A11), Advanced Protection (A10) | Implemented logging | **Not tested** |
| **5. Battery (Android)** | Detection vs baseline, same conditions, 60 min each; µAh where available | Baseline mode implemented | **Not tested** |
| **Clock changes** (brief §14) | Changing the system clock neither shortens nor extends a running session | `core::clock::SessionClock` + 14 tests incl. 2 property tests (Ubuntu 24.04, Rust 1.97.0). Native clock sources not yet wired | Logic **met**; on-device M3/M4 |
| **6. Cross-device status accuracy** | Never "protected" without fresh verified report; expired heartbeat → Unknown | Core logic tested (Linux) | Logic **met**; heartbeat transport is M5 |
| **2b′. App Control authoring without ConfigCI (Home)** | Our compiler's .cip matches `ConvertFrom-CIPolicy` | Spike built; 20 self-tests pass (Linux). Windows CI (windows-2025, commit 871aa94): Microsoft's `ConvertFrom-CIPolicy` accepted our XML; **parsed content identical** (options, allow/deny rules, scenarios, IDs); **bytes differ** — ours 1080 B, Microsoft 1036 B, first difference at byte 0 (header format version: ours writes 11). Not deployed anywhere. | **In progress** |
| **CI** | All jobs green; downloadable artifacts | Run 40836172393 (2026-10-29, commit 330537a): core tests, Android APK build, Windows x64/ARM64 self-contained builds, headless Windows sanity run (start → guardian → timed release → `--recover`), VM script lint — all green | **Met** |

### Go / no-go

**Windows per-user layer — GO** if:
- the smoke test passes;
- minimize p95 ≤ 500 ms, sweep ≤ 2.5 s, mute ≤ 2 s (LockdInTarget and one real browser);
- R1–R3 restore sound as specified, and R4/R5 restore it via `--recover`;
- no data loss.

**NO-GO** → reassess before UI work.

**Windows prevention (App Control) — GO to prototype in M3** if, in the VM:
- apply ≤ 5 s, and removal without reboot ≤ 5 s;
- Smart App Control state unchanged;
- after `-SimulateCrash` + reboot, the block persists, and the planned
  scheduled cleanup design can remove it without user action (to be
  prototyped);
- 99-Cleanup leaves the VM's pre-existing policy intact;
- test 11 shows deploy + enforce works on Home;
- owner decisions U1 and U3 = yes.

AppLocker is evaluated with the same criteria. If neither passes, the product
offers **no launch prevention** on Windows, only work-preserving window/audio
restriction, and says so.

**Android default path — GO** if:
- detection p95 ≤ 1.5 s and ≤ 1/50 missed;
- block screen ≥ 49/50;
- survives screen-off and swipe-away;
- release on time 100%;
- battery overhead vs baseline acceptable (target ≤ 2% of capacity per hour).

If it fails while accessibility passes, owner decision U8 decides. If both
fail, NO-GO: redefine the Android promise with the owner. A10 (Advanced
Protection) results decide what the product may claim for those users.

**Feasibility closes when** rows 2–5 have device/VM evidence, CI is green,
ADR-0004 and ADR-0007 are finalized from evidence, and U1 and U3 are answered.
