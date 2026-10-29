# Milestone 1 — Feasibility findings

Researched 2026-10-29. Each finding is tagged:

- **Verified (doc)** — read in current official or primary documentation (source listed).
- **Reported** — from secondary reporting; confirm before relying on it.
- **Prior knowledge** — from general knowledge, not re-checked this session.
- **Experiment pending** — only a real-device probe can settle it (see `probes/`).

## Bottom line (revised 2026-10-29)

Both platforms give an ordinary app real levers against a restricted app.
On Windows these are minimizing, muting, catching launches, and (with a
privileged service) stopping launches and applying session-scoped policies.
On Android they are detecting the foreground app, bringing a block screen to
the front, and taking audio focus. Neither platform lets a consumer app make
restriction impossible for an administrator-owner to undo. That is a stated
limit, not the product definition: the goal is meaningful enforcement that
resists casual bypass while preserving work and recovering safely (owner
requirement R2).

The earlier framing "friction, never a lock" is **withdrawn**. The full
option-by-option comparison and the provisional recommendation are in
`docs/enforcement-comparison.md`. Nothing has been demonstrated on a device
yet; Milestone 1 stays open.

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

Superseded by `docs/enforcement-comparison.md`, which compares:
- a per-user agent;
- a privileged service plus agent;
- AppLocker and App Control for Business;
- per-app firewall rules, browser policies, process suspension, drivers and kiosk mode.

It covers what each prevents, privileges, already-running apps, bypass routes,
editions, conflicts, install/uninstall and crash recovery. Policy experiments
run only in a disposable VM (`probes/windows-vm/`).

### Websites

| Finding | Status |
|---|---|
| A Chrome/Edge MV3 extension can talk to the desktop app through native messaging; the host is registered per-user under `HKCU\SOFTWARE\Google\Chrome\NativeMessagingHosts\` (no admin), `allowed_origins` must list the exact extension ID | Verified (doc), [Chrome native messaging](https://developer.chrome.com/docs/extensions/nativeMessaging) |
| On a personal device the user can remove or disable the extension, and it must be explicitly allowed in Incognito/InPrivate | Prior knowledge |

**Proposal (revised 2026-10-29):** website restriction ships as a Chrome/Edge
extension that the desktop app verifies by heartbeat.
- A browser can be approved "with website restriction" only while the
  extension is installed, enabled and allowed in private windows.
- Unsupported browsers stay restricted like any other unapproved app.
- If the extension disappears mid-session, that browser becomes restricted
  until it returns.
- An unsupported browser is never silently an open route (requirement R4).
  Approving one requires an explicit "allow without website restriction"
  choice that is visible on Home and to paired devices.
- No hosts-file edits, DNS interception, proxies, VPN or root certificates.

Details: `docs/enforcement-comparison.md` § bypass gaps.

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

### Detection and cover options

Superseded by `docs/enforcement-comparison.md`. Key findings added on
2026-10-29:

- "Display over other apps" is a documented exception that lets an app start
  an activity from the background (Doc). Focus can therefore bring a block
  screen to the front, which moves the restricted app to the background,
  instead of relying on an overlay.
- An overlay leaves the app resumed and audible underneath, and apps can hide
  overlays with `HIDE_OVERLAY_WINDOWS` on Android 12+ (Doc).
- Android 17 background audio hardening (Doc):
  - silences apps that have neither a visible activity nor a foreground service;
  - refuses audio focus requests from background apps.

  Focus can take audio focus only while its block screen is visible.

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
| Android SDK, Google Maven, Gradle distributions | **Blocked** | Android probe cannot be compiled here; CI compiles it once a repository exists. Gradle's bundled Kotlin compiler was used for a syntax-only check. |
| Windows or Android devices | No | All enforcement testing needs the owner's devices |
