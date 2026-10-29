# Milestones and acceptance criteria

A milestone is complete only when every criterion is met **on the named
environment**. Visual completion is not functional completion.

## M1 — Feasibility and product contract

| # | Criterion | Status |
|---|---|---|
| 1.1 | Platform capabilities, policies and limits researched with sources | Done — `docs/feasibility.md` |
| 1.2 | Architecture and ADRs written | Done — `docs/architecture.md`, `docs/adr/` |
| 1.3 | Risk register and external prerequisites | Done — `docs/risks.md` |
| 1.4 | Core scheduling/state logic implemented with tests for DST, overrides, recovery, protection truthfulness | Done — 20 tests incl. 512-case property test, Linux |
| 1.5 | Windows probe run on owner's PC; W1–W10 recorded; minimize p95 ≤ 500 ms; elevated-app failure detected | **Pending owner run** |
| 1.6 | Android probe built and run on owner's phone with both detectors; A1–A10 recorded; detection p95 ≤ 1.5 s (polling) / ≤ 0.5 s (a11y); overlay shown for ≥ 99% of detections in a 60-min session | **Pending owner run** |
| 1.7 | ADR-0003 and ADR-0004 finalized from probe data | Pending 1.5, 1.6 |

## M2 — Design system and complete workflows
- Tokens and components for type, spacing, color, borders, elevation, controls, dialogs, notifications, motion — light and dark designed separately; reduced motion.
- All 10 brief flows with empty, loading, offline, denied-permission, partially-connected, error and success states.
- Every screen distinguishes desired / acknowledged / observed.
- Verified interactively in both themes: keyboard order, screen-reader labels, 200% text scaling, contrast ≥ WCAG 2.2 AA, 48 dp / 40 px minimum targets.

## M3 — Windows vertical slice (tested on Windows 11)
- MSIX (or chosen installer) installs, upgrades and uninstalls cleanly.
- Pick approved apps from installed apps; run a real scheduled session with a break; restricted windows minimized during focus and released at break/end within 1 s of the boundary.
- Override (delay, cancel), emergency release, sleep/resume, sign-out, reboot mid-session, and agent crash all end in a correct, honestly reported state.
- Closing the window ≠ stopping enforcement; tray shows status.

## M4 — Android vertical slice (tested on ≥ 2 physical phones, incl. owner's)
- Permission onboarding with verification after each grant and on every resume.
- Session start from UI and from schedule with screen off; break release and resume; end release.
- Process death, swipe-away, reboot, permission revocation and battery-saver cases each produce correct enforcement or an immediate, visible `Degraded` status.

## M5 — Cross-device coordination
- QR pairing with expiring single-use token; revoke + key rotation.
- Desired/ack/observed shown per device; "all protected" only per core rules.
- Offline device keeps executing accepted schedule; reconciles on reconnect.
- Measured timing skew between devices ≤ 2 s with synced clocks; skew warning > 5 s.
- Duplicate, delayed, out-of-order and conflicting messages covered by tests.

## M6 — Optional calendar
- OAuth with `calendar.freebusy` + `calendar.calendarlist.readonly`; suggestions accept/edit/dismiss; nothing activates from an unaccepted suggestion; reminders only for accepted sessions; disconnect deletes stored calendar data; manual scheduling unaffected by Google outages.

## M7 — Release hardening
- Published OS/architecture matrix from actual test runs; accessibility audit; threat-model review; signing; update verification; diagnostics with consent and redaction; privacy disclosures; dependency licenses; support docs. Report states exactly what was demonstrated.
