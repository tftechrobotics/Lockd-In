# ADR-0004: Android enforcement on personal devices

**Status:** Open — choose detector after probe results (`probes/android`, A1–A10)

## Direction
- Detector: **UsageEvents polling** (`PACKAGE_USAGE_STATS`) in a `specialUse`
  foreground service as the default; **AccessibilityService** (window-change
  events only, no content) as an optional faster mode behind the Play
  prominent disclosure. Never require accessibility.
- Cover: full-screen overlay (`SYSTEM_ALERT_WINDOW`) explaining when the app
  returns, with "Go to home screen". Apps are never killed.
- Always allowed: phone/emergency dialer, launcher, Settings, input methods,
  assistive technology, Focus.
- App picker: launcher-intent `<queries>`, no `QUERY_ALL_PACKAGES`.
- Self-check every few seconds during a session: permissions still granted,
  service alive; report `Degraded` immediately when not.

## Policy cost
Play declarations with videos for the `specialUse` foreground service (and for
accessibility if used). Approval cannot be guaranteed; the app must keep
working, with honest status, if a capability is refused.

## Decision rule after the probe
Pick the default detector by: detection p95 latency, missed detections over a
60-minute session, battery use, survival after swipe-away and screen-off.
