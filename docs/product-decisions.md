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
