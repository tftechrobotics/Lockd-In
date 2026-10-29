# Milestones, acceptance criteria and evidence

A milestone is complete only when every criterion has **recorded evidence**
from the named environment. Compiling, unit tests and screenshots are not
evidence that restriction works. Numeric thresholds are **targets until
measured**; record the measured value next to each.

## M1 — Feasibility and product contract · **OPEN**

### Evidence tracker

| Area | Criterion (target) | Evidence so far | Status |
|---|---|---|---|
| **1. Schedule correctness** | Timelines, DST, overrides, emergency recovery, conflicts and release reasons are correct | 21 tests incl. 512-case property test pass — Ubuntu 24.04, Rust 1.97.0, 2026-10-29 | Logic: **met**. On-device timer accuracy at boundaries: **not measured** (M3/M4) |
| **2. Restriction effectiveness — Windows** | Smoke S1–S3 pass; foreground minimize p95 ≤ 500 ms over ≥ 20 trials; off-focus window caught ≤ 2.5 s; audio muted ≤ 2 s; elevated-app failure reported, not silent | None | **Not started** — probe compiled, not run |
| **2. Restriction effectiveness — Android** | Smoke S1–S3 pass; detection p95 ≤ 1.5 s (usage access); ≤ 1 missed detection in 50 opens; block screen shown ≥ 49/50; audio-after-cover behavior recorded per cover method; PiP and split-screen recorded | None | **Not started** — probe not yet compiled (needs CI) |
| **3. Safe release and recovery** | Release on time 100%; emergency release immediate; sound restored after normal exit, console close, kill + `--recover`, and app restart (W4, W5, W5b); no data loss | Core invariants tested (logic only) | **Not started** on devices |
| **4. Device lifecycle** | Windows: sleep/resume, sign-out. Android: screen-off 10 min, swipe-away, reboot, permission revocation detected ≤ 10 s | None | **Not started** |
| **5. Battery impact (Android)** | Detection run vs baseline run, same conditions, 60 min each; overhead reported in µAh (or % if unsupported) | None | **Not started** |
| **6. Cross-device status accuracy** | Status model never shows "protected" without a fresh verified report; silent peer = Unknown | Tested in core (logic) | Logic: **met**. End-to-end: M5 |
| **7. Windows policy approaches** (VM only) | App Control, AppLocker, browser policies, firewall: time to apply/remove, reboot need, effect on running apps, admin bypass, Smart App Control state | None | **Not started** — scripts written, not run |

### Go / no-go for building on an enforcement approach

**Windows per-user layer — GO** if: smoke test passes; minimize p95 ≤ 500 ms;
sweep ≤ 2.5 s; mute ≤ 2 s on FocusTarget and one real browser; recovery
restores sound in W4, W5 and W5b; no data loss.
**NO-GO** if minimize or mute is unreliable for ordinary apps. In that case,
reassess before any UI work.

**Windows service layer — GO to prototype in M3** if: W2 shows a newly
launched restricted app can be closed within ≤ 1 s with no user-visible
window, owner decision U1 = yes, and owner decision U3 = yes. Otherwise the
product relies on the per-user layer plus pre-session handling, and its
promise must say so.

**AppLocker / App Control as "strict mode" — GO only if** in the VM:
- apply ≤ 5 s;
- removal without reboot ≤ 5 s;
- no change to Smart App Control state;
- 10/10 simulated-crash cleanups leave nothing blocked;
- for App Control, a viable policy-authoring route on Home.

Otherwise NO-GO for v1.

**Android default path (usage access + block screen) — GO** if:
- detection p95 ≤ 1.5 s and ≤ 1/50 missed;
- block screen ≥ 49/50;
- survives screen-off and swipe-away;
- release on time 100%;
- battery overhead vs baseline acceptable to the owner (target ≤ 2% of capacity per hour).

If it fails while accessibility passes, owner decision U8 decides.
If both fail, NO-GO: the Android promise must be redefined with the owner.

**M1 closes when:** rows 2–5 have recorded device evidence, row 7 has VM
evidence (or the owner defers strict mode), ADR-0004 and ADR-0007 are
finalized from that evidence, and the owner has answered U1 and U3.

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
- Installer installs, upgrades and uninstalls cleanly, including the service if U1 = yes.
- Pick approved apps; run a real scheduled session with a break; restriction applied during focus and released within 1 s (target) of each boundary.
- Override, emergency recovery, sleep/resume, sign-out, reboot mid-session and agent or service crash each end in a correct, honestly reported state.
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
