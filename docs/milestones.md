# Milestones, acceptance criteria and evidence

A milestone is complete only when every criterion has **recorded evidence**
from the named environment. Compiling, unit tests and screenshots are not
evidence that restriction works. Numeric thresholds are **targets until
measured**; record the measured value next to each.

## M1 — Feasibility and product contract · **OPEN**

### Evidence tracker

Each row records evidence separately: **implemented** (code exists),
**compiled/checked** (where), **tested on device/VM** (where), or
**proposed**. Thresholds are targets until measured.

| Area | Criterion (target) | Evidence so far | Status |
|---|---|---|---|
| **1. Schedule correctness** | Timelines, DST, override scopes, emergency recovery, conflicts, release reasons | 21 tests incl. 512-case property test — Ubuntu 24.04, Rust 1.97.0 | Logic **met**. On-device boundary accuracy not measured (M3/M4) |
| **2a. Restriction — Windows (per-user)** | Smoke S1–S3; minimize p95 ≤ 500 ms over ≥ 20 trials; off-focus window ≤ 2.5 s; mute ≤ 2 s; elevated-app failure reported | Probe implemented; compiles (SDK 10.0.113, Linux) | **Not tested** |
| **2b. Restriction — Windows (prevention, VM)** | App Control / AppLocker block the target; time to apply/remove; running copy unaffected; Home deploy (test 11) | Scripts implemented; parse + PSScriptAnalyzer clean (PowerShell 7.5.4, Linux); manifest logic unit-tested | **Not tested** |
| **2c. Restriction — Android** | Smoke S1–S3; detection p95 ≤ 1.5 s; ≤ 1/50 missed; block screen ≥ 49/50; audio-after-cover per method; PiP/split recorded | Probe implemented; Kotlin type-checks against AOSP android-16.0.0_r2 framework jar (Kotlin 2.0.21); **full APK build not yet done** (needs CI) | **Not tested** |
| **3. Safe release and recovery — Windows audio** | R1, R2 immediate; R3 guardian ≤ 2 s; R4, R5 restored by `--recover`; R6 reboot behavior recorded; no data loss | Guardian + ledger implemented; compiles | **Not tested** |
| **3. Safe release and recovery — policies (VM)** | Normal removal rebootless ≤ 5 s; `-SimulateCrash` + reboot: persistence measured; 99-Cleanup removes only owned items and reports conflicts | Implemented; ownership manifest unit-tested off Windows | **Not tested** |
| **3. Safe release — Android** | Release on time 100%; "End now" immediate; overlay/block screen gone at end | Implemented | **Not tested** |
| **4. Device lifecycle** | Windows sleep/resume, sign-out; Android screen-off 10 min, swipe-away, reboot, permission revocation ≤ 10 s, SAW revoked (A11), Advanced Protection (A10) | Implemented logging | **Not tested** |
| **5. Battery (Android)** | Detection vs baseline, same conditions, 60 min each; µAh where available | Baseline mode implemented | **Not tested** |
| **6. Cross-device status accuracy** | Never "protected" without fresh verified report; expired heartbeat → Unknown | Core logic tested (Linux) | Logic **met**; heartbeat transport is M5 |
| **CI** | All jobs green; downloadable artifacts | Workflow passes actionlint + YAML parse | **Not run**: no repository yet |

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

**M1 closes when** rows 2–5 have device/VM evidence, CI is green,
ADR-0004 and ADR-0007 are finalized from evidence, and U1 and U3 are answered.

## M2 — Design system and complete workflows
- Tokens and components for type, spacing, color, borders, elevation, controls, dialogs, notifications and motion. Light and dark designed separately; reduced motion.
- All 10 brief flows with empty, loading, offline, denied-permission, partially-connected, error and success states.
- Every screen shows desired, acknowledged and observed state separately.
- UI copy never claims a restricted app is paused when it may still be running (R3).
- Verified interactively in both themes:
  - keyboard order and screen-reader labels;
  - 200% text scaling;
  - contrast ≥ WCAG 2.2 AA;
  - targets ≥ 48 dp / 40 px.
- May start before M1 closes only for flows that don't depend on the enforcement choice (onboarding shell, scheduling, settings, theme).

## M3 — Windows vertical slice (tested on Windows 11)
- Installer installs, upgrades and uninstalls cleanly, including the service if U1 = yes, and leaves no policy behind.
- Pick approved apps; run a real scheduled session with a break; restriction applied during focus and released within 1 s (target) of each boundary.
- Override, emergency recovery, sleep/resume, sign-out, reboot mid-session, agent crash and service crash each end in a correct, honestly reported state, per the recovery matrix in `enforcement-comparison.md`.
- Never terminates a real app (R12).
- Clock-change handling: a wall-clock jump does not shorten a running session.

## M4 — Android vertical slice (≥ 2 physical phones incl. owner's)
- Permission onboarding verified after each grant and on every resume.
- Sessions start from the UI and from a schedule with the screen off; breaks release and resume; the end releases.
- Process death, swipe-away, reboot, permission revocation and battery saver each produce correct enforcement or an immediate, visible `Degraded`.

## M5 — Cross-device coordination
- Pairing, revocation and key rotation.
- Per-device status: Protected / NotYetApplied / Degraded / NotEnforcing / Unknown.
- Offline devices keep executing their schedule.
- Measured timing skew reported against the target (≤ 2 s with synced clocks).
- Duplicate, delayed, out-of-order and conflicting messages are tested.

## M6 — Optional calendar
- Free/busy suggestions that the user can accept, edit or dismiss.
- Nothing activates from an unaccepted suggestion.
- Disconnecting deletes calendar data.
- Manual scheduling is unaffected by Google outages.

## M7 — Release hardening
- Published OS/architecture matrix from actual runs.
- Accessibility audit, threat-model review, signing, update verification.
- Diagnostics with consent and redaction.
- Privacy disclosures and license review.
- A statement of exactly what was demonstrated.
