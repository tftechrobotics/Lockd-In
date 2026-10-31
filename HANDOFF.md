# Handoff — read this first

**Last session:** 2026-10-29 (fifth session, design pass) · **Milestone:** 1 — **OPEN**; M2 (design + main flow) **in progress**
**Read first:** `docs/product-spec.md` (v2, owner direction) and `docs/backlog.md`.
**Repository:** github.com/tftechrobotics/Lockd-In (private).
**Next:** owner sets up Azure Artifact Signing (Windows app + probe) and
Google Play (Android probe) so builds can run on real devices (R18). Developer:
Android Compose shell in Cream & Ink, then presets/resources (C2–C3), then the
Windows blocking agent (B4) and pairing relay (B6, needs hosting approval).
Owner wants architecture built before device setup; Azure (pay-as-you-go,
not free trial — Artifact Signing doesn't support trials) and Play wait.

## Status by evidence level

| Component | Implemented | Compiled / checked (where) | Tested on device / VM | Still proposed |
|---|---|---|---|---|
| `lockdin-core` | Yes, incl. `clock`; `examples/parity_vectors.rs` | 35 tests (3 property tests), fmt, clippy — Ubuntu 24.04, Rust 1.97.0 | n/a (pure logic) | Heartbeat transport (M5); native clock sources (M3/M4) |
| **Web app** (`apps/web`) | Yes: Cream & Ink; Focus (setup → focus → break → resume → recap, unlock countdown, emergency), Schedule, Profiles, Devices, Settings; local storage only | 19 unit tests incl. 773 Rust parity samples; 23 Playwright tests (flow on a simulated clock, axe A/AA × 2 themes × all pages, keyboard, 200% text, 320/390 px) — Chromium, Linux | n/a (blocks nothing, by design) | Pairing/relay (U12), presets, study timing, nudges UI beyond tone |
| **Windows app** (`apps/windows`) | Yes: WinUI 3 shell (Windows App SDK 2.5.1, .NET 10) hosting the shared UI in WebView2 (desktop layout); title bar, tray menu, close-to-tray, single instance, min size, offline bundle, validated bridge | Builds x64 + ARM64 in CI; 19 bridge tests (Linux + CI); **launch test passes on windows-2025** (window, tray, WebView2 loads bundled UI, handshake, clean quit) | **Not on a person's PC** (unsigned → Smart App Control) | Blocking agent (B4); launch at sign-in setting; installer (B7) |
| **Android app** (`apps/android`) | Yes: Compose shell in Cream & Ink — bottom nav, Focus flow, unlock/emergency, recap, Schedule (system time picker), Profiles, Devices, Settings; Kotlin engine + schedule + state | 7 JVM unit tests (773 parity samples, DST, state codec) locally and in CI; lint; 10 Compose UI tests on API 35 emulator; screenshots on branch `ci-screenshots` (`git show origin/ci-screenshots:android/01-setup-light.png`) | **Not on a phone** (Play pending) | Blocking (from probe), notifications, clock port |
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

## Design pass 4 — exploration, awaiting owner's pick (2026-10-31)
- Two isolated directions, same sample session: **A** session composer + refined padlock with
  integrated countdown; **B** sentence composer + typography-led numerals with a mini lock and a
  session rail. Web: `apps/web/lab.html` + `src/lab/` (dev server only, not in the build:
  `npx vite`, then `/lab.html?dir=a|b&screen=setup|focus|break|done|states&shell=web|desktop&theme=light|dark`).
  Android: `app/src/androidTest/.../lab/` (never ships); `LabScreenshotTest` → `ci-screenshots` (lab-*.png).
- Lock rule in both: shackle closes only when blocking is verified; timer-only focus shows an open shackle.
- Known lab defect: A's "rounds · Change" row squeezes at 2× text on Android (fix with FlowRow when applied).
- CI: upload-artifact steps are now non-fatal (GitHub artifact storage quota was hit); retention 7 days.
- Next: apply the chosen direction to production Focus (web, Windows, Android), then the remaining screens.

## What changed this session (design pass 3 — R33)
- Owner: the mockup is a mood reference; fix hierarchy and usability. Rules now live in the pass-3
  block at the top of `docs/design-spec.md`.
- Web + Windows layout (`apps/web/src/ui/FocusPage.tsx`, `SchedulePage.tsx`, `styles/app.css`):
  new Focus setup (task, segmented length + inline custom, Breaks/Focus rounds, More options,
  summary card with the primary action), running view, Schedule list + dialog editor,
  actionable Devices page. Library artwork removed from the shell.
- Android (`ui/Kit.kt`, `FocusScreen.kt`, `ScheduleScreen.kt`, `LockdInApp.kt`, `OtherScreens.kt`):
  same structure natively; action bar pinned above bottom nav; full-screen schedule editor.
- Model: schedules gained one-time `date` and `enabled` (web store + Android codec; old saves load
  as weekly/on). Tests added on both sides.
- Honesty: "Start timer" everywhere blocking isn't verified. Android flag
  `PHONE_BLOCKING_AVAILABLE` (FocusScreen.kt) stays false until device tests pass.
- Known gaps: the clickable prototype artifact (`design/prototype/`) still shows the earlier
  design; Profiles app selection is not built (needs blocking); Android Settings/Profiles got the
  new kit but no new screenshots beyond Devices.

## What changed this session (design pass, earlier)
- Owner re-sent the approved Cream & Ink reference (now `design/reference/cream-and-ink-mockup.png`)
  and asked for faithful implementation. All three Focus screens rebuilt to it; other pages restyled.
  Exact rules: `docs/design-spec.md` (incl. §9 deliberate deviations: no "What's the move?",
  honest device phrases, Custom pill, periods control, lock icon for the Focus tab).
- Reusable instructions: `design/skill/lockd-in-design/SKILL.md` (offered to the owner as a skill).
- CI now photographs the **real WinUI window** (light/dark, 620×748 and minimum 480×600) on
  windows-2025 and pushes to branch `ci-screenshots-windows`; Android screenshots stay on `ci-screenshots`.
- Tokens: `controlLine` light → #8F8270 (3.3:1), new `statusDot`, drawing opacity up; Outfit 800 added
  for timer digits (web variable font; Android `outfit_800.ttf` via `design/export-android.py`).
- Verified: Chromium (web + Windows layout, 30 Playwright tests incl. axe/keyboard/200% text),
  Android emulator API 35 (10 UI tests + screenshots incl. 2× font), real WinUI window on a CI runner.
  Not verified: owner's PC or phone, NVDA/TalkBack, Firefox/Safari.

## What changed this session (fifth, later)
- Android app shell (A8). Compose BOM pinned to 2025.08.00 because 2026.x
  requires AGP 9.1 + compileSdk 37 (A9). Local Kotlin checks without Maven:
  `apt-get install junit4 libhamcrest-java libandroid-json-java`, then compile
  `engine/`, `state/AppState.kt` and `src/test` with the Gradle-bundled kotlinc
  (see the command in this session's history: classpath = kotlin-stdlib,
  /usr/share/java/junit4-4.13.2.jar, hamcrest-core.jar, com.android.json.jar).
- Android CI publishes UI-test screenshots to `ci-screenshots` (artifact
  downloads are blocked from the sandbox; git fetch works).
- Owner chose to build architecture before device setup (Azure, Play).

## What changed this session (fifth, continued)
- Owner answered U11 → R31 (native shell + shared UI, desktop-specific
  layout, offline, blocking independent of the window; ADR-0009) and U12 → R32
  (paired browser, no accounts, encrypted relay; ADR-0010).
- Shared UI: desktop layout (Focus/Schedule switch + keyboard menu), honest
  "This PC" status from the host, tray quick-start command, session/theme
  reports to the shell; 30 Playwright tests incl. a WebView2 bridge stand-in.
- Windows shell implemented; CI job `windows-app`. Lessons: unpackaged WinUI 3
  needs `EnableMsixTooling=true` or XAML fails to load (PRI missing); the
  lambda in `Application.Start` must not be named `_`.

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
