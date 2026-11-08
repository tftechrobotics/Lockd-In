# Product decisions

Three kinds of entry, kept apart on purpose:

- **Requirements:** stated by the owner (or the brief). Owner statements override the brief.
- **Engineering recommendations:** the developer's proposals. Not approved until the owner says so; silence is not approval.
- **Unresolved decisions:** need the owner. Each notes when it blocks work.

## Requirements

| # | Requirement | Source |
|---|---|---|
| R1 | Lockd In is a serious, **non-VPN** focus restriction app for personal Windows and Android devices. Approved apps are chosen separately per device; focus periods and breaks are scheduled and synchronized. | Owner 2026-10-29 |
| R2 | Meaningful enforcement and resistance to casual bypass must coexist with preserving work and safe recovery. An administrator-owner can eventually bypass it; that does **not** redefine the product as "friction, never a lock". | Owner 2026-10-29 |
| R3 | Never call an app "paused" while its process, audio or playback continues. | Owner 2026-10-29 |
| R4 | In an allowlist session, an unsupported browser must not silently become an unrestricted route to blocked websites. | Owner 2026-10-29 |
| R5 | Keep scheduled breaks, delayed overrides, immediate emergency recovery and enforcement failure separate. Preserve emergency access, assistive technology and user data; never remove safety features to improve blocking. | Owner 2026-10-29 |
| R6 | A disconnected peer is "unknown" — not protected, not stopped. Timing tolerances are targets until measured. | Owner 2026-10-29 |
| R7 | No persistent OS-policy experiments on the owner's everyday PC; use a disposable VM first. | Owner 2026-10-29 |
| R8 | Testing: compiled, reproducible probes with pinned toolchains; CI-built downloads with install, permission, removal and limitation notes; smoke test first; battery measured against a baseline; logs local, with a disclosure that app identifiers reveal usage. | Owner 2026-10-29 |
| R9 | Milestone 1 stays open until restriction is demonstrated; don't build the product on an unproven enforcement assumption. | Owner 2026-10-29 |
| R10 | Scope: Windows and Android, optional Google Calendar, native light/dark UI. No LMS integrations, tutoring, assignment tracking, decorative dashboards or VPN. | Owner + brief |
| R12 | Detect-and-terminate is not launch prevention. Termination is restricted to the harmless test target; never applied to real apps, and a newly launched process is not assumed to hold no valuable state. | Owner 2026-10-29 (review 2) |
| R13 | Describe recovery accurately: next-launch or `--recover` recovery is not immediate crash recovery. Agent crash, service crash, reboot and "nothing restarts" are defined and tested separately. | Owner 2026-10-29 (review 2) |
| R14 | VM policy experiments preserve pre-existing settings, restore only what they changed, handle concurrent changes, never clear a policy because a backup is absent, and ship as a separate artifact. | Owner 2026-10-29 (review 2) |
| R15 | State exact Android permission dependencies; verify Advanced Protection behavior rather than assume it. | Owner 2026-10-29 (review 2) |
| R16 | An abruptly stopped component can't be relied on to report its own stop: use authenticated heartbeats and "unknown" when they expire. | Owner 2026-10-29 (review 2) |
| R17 | The product is named **Lockd In** (was the working name "Focus"). Repository: `tftechrobotics/Lockd-In`. Trademark and store-name availability not yet checked. | Owner 2026-10-29 |
| R18 | **Never disable device protections to test or install** (Smart App Control, Samsung Auto Blocker, Advanced Protection, Play Protect). Builds reach devices only through trusted channels: Windows binaries code-signed by a trusted CA; Android through Google Play (internal testing track). | Owner 2026-10-29 |
| R19 | Specification v2 (`docs/product-spec.md`) is the current product definition; it overrides the brief where they differ. | Owner 2026-10-29 PM |
| R20 | **Cream & Ink** is the approved visual direction (reference image): cream, taupe, espresso ink; paper texture; faint library drawings at edges; padlock timer with clock; lock as the "o" in "lockd in"; coordinated dark theme plus system option. No orange accents. | Owner 2026-10-29 PM |
| R21 | Add a **web companion app** (masthead navigation). It never claims to enforce restrictions and states when the installed app is required or disconnected. | Owner 2026-10-29 PM |
| R22 | Platform layouts: Windows = native titlebar, Focus/Schedule switch, Menu, tray, close ≠ end session; Android = bottom nav Focus/Schedule/Profiles; Web = masthead with five destinations, schedule on its own page. | Owner 2026-10-29 PM |
| R23 | Optional session intention (subject → task), presets with linked apps/sites/documents, optional opening of resources at start, review before start. Choosing a subject never silently changes blocking rules. Blocker works without any task. **Clarification of R10:** this is self-entered intention and saved links, not assignment tracking or LMS integration (Canvas/PrairieLearn/SmartPhysics stay out of the first release). | Owner 2026-10-29 PM |
| R24 | Optional question/task timing with explicit controls and a short recap; time is never treated as understanding or failure; restrictions are never extended automatically. | Owner 2026-10-29 PM |
| R25 | Optional nudges and personalized block screens with tones (Calm, Direct, Playful, Custom, Quiet), frequency control, no shaming, no emotion claims. | Owner 2026-10-29 PM |
| R26 | Optional inactivity check-ins using idle time only; cooldowns; never punitive; no keystrokes, screenshots, document contents or browsing history. | Owner 2026-10-29 PM |
| R27 | Location routines are a later, investigated feature: reminders first, on-device evaluation, no history, never increase restriction on departure. | Owner 2026-10-29 PM |
| R28 | Calendar free/busy suggestions need acceptance; accountability check-ins in-app/push first; SMS is a later evaluation; no paid AI or SMS needed for blocking. | Owner 2026-10-29 PM |
| R29 | Home answers only: what, how long, what's available, which devices are ready, how to start. Concise recap; no streaks, guilt or engagement loops. | Owner 2026-10-29 PM |
| R30 | Build order: design → main flow → enforcement/pairing/scheduling/recovery → presets/study timing → calendar/check-ins → location/SMS. | Owner 2026-10-29 PM |
| R31 | **Windows UI = native shell + shared UI** (answers U11). A WinUI 3 window with a minimal native title bar, native window controls, system tray and native menus hosts the shared interface components in a desktop-specific layout (not identical to the web). The interface is bundled and works offline. Blocking runs in a separate service/agent that works whether or not the window is open. | Owner 2026-10-29 PM |
| R32 | **Web ↔ devices = paired browser, no accounts** (answers U12). The browser pairs by QR code like a device; a small hosted relay forwards only end-to-end encrypted messages. Hosting cost (~$5–10/month, estimate) is accepted in principle; the actual provider and spend need owner approval before deployment. | Owner 2026-10-29 PM |
| R33 | **Design pass 3.** The Cream & Ink mockup is a mood reference, not a layout spec. Keep cream/taupe/espresso, the lock in the wordmark, a lock-and-timer motif, calm copy, light/dark. Remove the library artwork from the workspace for now; texture only sparingly. Figtree for the interface, Outfit reserved for the wordmark, titles and timer figures. Separate setup and running states. Where blocking isn't verified the primary action says **Start timer** and explains apps stay available, with a separate path to set up blocking. Schedule shows plans first (weekly routines and one-time sessions, on/off), with creation/editing in a dedicated screen or dialog. | Owner 2026-10-29 night |
| R34 | **Design pass 4: "A setup, B timer".** Focus setup becomes a question composer (What → How long → Which profile → Start; rounds/breaks behind Change). The running session is typography-led: display numerals, a mini lock that closes only with verified blocking, and a session rail. End now moves inside "Need out?". Applies to web, Windows (shared UI) and Android. | Owner 2026-10-31 |
| R35 | **UI design phase.** Until the owner approves a batch of screens: UI design and interactive prototyping only. Iterate in the browser design preview (`npm run preview:dev` → `/preview.html`; published as the "Lockd In Preview" artifact), with Windows and Android shown as labeled browser approximations and connected/protected states as labeled demo data. No emulator CI or native Windows builds per visual revision; no blocking, pairing, sync or servers. Order: Focus (approve first), then Schedule, Profiles, Devices, Settings, one at a time, each checked in light/dark, small screens and 200% text. Native integration and platform testing follow approval. | Owner 2026-10-31 |
| R36 | **Circular lock timer and wordmark (approved direction).** The timer is a circular lock: round body = clock face, substantial shackle above (~0.69 of body width, ~0.4 of its diameter tall), centred MM:SS (tabular), a faint full track and a muted taupe arc of time remaining that starts at 12 o'clock and shrinks, computed from remaining ÷ current focus/break length. Flat, no hands, no gloss. Shackle closed only while restriction is verified and applied. The wordmark's "o" becomes an optical variant (round body with a knocked-out ring, closed shackle to the ascender, no countdown); the same mark is the standalone symbol (favicon, title-bar icon). Supersedes the numerals-led running timer of R34; the composer setup stays. | Owner 2026-10-31 |
| R37 | **UI approved; functional development begins.** Preserve the approved layouts, Cream & Ink, the circular lock timer and the wordmark. Build reliable Windows + Android blocking with a companion web app, replanned as M1 single-device focus → M2 connected devices → M3 planning (recurring, reminders, presets, Google Calendar) → M4 insights → M5 optional support; SMS, tutoring, LMS and iOS later. No VPN-based blocking. Detection/minimize/mute/overlay are never called launch prevention. Apps are never closed without a separate approved decision. Analytics opt-in, local by default, no keystrokes/screenshots/contents, no guilt scores. Calendar never creates or moves anything without the user. Canvas/PrairieLearn/SmartPhysics out of the initial release. Ask before recurring cost, privacy/security changes or changes to agreed requirements. Supersedes R35 (UI-only phase). See `docs/audit-2026-11.md`, `docs/milestones.md`. | Owner 2026-10-31 |
| R38 | **Android: Accessibility detection now (owner, after comparing with Freedom).** The app offers an optional Accessibility service, recommended in setup and opened only after an in-app disclosure. It covers blocked apps the moment they open and blocks websites by domain in Chrome, Samsung Internet, Edge, Firefox, Brave and Opera, by reading only the address bar during focus. Usage access + display over other apps stays as the fallback (and is used automatically if Accessibility is turned off or revoked by Advanced Protection). Supersedes E10 for the app: the Play submission will need the Accessibility declaration, prominent disclosure and demo video (`isAccessibilityTool=false`). Installing on the owner's phone still requires Play internal testing (Auto Blocker). | Owner 2026-11-01 |
| R39 | **Sideloaded test APK (owner, 2026-11-01).** Until Play internal testing exists, the owner tests a debug APK from the `test-apk` branch (workflow "Android test APK"), signed with a committed PUBLIC test key so builds update in place. The owner turns Samsung Auto Blocker off only to install, then back on. This relaxes R18 for owner testing only. Play builds remain separate and signed with the upload key. | Owner 2026-11-01 |
| R40 | **Android phone app only for now (owner, 2026-11-01).** Windows and web development are paused; their code is kept. The owner's 12-area Android backlog is mapped in `docs/android-backlog.md`. Order: A schedules + reminders → B history, presets, basic analytics → C Calendar + widgets → D coaching, question timing, check-ins → E location. The completion summary auto-returns home after 30 s, and Home keeps a "Last session" card until dismissed, which satisfies both requests. | Owner 2026-11-01 |
| R41 | **Profiles must never break basic phone use; websites can be allow-only (owner, 2026-11-01).** Samsung Wallet and Google Wallet, clock, camera, contacts, phone, Settings, keyboard and home screen are never blocked and aren't listed in the picker; covers act only on the app that owns the active window (a blocked Wallet's swipe-up panel had made the home screen unusable). Websites get "Block these" / "Allow only these"; allow-only covers every other site in supported browsers and blocks unsupported browsers whole during focus, because their address can't be read. The profile editor shows app icons; the Profiles list shows name, a one-line summary and a chevron. | Owner |
| R42 | **Sculpted dial and manual-start sequence (owner, 2026-11-01; option 02 of three mockups).** The lock gets matte depth (soft lower-right shadow, upper-left rim light, shaded face and shackle) in light and dark, keeping the palette, ticks and digits. When the person presses Start in the app, the tick ring turns one revolution in 12 clicks (~0.9 s) with clock hands (minute hand with the dial, hour hand counter-clockwise), the hands fade into the digits (upright, real session clock), then the shackle drops shut with a settle only after the blocking service reports PROTECTING; otherwise it stays open with the usual status. Plays once per manual start (in-memory one-shot), never for scheduled starts, reopening, rotation, a blocked app, process restore or breaks; a timer-only start never shuts the shackle. Blocking starts immediately; the animation never delays it. Remove animations → a short fade. Haptic clicks (default on) and click sound (default off) are separate settings and follow the phone's touch feedback/sound settings. | Owner |
| R43 | **Notification switches and new app icon (owner, 2026-11-02).** Lockd In's notifications are local (no server push). Settings › Notifications gets a main "Allow notifications" switch plus its own switch for reminders, focus/break notices, missed follow-ups and blocking problems; a "Turn on notifications" row when Android blocks them, and a link to Android's notification settings. The ongoing "Focusing" notice can't be turned off while blocking runs (Android requires it for the blocking service); the app says so. App icon: the owner's padlock-with-clock design, espresso on cream, as an adaptive icon with a monochrome layer (`design/icon/gen.py`). The "lockd in" wordmark is left off the launcher icon because Android prints the app name under it and the text would be ~5 dp tall. | Owner |
| R44 | **Android roadmap order (owner, 2026-11-02).** 1) physical-phone checks and fixes; 2) B history + presets; 3) B2 profile-based study audio (offline licence-clean ambient first; streaming researched during B and added one service at a time; playlist attached in profile setup; only play/pause, skip and volume during focus; music, ambience, both or silence; embedded playback clearly distinguished from controlling an external music app; provider limits never hold up the rest); 4) C Google Calendar + widgets; 5) C2 optional static backgrounds and short quotes (default unchanged, no rotation/animation/interruptions in a round, separate off switches, readable text); 6) D coaching; 7) E location routines. Release readiness (onboarding, privacy controls, phone battery measurements, repeated after audio) runs alongside. Windows, web and cross-device sync stay paused. | Owner |
| R45 | **Free options first, then Apple; build without stopping (owner, 2026-11-02).** B2 uses no paid services: generated ambient sound plus controlling Spotify, YouTube Music, Apple Music and SoundCloud through their own apps (media keys and volume; no extra permission). Apple Music playback inside Lockd In (MusicKit, $99/yr) comes later. Engineering choices made under this: Google Calendar is read through Android's calendar provider (no cloud credentials, read-only, opt-in); the widget is a RemoteViews widget with a Chronometer countdown; places use Play services geofences with reminders by default. The owner asked to keep building through the roadmap without waiting for approval, documenting issues instead. | Owner |
| R11 | Everything else in `Focus_Project_Brief.md` (allowlist profiles per device, no force-closing or lost work, no hidden processes, no indefinite lockouts, offline execution, privacy minimums, etc.). | Brief v1.0 |

## Engineering recommendations (not approved)

| # | Recommendation | Where |
|---|---|---|
| E1 | Windows: privileged service plus per-user agent; launch prevention via a session-scoped App Control policy (AppLocker fallback) if VM go criteria pass; window/audio restriction for everything else; never terminate real apps | ADR-0007 |
| E2 | Android: usage access + block screen + audio focus by default; accessibility optional and evidence-dependent | ADR-0004 |
| E3 | Defined behavior for each ordinary bypass gap | `enforcement-comparison.md` |
| E4 | Local-first, no accounts, device-group pairing | ADR-0005 |
| E5 | Calendar via free/busy scopes only | ADR-0006 |
| E6 | Session limits: focus 1–240 min, break 1–120 min, session ≤ 12 h (guarantees every session ends) | Implemented in core as a safety bound; values adjustable |
| E7 | DST: nonexistent start shifts later by the gap; repeated start uses the first occurrence (RFC 5545) | Implemented in core |
| E8 | Supported OS at launch: Windows 11 25H2/26H2 x64+ARM64; Android minSdk 29 | Pending owner device details |
| E9 | Rust shared core; WinUI 3; Kotlin/Compose | ADR-0001, ADR-0002 (engineering, reversible) |
| E10 | First Play release of the probe omits the optional accessibility detector (avoids its declaration, video and review risk); added later only if usage access fails M1 (U8) | `docs/play/README.md` (reversible) |
| E11 | Clock rule: sessions advance by monotonic time; after a reboot, max(wall clock, checkpoint + uptime). Clock set forward while the device is off can end a session early and is reported, not prevented | `core/src/clock.rs` |
| E12 | ~~"Quiet instrument" visual direction~~ — superseded by R20 (Cream & Ink). Status language (icon + word, five core states) is kept. | `docs/design.md` |
| E13 | Web app stack: React + TypeScript + Vite; session timing in TypeScript checked against Rust-generated vectors (ADR-0001 fallback) until a WASM build is available | `apps/web` |
| E14 | Typeface: Outfit (OFL) for the wordmark and display; Figtree (OFL) for interface text; both self-hosted, no font CDN | `design/tokens` |

## Unresolved decisions (need the owner)

| # | Question | Blocks |
|---|---|---|
| U1 | Is a Windows service running as SYSTEM, installed with admin consent, acceptable? | M3 architecture; not the current probes |
| U2 | Unapproved apps already open when a session starts: offer to let the user close them, or minimize and mute them? Lockd In never closes them itself. | M2 design |
| U3 | App Control policies are machine-wide: during a session, prevented apps would also be blocked for other Windows accounts on the same PC. Acceptable, or use per-user AppLocker rules (weaker: an admin can stop its service)? | After VM tests 10/20; not now |
| U4 | Emergency recovery is immediate. Should it also require a typed confirmation and notify paired devices? | M2 design |
| U5 | Override countdown length (recommended 90 s) and whether "end whole session" has a longer delay | M2 design |
| U6 | No accounts / device-group pairing (ADR-0005) vs accounts | M5 |
| U7 | Service mode may set browser policies that make Chrome/Edge show "managed by your organization". Acceptable? | After VM test 30 |
| U8 | Android: offer an accessibility mode (Play review risk), or require it if usage access proves insufficient? | After Android probe |
| U9 | Support Windows 10? | M3 |
| U10 | Android: request notification access to hide restricted apps' notifications during focus? | M4 |
| ~~U11~~ | Answered → R31 | — |
| ~~U12~~ | Answered → R32 | — |

## Withdrawn

| Date | Statement | Why |
|---|---|---|
| 2026-10-29 | "Lockd In is friction, never a lock" | Owner did not approve; replaced by R2 |
| 2026-10-29 | "Proposed decisions stand unless you object" | Silence is not approval (owner) |
| 2026-10-29 | Probe messages saying a covered or minimized app is "paused" | Inaccurate (R3) |
| 2026-10-29 | Former U3 ("may Lockd In close a restricted app seconds after launch?") | Superseded by R12: Lockd In never terminates real apps |
| 2026-10-29 | "Launch gating" by detect-and-terminate | Not prevention (R12) |
| 2026-10-29 | "App Control authoring not on Home, so impractical" | Wrong: enforcement works on all editions; authoring can be done in-app (see comparison) |
| 2026-10-29 | "Service reports tamper events" | An abrupt stop can't self-report (R16) |

## R46 — Owner-supplied background images (2026-11-02)
- Library after hours, Quiet ambition and Words to work by now use the owner's own images (owner holds the rights), one dark and one light version each, shown at 80% over the theme ground (words image 50%, owner asked 2026-11-02 for more transparency). They replace the procedural renders from `design/backgrounds/gen.py`, which is kept only for reference.
- Words to work by: the owner's image is shown for the default line; a custom line is still drawn live, at alpha 0.12 dark / 0.10 light.
- Backgrounds show only while the lock is closed (blocking confirmed during focus); not on Home, setup, breaks, timer-only sessions or other tabs (owner, 2026-11-02).
- Your reason (blueprint) stays drawn in code, with stronger lines (alpha 0.24 / 0.20).
- Per the owner, no emulator run for this image-only change; checked by build only.

## R47 — Music: saved playlists and music control through notification access (2026-11-02)
- Owner reports: starting focus opened the music app over the lock animation, and opening a playlist link didn't start it playing. One link per profile was too limiting.
- Lockd In no longer switches to the music app when a session starts. Music starts in the background and the start sequence stays on screen.
- Saved playlists (up to 20, each a name and a link) replace the single link. Each profile picks its starting playlist, and the session screen can switch to any saved one. Links profiles used before this change are added to the list automatically.
- The owner chose **notification access** (Android's only route to other apps' media players). Lockd In's listener overrides no notification callbacks, so it never reads or keeps notifications. With access granted, Lockd In calls `playFromUri` on the music app's player: a `spotify:` URI for Spotify, the link itself for the others. If the app has no player yet, it is woken with a media key sent only to that app.
- Without access, Lockd In can only resume the music app, which plays whatever it played last. "Open in …" opens the playlist in its app; for YouTube Music this uses the `watch?list=` form, which plays on its own.
- Not possible for an individual developer: Spotify's App Remote SDK (development mode is limited to 5 users, Feb 2026). Apple MusicKit is the $99/yr item deferred in R45. SoundCloud has no playback API.
- Which apps honour `playFromUri` is unverified; it must be checked on a phone (checklist item 27).

## R48 — Study audio: built-in player and music-app access (2026-11-02, owner spec)
- **Supersedes R47 and the B2 music parts.** Removed: playlist links, notification access (the listener service is gone), generated live ambient sound, and opening the music app at start. Old saves migrate: an ambient choice becomes built-in Rain with the same start and break options, and playlist links are dropped.
- **Two independent per-profile settings (profile › Audio):**
  - **Built-in study audio** (off by default): Piano, Strings or Rain, with preview, volume, "Play when focus starts", and pause or continue during breaks.
  - **Music apps during focus:** Follow blocking rules (default), Limited browsing (5 min suggested, shared across the chosen apps), or Always allow.
- **Player:** Media3 ExoPlayer 1.11.1 with a MediaSession, hosted in FocusService. That service is already a foreground service of type mediaPlayback, so scheduled starts don't need a new background foreground-service start, which Android restricts.
  - The session notification turns into a media-style notification with play/pause and next while audio is loaded, which gives lock-screen and quick-settings controls.
  - Audio focus is handled by ExoPlayer, so built-in audio and external music never play over each other. It pauses on headphone or Bluetooth disconnect.
  - It fades out at session end and on emergency recovery.
  - It starts by itself only within 60 s of a focus period beginning, so it never restarts after a reboot or recovery. No playback state is saved.
- **Allowance accounting:**
  - **What counts:** time only while a chosen app is the front window, the screen is on, and a focus period is restricting. FocusService checks this once a second, using Accessibility's active window or usage events.
  - **Clock safety:** each step uses the elapsedRealtime gap, capped at 3 s, so wall-clock changes can't refill it.
  - **Persistence:** saved on ActiveSession every 5 s and on every change, so restarts and reboots keep it.
  - **Breaks and new sessions:** breaks don't refill it; a new session gets a new allowance.
  - **Warning:** one quiet notification at 30 s left.
  - **When spent:** the chosen apps are covered.
- **Narrow exception:** the block screen normally requests audio focus to stop the covered app's sound. It skips that only for a chosen music app whose allowance is spent, and while Lockd In's own audio is playing.
- **Rules snapshot:** limited apps are added to the session's restricted set at start and let through until the allowance is spent; always-allowed apps are removed. The session keeps this snapshot, so profile edits mid-session apply next session and can't become a bypass.
- **Eligible apps:** apps with a media browser service and a launcher icon. The editor warns that allowing an app allows all of its content.
- **Setup:** a one-line audio summary plus "Choose music": collection, play-on-start and allowance for this session only, and "Open <app>" before the countdown starts.
- **Session screen:** one collapsible line under the timer, with remaining browsing time shown only for limited access.
- **Recordings:** development recordings synthesised by `design/audio/gen.py`, owned by the project and labelled in the app. See `docs/audio-assets.md` for the content needed before release.

## R49: Partner code, emergency end, admin messaging, setup tour (2026-11-02, owner; implemented 2026-11-03, see status below)
- **Partner code:** set up in onboarding, which needs internet once.
  - Lockd In creates a secret key on the phone and stores it in Android Keystore. The person never sees it.
  - An encrypted copy goes to the Lockd In service, which returns a private partner link that the person sends from the share sheet.
  - The partner opens the link and sets a 4-digit PIN, which claims the link. If the person opened it first, the partner sees "already claimed".
  - The page shows a 6-digit code that changes every 30 s, computed from the key. The app checks codes offline.
  - The owner chose not to use authenticator apps.
- **Ending early with a partner:** the code ends the session at once.
- **Emergency end without a code:**
  - The wait is ¾ of the remaining focus time.
  - Calls and messages unlock as soon as the wait starts. Phone and emergency calls are never blocked.
  - The wait can be cancelled, and it is logged in History. No indefinite lockout is possible.
- **Admin:**
  - Requests come as messages from the person's own account inside the app (authenticated by the device), not phone calls.
  - Admin (the owner now, an AI agent later) can send an unlock for the running session only. The service enforces this scope.
  - Admin never sees keys or codes, and every action is audit-logged.
- **Hosting:** the owner's website domain (already bought; name to come). Build and test on a free temporary Cloudflare address first. Needs a privacy policy, Play Data safety update, and account and data deletion.
- **Setup:** a guided walkthrough (Welcome, first profile, blocking permission, optional partner, tour with tips) with "End tutorial" on every step and replay from Settings › Help and tour.
- **No built-in profiles:** the first profile is created in setup.
- **Canvas comments (2026-11-02) accepted:**
  - Settings and calendar accounts become collapsible sections.
  - The block-screen "still open behind" line is removed. On the music block screen, the playback line moves under the buttons.
  - The Choose music dialog only turns music-app access on and sets the allowance; there are no Open buttons.
  - The Your reason (blueprint) background is removed.
  - A completion animation plays when the app is opened after a session ended, unless another session is running.
  - The real wordmark is used everywhere.
  - The focus session fits one screen.
  - The home-screen widget is redesigned as a mini lock. A lock-screen widget is to be checked on the S24+.
- **Owner answers (2026-11-02):** more study music and more quiet, chill options (added: Lo-fi, Ambient, Quiet piano, Ocean, Forest, Brown noise); "Locked" (and Partner code) are chosen in session setup under "Ending early".
- **Code delivery (owner, supersedes the QR idea):** a link, not an authenticator app. Partner PIN is 4 digits; the page then shows the code.
- **Status 2026-11-03:**
  - Implemented: service `services/partner` (Cloudflare Worker + D1, on the temporary address https://lockdin-partner.lockdin-tf.workers.dev), deployed by `.github/workflows/partner-service.yml`; app side in `partner/` and `ui/PartnerUi.kt`; messaging apps (default dialer, default SMS, Google/Samsung messages and contacts) are not blocked while an emergency end is waiting.
  - Tested: service crypto unit tests incl. RFC 6238 vectors (Node 22, CI); app PartnerCode against the same vectors and a grant computed by the service code (JVM unit tests); emulator UI test for the end-early screen and emergency wait.
  - Not yet tested: the full link → PIN → code → unlock flow with two real phones; admin console (off until the `LOCKDIN_ADMIN_PASSWORD` repo secret is set).
  - Privacy: the app now has the INTERNET permission, used only for the partner service. Play Data safety and a privacy policy must say so before release.

## R50: Lockd In accounts in first-run setup (2026-11-03, owner; supersedes the "no accounts" part of R32)
- **Owner:** setup should create an account so the phone connects to the online account, and to the computer once pairing exists. Before it, a short slideshow on why the app helps, ending "so let's get started by making your account".
- **Built:** first run = 4 why-slides → account (create or sign in) → first profile → blocking → optional partner → tour. Settings › Account shows the account, Sign out and Delete account.
- **Service:** email + first name + password on the existing Lockd In service (`/api/users`, `/api/signin`, `/api/me`); passwords stored as salted PBKDF2-SHA256 (100k) hashes; one device token per phone (stored hashed); 10 wrong passwords → 15-minute lock; 5 sign-ups per network per hour. A partner added while signed in belongs to the account.
- **Developer choice, needs the owner's OK:** "Not now, use Lockd In on this phone only" stays available on the account step, because the brief requires offline operation and a timer must never need the internet.
- **Not yet possible (blocked on an email provider):** email verification and password reset. Until then a forgotten password means a new account. Needs an email-sending service (e.g. Resend, Postmark, or Cloudflare Email) and its API key.
- **Privacy:** the service now holds emails. Play Data safety and the privacy policy must list email, name and account credentials, plus account deletion (built: Delete account).

## R51: Partner approves instead of reading a code (2026-11-03, owner)
- **Owner:** the partner shouldn't have to keep their link open and read a code; the ask message should carry a link; ideally the partner just unlocks and taps accept.
- **Built:** "Ask <partner>" creates a request (one session, expires after 30 min or at the session's end) and shares a message with its link `/r/<id>`. The partner enters their 4-digit PIN and taps Allow or Don't allow. The phone polls and ends the session only when the service's grant matches HMAC(key, session id) checked on the phone. The partner's own link also shows a waiting request.
- The 6-digit code stays as a fallback ("<partner> is with you? Use their code"), and the emergency wait and admin messages are unchanged.
- Wrong PINs on a request count toward the same 5-tries / 15-minute lock as the partner link.

## R52: Owner phone feedback on build 34638d7 (2026-11-03)
1. Focus status line: when blocking is already set up but the chosen profile blocks nothing, show "<profile> doesn't block any apps yet" with **Add apps** (opens that profile; setup reopens with the same choices). "Set up blocking" only shows when permissions are missing.
2. Blocking setup decluttered: one status card, then only Accessibility (or its two steps), Notifications, Battery. Usage access + display over other apps are behind "Can't use Accessibility?" and hidden once Accessibility is on. The owner's two-step setup is Android 13+ "restricted settings" for apps installed outside Google Play; the screen now explains it as step 1 (opens App info) and says Play installs skip it.
3. Partner approval: see R51.
4. Partner details (and the code wording) only show in Settings › Ending early when Partner is the chosen option.
5. Tour: six small callouts that point at New session, the next-session card, Schedule, Profiles, History and the Settings gear, with the rest of the screen dimmed.
6. Settings is a home list of categories (Account, Blocking, Ending early, Notifications / Sessions, Look and messages, Calendar and places / History and data, Help and tour) that open to their own pages, following the owner's Freedom reference. Replaces the R49 collapsible sections.
7. First-run account and why-slides: see R50.

## R53: Interface pass 5 with the new design skills (2026-11-07, owner)
- **Owner:** update the app's interface using the newly added skills (ui-ux-pro-max set in `.claude/skills/`),
  without generic fonts or the usual AI layouts, before the widgets.
- **Built (Android):** Archivo (Omnibus-Type, OFL; static instances in `res/font/`, licence in
  `design/fonts/Archivo-OFL.txt`) replaces Outfit + Figtree. Expanded cuts are the stamped voice (titles, big
  numbers, timetable times, the primary button); width carries hierarchy. Outlined cards removed: tonal plates
  with background-colour cuts, tonal secondary buttons and chips, rounded-rectangle segments instead of pills.
  Home "Next session" and Schedule "Next up" are timetable plates with the start time stamped in a left column.
- **Kept:** the approved Cream & Ink colours (contrast unchanged and passing) and the padlock.
- The ui-ux-pro-max database suggested a blue Swiss-style system with testimonials; not used. Its rules
  (contrast, 48 dp targets, reduced motion) were applied through the existing checks.
- Web and Windows still use Outfit + Figtree (paused, R40).

## R54: Greeting under the lock, Bricolage Grotesque (2026-11-07, owner)
- **Owner:** pass 5 still read as AI (text too big, digits overlapping the clock ticks); pointed to Freedom's
  home (greeting + line, characterful face). Chose the greeting mockup, with the greeting **under the lock**.
- **Built (Android):** Home = lock (210 dp) on top, then "Good evening, <first name>" (Bricolage Grotesque 700)
  and one plain line about what's next ("Nothing planned tonight." / "Physics review in 25 min."), tappable to
  Schedule; the "Next session" box is gone. Main button "Start a session". Bricolage Grotesque (OFL) for
  headings, buttons and digits; Archivo stays for interface text. Clock digits 46 units so "10:35" fits.
- Supersedes the expanded-Archivo voice from R53 (Archivo Wide is no longer used in the app).

## R55: No boxes outside Settings; more motion (2026-11-07, owner)
- **Owner:** the boxes around words read as AI everywhere except Settings; the lock sat too high; wants more
  movement and interactivity.
- **Built (Android):** `LocalBoxed` (true only in Settings). Elsewhere groups are plain rows with hairlines,
  secondary buttons are underlined text actions, single choices (length, profile, segmented, chips) are text
  with an ink bar that grows under the chosen one. Home lock and greeting moved down (56 dp top, 32 dp gap).
- **Motion (all off with Android's Remove animations):** buttons press in; selection bar grows/slides;
  tabs fade and slide toward the chosen tab; Settings pages slide in and out; the Home greeting rises in;
  tapping the lock makes it rattle with a haptic tick; the "Ends at" time rolls when the length changes.
- **Font:** see R56.

## R56: Literata throughout (2026-11-08, owner)
- Owner picked Literata (book serif) from four options shown on the home screen (Bricolage, Literata,
  Gabarito + Nunito Sans, Unbounded + Nunito Sans). Literata (OFL) now sets all app text: text cuts (opsz 12-14,
  400/500/600), display cuts (opsz 36, 600/700) for headings, buttons and the greeting, opsz 48 for clock
  digits (lining, tabular). Archivo and Bricolage removed from the app. The wordmark is unchanged (vector).

## R57: Dark cocoa for dark mode (2026-11-08, owner)
- Owner kept the chocolate direction and picked "Dark cocoa" from four variations: ground #160F0B, surface
  #1F1611, taupeSoft #2A1F18, line/taupe #43342A, ink #F2E6D6, inkMuted #C4AE98 (all contrast checks pass).
  Lock, widget and window background follow. A glossy glass look was requested next (mockups first).

## R58: Glass (2026-11-08, owner)
- From mockups the owner chose: frosted glass for the Settings plates, a polished lock, and no box around
  the Home greeting. For the tab bar and "Start a session" they picked "Lens", but without the round lens
  over the active tab.
- What was built: Settings plates are a translucent tint with a 1 dp edge that's brightest at the top. On
  Settings the chosen background shows behind them, blurred (Android 12+) and dimmed. The shackle is
  banded metal with a top gloss, and the dial face has a glass reflection. The tab bar is smoked glass with a
  light top edge, and the active tab is marked by ink and weight only. "Start a session" is a clear glass
  capsule with a bright rim, a top highlight and a darker bottom lip.
- Limitation: Android can't blur what's behind a view. Only the app's own background image is blurred.
  With no background chosen, glass shows as a tint over the plain ground.

## R59: Glass fixes, Schedule calendar, focus streak (2026-11-08, owner)
- Settings glass was too faint on the phone: stronger tint, brighter top edge, diagonal sheen, lighter blur and dim.
- No solid strip behind pinned actions (e.g. "Start a session"); the tab bar is mostly see-through.
- Schedule gets a month calendar (dots on days with plans). Tapping a day shows its sessions, a "Plan a
  session" action and that day's free time, with roomier rows (time on its own line, "Plan" action).
- Focus streak (approved, not built yet): lives in History. It counts consecutive days with sessions finished
  without trying to open a blocked app or ending early. Days with no session pause the streak and don't
  break it. At 30 days History suggests trying an unlocked session; nothing changes unless the person taps it.
  The streak is a measure of consistency, not proof of a habit. Browser tabs only count where sites are
  already watched.

## R60: Lock look "A: machined + glass lens" (2026-11-08, owner)
- The R58/R59 gloss (face reflection oval and glint) read as a bubble with glare. From three mockups the owner
  picked A. The shackle is a round bar (stacked strokes, dark edge to bright core) and the ring has an outer
  bevel lit from the upper left. An inner reversed bevel makes the face look sunk. The lens is flat, with one
  faint diagonal band and a hairline of light on its upper-left edge. Soft drop shadows under shackle and body.

## R61: Lock "Watch crystal", shorter shackle (2026-11-08, owner)
- Lock A (R60) was rejected: the lock felt too long and the design didn't land. From four more mockups the
  owner picked "Watch crystal": a thin polished bezel (bright upper left to dark lower right) and a dark seat.
  The face sits under a watch glass, with a soft cap of light over the upper half and a hairline edge. The
  round-bar shackle stays, now thicker (STROKE 28) and shorter (TOP 56, open lift 50), so the whole lock is
  shorter.

## R62: Focus streak and unlocked sessions (2026-11-08, owner: "Great add it")
- History opens with a **Focus streak**: a day count and a glass bar filling toward 30 days, plus the best run
  so far and why it last started over (when that was recent).
- A day is clean when a session finished as planned and none ended early (including emergency recovery) or had
  a blocked app or site come up. Any slip that day resets the streak. Days with no session pause it.
  Logic is in `engine/Streak.kt`, with unit tests in `StreakTest`.
- At 30 days History offers **"Try an unlocked session"**. Nothing changes unless it's tapped. Setup then shows
  "Unlocked session" (with "Lock apps instead"), and the button reads "Start unlocked".
- During an unlocked session the enforcement services still watch, but they never show the block screen. Each
  opening of a would-be-blocked app or site is noted once (5-minute window, `UnlockedNotes`) in the session's
  `blocked` map. That breaks the streak and appears in History as "Unlocked · blocked apps opened".
- Limits: the streak counts Lockd In sessions only. Browser tabs count only where sites are watched today.

## R63: Streak ring (2026-11-08, owner's reference image)
- The streak bar is replaced by the owner's design: a 30-tick ring (one tick per day, filled clockwise from the
  top in ink, days ahead in taupe), with a lock icon, the count and "of 30 days" in the centre. Below it are
  "One day at a time." in Literata italic, a legend (Completed day / Day ahead) and "N days until your next
  milestone". The rule text, start-over reason, best run and the 30-day unlocked offer stay under it.
- "One day at a time." is fixed copy the owner asked for. It's a deliberate exception to the no-motivational-filler rule.
- Added `res/font/literata_display_italic_500.ttf`: a static instance (opsz 36, wght 500) of Google Fonts'
  Literata Italic, subset to Latin (OFL, same license as the other Literata files).
