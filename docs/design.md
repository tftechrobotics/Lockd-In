# M2 design direction (proposed, not approved)

Canvas: "Lockd In — M2 design direction" (claude.ai artifact, private to the
owner). Static mockups only; nothing here is native UI, and nothing has been
checked with a screen reader, keyboard or text scaling yet (M2 acceptance).

## Look
"Quiet instrument": cool neutral grounds, one deep teal accent reserved for
the next action, no gradients or decorative cards. Platform fonts (Segoe UI
Variable on Windows, Roboto on Android) at one shared scale; tabular figures
for every time and countdown.

## Tokens (light / dark, designed separately)
| Role | Light | Dark |
|---|---|---|
| Ground | #F4F5F6 | #0F1113 |
| Surface | #FFFFFF | #171A1D |
| Surface 2 | #ECEEF0 | #21252A |
| Border | #CDD3D9 | #3A4047 |
| Text | #15181B | #E9ECEF |
| Text 2 | #4A525A | #A8B0B8 |
| Accent | #1A5C69 | #7FC4CE |
| On accent | #FFFFFF | #0A1E22 |
| Warning | #8F4500 on #FDEBD8 | #F0A35C on #3A2614 |
| Danger | #B3261E on #FBE4E2 | #F28B82 on #3B1A18 |

Text pairs were chosen for at least 4.5:1 by calculation; not yet verified
with a contrast tool on rendered screens.

Spacing 4/8/12/16/24/32/48 · radius 6 inputs, 8 buttons, 12 surfaces ·
motion 150 ms ease-out, none with reduced motion · targets ≥ 44 px / 48 dp.

## Rules carried into every screen
- Device status = the core model's five states, always icon + word:
  Protected, Not yet applied, Degraded, Not enforcing, Unknown.
  Outside a session, readiness uses "Ready" / "Not ready".
- Each device row shows plan sent / acknowledged / observed separately.
- Never "paused" or "closed": restricted apps are "minimized and muted"
  (Windows) or "covered and moved to the background" (Android).
- Override: delayed (90 s placeholder, U5 open), scoped, cancellable.
  Emergency recovery: immediate, always visible, low emphasis.
- A 10-minute start is one tap from Home on both platforms.

## Screens on the canvas (light and dark each)
Windows: Home (phone Unknown), New session (overlap error, timeline
preview, DST note), Active focus (override scopes, emergency recovery).
Android: Home (usage access off → warning, Not ready), Start now sheet,
Active focus (override countdown pending).

## Not yet designed
Onboarding and permission flow, restricted-app block screen, app profiles,
pairing and device management, calendar suggestions, settings and history,
diagnostics and recovery; loading, offline and empty states beyond those shown.
