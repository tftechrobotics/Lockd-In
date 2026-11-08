---
name: lockd-in-design
description: Design and build Lockd In interfaces (Windows, web, Android) in the approved Cream & Ink style; use for any Lockd In screen, component, artwork or theme change and its visual verification.
---

# Lockd In design (Cream & Ink)

Lockd In is a focus app for Windows, Android and the web. Its look is **approved**: the owner's
Cream & Ink reference (`design/reference/cream-and-ink-mockup.png` in the repo). Work on Lockd In
UI is faithful implementation and refinement of that reference, not new aesthetic exploration.

## Before you change anything

1. Read `docs/design-spec.md` (exact values, platform chrome, deviations) and `design/tokens.json`.
2. Open the reference image and the latest real screenshots of the screen you are changing:
   - Web and the Windows layout: render locally with Playwright (`apps/web`, `npm run build`,
     `npx vite preview`), `?layout=desktop` plus a stub `window.chrome.webview` for the Windows layout.
   - Windows native window: `ci-screenshots-windows` branch (`git fetch origin ci-screenshots-windows`).
   - Android: `ci-screenshots` branch (emulator, from the Compose UI tests).
3. Check `docs/product-decisions.md` for owner decisions that override the reference
   (e.g. no "What's the move?" headline).

## Non-negotiables

- **Colors only from tokens.** Change `design/tokens.json`, then regenerate:
  `node apps/web/scripts/gen-tokens.mjs`, `python3 design/export-android.py`, and run
  `node design/check-contrast.mjs` (must pass: text 4.5:1, control borders and focus 3:1).
- **Type (R54, current):** Bricolage Grotesque (display 600/700, text 600, digits wdth 92) for greetings,
  headings, buttons and clock digits; Archivo (400/500/600) for interface text. Home = lock, then a
  greeting + one line about what's next under it (no "Next session" box).
- *(Superseded by R54)* **Type (pass 5, R53):** Archivo only. Archivo Expanded (wdth 125, 600/800) for screen titles,
  big numbers, stamped times and the primary button; semi-expanded 800 for timer digits; normal width
  400/500/600 for the interface. Width carries hierarchy. (Android done; web and Windows still use the
  old Outfit + Figtree pair until they are updated.)
- **Surfaces (pass 5):** grouped content on solid tonal plates (taupeSoft, radius 20) separated by
  2 dp cuts of the ground colour. No outlined cards; secondary buttons and chips are tonal.
  Sentence case. No all-caps labels or eyebrows, no monospace data labels.
- **Personality lives in four places only:** the wordmark (lock as the "o"), the padlock timer,
  the paper grain, and the faint architectural drawings. Everything else stays quiet.
- **Never add** decorative cards, gradients, shadows (except popovers), statistics, streaks,
  motivational filler, ambient animation, or controls that imply features that don't exist.
- **Honesty:** never show "ready", "protected" or "connected" unless the code knows it. Use the
  device row phrases from the spec ("PC not paired", "Browser timer only", "PC blocking off").
- **Platform chrome:** Windows = native title bar + Focus/Schedule switch + Menu, no sidebar.
  Web = broad taupe masthead, spacious lock-beside-controls stage. Android = Material 3 top bar
  with centered wordmark and a bottom navigation bar (Focus, Schedule, Profiles).

## The padlock timer

Vector art with real text over it. Geometry (viewBox `0 36 240 260`): body x 14–226, y 120–292,
radius 22; shackle stroke 22, centerline radius 44.5, legs at x 75.5 / 164.5. Open = left leg
seated, right end released just above the arc's midline. Closed (focus restricting) = shackle
dropped 14, both legs seated, colors 18% toward ink. Face = clock disc, digits (Archivo semi-expanded 800),
caption. Screen readers get a single minute-level summary. Keep the web (`LockTimer.tsx`) and
Compose (`Components.kt`) versions identical.

## Workflow for a screen change

1. Implement with existing components (pills, icon dropdowns, device row, Next row/card,
   FocusLayout) before inventing new ones. Keep copy short: one job per line.
2. Render and **look at screenshots** — web at 1280 and 755 px and 390 px, Windows layout at
   640×820 and 480×600, light and dark. Compare with the reference for proportions, spacing,
   type sizes, lock geometry, drawings, navigation and density. Fix differences before moving on.
3. Test: `npx playwright test` in `apps/web` (axe in light/dark, keyboard path, no horizontal
   scroll at 320/390 px with 200% text) and `npx vitest run`. Push to get the Android emulator run
   (UI tests + screenshots, includes 2× font) and the Windows window screenshots.
4. Never claim visual verification without having inspected the screenshots, and say which
   environment produced them (Chromium, emulator API 35, GitHub windows-2025 runner). These are
   not real-device checks.
5. Record deliberate deviations from the reference in `docs/design-spec.md` §9.
