# Backlog (ordered)

Statuses: **Done (evidence)**, **In progress**, **Next**, **Later**,
**Blocked (on)**. M-numbers map to `milestones.md`. Owner order of 2026-10-29:
design → main flow → enforcement/pairing/scheduling/recovery → presets and
study timing → calendar and check-ins → location and SMS.

## A. Foundations
| # | Item | Status |
|---|---|---|
| A1 | Session/schedule core (DST, overrides, emergency, protection status, clock changes) | Done — 35 tests, Linux |
| A2 | Cream & Ink tokens (light, dark, system) shared by all clients | In progress |
| A3 | Wordmark with lock "o", padlock timer, paper and library assets as scalable SVG | In progress |
| A4 | Web app shell: masthead, Focus, Schedule, Profiles, Devices, Settings | In progress |
| A5 | Main flow configure → start → focus → break → resume → finish (web, local timing only) | In progress |
| A6 | Rust ↔ TypeScript parity vectors for session timing (R13 fallback) | In progress |
| A7 | Windows app shell in chosen stack (U11) with tray and close ≠ end | Next — needs U11 |
| A8 | Android app shell (Compose) with bottom nav, insets, large fonts | Next |

## B. Enforcement and trust (M1 continues in parallel)
| # | Item | Status |
|---|---|---|
| B1 | Android probe on S24+ via Play internal testing | Blocked (Play account) |
| B2 | Windows probe on owner PC, signed | Blocked (Azure Artifact Signing) |
| B3 | App Control compiler: match Microsoft's header version; VM deploy test | Next |
| B4 | Windows privileged service + agent (ADR-0007), U1 | Later (after B2/B3) |
| B5 | Website restriction per browser (extension approach) | Later |
| B6 | Pairing, relay, heartbeats, offline execution (M5); web app connection (U12) | Next — needs U12 |
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
