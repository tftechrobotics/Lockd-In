# Android backlog and roadmap (updated 2026-11-02, R44)

This doc follows the owner's Android-only product direction. Windows, web and cross-device sync are paused, with their code kept as it is. The order of work is the **Roadmap** section below (R44); it replaces the earlier A → E order.

Status values:
- **Done** = implemented and tested (environment named).
- **Partial** = some of it exists.
- **Missing** = not built.

"Phone" means the owner's own phone (Galaxy S24+), running a sideloaded test build. "Emu" means the CI emulator (API 35).

| # | Area | Status | What exists / what's missing |
|---|---|---|---|
| 1 | Focus sessions & blocking | **Done (Emu + Phone)** | **Exists:** <br>• Quick start with a task name; 25/40/60 or custom lengths; rounds and breaks. <br>• Restrictions lift during breaks and return afterwards. <br>• Per-profile "Block these" / "Allow only these" for apps; websites by domain, block list or allow-only (R41), in Chrome, Samsung Internet, Edge, Firefox, Brave and Opera (needs Accessibility); allow-only blocks other browsers whole. App icons in the picker. <br>• No VPN. <br>• Flexible / Delayed / Locked commitment, plus emergency recovery. <br>• Honest blocking status; permission-loss notice. <br>• Restart after process kill and reboot. <br>• Essentials are never blocked. <br>• The block screen says the app is still open behind it. <br>**Missing:** a quick start from a saved preset (needs #5). |
| 2 | Automatic schedules | **Done (Emu); phone check pending** | One-time and weekly sessions, automatic start with an explanation first, skip one occurrence, late and missed starts, time-zone/clock changes, next session on Home (milestone A). |
| 3 | Reminders & notifications | **Done (Emu); phone check pending** | Reminders before sessions, start notice, focus/break/complete notices, missed follow-up, quiet hours, hourly limit; main "Allow notifications" switch plus one per kind incl. blocking problems (R43). |
| 4 | Google Calendar | Missing (roadmap 4, C) | — |
| 5 | Study tasks & presets | Partial (roadmap 2, B) | Exists: recent task names only. Missing: saved tasks/courses with profile/duration/breaks, due dates, steps, spreading exam prep, "Open my study resources". |
| 6 | History & analytics | Partial (roadmap 2, B) | Exists: the end-of-session summary with blocked attempts (per app/site, counted once per cover). Missing: persistent history, daily/weekly views, planned vs completed, usage stats, export/retention/deletion. |
| 7 | Block screen & encouragement | Partial (roadmap 6, D) | Exists: task, next break, tone setting (Calm/Direct/Playful/Off), template messages. Missing: user-written and per-app messages. |
| 8 | Question timing | Missing (roadmap 6, D) | — |
| 9 | Inactivity check-ins | Missing (roadmap 6, D) | — |
| 10 | Location routines | Missing (roadmap 7, E) | — |
| 11 | Home-screen widgets | Missing (roadmap 4, C) | — |
| 12 | Settings & release quality | Partial | **Exists:** <br>• Themes; tone; commitment default; delete data. <br>• 200% text and TalkBack labels (UI tests). <br>• Permission setup with explanations. <br>• Excluded from backups. <br>• Notification controls (R43); app icon (R43); sculpted dial and start sequence (R42). <br>**Missing (runs alongside the roadmap):** <br>• onboarding flow; privacy screen and controls; session/break defaults screen; battery measurements on the phone (again after audio). <br>• Completion summary kept until dismissed: the summary returns to Home after 30 s (owner request); A adds a "Last session" card on Home that stays until dismissed, so both requests hold. |
| 13 | Study audio (B2) | Missing (roadmap 3) | — |
| 14 | Backgrounds & quotes (C2) | Missing (roadmap 5) | — |

## Roadmap (owner, 2026-11-02, R44)

Steps run in this order. Release-readiness work runs alongside every step. Windows, web and cross-device sync stay paused.

| Step | Scope | Status |
|---|---|---|
| 1 | Physical-phone checks of everything built so far; fix every failure | **Waiting on the owner's phone results**; later steps continued meanwhile (owner, 2026-11-02: keep building, document issues, don't stop) |
| 2 | **B:** persistent history and reusable study presets | **Implemented** (6b2a9ab). Emulator UI 53/53; history persistence E2E added |
| 3 | **B2:** profile-based study audio | **Implemented** (970bf7b): generated ambient sounds plus control of Spotify, YouTube Music, Apple Music and SoundCloud through their own apps (owner: free options now, Apple embedded later) |
| 4 | **C:** Google Calendar and home-screen widgets | **Implemented** (f06c648): device calendar provider (read-only), free-time suggestions, clash warning, RemoteViews widget |
| 5 | **C2:** optional backgrounds and quotes | **Implemented** (61036c4, build fix 1161ff1) |
| 6 | **D:** coaching: own block-screen messages, question timing, inactivity check-ins | **Implemented** (14ecef6) |
| 7 | **E:** location routines | **Implemented** (c78d39a): Play services geofences, reminders by default, auto start opt-in |
| Alongside | Release readiness: onboarding, privacy controls, defaults screen, phone battery measurements (repeated after B2), Play listing | **Partial** (0a601cf): onboarding, privacy section, defaults done; battery protocol written (needs the phone); Play listing needs the owner's developer account |

### Step 1: pending phone checks (owner's Galaxy S24+, test APK)

The checklist is in `docs/device-test-checklist.md` under "Pending phone checks (2026-11-02)". Every failure becomes a fix plus a test before step 2 closes. Step 2 design work may start meanwhile, but no B build is sent until step 1's failures are fixed.

### Step 2 (B): acceptance criteria (draft)

1. **History is kept.**
   - Every session (manual, automatic, ended early, emergency) is saved on the phone when it ends: planned and actual times, rounds, profile, task, blocked attempts per app or site, and how it ended.
   - It survives restarts and app updates (state migration with tests).
2. **History views.**
   - A list by day.
   - A week view with focused minutes and planned vs completed.
   - Each entry opens its summary.
   - No scores, streak pressure or guilt wording.
   - Empty, loading and error states.
3. **Basic analytics.**
   - Local only and opt-in where it uses usage access (time in blocked apps outside sessions is off by default).
   - Exact numbers, no estimates.
4. **Retention and deletion.**
   - Keep for 30 / 90 / 365 days / forever.
   - Delete one entry, a day, or everything.
   - Export as CSV via Android's share sheet.
5. **Presets.**
   - Save a session setup as a preset: name, length, breaks, rounds, profile, optional due date and steps.
   - Start a preset in one tap from Home and setup.
   - A preset used by a planned session can't be deleted without a choice.
6. **Tests.**
   - JVM: history codec and migration, retention.
   - Compose UI: list, week view, delete, presets.
   - Emulator E2E: a finished session appears in history after a process kill.
   - Phone checklist.

### Step 3 (B2): study audio: scope and rules

- **Ambient first, offline, licence-clean.**
  - Sounds come with the app, or are generated on the device (noise and rain-like textures need no licence).
  - Any recorded sound must have a licence that allows redistribution in an app (e.g. CC0), recorded in `docs/audio-licences.md`.
- **Per profile.**
  - Each profile chooses ambient sound, music, both or silence (as far as each option is technically possible).
  - Optional automatic playback when focus starts.
- **During focus:** play/pause, skip (music only) and volume. No browsing.
- **Breaks:** audio pauses at breaks unless the person chooses otherwise. Audio stops at session end and on emergency recovery.
- **Streaming.**
  - Feasibility is researched during B, against current provider documentation.
  - Services are then added one at a time. The person attaches an existing playlist while setting up a profile.
  - The app states clearly whether it **plays the audio itself** (embedded) or **controls another music app** (external control, which needs that app installed and signed in).
  - Provider limits never block ambient audio or later steps.
- **Battery** is measured on the phone again after B2.

**Streaming feasibility (checked 2026-11-02; recheck before building)**

| Service | Plays inside Lockd In? | Control an external app | Notes |
|---|---|---|---|
| Spotify | No | Yes: App Remote SDK (play a playlist URI, pause/resume, shuffle; Spotify app installed, online) or the generic route | Development Mode (Feb 2026): 5 users per Client ID, Premium required. A public release needs Spotify's extended access. |
| YouTube Music | No (no public API) | Generic route only: open the playlist link in YouTube Music, then control it via its media session | — |
| Apple Music | Yes: MusicKit for Android | — | Needs an Apple Music subscription, an Apple developer account ($99/yr) and a signed developer token. |
| SoundCloud | Yes: API with OAuth 2.1 + PKCE, `/me/playlists`, stream URLs | — | Attribution required (uploader, logo, links). Whether new app registrations are open is unconfirmed. |

**Generic route ("controls your music app").** Store the playlist link per profile. At focus start, open it in its app. Then control it with play/pause, skip and volume through Android's media sessions (`MediaSessionManager.getActiveSessions`, which needs notification-listener access the person grants). This works for all four services without provider agreements.

**Proposed B2 order:**
1. Ambient sounds.
2. The generic route for all four services.
3. Embedded playback for one service at a time (Apple Music or SoundCloud).
4. Spotify App Remote, only with extended access.

**Owner decisions pending:** notification access for music control; whether to pay Apple's $99/yr for MusicKit.

### Step 5 (C2): backgrounds and quotes: rules

- Both are optional and off by default. The plain cream/espresso design and the sculpted lock stay the default and stay unchanged.
- Backgrounds are static images only, with no animated scenery. Text contrast must stay AA (checked).
- Quotes are short, shown at session start and on the block screen. There are no rotating quotes and no extra interruptions during a round.
- Backgrounds and quotes have separate switches.

## Known limitations (2026-11-02)

- **Phone checks:** none of B–E has been checked on a physical phone yet. Emulator evidence (2026-11-02):
  - Screen tests: 64/64 passed (e3a31d8).
  - Blocking end-to-end: all pass, including B history persisting after force-stop, B2 ambient on/off and the start sequence. The exception is the intermittent emulator-Chrome site check, which passed in the previous run with the same app code.
  - C reads a real provider calendar event (6643f6e).
  - E: a geofence arrival, fed through Play services mock location, posted the reminder; a second arrival the same day was skipped (6643f6e).
- **Music:** external control uses Android media keys and media volume. It can't show what's playing, and play/pause goes to whichever app last played audio.
  - Opening a playlist brings that app to the front for about 2.5 s, then Lockd In returns. That return can be blocked on Android without "display over other apps"; the person then switches back themselves.
  - Scheduled and place-based starts never open playlists.
- **Ambient sound:** procedurally generated (no recordings). Plays from the session service, which also declares `mediaPlayback`. Battery cost is not yet measured on the phone.
- **Calendar:** sees only calendars synced to the phone (Google accounts added in Android settings). Free time is 8 AM–10 PM, ≥ 25 min, with 10-min buffers. These are not yet configurable.
- **Widget:** shows state and a live countdown only. Tapping opens the app; nothing starts from the widget itself. It refreshes on state changes and planner runs; the "Today/Tomorrow" label may be stale until the next refresh (30 min at most).
- **Inactivity check-ins:**
  - The only signal is when the screen was last on. Android shows at most 3 notification buttons, so "Take a break" is reached by tapping the notification.
  - There is no emulator test, because a check-in needs 10+ real minutes.
- **Places:**
  - Need Google Play services and location "All the time".
  - Arrival can be late or missed indoors or with weak signal.
  - Geofences are re-registered whenever the app process starts (including after reboot, via boot receivers).
  - A Play Console background-location declaration will be needed before Play release.
- **Release:**
  - The Play listing needs the owner's developer account.
  - Calendar and location permissions will need Play data-safety answers (data stays on device).

## Milestone A: acceptance criteria

1. **Auto start.**
   - A weekly or one-time session with "Start automatically" on starts at its time without the app being open.
   - With "Alarms & reminders" allowed: start within 1 minute (exact alarm). Without it, the app says starts can be late and uses an inexact alarm.
   - Turning auto start on first shows what will happen and what gets blocked.
2. **Conflicts and missed starts.**
   - If a session is already running at the start time, the scheduled one doesn't replace it; a notice says so.
   - If the phone was off or the alarm was late and the occurrence is still in progress, the session starts in progress (remaining phases only) and says it started late.
   - If the occurrence fully passed, it counts as missed.
3. **Skip.** "Skip this time" removes only the next occurrence. Home and the reminders move to the following one.
4. **Clock and time zone.** A time-zone or clock change reschedules everything. Occurrence times follow local wall-clock time, including DST (JVM tests).
5. **Reminders.**
   - Before a session: Off, 5, 10, 15 or 30 minutes, per session. Actions: Start now / Remind me in 10 min / Skip this time.
   - At the start time of a session without auto start: Start now / Remind me in 10 min / Skip.
6. **Session notifications.** Focus starting, break starting, break ending (focus back on) and session complete, each sent once.
7. **Missed follow-up** (optional). After a missed session: "Start a 10-minute session" / "Reschedule". At most one per occurrence.
8. **Controls.**
   - Separate settings for reminders, session notifications and missed follow-ups; quiet hours suppress reminders and follow-ups.
   - At most 4 reminders/follow-ups per hour.
   - Each kind has its own Android notification channel, so the system controls work too.
   - Without the notification permission, the app says reminders can't be shown.
9. **Tests.**
   - JVM: occurrence planning, skip, DST, missed and in-progress decisions, quiet hours, rate limit.
   - Compose UI: editor toggle with explanation, reminder settings.
   - Emulator E2E: auto-start fires and blocks; a reminder is posted.
   - Phone checklist for the owner.
