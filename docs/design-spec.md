# Lockd In design specification — Cream & Ink, pass 4

> **Circular lock (2026-10-31, R36) replaces the pass-4 numerals timer.** Component `CircleLock`
> (`apps/web/src/ui/Timer.tsx`, styles under "Circular lock timer" in `app.css`): viewBox 240 × 344,
> body r 108 with a 2.5 espresso edge, progress ring r 87 / 9 wide (track = ink at 9–10%, arc =
> taupe #94877B light / #B3A595 dark, break arc lighter), shackle stroke 21 in muted taupe,
> digits Outfit 500 50u tabular, caption Figtree 500 15u ("remaining", "of break left", "per round",
> "complete"; hidden below 130 px wide). States: ready (muted digits, open), focus (open unless
> verified), break (lighter body and arc, open), done ("Done", empty ring, open). Sizes: web
> 200–300 px, Windows 170–220 px, Android ≤ 210 px. Wordmark/symbol: `scripts/build-wordmark.mjs`
> (round body = "o" size, knocked-out ring, closed shackle to the ascender) → `design/assets/`.

> **Pass 4 (2026-10-31, R34) supersedes pass 3 for the Focus screen.** Owner picked "A setup, B timer"
> from two isolated directions (lab: `apps/web/lab.html`, `apps/android/.../androidTest/.../lab/`).
>
> - **Setup is a question composer**, grouped by spacing, not cards: *What are you working on?*
>   (large underlined field, Outfit 500) → *How long?* (tiles 25 / 40 / 60 / Custom; 2 × 2 on narrow
>   screens and at large text; a custom value equal to a preset selects it) → a rounds line
>   ("2 rounds · 10 min break between" with **Change**, which reveals Focus rounds + Breaks) →
>   *Which profile?* (chips) → **Start timer**. Web wide adds a preview column (Ready status,
>   medium numerals, rail, "If you start now, you're done at…"); single-column layouts show
>   "Ends at … · 1 h 30 min with breaks" next to the action instead. Android pins that line and the
>   button above the bottom navigation.
> - **Running is numerals-led**: task (heading) → status with a mini lock ("Focusing · round 1 of 2",
>   "On a break · …", "Unlocked · …", "Session complete") → display numerals (Outfit 500, tabular,
>   tight tracking) → session rail (segment widths follow minutes, elapsed filled, breaks dashed,
>   start / break / end labels) → facts (Next break or Focus resumes, Session ends, Profile) →
>   nudge → honest protection line → **Need out?** only. *End now* lives inside the Need-out panel
>   and the pending-unlock row, so it never competes with the timer.
> - **States:** ready and done numerals use muted ink; break numerals use taupe mixed toward ink;
>   focus uses ink. The mini lock is closed **only** when restriction is verified on that device
>   (web never; Windows when the agent is running and the phase restricts; Android when
>   `PHONE_BLOCKING_AVAILABLE` and the phase restricts).
> - **Weights:** Outfit 500 for questions, task, numerals; Figtree 400/500/600 for interface text.
> - **Large text:** numerals size from the available width (web `min(clamp(...), 34cqi)`, Android
>   `min(104dp, 31% of width)`), so 200% text never pushes them off-screen; the lock icon follows
>   the text size; the rounds line wraps (FlowRow) instead of squeezing.
> - The padlock `LockTimer` (pass 3) is retired from Focus on web and Android.

> **Pass 3 (2026-10-29 night, R33) supersedes the "faithful to the mockup" rules below where they
> conflict.** The mockup is now a mood reference. Current rules:
>
> - **Type:** Figtree for all interface text (15 px web base; 13–16 px roles). Outfit only for the
>   wordmark, page titles (26 px web, 22 sp Android) and timer/summary figures.
> - **Sizes:** controls 44 px (web) / 48 dp (Android); primary action 48 px / 52 dp; icons 20;
>   radii 8 px controls, 12 px grouped surfaces; full pills only for segmented choices, day
>   toggles, status tags and the desktop Focus/Schedule switch.
> - **No library artwork** in the workspace. Paper grain at 60% of the token opacity.
> - **Focus setup:** task first; one segmented duration control with inline custom (a custom value
>   equal to a preset selects it); Breaks and Focus rounds as labeled settings (rounds need a
>   break, the two stay consistent); profile under "More options" on web/Windows; a plan summary
>   (small lock, total, end time, plan in words) next to the primary action.
> - **Primary action:** "Start timer" whenever blocking isn't verified, with "Timer only. Apps and
>   websites stay available… Set up blocking". "Start focus" only when the Windows service reports
>   running (Android: when `PHONE_BLOCKING_AVAILABLE` is true after device verification).
> - **Running:** remaining time in the lock (closed only when restriction is verified), task,
>   status tag "Focus · round 1 of 2" / "Break · round 1 of 2", next break or resume time, session
>   end, honest protection line, Need out? / End now.
> - **Android:** compact top bar (wordmark + Settings), scrolling content, primary action pinned
>   above the bottom navigation (status note moves into content at large text), bottom navigation
>   Focus / Schedule / Profiles. Schedule editor is a full screen; day toggles are seven equal
>   targets with full names for TalkBack.
> - **Web:** compact taupe masthead (128 px wordmark), restrained page heading, form column
>   (max 520) beside a summary card (260–320), device row with actions, one "Next" row.
> - **Windows:** no masthead; Focus/Schedule switch + Menu; single column; the summary card with
>   the primary action sticks to the bottom of the window.

---


Status: **approved direction, implemented on all three Focus screens** (2026-10-29).
Reference: `design/reference/cream-and-ink-mockup.png` (owner's image: Windows, web, Android).
Source of truth for values: `design/tokens.json` → generated `apps/web/src/styles/tokens.css` and
`apps/android/.../ui/Tokens.kt` (`python3 design/export-android.py`). Run
`node design/check-contrast.mjs` after any color change.

This is a faithful implementation of the reference, with the deviations listed at the end. The
personality lives in the wordmark, the padlock timer, the paper and the drawings. Everything else is
quiet: no decorative cards, gradients, statistics or ambient animation.

## 1. Color

| Token | Light | Dark (espresso) | Use |
|---|---|---|---|
| ground | #F5F0E6 | #1C1814 | Page / window background (cream paper) |
| surface | #FBF8F2 | #25201B | Inputs, dropdowns, Next card, dialogs |
| taupe | #D7C8B4 | #4B4137 | Web masthead, lock body |
| taupeSoft | #EBE2D4 | #322B24 | Unselected pills, switch track, nav indicator |
| line | #D3C5B1 | #4B4137 | Dividers, pill borders, card borders (decorative) |
| controlLine | #8F8270 | #8F8170 | Input and dropdown borders (≥ 3:1, WCAG 1.4.11) |
| ink | #302A25 | #F0E8DB | Text, selected pill, primary button |
| inkMuted | #5C5249 | #C3B6A4 | Secondary text (≥ 4.5:1 on every surface) |
| onInk | #FBF8F2 | #221C17 | Text on ink |
| statusDot | #8C6E57 | #C9A98D | Device status dots (filled = ready, ring = not) |
| danger / dangerSoft | #8E2F25 / #F3DED8 | #EBA597 / #3E2420 | End now, errors |
| drawing | ink at 11% | cream at 7.5% | Architectural line art |
| grain | 5% | 7% | Paper texture opacity |

Dark theme is espresso, not an inversion: the primary button and selected pill become cream on
espresso; the lock body is a warm brown. No orange or terracotta anywhere.

## 2. Typography

Two families, both OFL and self-hosted: **Outfit** (wordmark, headlines, timer, button) and
**Figtree** (all interface text). Sentence case everywhere; no all-caps labels.

| Role | Web / Windows (CSS px) | Android (sp) |
|---|---|---|
| Web headline ("Ready when you are.") | Outfit 700, 42/46, −0.02em (32 under 680 px) | — |
| Desktop/Android status headline ("Locked in.") | Outfit 700, 28 | Outfit 700, 28/34 |
| Task title (desktop, Android setup) | Outfit 600, 26 centered | Outfit 600, 22 centered |
| Page title (Schedule, Profiles…) | Outfit 700, 32 | Outfit 700, 28 |
| Timer digits | Outfit 800, 30% of lock-face width, tabular | Outfit 800, 26.5% of lock width |
| Timer caption ("Focus time") | Figtree 700, ≈10% of face width | Figtree 700, 9.2% of lock width |
| Primary button ("Lock in") | Outfit 600, 22 (desktop 20) | Outfit 600, 20 |
| Labels ("What are you working on?") | Figtree 500, 15 | — (no labels on phone setup) |
| Body / controls | Figtree 400–500, 15–16 | Figtree 400–500, 15–16 |
| Facts (Break at 6:25 PM) | Outfit 600, 22, tabular | caption line |
| Captions, device row | Figtree 400, 15 | Figtree 400, 14 |

## 3. Spacing, sizes, radii

Spacing scale: 4, 8, 12, 16, 24, 32, 48, 64.

| Element | Web | Windows layout | Android |
|---|---|---|---|
| Control height (input, dropdown) | 44 | 44 | 52 dp |
| Pill height | 40 | 40 | 44 dp |
| Primary button | 52 high, radius 14 | 46 high, radius 23 (pill) | 54 dp, full pill |
| Radius: controls / cards / surfaces | 10 / 12 / 16 | same | 12 dp / 16 dp |
| Pills, switch | fully rounded | fully rounded | fully rounded |
| Icons | 20 (device row, dropdowns), 22 (Next), 16–18 (chevrons) | same | 20–22 dp |
| Status dot | 8 | 8 | 8 dp |
| Gap in controls column | 16 | 12 | 14 dp |
| Lock width | 230 max (168 under 680 px) | 184 max | 196 dp max |
| Content width | 820 max page, stage = lock 170–230 + controls ≤ 380, gap 28–48 | 420 max column | 560 dp max |

Surfaces: flat. One hairline (`line`) where the reference shows one: above the Next row (web),
above the device row (desktop), the Next card border, and dividers between device items. No
shadows except the open Menu popover.

## 4. Brand artwork

**Wordmark** — "lockd in" in Outfit 700 glyph outlines; the "o" is a closed padlock whose body
holds a clock (cut-out). `design/assets/wordmark.svg`, `currentColor`, generated by
`apps/web/scripts/build-wordmark.mjs`. Web masthead width 204 px (136 under 640 px); Android top
bar 32 dp high, centered. The Windows title bar uses the lock mark icon plus "lockd in" in the
system font at 13 px (a title bar is not a brand surface).

**Padlock timer** — real text over vector art (`LockTimer.tsx`, `Components.kt` LockTimer).
Geometry in a 240 × 260 box (viewBox `0 36 240 260`):
- Body: x 14–226, y 120–292, radius 22 (aspect ≈ 1.23 : 1), fill `taupe`.
- Shackle: stroke 22 (≈ 10% of body width), round caps, centerline radius 44.5, centered on the
  body (legs at x 75.5 and 164.5); fill a touch lighter than the body when open.
- **Open** (nothing restricted: setup, break, recap): left leg seated, arc runs over the top and
  stops just above its midline on the right (released shackle).
- **Closed** (focus is restricting): shackle drops 14 units, both legs seated; body and shackle
  darken 18% toward ink.
- Face: clock glyph (ink disc, body-colored hands) ≈ 22–24% of body width, then digits, then
  caption. Screen readers get one summary ("Focus 1 of 2: 23 minutes left"), not ticking digits.

**Paper texture** — fractal-noise SVG fixed over the ground at `grain` opacity; multiply in light,
screen in dark. Never on controls.

**Architectural drawings** — `design/assets/library-edge.svg`: a tall arched window seen slightly
in perspective, with a bookcase in front of its lower half. Line art in `currentColor` =
`drawing`, mirrored on the right. Behind content, never interactive, `aria-hidden`.
- Windows: always on, 200 px wide, pushed 34 px past each window edge, from below the top bar.
  Hidden under 420 px.
- Web: in the page margins, 220 px wide (narrows with the window), from below the masthead.
  Hidden under 760 px.
- Android: none inside the app (phone width leaves no margin; matches the reference).

## 5. Platform chrome

**Windows** (WinUI 3 shell + shared UI, ADR-0009)
- Native title bar extended into content: lock icon + "lockd in", system caption buttons recolored
  to ink on the ground color; light/dark follow the theme.
- Top row: compact **Focus | Schedule** switch (pill track, selected = ink) on the left; outlined
  **Menu** button (hamburger + "Menu") on the right, opening Profiles, Devices, Settings.
- No sidebar and no website navigation. One centered column; drawings at both edges.
- Tray icon and close-to-tray stay native.

**Web**
- Broad taupe masthead: large wordmark left; Focus, Schedule, Profiles, Devices with a 2 px
  underline for the current page; gear icon for Settings.
- Spacious stage: headline, then lock left and controls right; device row with dividers; hairline;
  "Next: …" with "View schedule →" (or "Plan a session →").
- Under 680 px the stage stacks; under 640 px the nav moves under the wordmark.

**Android** (Compose, Material 3 conventions)
- Top bar: centered wordmark, Settings gear at the end; Back replaces nothing on top-level tabs.
- Bottom navigation bar (Focus, Schedule, Profiles) on the ground color with a hairline above;
  M3 indicator in `taupeSoft`; selected label semibold. Devices and Settings are secondary screens.
- Setup column: task title, lock, pills, Break dropdown (plus a "× N" periods dropdown beside it
  when a break is chosen), Profile dropdown, Lock in, device row, Next line (only if planned).

## 6. Components

| Component | Behavior |
|---|---|
| Duration pills | 25, 40, 60 min + Custom; radio group; Custom reveals a Minutes field (1–240) |
| Dropdown | Native `<select>` (web) / M3 DropdownMenu (Android) dressed with leading icon, "Break · 10 min" value text, chevron |
| Focus periods | Web/Windows: small quiet select at the right of the "Focus time" label row, only when a break is chosen. Android: "× N" dropdown beside Break |
| Device row | Icon, status dot, short phrase ("PC not paired", "Browser timer only", "PC blocking off"); dividers between items |
| Next | Web: rule + "Next: Physics review · Today 7:00 PM" + link. Windows: bordered card with "Next up". Android: one line, only when planned |
| Session view | Headline status ("Locked in." / "On a break." / "Unlocked for now."), task line, closed lock, facts, one nudge line, Need out? and End now, one honesty line |

## 7. States

Loading: the native shells paint `ground` before the page (no white flash). Empty: "Next: nothing
planned · Plan a session", "No sessions planned", "No apps chosen". Error: brick-red inline
message with `role="alert"`. Offline: the interface is local-first; no network state to show yet.
Permission-denied and recovery states for blocking arrive with the blocking milestone; the
honesty line says "Not blocking" until then. Unlock countdown and emergency recovery keep their
existing behavior.

## 8. Accessibility rules

Text ≥ 4.5:1, control boundaries and focus ring ≥ 3:1 (checked by script). Visible 3 px focus
ring. Targets ≥ 40 px (web) / 44–48 dp (Android). Status never by color alone (dot + words).
Reduced motion removes the shackle transition. Layout must survive 200% text at 320 px (web
tests) and 2× font scale (Android test).

## 9. Deviations from the reference (deliberate)

| Reference | Implementation | Why |
|---|---|---|
| "What's the move?" headline (Windows, Android) | Removed; the task is the centered title | Owner decision 2026-10-29 |
| "PC ready · Phone ready · Desktop app connected" | "PC not paired", "Browser timer only", etc. | Honesty: pairing and blocking aren't built |
| Three pills (25/40/60) | Adds "Custom" | Preserves existing custom lengths (1–240) |
| No focus-periods control | Small periods control appears when a break is chosen | Preserves multi-period sessions |
| Android "Focus" tab uses a house icon | Lock icon | The lock is the product's mark; "home" misdescribes the tab |
| Lighter input borders | `controlLine` #8F8270 (3.3:1) | WCAG 1.4.11 control contrast |
