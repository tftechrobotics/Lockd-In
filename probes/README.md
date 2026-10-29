# Enforcement probes (Milestone 1 experiments)

**Experiments, not product code.** Each answers one high-risk question with
evidence from a real device or a disposable VM. Nothing here may be described
as "working blocking" until its results are recorded in `docs/milestones.md`.

| Folder | What | Implemented | Compiled / checked (where) | Tested on device/VM |
|---|---|---|---|---|
| `windows/` | `FocusProbe.exe`: per-user minimize, per-app mute with ledger and a **guardian** process for crash recovery, launch detection. `FocusTarget.exe`: harmless app that logs its own exposure and mute state. | Yes | Compiles with .NET SDK 10.0.113, warnings as errors (Linux). Self-contained Windows executables come from CI, which also runs a headless start/release/exit check. | No |
| `android/` | `focus-probe.apk`: usage-access or accessibility detector; block screen, overlay or home cover; audio-focus option; battery baseline; logs permission changes, Advanced Protection state, reboot and swipe. `focus-probe-target.apk`: harmless app with tone, picture-in-picture and self-measured exposure. | Yes | Kotlin **type-checks with zero errors and warnings** against the AOSP `android-16.0.0_r2` framework jar (Kotlin 2.0.21, Linux). **Full APK build not yet done**: AGP/aapt2 live only on Google's Maven, which this sandbox can't reach. CI does it. | No |
| `windows-vm/` (**separate download**) | App Control (Pro build + Home deploy-only), AppLocker, browser policies, firewall, after-reboot check, ownership-based cleanup. Refuse to run unless on a VM with `-ConfirmDisposableVM`. | Yes | Parse clean and **PSScriptAnalyzer clean** (PowerShell 7.5.4, Linux). Ownership-manifest bookkeeping unit-tested off Windows. | No |

## Safety rules all probes follow

- Never terminate the user's apps. The only exception is `--test-close-target-after-launch`, a timing measurement that is hard-restricted to the harmless `FocusTarget.exe`. That is detect-and-terminate, not launch prevention.
- Never discard data.
- Never require disabling Smart App Control or Advanced Protection.
- Stop by themselves (≤ 60 min).
- Have an immediate stop.
- Undo every change they make. Recovery is described precisely, not as "always immediate":
  - Windows audio: immediate on normal stop; within seconds via the guardian if only the probe dies; only on next start or `--recover` if probe and guardian both die; unknown after reboot (test R6).
  - VM policies: restored only from the ownership manifest; `-SimulateCrash` measures what persists when nothing cleans up.
- Log only app identifiers, timings and outcomes, locally. App identifiers can still reveal personal usage, and the testers' instructions say so.

## Pinned toolchains

| Part | Pin |
|---|---|
| Rust core | `rust-toolchain.toml` → 1.97.0 |
| Windows | `windows/global.json` → .NET SDK 10.0.1xx (latest patch) |
| Android | Gradle wrapper 8.14.3; `android/gradle/libs.versions.toml` → AGP 8.13.0, Kotlin 2.2.20; compile/target SDK 36; JDK 17 |

The Gradle wrapper was generated offline. Its distribution checksum
(`distributionSha256Sum`) is not yet set; CI validates the wrapper JAR. Add
the checksum from gradle.org/release-checksums the first time CI runs.

## How to test

Start with `docs/device-test-checklist.md`. Tester instructions ship inside
each download as `TESTER.md`.
