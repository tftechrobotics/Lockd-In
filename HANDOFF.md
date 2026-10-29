# Handoff — read this first

**Last session:** 2026-10-29 (fifth session) · **Milestone:** 1 — **OPEN**; M2 (design + main flow) **in progress**
**Read first:** `docs/product-spec.md` (v2, owner direction) and `docs/backlog.md`.
**Repository:** github.com/tftechrobotics/Lockd-In (private).
**Next:** owner answers U11 (Windows UI stack) and U12 (how the web app
reaches devices); owner sets up Google Play and Azure Artifact Signing so the
probes can run on real devices (R18: protections stay on).

## Status by evidence level

| Component | Implemented | Compiled / checked (where) | Tested on device / VM | Still proposed |
|---|---|---|---|---|
| `lockdin-core` | Yes, incl. `clock`; `examples/parity_vectors.rs` | 35 tests (3 property tests), fmt, clippy — Ubuntu 24.04, Rust 1.97.0 | n/a (pure logic) | Heartbeat transport (M5); native clock sources (M3/M4) |
| **Web app** (`apps/web`) | Yes: Cream & Ink; Focus (setup → focus → break → resume → recap, unlock countdown, emergency), Schedule, Profiles, Devices, Settings; local storage only | 19 unit tests incl. 773 Rust parity samples; 23 Playwright tests (flow on a simulated clock, axe A/AA × 2 themes × all pages, keyboard, 200% text, 320/390 px) — Chromium, Linux | n/a (blocks nothing, by design) | Pairing/relay (U12), presets, study timing, nudges UI beyond tone |
| Cream & Ink design system | `design/tokens.json` + contrast check; wordmark, lock mark, library edge SVGs | Contrast check passes (24 pairs + control borders) | — | Windows (XAML) and Android (Compose) token outputs |
| Windows probe + target + guardian | Yes | CI x64/ARM64 builds; headless runner sanity run passes | **No** — Smart App Control blocks unsigned build on owner PC | — |
| Windows signing in CI | Yes (Azure Artifact Signing via OIDC, gated on repo variables, verifies every signature) | actionlint clean; unsigned path runs green | n/a | Needs owner's Azure account |
| Android probe + target | Yes. Play (release) build omits accessibility detector | Debug APKs + signed-bundle job in CI | **Emulator only** (API 35, CI): notification, block screen, release all pass. **Not on a phone** — Auto Blocker blocks sideload | — |
| Play submission kit | `docs/play/` (guide, form answers, privacy policy page) | — | — | Needs Play account, privacy-policy URL, contact email |
| App Control compiler spike | `probes/windows/AppControlSpike` (no deployment) | 20 self-tests (Linux); Windows CI: same parsed content as `ConvertFrom-CIPolicy`, bytes differ (header version) | **No** enforcement test (VM only) | — |
| VM policy scripts | Yes | Parse + PSScriptAnalyzer clean | **No** | — |
| Earlier design canvas ("Quiet instrument") | Superseded by Cream & Ink (R20) | — | — | — |
| Windows service, product UI, pairing, calendar | No | — | — | ADR-0007, M2–M6 |

`tools/sandbox-checks.sh` reruns every Linux-side check (core, .NET builds,
spike self-test, Kotlin type-check with an R stand-in, VM script lint,
actionlint).

## What changed this session (fifth)
1. Owner's v2 direction recorded: `docs/product-spec.md`, `docs/backlog.md`,
   R19–R30, U11–U12 in `product-decisions.md`; E12 superseded; E13–E14 added.
2. Cream & Ink design system and assets (`design/`), `docs/design.md`.
3. Web companion app with the full main flow, honest "timer only" status,
   tests and CI job `web`; core CI job now fails if parity vectors drift.
4. Private preview page of the web app published (artifact "Lockd In web
   preview"); rebuild with `npm run build && node scripts/build-preview.mjs <file>`.

## Earlier this day (fourth session)
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
0. **Owner:** answer U11 and U12 (see product-decisions). Then the developer
   builds the Windows shell (Focus/Schedule switch, Menu, tray, close ≠ end)
   and the Android shell (bottom nav, insets, large fonts) in Cream & Ink,
   and generates XAML/Compose tokens from `design/tokens.json`.
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
4. **Developer, independent:** presets + saved resources (C2/C3), custom
   block messages (C4), study timing (C5) in the web app and core; block-screen
   design; onboarding/permissions design; bump
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
