# Enforcement probes (Milestone 1 experiments)

**Experiments, not product code.** Each answers one high-risk question with
evidence from a real device or a disposable VM. Nothing here may be described
as "working blocking" until its results are recorded in `docs/milestones.md`.

| Folder | What | Build status (2026-10-29) | Run status |
|---|---|---|---|
| `windows/` | `FocusProbe.exe` (per-user: minimize, per-app mute with crash-safe ledger, launch detection; terminate-at-launch only for the harmless target) and `FocusTarget.exe` (harmless app that logs its own exposure and mute state) | Compiles with .NET SDK 10.0.113, warnings as errors, on Linux. Self-contained Windows executables are built by CI. | Not run |
| `android/` | `focus-probe.apk` (usage-access or accessibility detector; block screen, overlay or home cover; audio-focus option; battery baseline mode; reboot/swipe/permission logging) and `focus-probe-target.apk` (harmless app: tone, picture-in-picture, self-measured exposure) | **Not compiled yet**: this sandbox can't reach Google's Maven repository. Syntax-checked with Gradle's bundled Kotlin compiler. CI compiles it. | Not run |
| `windows-vm/` | PowerShell experiments for App Control, AppLocker, browser policies and firewall, plus cleanup. **Refuse to run unless the machine is a VM and `-ConfirmDisposableVM` is passed.** | Written; not executed or syntax-checked (no PowerShell here) | Not run |

## Safety rules all probes follow

- Never terminate the user's apps. The only exception is `--terminate-new-launches`, which is restricted to the harmless `FocusTarget.exe`.
- Never discard data.
- Never require disabling Smart App Control or Advanced Protection.
- Stop by themselves (≤ 60 min).
- Have an immediate stop.
- Undo every change they make, including after a crash (Windows mute ledger, `--recover`).
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
