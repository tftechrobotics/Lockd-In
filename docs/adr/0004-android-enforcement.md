# ADR-0004: Android enforcement on personal devices

**Status:** **Open — choose after probe results** (smoke test, then A1–A9, battery pair)
**Comparison:** `docs/enforcement-comparison.md`

## Current direction (proposed, not approved)
- **Detector:** usage access (UsageEvents polling) in a `specialUse`
  foreground service as the default, because it keeps working under Advanced
  Protection. AccessibilityService (window-change events only, no content) as
  an optional faster mode behind the Play prominent disclosure, offered only if
  data justifies it. Users are never asked to turn off Advanced Protection.
- **Cover:** a Focus **block screen** brought to the front, using the documented
  "display over other apps" background-launch exception. This moves the
  restricted app to the background. An overlay alone is not used as the primary
  method: the app stays resumed and audible underneath, and apps can hide overlays.
- **Sound:** while visible, the block screen takes audio focus so well-behaved
  players stop. The UI reports whether audio is still playing. It never says "paused".
- **Always allowed:** phone/emergency dialer, launcher, Settings, input methods,
  assistive technology, Focus.
- **App picker:** launcher-intent `<queries>`, no `QUERY_ALL_PACKAGES`.
- **Self-check:** permissions and service checked every few seconds; any loss
  is reported as `Degraded` immediately.

## Go/no-go
See `docs/milestones.md` §M1 go/no-go. If usage access cannot meet them, the
owner must choose between requiring accessibility (excludes Advanced
Protection users) and narrowing the promise for those users.

## Change log
- 2026-10-29: overlay demoted from primary cover to measured alternative;
  block-screen and audio-focus behavior added; "paused" wording removed;
  Advanced Protection handled without asking users to disable it; battery
  measurement now requires a baseline run.
