# Focus

Windows and Android app that helps people start focused work: scheduled focus
periods and breaks, approved apps per device, synchronized timing across a
computer and a phone, and optional Google Calendar suggestions.

**Current stage:** Milestone 1 (feasibility). There is no installable app yet.
Read `HANDOFF.md` first.

## Repository

| Path | What | State |
|---|---|---|
| `core/` | `focus-core` Rust crate: schedules, DST-correct timelines, session state, overrides, conflicts, protection status | Implemented, tested on Linux |
| `probes/windows/` | Windows enforcement experiment (C#/.NET 10) | Compiles; not yet run on Windows |
| `probes/android/` | Android enforcement experiment (Kotlin) | Not yet compiled |
| `docs/` | Feasibility, architecture, ADRs, milestones, risks, decisions | Current |
| `.github/workflows/ci.yml` | CI for all three | Not yet run |

## Build and test

Core (any OS with Rust ≥ 1.85):

```sh
cargo test                                   # unit, integration and property tests
cargo clippy --all-targets -- -D warnings
cargo run -p focus-core --example timeline   # prints the brief's example session
cargo run -p focus-core --example timeline -- my-schedule.json 2026-11-03
```

Probes: see `probes/README.md`.
