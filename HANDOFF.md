# Handoff — read this first

**Last session:** 2026-10-29 (third session) · **Milestone:** 1 — **OPEN**
**Repository:** github.com/tftechrobotics/Lockd-In (private). **CI:** green as of run 40836172393. **Next:** owner runs the two smoke tests.

## Status by evidence level

| Component | Implemented | Compiled / checked (where) | Tested on device / VM | Still proposed |
|---|---|---|---|---|
| `lockdin-core` | Yes | 21 tests (incl. 512-case property test), fmt, clippy — Ubuntu 24.04, Rust 1.97.0 | n/a (pure logic) | Heartbeat transport (M5), monotonic-clock tracking (M3/M4) |
| Windows probe + target + guardian | Yes | Compiles, 0 warnings (.NET SDK 10.0.113, Linux) | **No** | — |
| Android probe + target | Yes | Kotlin type-checks clean against AOSP `android-16.0.0_r2` framework jar (Kotlin 2.0.21). **No APK built yet** | **No** | — |
| VM policy scripts | Yes | Parse + PSScriptAnalyzer clean (PowerShell 7.5.4); manifest logic unit-tested | **No** | — |
| CI (4 jobs, separate VM artifact) | Yes | actionlint + YAML parse clean | **Never run** | — |
| Windows service, App Control prevention, in-app policy compiler | No | — | — | ADR-0007 |
| Product UI | No | — | — | M2 |

`tools/sandbox-checks.sh` reruns every check above from pinned public
sources. It needs no Google Maven or NuGet access.

## What changed this session (owner review 2)

1. **Detect-and-terminate ≠ prevention.** "Launch gating" removed. The probe's
   flag is now `--test-close-target-after-launch`, hard-limited to
   LockdInTarget. Production: never terminate real apps (R12). Prevention only
   via OS policy (App Control, or AppLocker); everything else gets
   work-preserving window/audio restriction.
2. **Recovery stated accurately.**
   - New guardian process restores audio when the probe dies.
   - If both die, recovery waits for the next start or `--recover`.
   - The recovery matrix covers agent crash, service crash, reboot and
     "nothing restarts" (`docs/enforcement-comparison.md`).
   - Tests R1–R6 are separate.
3. **VM rollback rebuilt on an ownership manifest:**
   - restores only Lockd In-owned values still equal to what it set;
   - reports conflicts;
   - refuses when pre-existing AppLocker EXE rules or extension policies exist;
   - never clears a policy for lack of a backup;
   - adds `-SimulateCrash` + reboot tests;
   - ships as a separate CI artifact.
4. **App Control on Home corrected.** Enforcement works on all editions;
   `CiTool` deploy on Home is VM test 11. In-app authoring is possible (an
   MIT-licensed open-source XML→binary converter exists; source reviewed).
   App Control is now the leading prevention candidate, subject to VM evidence.
5. **Android permissions exact:**
   - the block screen depends on `SYSTEM_ALERT_WINDOW` as its background-launch exception;
   - Advanced Protection effects are unverified (probe A10);
   - the probe logs the AP state and the overlay permission at every launch.
6. **No self-reported stops:** authenticated heartbeats; expired → Unknown (R16).

## Next actions

1. **Owner:** create an **empty private** GitHub repository (no README or
   license) and reply with `owner/name`. The session then attaches it with
   push access, pushes, and runs CI.
2. **Developer:**
   - fix whatever CI reports (most likely Android build configuration, since only type-checking was possible here);
   - set the Gradle `distributionSha256Sum`;
   - confirm the headless Windows sanity run passes;
   - publish the artifact links.
3. **Owner:** smoke tests only (checklist steps 1 and 2), plus device details.
4. **Developer:** record evidence in `docs/milestones.md`, then the next probe iteration.
5. **Later:** recovery R1–R6, extended tests, battery pair, VM experiments (Pro VM, then Home VM).
6. **Independent work meanwhile:**
   - App Control in-app compiler spike with round-trip tests;
   - M2 design for enforcement-independent flows;
   - FFI spike;
   - monotonic-clock design.

## Open owner decisions

U1–U10 in `docs/product-decisions.md` (U3 is now about machine-wide App Control
scope). None blocks CI or the smoke tests. U1 and U3 are needed to close M1.

## Environment notes for the next session

- **Blocked:** Google Maven, Android SDK, NuGet, services.gradle.org, static.rust-lang.org.
- **Reachable:** crates.io, npm, PyPI, the Ubuntu archive, and anonymous GitHub reads (clone and release downloads).
- .NET 10 SDK: `apt-get install dotnet-sdk-10.0`. Run Rust with `RUSTUP_TOOLCHAIN=stable` (it is 1.97.0).
- `add_repo` can attach an existing repository with `access: "push"`; there is no tool to create one.
