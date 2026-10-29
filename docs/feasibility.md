# Milestone 1 — Feasibility findings

Researched 2026-10-29. Each finding is tagged:

- **Verified (doc)** — read in current official or primary documentation (source listed).
- **Reported** — from secondary reporting; confirm before relying on it.
- **Prior knowledge** — from general knowledge, not re-checked this session.
- **Experiment pending** — only a real-device probe can settle it (see `probes/`).

## Bottom line

App blocking on personal Windows and Android devices is **feasible as a
commitment tool with deliberate friction, not as a lock**. Both platforms let
an ordinary app notice a restricted app and move it out of the way; neither
lets a normal app make that impossible to undo for the device owner. That
matches the brief, which forbids irreversible traps. The product must say so
plainly.

The two highest-risk assumptions — fast, reliable detection-and-cover on
Windows without admin rights, and on Android without managed-device features —
are now packaged as probes that need one run on the owner's real devices.

---

## Windows

### Supported versions

| Finding | Status |
|---|---|
| Windows 11 24H2 Home/Pro stopped receiving security updates on 13 Oct 2026; 25H2 is supported to 12 Oct 2027 and 26H2 (released 29 Sep 2026) to 10 Oct 2028 | Reported ([Tech-ish](https://tech-ish.com/2026/10/04/windows-11-24h2-home-and-pro-lose-security-updates-from-13th-october/)); confirm on Microsoft lifecycle pages |
| Windows 10 mainstream support ended 14 Oct 2025; consumer ESU reportedly extended to 12 Oct 2027 | Reported (same source); **must be confirmed** before any Win10 promise |

**Proposed support matrix:** Windows 11 25H2 and 26H2, x64 and ARM64.
Windows 10 22H2: not supported at launch; revisit only if the owner's PC needs it.

### Enforcement options

| Option | Verdict | Why |
|---|---|---|
| **Per-user window restriction** — `SetWinEventHook` foreground/restore events + 2 s sweep; minimize restricted windows and show an explanation | **Chosen, pending probe** | No admin rights, no driver, never closes apps or loses work. Win32 APIs are long-stable (Prior knowledge). |
| App Control for Business / AppLocker launch policies | Rejected for v1 | Policies take effect on all editions (Verified (doc), [Microsoft Learn](https://learn.microsoft.com/en-us/windows/security/application-security/application-control/app-control-for-business/feature-availability)), but deployment is an admin-level, system-wide code-integrity change; it governs launches, not already-open windows; toggling it at every break is risky. Possible future "strict mode". |
| Terminating processes | Rejected | Violates "do not force-close or discard unsaved work". |
| IFEO "debugger" hijacks, kernel drivers, hidden processes | Rejected | Weakens OS security or conceals behavior — forbidden by the brief. |
| Privileged Windows service for tamper resistance | Deferred | Adds attack surface. Only add if real use shows the per-user agent is too easy to stop. |

**Known limits of the chosen approach (to confirm with probe W5–W7):**
an unelevated process cannot manipulate windows of elevated (admin) apps
(UIPI); exclusive-fullscreen games may not minimize cleanly; the owner can
always end Focus from Task Manager. Store apps run inside
`ApplicationFrameHost.exe`, so identity must look through to the real process
and its AppUserModelID (handled in the probe).

### Websites

| Finding | Status |
|---|---|
| A Chrome/Edge MV3 extension can talk to the desktop app through native messaging; the host is registered per-user under `HKCU\SOFTWARE\Google\Chrome\NativeMessagingHosts\` (no admin), `allowed_origins` must list the exact extension ID | Verified (doc), [Chrome native messaging](https://developer.chrome.com/docs/extensions/nativeMessaging) |
| On a personal device the user can remove or disable the extension, and it must be explicitly allowed in Incognito/InPrivate | Prior knowledge |

**Proposal:** website restriction ships as a Chrome/Edge extension that the
desktop app detects and reports honestly ("Edge: websites restricted" vs
"Chrome: extension not installed — websites not restricted"). If a browser is
an approved app and has no working extension, Focus says websites are
**not** restricted. No hosts-file edits, DNS interception, proxies or root
certificates. Firefox: later.

---

## Android

### Platform state

| Finding | Status |
|---|---|
| Android 17 (API 37) stable released 16 Jun 2026; adds a built-in per-app lock (not usable by third-party apps) | Reported ([Wikipedia](https://en.wikipedia.org/wiki/Android_17)) |
| With **Advanced Protection** on, Android 17 limits AccessibilityService to verified accessibility tools — other apps lose it. Advanced Protection is user-enabled | Reported ([The Hacker News, Oct 2026](https://thehackernews.com/2026/10/android-17-advanced-protection-locks.html); [Mar 2026](https://thehackernews.com/2026/03/android-17-blocks-non-accessibility.html)) |
| Background activity launches restricted since Android 10 and tightened in Android 15 | Verified (doc), [Android 15 behavior changes](https://developer.android.com/about/versions/15/behavior-changes-15) |
| Android 15: an app may start a foreground service from the background via the overlay exemption only while it already has a **visible** overlay window | Verified (doc), same page |
| Kiosk / device-owner features require a managed device — unavailable on a personal phone | Prior knowledge; consistent with brief |

### Detection options (the probe compares both)

| Option | Strengths | Costs and risks |
|---|---|---|
| **UsageEvents polling** (`PACKAGE_USAGE_STATS` special access) inside a foreground service | No Accessibility declaration; works under Advanced Protection | Polling latency and battery cost unknown (Experiment pending); needs a foreground service of type `specialUse`, which requires a Play declaration with a video and is reviewed (Verified (doc), [Play FGS requirements](https://support.google.com/googleplay/android-developer/answer/13392821)) |
| **AccessibilityService** (window-change events only, no content) | Event-driven, likely faster and cheaper | Non-tool apps must show an in-app prominent disclosure with affirmative consent and submit a Play declaration with video; deceptive use risks suspension (Verified (doc), [Play AccessibilityService policy](https://support.google.com/googleplay/android-developer/answer/10964491)); unavailable under Advanced Protection; users find the permission alarming |

**Cover:** a full-screen `TYPE_APPLICATION_OVERLAY` window (needs "display
over other apps") with a "Go to home screen" action. It covers the app; it
does not stop it or touch its data.

**Proposed direction (decide after probe A1–A10):** UsageEvents as the
default detector, accessibility as an optional faster mode with the
disclosure. Never require accessibility.

### App selection

`QUERY_ALL_PACKAGES` is permitted only when broad visibility is the app's core
purpose and a narrower method won't do (Verified (doc),
[Play policy](https://support.google.com/googleplay/android-developer/answer/10158779)).
**Proposal:** list launchable apps with a `<queries>` entry for
`MAIN/LAUNCHER` — enough to choose approved apps — and avoid
`QUERY_ALL_PACKAGES`.

### Distribution

| Finding | Status |
|---|---|
| Developer verification: registration requirements started 30 Sep 2026 in Brazil, Indonesia, Singapore and Thailand; global expansion in 2027. A free limited-distribution account lets students share with up to 20 devices without ID or fee. Unverified apps need an "advanced sideloading" flow with a 24-hour wait | Reported ([Android Authority](https://www.androidauthority.com/android-sideloading-changes-timeline-3679204/)) |
| Google Play developer account has a one-time registration fee | Prior knowledge (amount to confirm) |

Development on the owner's own phone via USB/ADB is unaffected for now.

### Open Android questions for Milestone 4

- Can a session that starts from a scheduled alarm (screen off, app in
  background) legally start its foreground service on Android 15+? Which
  exemption applies (exact alarms vs. overlay visibility)? Needs doc check and
  device test.
- Manufacturer battery management (Samsung, Xiaomi, etc.) — device test only.
- Apps in Private Space or a work profile are not visible to the main profile —
  document as a limitation.

---

## Google Calendar

| Finding | Status |
|---|---|
| Narrow scopes exist: `calendar.freebusy` (availability only), `calendar.events.freebusy`, `calendar.calendarlist.readonly` | Verified (doc), [Calendar API scopes](https://developers.google.com/calendar/api/auth) |
| Sensitive-scope verification typically takes 3–5 business days and requires a public home page, privacy policy on a verified domain, and a demo video; not required while the app stays in Testing with listed test users | Verified (doc), [Sensitive scope verification](https://developers.google.com/identity/protocols/oauth2/production-readiness/sensitive-scope-verification) |
| The Calendar page does not state which scopes are "sensitive" | Verified (doc) — check in Google Cloud console |
| Testing-mode refresh tokens expire after about 7 days; unverified apps are capped at about 100 users | Prior knowledge (both pages mention limits without numbers) |

**Proposal:** request `calendar.freebusy` + `calendar.calendarlist.readonly`
only. Focus then never receives event titles, attendees or descriptions —
just busy intervals, which the free/busy API already expands for recurring
events. Reminders are local notifications for accepted sessions, so no write
scope is needed. Writing sessions back to Google Calendar stays a separate
opt-in for later. Caveat to test: all-day and "free"-marked events don't count
as busy in free/busy results.

---

## Skills available in this environment

Checked and noted: `frontend-design` (will be used for Milestone 2 visual
direction), artifact design/diagramming skills, document skills. There are no
Windows/Android-native, threat-modeling, testing or CI skills available, so
those disciplines are applied directly and recorded in the docs.

## Development environment (this session)

| Tool | Available | Use |
|---|---|---|
| Rust 1.97, cargo, crates.io | Yes | Shared core: built, linted, tested here |
| .NET 10 SDK (installed from Ubuntu archive) | Yes, but no NuGet | Windows probe compiles; cannot run (Linux) |
| Android SDK, Google Maven, Gradle distributions | **Blocked** | Android probe cannot be compiled here |
| Windows or Android devices | No | All enforcement testing needs the owner's devices |
