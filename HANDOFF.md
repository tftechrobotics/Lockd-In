# Handoff — read this first

**Last session:** 2026-11-01 (functional development, R37) · **Milestone:** M1 reliable single-device focus — **IN PROGRESS**
**Read first:** `docs/audit-2026-11.md` (inventory, Freedom, platform limits, architecture) and `docs/milestones.md` (new M1–M5 criteria + evidence table).
**Repository:** github.com/tftechrobotics/Lockd-In (private).

**Where things stand**
- **Android blocking is in the real app** (`apps/android/app/src/main/java/app/lockdin/enforce/`): `FocusService`
  (specialUse FGS, usage events, 500 ms poll), `BlockActivity`, `BlockPolicy` (pure, tested), `Capabilities`,
  `BootReceiver`; state v2 (per-profile rules, commitment, session snapshot) with v1 migration; Store is a process
  singleton shared by UI and service. Lock closes only on a fresh PROTECTING report (`EnforcerStatus`).
  Emulator E2E: `apps/android/ci/enforcement-e2e.sh` (job "Blocking end-to-end"); debug-only adb control in `src/debug`.
- **Accessibility mode (R38):** `FocusAccessibilityService` covers apps on window change and blocks sites by
  domain via browser address bars (`BROWSER_URL_BARS`). Setup screen shows a disclosure first. Usage access is
  the fallback. Emulator E2E has an Accessibility phase (Chrome site test runs only if the image has Chrome).
- **Windows agent** (`apps/windows/LockdIn.Agent`, rules in `LockdIn.Enforcement`): minimize + mute after launch,
  mute ledger + guardian, plan/status files in `%LOCALAPPDATA%\LockdIn\agent`. Shell writes the plan from the UI's
  `ui.restriction` message, starts the agent, relays `host.agent_status`; Profiles page has a PC app picker
  (`host.apps`). Runner E2E: `apps/windows/scripts/agent-e2e.ps1`. **Not yet observed working on any Windows machine.**
- **Phone UI:** approved R36 views are native now (`ui/ClockLock.kt`; Focus tab = idle lock → setup with Back →
  minimal running/recap with 'Details/Summary and options').
- **Known gaps:** emulator
  detection p95 3.6 s (measure on phones); scheduled starts (alarms) not implemented; Windows launch at sign-in to
  resume after reboot not implemented (agent resumes when the app is opened); no website rules yet.
- **Test APK (R39):** every push to apps/android builds `lockd-in-test.apk` on branch `test-apk`
  (`git show origin/test-apk:lockd-in-test.apk > x.apk`). Debug build, public test key.
- **Owner setup needed for real-device evidence:** Google Play developer account (internal testing) and Azure
  Artifact Signing. Relay hosting only for M2. None started.

`tools/sandbox-checks.sh` reruns every Linux-side check (core, .NET builds,
spike self-test, Kotlin type-check with an R stand-in, VM script lint,
actionlint).

## Current phase — UI design and prototyping only (R35, 2026-10-31)
- Review tool: **design preview studio** (`src/preview/Studio.tsx`, `src/demo.ts`): one live copy of the app in
  a Windows window / Android phone / web page, in a single document (the earlier iframe shell is retired —
  pointer input into nested frames was unreliable in the artifact viewer). `npm run build:preview` then
  `node scripts/stage-preview.mjs <dir>` → publish as the "Lockd In Preview" artifact (opens the studio).
  Dev: `npm run preview:dev`, open `/?studio=1`. Samples: idle / focusing / break / unlock pending / done; theme;
  100/200% text; demo switches (also on the Devices page: "Turn on blocking", "Pair", each labelled Demo).
  Phone demo file: `npx vite build --config vite.phone.config.ts --mode preview && node scripts/build-phone-demo.mjs out.html`.
  Demo code is compiled out of the production build.
- Lock timer: 60 clock ticks (elapsed dims clockwise from 12); shackle is its own shape behind the body and
  slides shut (520 ms) when blocking is verified, starting open on first appearance so locking is visible.
- Status: owner approved the circular lock direction (R36); it is in the web/Windows/Android previews with a
  "Timer & wordmark" sheet. Next: owner sign-off on Focus, then Schedule → Profiles → Devices → Settings.
- Not yet carried to native (deferred to the native batch): Android `wordmark.xml`/`lock_mark.xml`
  (`python3 design/export-android.py`), Android Compose timer (`ui/Timer.kt` still numerals), launcher icons,
  Windows `lock-mark-32.png` title icon and `app-icon` art.
- **Unresolved native issue (deferred):** Android CI `AppTest.configureStartFocusBreakResumeFinish` fails at
  "Ends at 6:55 PM … is not displayed" (pinned plan line) after choosing 25 min + 5 min breaks; not the keyboard
  (reordering didn't fix it). Other 14 emulator tests pass and screenshots render. Investigate when native
  validation resumes.

## Design pass 4 — "A setup, B timer" applied to Focus (2026-10-31, R34)
- Owner picked the mix from the two lab directions. Rules: pass-4 block at the top of `docs/design-spec.md`.
- **Web + Windows layout** (`apps/web/src/ui/FocusPage.tsx`, new `ui/Timer.tsx`, `styles/app.css`):
  question composer (What / How long tiles / rounds line with Change / Which profile / Start timer),
  web-wide preview column; running = task, mini-lock status, numerals, session rail, facts,
  Need out? (End now inside). Recap uses the same column. **Tested:** 32 Playwright (flow,
  axe light/dark incl. active session, keyboard, 320/390 px at 200% text incl. running) +
  23 unit — Chromium, Linux. Rendered screenshots checked at 1280/390 web, 640/480 desktop layout,
  dark break, 320 px at 200% (numerals overflowed → fixed with container-sized numerals).
- **Android** (`ui/FocusScreen.kt`, new `ui/Timer.kt`, `Kit.kt` `CustomLengthRow`; `Components.kt`
  LockTimer removed): same structure natively; tiles 2 × 2 at large text; rounds line is a FlowRow;
  "Ends at … · total" + Start timer pinned; running facts as divided rows; Need out? only.
  `AppTest.kt` updated + new screenshots 11 (running 200%), 12 (rounds open), 13 (recap).
  Compiles/tests only in CI (no Android SDK in the sandbox) — see CI status below.
- Windows real window: check `ci-screenshots-windows` after the next Windows CI run.
- Lab sources (`apps/web/lab.html`, `src/lab/`, androidTest `lab/`) are kept for reference; dev/test only.
- Next: carry the system to Schedule / Profiles / Devices / Settings; refresh the prototype artifact.
- CI: upload-artifact steps are non-fatal (GitHub artifact storage quota was hit); retention 7 days.

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
