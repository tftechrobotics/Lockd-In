# Backlog (ordered)

Statuses: **Done (evidence)**, **In progress**, **Next**, **Later**,
**Blocked (on)**. M-numbers map to `milestones.md`. Owner order of 2026-10-29:
design → main flow → enforcement/pairing/scheduling/recovery → presets and
study timing → calendar and check-ins → location and SMS.

## A. Foundations
| # | Item | Status |
|---|---|---|
| A1 | Session/schedule core (DST, overrides, emergency, protection status, clock changes) | Done — 35 tests, Linux |
| A2 | Cream & Ink tokens (light, dark, system) shared by all clients | Done (web); Android Compose theme next |
| A3 | Wordmark with lock "o", padlock timer, paper and library assets, app/tray icon | Done |
| A4 | Web app shell: masthead, Focus, Schedule, Profiles, Devices, Settings | Done (Chromium-tested) |
| A5 | Main flow configure → start → focus → break → resume → finish (web, local timing only) | Done (Chromium-tested) |
| A6 | Rust ↔ TypeScript parity vectors for session timing (R13 fallback) | Done (CI-enforced) |
| A7 | Windows app: native shell + shared UI (R31) — title bar, tray menu, close-to-tray, single instance, offline bundled UI, bridge | In progress — implemented; CI build/launch test |
| A8 | Android app shell (Compose) with bottom nav, insets, large fonts | Done (emulator-tested in CI) — not yet on a phone |
| A9 | Android toolchain upgrade: AGP 9.1 + compileSdk 37 for Compose 1.12+ | Later |
| A10 | Port core `clock` (monotonic session time) to Kotlin and TypeScript | Next |

## B. Enforcement and trust (M1 continues in parallel)
| # | Item | Status |
|---|---|---|
| B1 | Android probe on S24+ via Play internal testing | Blocked (Play account) |
| B2 | Windows probe on owner PC, signed | Blocked (Azure Artifact Signing) |
| B3 | App Control compiler: match Microsoft's header version; VM deploy test | Next |
| B4 | Windows privileged service + agent (ADR-0007), U1 | Later (after B2/B3) |
| B5 | Website restriction per browser (extension approach) | Later |
| B6 | Pairing, relay, heartbeats, offline execution (M5); browser pairs like a device, no accounts (R32, ADR-0010) | Next — relay hosting needs owner approval of provider/spend |
| B7 | Installers, updates, uninstall, settings restoration | Later (M7) |

## C. Study features
| # | Item | Status |
|---|---|---|
| C1 | Optional intention (subject → task) on the Focus screen | In progress (web) |
| C2 | Presets with profile, links, documents; review-before-start | Next |
| C3 | Open selected resources at start (Windows ShellExecute, Android intents, web links) | Next |
| C4 | Custom block messages, tones, quiet mode | Next |
| C5 | Question/task timing with recap and gentle suggestions | Next |
| C6 | Session recap (no streaks) | Next |

## D. Assistance
| # | Item | Status |
|---|---|---|
| D1 | Google Calendar free/busy suggestions (M6) | Later |
| D2 | Inactivity check-ins (idle time only) with cooldowns | Later |
| D3 | Accountability check-ins (in-app/push), quiet hours, opt-out | Later |
| D4 | Location routines — feasibility, battery, Play policy | Later (investigate) |
| D5 | SMS coach — consent, cost, delivery, unsubscribe evaluation | Later (evaluate only) |
