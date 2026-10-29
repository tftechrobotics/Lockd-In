# Handoff — read this first

**Last session:** 2026-10-29 (fourth session) · **Milestone:** 1 — **OPEN**
**Repository:** github.com/tftechrobotics/Lockd-In (private).
**Next:** owner sets up Google Play (blocked on ID upload) and Azure Artifact
Signing; developer then runs phone and PC smoke tests through those trusted
channels (R18: device protections stay on).

## Status by evidence level

| Component | Implemented | Compiled / checked (where) | Tested on device / VM | Still proposed |
|---|---|---|---|---|
| `lockdin-core` | Yes, incl. new `clock` module | 35 tests (3 property tests), fmt, clippy — Ubuntu 24.04, Rust 1.97.0 | n/a (pure logic) | Heartbeat transport (M5); native clock sources (M3/M4) |
| Windows probe + target + guardian | Yes | CI x64/ARM64 builds; headless runner sanity run passes | **No** — Smart App Control blocks unsigned build on owner PC | — |
| Windows signing in CI | Yes (Azure Artifact Signing via OIDC, gated on repo variables, verifies every signature) | actionlint clean; unsigned path runs green | n/a | Needs owner's Azure account |
| Android probe + target | Yes. Play (release) build omits accessibility detector | Debug APKs + signed-bundle job in CI | **Emulator only** (API 35, CI): notification, block screen, release all pass. **Not on a phone** — Auto Blocker blocks sideload | — |
| Play submission kit | `docs/play/` (guide, form answers, privacy policy page) | — | — | Needs Play account, privacy-policy URL, contact email |
| App Control compiler spike | `probes/windows/AppControlSpike` (no deployment) | 20 self-tests (Linux); Windows CI: same parsed content as `ConvertFrom-CIPolicy`, bytes differ (header version) | **No** enforcement test (VM only) | — |
| VM policy scripts | Yes | Parse + PSScriptAnalyzer clean | **No** | — |
| M2 design | Canvas: foundations + 6 screens × light/dark; `docs/design.md` | — | Not checked with screen reader/keyboard/scaling | Remaining flows |
| Windows service, product UI, pairing, calendar | No | — | — | ADR-0007, M2–M6 |

`tools/sandbox-checks.sh` reruns every Linux-side check (core, .NET builds,
spike self-test, Kotlin type-check with an R stand-in, VM script lint,
actionlint).

## What changed this session
1. **Play path (R18):** signed AAB CI job (secrets `LOCKDIN_UPLOAD_KEYSTORE_B64`,
   `LOCKDIN_UPLOAD_STORE_PASSWORD`; alias `lockdin-probe-upload`; versionCode =
   run number). A probe-only upload key was handed to the owner as a file;
   the session's copy was shredded. Release build drops the accessibility
   detector (E10). Emulator workflow records the foreground-service demo
   video Play asks for and checks notification / block screen / release.
2. **Windows signing:** CI step for Azure Artifact Signing (actions pinned by
   commit: azure/login v3.1.0, artifact-signing-action v2.0.0), plus
   `SIGNING.txt` in each download. Owner guide `docs/windows-signing.md`.
3. **Clock changes (R12):** `core::clock::SessionClock` (E11).
4. **M2 design direction** (E12), proposed only.
5. **App Control compiler spike** (R6b) with vendored MIT code, attributed.

## Next actions
1. **Owner — Play:** account (ID upload), add the two secrets, choose
   privacy-policy hosting and contact email, then follow `docs/play/README.md`.
   Developer then: replace `CONTACT_EMAIL_PLACEHOLDER`, help upload, smoke
   test S1–S5 on the S24+.
2. **Owner — Windows:** Azure Artifact Signing per `docs/windows-signing.md`
   (validation 1–20 business days). Developer then: confirm `SIGNING.txt`
   shows Valid, owner runs the Windows smoke test.
3. **Developer — App Control spike result (CI, windows-2025):** Microsoft's
   compiler accepted our XML and both binaries parse to identical content,
   but the bytes differ from byte 0: our compiler writes header format
   version 11 (44 bytes of newer sections), Microsoft's wrote an older
   version. Next: emit the lowest format version the policy needs (or
   confirm `CiTool` accepts v11 in the VM, test 11), aim for byte equality,
   then add hash and package-family rules.
4. **Developer, independent:** remaining M2 flows (onboarding/permissions,
   block screen, app profiles, pairing, settings, diagnostics); bump
   deprecated actions (checkout/setup-java v4 → v5); wire native clock
   sources into the probes.
5. **Later:** recovery R1–R6, extended tests, battery pair, VM experiments.

## Open owner decisions
U1–U10 in `docs/product-decisions.md`. U1 and U3 are needed to close M1;
U5 (override delay, 90 s shown as placeholder) affects M2 design.

## Environment notes for the next session
- **Blocked from the sandbox:** Google Maven, Android SDK, NuGet,
  services.gradle.org, static.rust-lang.org, GitHub Actions artifact and log
  downloads (read CI results through job annotations instead), the GitHub
  secrets API.
- **Reachable:** crates.io, npm, PyPI, Ubuntu archive, anonymous GitHub reads.
- .NET 10 SDK: `apt-get install dotnet-sdk-10.0`. Rust: `RUSTUP_TOOLCHAIN=stable` (1.97.0).
- Commit as `Claude <noreply@anthropic.com>` with the session trailers.
