# Handoff — read this first

**Last session:** 2026-10-29 · **Milestone:** 1 (feasibility) · **Next:** owner runs device probes

## Where things stand

| Item | State | Evidence |
|---|---|---|
| Platform research (Windows, Android, Play policy, Calendar, browsers) | Done | `docs/feasibility.md` with sources and confidence tags |
| Architecture + 6 ADRs | Proposed | `docs/architecture.md`, `docs/adr/` |
| Risks, prerequisites, acceptance criteria | Done | `docs/risks.md`, `docs/milestones.md` |
| `focus-core` scheduling/state/protection | **Implemented and tested** | 20 tests (incl. 512-case property test) pass; clippy clean — Ubuntu 24.04, Rust 1.97 |
| Windows probe | **Implemented, compiled; not run** | `dotnet build` on Linux, .NET 10.0.113, 0 warnings |
| Android probe | **Implemented; not compiled** | Sandbox could not reach Google Maven/Android SDK |
| CI workflow | Written; not run | No repository yet |
| Product UI, enforcement in a real app, sync, calendar | Not started | — |

Nothing blocks a real device today. No restriction has been demonstrated on
any device yet.

## Next actions

1. **Owner:** run the Windows probe (W1–W10) and Android probe (A1–A10) per
   `probes/README.md`; send the two log files and device details.
2. **Owner (recommended):** create a private GitHub repo so the code persists
   between sessions and CI can compile the Android probe.
3. **Developer:** finalize ADR-0003/0004 from probe data (M1.5–1.7).
4. **Developer, can start now in parallel:** Milestone 2 design system
   (use the `frontend-design` skill); FFI spike (UniFFI → Kotlin, C ABI → C#);
   persistence and migrations in core.

## Decisions that need the owner

None blocking. Proposed decisions awaiting objection are listed in
`docs/product-decisions.md` (no accounts/local-first, allowlist with system
baseline, override defaults, Windows 11-only, Chrome/Edge-only websites).

## Known limitations so far

- Personal-device restriction is friction, not prevention; the owner can always stop or uninstall Focus.
- Windows: cannot pause apps running as administrator (to be confirmed by W5).
- Android: accessibility mode unavailable when Advanced Protection is on (Android 17); apps in Private Space/work profile not visible.
- Some research findings are from secondary reporting — marked "Reported" in `docs/feasibility.md`.
