# Android backlog: what exists (2026-11-01, R40)

This doc follows the owner's Android-only product direction. Windows and web are paused, with their code kept as it is. Implementation order is A → E, as listed at the end.

Status values:
- **Done** = implemented and tested (environment named).
- **Partial** = some of it exists.
- **Missing** = not built.

"Phone" means the owner's own phone (Galaxy S24+), running a sideloaded test build. "Emu" means the CI emulator (API 35).

| # | Area | Status | What exists / what's missing |
|---|---|---|---|
| 1 | Focus sessions & blocking | **Done (Emu + Phone)** | **Exists:** <br>• Quick start with a task name; 25/40/60 or custom lengths; rounds and breaks. <br>• Restrictions lift during breaks and return afterwards. <br>• Per-profile "Block these" / "Allow only these" for apps; websites by domain in Chrome, Samsung Internet, Edge, Firefox, Brave and Opera (needs Accessibility). <br>• No VPN. <br>• Flexible / Delayed / Locked commitment, plus emergency recovery. <br>• Honest blocking status; permission-loss notice. <br>• Restart after process kill and reboot. <br>• Essentials are never blocked. <br>• The block screen says the app is still open behind it. <br>**Missing:** documented per-browser support table in the app; a quick start from a saved preset (needs #5). |
| 2 | Automatic schedules | **Done (Emu); phone check pending** | **Exists:** one-time and weekly sessions with add/edit/disable/delete, overlap check, next session on Home, DST-correct times (JVM tests). <br>**Missing:** starting automatically, skipping an occurrence, missed-start handling, time-zone/clock-change rescheduling, and the explanation shown before turning it on. |
| 3 | Reminders | **Done (Emu: start notice); rest unit-tested; phone check pending** | Missing: <br>• reminders before a session; <br>• focus/break/end notifications; <br>• follow-up after a missed session with Start now / Remind me later / Reschedule; <br>• quiet hours, frequency limit, per-type controls. |
| 4 | Google Calendar | Missing (C) | — |
| 5 | Study tasks & presets | Partial (B) | Exists: recent task names only. Missing: saved tasks/courses with profile/duration/breaks, due dates, steps, spreading exam prep, "Open my study resources". |
| 6 | History & analytics | Partial (B) | Exists: the end-of-session summary with blocked attempts (per app/site, counted once per cover). Missing: persistent history, daily/weekly views, planned vs completed, usage stats, export/retention/deletion. |
| 7 | Block screen & encouragement | Partial (D) | Exists: task, next break, tone setting (Calm/Direct/Playful/Off), template messages. Missing: user-written and per-app messages. |
| 8 | Question timing | Missing (D) | — |
| 9 | Inactivity check-ins | Missing (D) | — |
| 10 | Location routines | Missing (E) | — |
| 11 | Home-screen widgets | Missing (C) | — |
| 12 | Settings & release quality | Partial | **Exists:** <br>• Themes; tone; commitment default; delete data. <br>• 200% text and TalkBack labels (UI tests). <br>• Permission setup with explanations. <br>• Excluded from backups. <br>**Missing:** <br>• session/break defaults screen; notification controls (A); privacy screen; onboarding flow; battery measurements. <br>• Completion summary kept until dismissed: the summary returns to Home after 30 s (owner request); A adds a "Last session" card on Home that stays until dismissed, so both requests hold. |

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
