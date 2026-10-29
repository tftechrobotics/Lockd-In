# Focus

A serious, non-VPN focus restriction app for personal Windows and Android
devices: scheduled focus periods and breaks, approved apps per device,
synchronized timing, optional Google Calendar suggestions.

**Stage:** Milestone 1 (feasibility) — **open**. No installable app yet, and
no restriction has been demonstrated on a device. Read `HANDOFF.md` first.

## Repository

| Path | What | State |
|---|---|---|
| `core/` | `focus-core` (Rust): schedules, DST-correct timelines, session state, override scopes, emergency recovery, release reasons, conflicts, per-device status | Implemented; 21 tests pass (Linux) |
| `probes/windows/` | Windows probe + harmless target (C#/.NET 10) | Compiles; not run |
| `probes/android/` | Android probe + harmless target (Kotlin) | Not yet compiled (needs CI) |
| `probes/windows-vm/` | VM-only policy experiments (PowerShell) | Written; not run |
| `docs/` | Enforcement comparison, ADRs, milestones/evidence, risks, decisions, device checklist | Current |
| `.github/workflows/ci.yml` | Tests + downloadable probe builds | Not yet run (no repository) |

## Build and test

```sh
cargo test                                   # core: unit, integration, property tests
cargo clippy --all-targets -- -D warnings
cargo run -p focus-core --example timeline   # prints the brief's example session

dotnet build probes/windows/Probe            # Windows probe (any OS; runs on Windows only)
dotnet build probes/windows/Target
cd probes/android && ./gradlew :probe:assembleDebug :target:assembleDebug   # needs Android SDK
```

Device testing: `docs/device-test-checklist.md`.
