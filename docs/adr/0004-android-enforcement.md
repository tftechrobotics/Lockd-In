# ADR-0004: Android enforcement on personal devices

**Status:** **Open — choose after probe results** (smoke test, A1–A11, battery pair)
**Detail:** `docs/enforcement-comparison.md` (includes the exact permission table)

## Current direction (proposed, not approved)
- **Detector:** usage access (UsageEvents polling) in a `specialUse`
  foreground service. AccessibilityService (window-change events only) is an
  optional faster mode, offered only if data justifies the Play review risk.
- **Cover:** a Lockd In block screen brought to the front. This **depends on
  `SYSTEM_ALERT_WINDOW`** ("Display over other apps"), whose grant is the
  documented background-activity-launch exception, even though nothing is
  drawn as an overlay. Overlay-only cover is measured but not recommended.
- **Sound:** audio focus taken while the block screen is visible; truthful
  status shown; never "paused".
- **Advanced Protection:** users are never asked to disable it. Whether usage
  access, "Display over other apps" and background launches work with it on is
  **unverified** (probe A10). Sideloading is blocked under it (Doc), so testing
  on such a phone may need Play internal testing.
- **Always allowed:** dialer/emergency, launcher, Settings, input methods,
  assistive technology, Lockd In.
- **App picker:** launcher-intent `<queries>`, no `QUERY_ALL_PACKAGES`.
- **Self-check:** every few seconds; any loss reported as `Degraded`.

## Change log
- 2026-10-29 (first revision): overlay demoted; block screen and audio focus
  added; "paused" wording removed; battery baseline required.
- 2026-10-29 (second review): exact permission dependencies listed;
  `SYSTEM_ALERT_WINDOW` dependency of the block screen made explicit; "works
  under Advanced Protection" downgraded from statement to hypothesis with a
  test row; probe now logs Advanced Protection state and the overlay
  permission at each background launch.
