# ADR-0001: Shared domain logic in a Rust core

**Status:** Engineering decision (2026-10-29), reversible per Consequences · **Implemented:** scheduling, timeline, session state, conflicts, protection

## Context
Schedules, DST handling, override rules and "is the group protected" must give
identical answers on Windows and Android. Divergence would show up as one
device restricting while the other doesn't — the failure users would notice most.

## Options
1. **Rust core** with UniFFI → Kotlin and a C ABI → C#.
2. Two implementations (C# and Kotlin) checked against shared JSON test vectors.
3. Kotlin Multiplatform core (JVM/Android natively; Windows via Kotlin/Native DLL).

## Decision
Option 1. Rust compiles natively for Android (NDK) and Windows (x64/ARM64), has
mature time-zone support (`chrono-tz`) and property testing, and can be built
and tested in any CI. UniFFI is the standard Kotlin binding route.

## Consequences
- Three languages in the repo (Rust, C#, Kotlin). Mitigated by keeping the
  core free of OS calls and the FFI surface small and JSON-shaped.
- C# bindings: choose between `uniffi-bindgen-cs` and a hand-written C ABI in
  Milestone 3 after a spike; both are reversible.
- If FFI proves painful, option 2 remains available using the core's test
  cases as the shared vectors.
