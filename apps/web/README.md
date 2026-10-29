# Lockd In web app (companion)

**Status:** implemented and tested in Chromium (Playwright, Linux). Local to one
browser: no accounts, no sync, no pairing yet (backlog B6, owner question U12).
It times sessions and keeps a schedule; it **cannot restrict apps** and says so.

## Commands
```
npm ci                 # .npmrc sets legacy-peer-deps (npm 10 resolver bug with plugin-react peers)
npm run dev            # http://localhost:5173
npm test               # unit tests incl. 773 parity samples vs the Rust core
npm run build          # tokens -> CSS, typecheck, production build in dist/
npx playwright test    # end-to-end + axe accessibility (needs `npm run build`)
npm run assets         # rebuild design/assets/wordmark.svg + lock-mark.svg (needs python fontTools)
```

## How it is put together
- `src/engine/session.ts` — port of `core/src/session.rs::evaluate`. Never edit
  one without the other: CI regenerates `parity-vectors.json` from Rust and
  fails on any difference, and `session.test.ts` checks every sample.
- `src/engine/schedule.ts` — weekly sessions in the browser's local time; DST
  behavior checked against the core's rule (test in `schedule.test.ts`).
- `src/state/store.ts` — versioned localStorage state with validation; works
  (for the tab only) when storage is blocked.
- Design tokens come from `design/tokens.json` (`npm run tokens`).

## Known limitations
- Clock changes: a running session uses the wall clock (a page can't keep a
  monotonic clock across reloads). Native apps use `core::clock`.
- Device statuses are fixed to "Timer only" / "Not paired" until pairing exists.
- Profiles show no apps: apps are chosen on each device from its installed list.

## Third-party
Fonts: Outfit and Figtree, SIL Open Font License 1.1 (via @fontsource-variable).
