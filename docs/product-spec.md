# Lockd In — product specification v2

Owner direction of 2026-10-29 (afternoon), with the approved **Cream & Ink**
reference image. This document overrides `Focus_Project_Brief.md` where they
differ (recorded as R19–R31 in `product-decisions.md`). Status words follow
the project rule: **Proposed**, **Implemented**, **Tested (where)**,
**Blocked**.

## 1. Purpose
Lockd In helps people — especially college students — start focused work and
stay with it. Encouraging, useful, a little personality; starting a session
is fast; advanced features are optional and revealed progressively.

Core loop (every platform):
1. Say what you're working on (optional).
2. Pick focus length, breaks and an app profile.
3. Pick participating devices.
4. Start a synchronized session.
5. Approved tools stay available; distractions are restricted.
6. Access returns during scheduled breaks and at the end.

## 2. Platforms
| Platform | Role | Enforcement |
|---|---|---|
| Windows app | Full app; tray menu; native titlebar | Yes, via native agent (see §4) |
| Android app | Full app; bottom navigation | Yes, via usage access + block screen (M1 evidence pending) |
| **Web app (new)** | Companion: plan, start, watch status, edit profiles and schedule | **None.** A browser cannot restrict other apps. It says so, and says when the installed app is required or disconnected. |

## 3. Visual identity — Cream & Ink (approved)
Warm cream ground, soft taupe surfaces, espresso ink text and primary buttons;
subtle paper texture; very faint library architecture drawings at the edges
only; padlock containing the countdown with a small clock; the lock replaces
the "o" in the lowercase wordmark "lockd in". Coordinated dark theme (not an
inversion) and a "match system" option. No orange, page folds, neon, glossy
locks, card clutter, decorative charts or SaaS-dashboard styling. Statuses and
sessions in the reference image are illustrative only.

Layouts:
- **Windows:** native titlebar; compact Focus/Schedule switch; Menu button
  for Profiles, Devices, Settings; no sidebar or top website nav. Main view:
  intention, lock timer, duration, break, profile, start, device status, next
  session. Tray menu with status, quick actions and "Open Lockd In". Closing
  the window never ends a session; ending a session is a separate, labeled
  action.
- **Web:** wide branded masthead (wordmark left; Focus, Schedule, Profiles,
  Devices, Settings); lock timer one side, controls the other; compact device
  status; one upcoming-session row; full schedule on its own page.
- **Android:** system controls and permission flows; central timer; primary
  action within thumb reach; bottom navigation Focus, Schedule, Profiles;
  Devices and Settings one tap away; small screens, large fonts, keyboard and
  insets handled.

## 4. Blocking and scheduling (foundation, unchanged in intent)
Per-device approved apps and websites; reusable profiles (Study, Writing,
Coding, Custom); immediate, future and recurring sessions; custom focus/break
cycles; breaks restore access, then restrictions resume; shared timing across
paired devices; secure pairing; offline execution; honest sync status; late
connections, missed updates, clock changes, conflicting edits and restarts
handled; delayed override distinct from breaks; immediate emergency recovery;
no VPN.

Enforcement claims stay exact: minimizing, covering, muting or detecting an
app is **not** preventing its launch (R12). Work is never discarded.

## 5. Task-based sessions (new, optional)
Intention = subject → task → length (e.g. "TAM 212 → Practice problems → 40
min"). Users can create subjects and tasks, save presets, attach apps,
websites and documents to a preset, optionally open chosen resources at
start, and review what will open and what stays available before starting.
Choosing a subject never silently changes blocking rules: the preset shows
its profile, editable before start. The blocker works with no task at all.
No Canvas/PrairieLearn/SmartPhysics integration in the first release; saved
links only.

## 6. Question and task timing (new, optional study mode)
Explicit controls: Start question, Next question, Mark as stuck, Pause
tracking, Finish task. Time per item and a short recap. Time spent is not
treated as understanding or failure; reading, paper and thinking are work.
Gentle suggestion when an item runs long compared with the user's own recent
items: keep going, mark for review, or add 10 minutes. Never extends
restrictions automatically. Automatic question detection is out of scope.

## 7. Nudges and block screens (new, optional)
Tone: Calm, Direct, Playful, Custom, or Quiet (no messages). Frequency limits.
Block screen: current task, time to next break, a short message (may vary by
app or category), "Return to my work", and the override/recovery flow. Never
shaming, never claims to know feelings; no repeated pop-ups or sounds.

## 8. Inactivity check-ins (new, optional)
After a user-chosen idle period: "Still working? Reading or writing on paper
counts." Answers: Still working, Working on paper, Take a break, Remind me
later. Cooldowns and suppression (e.g. during approved video, in the first
minutes of a session, after a recent answer). Never punitive. Signal: time
since last input only (Windows `GetLastInputInfo`; Android screen
interaction) — no keystrokes, screenshots, documents or browsing history.

## 9. Location routines (later milestone, investigate)
Opt-in places + time windows + profile; reminders first, automatic start only
with separate explicit setup; arrival/departure grace periods; easy pause;
evaluated on device; no location history. Leaving a place may prompt a
check-in, never more restriction. Must document denied permission,
inaccuracy, offline. Android background location needs a Play declaration;
battery cost to be measured.

## 10. Calendar and accountability
Google Calendar optional; free/busy finds open time but cannot see titles or
deadlines (explained to the user); suggestions need acceptance. Accountability
coach: in-app and push check-ins first ("You planned physics at 7. Ready for a
10-minute start?"), clearly automated, with quiet hours, frequency limits,
snooze, opt-out. SMS is a later evaluation (consent, cost, delivery, privacy,
unsubscribe). Neither paid AI nor SMS is needed for basic blocking.

## 11. Home screen answers only
What am I working on? How long? What will be available? Which devices are
ready? How do I start? During a session: timer, task, next break, actual
protection status. Concise recap after. No analytics walls, guilt streaks or
engagement loops.

## 12. Production requirements
Secure storage, pairing, authenticated communication; minimal data; privacy
controls; permission-denied, disconnected, offline, loading, empty and
failure states; keyboard, screen reader, visible focus, contrast, reduced
motion, text scaling; installers, signing, updates, uninstall, recovery;
restore settings Lockd In changed; tested OS-version matrix; real-device
validation of blocking, breaks, recovery, sync and battery; failure-case
tests; previews clearly separated from working functionality.
