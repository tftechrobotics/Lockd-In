# Handoff — read this first

**Last session:** 2026-10-29 (second session) · **Milestone:** 1 — **OPEN**
**Next:** get the code into a GitHub repo so CI builds the test downloads → owner runs both smoke tests

## What changed this session (owner corrections of 2026-10-29)

- Withdrew the "friction, never a lock" framing, and withdrew "proposed decisions stand unless you object". `docs/product-decisions.md` now separates owner requirements (R1–R11), engineering recommendations (E1–E9) and unresolved owner decisions (U1–U10).
- Windows: window minimization demoted to the experimental baseline. New comparison of per-user agent, service + agent, AppLocker, App Control, firewall, browser policies and others. Provisional recommendation: service + agent (ADR-0007, not approved).
- Android: the overlay is no longer the primary cover. The block screen is brought to the front via the documented overlay-permission background-launch exception, plus an audio-focus request. Advanced Protection is handled without asking users to disable it (ADR-0004, still open).
- Core: override scopes (current focus period / rest of session); immediate `EmergencyRecovery`; `release_reason` on every snapshot; silent peers are `Unknown` (ADR-0008).
- Probes rebuilt: harmless target apps on both platforms, honest wording (never "paused"), Windows per-app mute with a crash-safe ledger and `--recover`, Android detector/cover/poll options plus battery baseline mode, reboot and swipe logging, pinned toolchains, Gradle wrapper, CI that publishes downloads.
- VM-only policy scripts (refuse to run outside a VM).

## Status

| Item | State | Evidence |
|---|---|---|
| `focus-core` | **Implemented, tested** | 21 tests (incl. 512-case property test) pass; fmt and clippy clean — Ubuntu 24.04, Rust 1.97.0 |
| Windows probe + target | **Implemented, compiled; not run** | `dotnet build` with SDK 10.0.113, warnings as errors, on Linux |
| Android probe + target | **Implemented; not compiled** | Google Maven blocked here. Syntax-only check with Kotlin 2.0.21 found no syntax errors; type-checking needs the Android SDK (CI) |
| VM policy scripts | Written; **not executed, not syntax-checked** | No PowerShell in this sandbox |
| CI workflow | Written; **not run** | No repository yet |
| Restriction on any device | **Not demonstrated** | — |

## Next actions

1. **Owner:** create an empty private GitHub repository and share its `owner/name`. The next session attaches it, pushes this code and runs CI. That produces the first compiled Android build and the downloadable test packages.
2. **Developer (after CI is green):** fix any Android compile errors CI reports. Set `distributionSha256Sum` in the Gradle wrapper. Hand the owner the download links.
3. **Owner:** smoke tests only (`docs/device-test-checklist.md` steps 1–2), plus device details.
4. **Developer:** record the evidence in `docs/milestones.md` and decide the next probe iteration.
5. **Then:** extended tests, battery pair, VM experiments (owner needs a disposable Windows 11 Pro VM).
6. **Independent work that doesn't depend on the enforcement choice:**
   - M2 design for scheduling, onboarding shell, settings and themes (use the `frontend-design` skill);
   - FFI spike for the core (UniFFI → Kotlin, C ABI → C#);
   - monotonic-clock tracking design;
   - persistence and migrations in core.

## Open owner decisions

U1–U10 in `docs/product-decisions.md`. None blocks the smoke tests. U1
(Windows service acceptable?) and U3 (close restricted apps right after
launch?) are needed to close M1.

## Known limitations so far

- An administrator-owner can stop or uninstall Focus; the goal is detection and reporting, not prevention.
- Windows agent-only mode can't act on apps running as administrator.
- Windows minimizing and muting leave apps running in the background.
- Windows mute recovery may unmute a session the user muted themselves in the same app (probe only).
- Android: covering never pauses an app. The block screen moves it to the background, and audio may continue for apps with their own media service.
- Android: picture-in-picture windows are not reported by usage access.
- Android: accessibility mode is unavailable under Advanced Protection (Android 17).
- Android: replying from a restricted app's notification is not blocked (decision U10).
- Unsigned Windows test builds may be blocked by Smart App Control.

## Environment notes for the next session

- The sandbox blocks Google Maven, the Android SDK, NuGet, services.gradle.org and static.rust-lang.org. crates.io, npm, PyPI, the Ubuntu archive and GitHub are reachable.
- .NET 10 SDK installs from the Ubuntu archive (`apt-get install dotnet-sdk-10.0`).
- Run Rust with `RUSTUP_TOOLCHAIN=stable` here: the installed stable is 1.97.0, but the pinned name can't be downloaded.
