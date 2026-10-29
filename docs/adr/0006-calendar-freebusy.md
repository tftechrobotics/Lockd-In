# ADR-0006: Google Calendar via free/busy only

**Status:** Recommendation (2026-10-29) — awaiting owner decision; Milestone 6

## Decision
Request `calendar.freebusy` and `calendar.calendarlist.readonly`. Suggest
sessions from busy intervals with deterministic rules (preferred hours,
lengths, buffers). Reminders are local notifications for accepted sessions.
No AI. No event titles, attendees or descriptions are ever received or stored.

Writing sessions to the user's calendar is a separate, later opt-in requiring
a write scope and a second consent.

## Consequences
- Smallest privacy footprint; recurring-event expansion is done by Google.
- Suggestions can't use event names (e.g. "Exam") — acceptable.
- Production use still requires Google OAuth verification (domain, privacy
  policy, demo video). Development uses Testing mode with the owner as a test
  user. Confirm scope sensitivity in the Cloud console.
