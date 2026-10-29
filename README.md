# Focus

A serious, non-VPN focus restriction app for personal Windows and Android
devices: scheduled focus periods and breaks, approved apps per device,
synchronized timing, optional Google Calendar suggestions.

**Stage:** Milestone 1 (feasibility) — **open**. No installable app yet, and
no restriction has been demonstrated on a device. Read `HANDOFF.md` first.

## Repository

| Path | What | Evidence |
|---|---|---|
| `core/` | `focus-core` (Rust): schedules, DST, session state, override scopes, emergency recovery, release reasons, conflicts, per-device status (Unknown for silent peers) | 21 tests pass (Linux) |
| `probes/windows/` | Windows probe + guardian + harmless target (C#/.NET 10) | Compiles; not run on Windows |
| `probes/android/` | Android probe + harmless target (Kotlin) | Type-checks against API 36; APK not yet built (CI) |
| `probes/windows-vm/` | VM-only policy experiments (PowerShell), separate download | Parse + lint clean; not run |
| `tools/sandbox-checks.sh` | Reruns all checks that don't need Google Maven/NuGet/Windows | Passing |
| `docs/` | Enforcement comparison, ADRs, milestones/evidence, risks, decisions, device checklist | Current |
| `.github/workflows/ci.yml` | Tests, APK and Windows builds, headless Windows sanity run, artifacts | Lint clean; never run (no repository) |

## Build and test

```sh
tools/sandbox-checks.sh                      # everything checkable without Windows/Android tooling
cargo test                                   # core only
cd probes/android && ./gradlew :probe:assembleDebug :target:assembleDebug   # needs Android SDK (CI)
```

Device testing: `docs/device-test-checklist.md`.
