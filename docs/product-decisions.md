# Product decisions

Three kinds of entry, kept apart on purpose:

- **Requirements:** stated by the owner (or the brief). Owner statements override the brief.
- **Engineering recommendations:** the developer's proposals. Not approved until the owner says so; silence is not approval.
- **Unresolved decisions:** need the owner. Each notes when it blocks work.

## Requirements

| # | Requirement | Source |
|---|---|---|
| R1 | Lockd In is a serious, **non-VPN** focus restriction app for personal Windows and Android devices. Approved apps are chosen separately per device; focus periods and breaks are scheduled and synchronized. | Owner 2026-10-29 |
| R2 | Meaningful enforcement and resistance to casual bypass must coexist with preserving work and safe recovery. An administrator-owner can eventually bypass it; that does **not** redefine the product as "friction, never a lock". | Owner 2026-10-29 |
| R3 | Never call an app "paused" while its process, audio or playback continues. | Owner 2026-10-29 |
| R4 | In an allowlist session, an unsupported browser must not silently become an unrestricted route to blocked websites. | Owner 2026-10-29 |
| R5 | Keep scheduled breaks, delayed overrides, immediate emergency recovery and enforcement failure separate. Preserve emergency access, assistive technology and user data; never remove safety features to improve blocking. | Owner 2026-10-29 |
| R6 | A disconnected peer is "unknown" — not protected, not stopped. Timing tolerances are targets until measured. | Owner 2026-10-29 |
| R7 | No persistent OS-policy experiments on the owner's everyday PC; use a disposable VM first. | Owner 2026-10-29 |
| R8 | Testing: compiled, reproducible probes with pinned toolchains; CI-built downloads with install, permission, removal and limitation notes; smoke test first; battery measured against a baseline; logs local, with a disclosure that app identifiers reveal usage. | Owner 2026-10-29 |
| R9 | Milestone 1 stays open until restriction is demonstrated; don't build the product on an unproven enforcement assumption. | Owner 2026-10-29 |
| R10 | Scope: Windows and Android, optional Google Calendar, native light/dark UI. No LMS integrations, tutoring, assignment tracking, decorative dashboards or VPN. | Owner + brief |
| R12 | Detect-and-terminate is not launch prevention. Termination is restricted to the harmless test target; never applied to real apps, and a newly launched process is not assumed to hold no valuable state. | Owner 2026-10-29 (review 2) |
| R13 | Describe recovery accurately: next-launch or `--recover` recovery is not immediate crash recovery. Agent crash, service crash, reboot and "nothing restarts" are defined and tested separately. | Owner 2026-10-29 (review 2) |
| R14 | VM policy experiments preserve pre-existing settings, restore only what they changed, handle concurrent changes, never clear a policy because a backup is absent, and ship as a separate artifact. | Owner 2026-10-29 (review 2) |
| R15 | State exact Android permission dependencies; verify Advanced Protection behavior rather than assume it. | Owner 2026-10-29 (review 2) |
| R16 | An abruptly stopped component can't be relied on to report its own stop: use authenticated heartbeats and "unknown" when they expire. | Owner 2026-10-29 (review 2) |
| R17 | The product is named **Lockd In** (was the working name "Focus"). Repository: `tftechrobotics/Lockd-In`. Trademark and store-name availability not yet checked. | Owner 2026-10-29 |
| R11 | Everything else in `Focus_Project_Brief.md` (allowlist profiles per device, no force-closing or lost work, no hidden processes, no indefinite lockouts, offline execution, privacy minimums, etc.). | Brief v1.0 |

## Engineering recommendations (not approved)

| # | Recommendation | Where |
|---|---|---|
| E1 | Windows: privileged service plus per-user agent; launch prevention via a session-scoped App Control policy (AppLocker fallback) if VM go criteria pass; window/audio restriction for everything else; never terminate real apps | ADR-0007 |
| E2 | Android: usage access + block screen + audio focus by default; accessibility optional and evidence-dependent | ADR-0004 |
| E3 | Defined behavior for each ordinary bypass gap | `enforcement-comparison.md` |
| E4 | Local-first, no accounts, device-group pairing | ADR-0005 |
| E5 | Calendar via free/busy scopes only | ADR-0006 |
| E6 | Session limits: focus 1–240 min, break 1–120 min, session ≤ 12 h (guarantees every session ends) | Implemented in core as a safety bound; values adjustable |
| E7 | DST: nonexistent start shifts later by the gap; repeated start uses the first occurrence (RFC 5545) | Implemented in core |
| E8 | Supported OS at launch: Windows 11 25H2/26H2 x64+ARM64; Android minSdk 29 | Pending owner device details |
| E9 | Rust shared core; WinUI 3; Kotlin/Compose | ADR-0001, ADR-0002 (engineering, reversible) |

## Unresolved decisions (need the owner)

| # | Question | Blocks |
|---|---|---|
| U1 | Is a Windows service running as SYSTEM, installed with admin consent, acceptable? | M3 architecture; not the current probes |
| U2 | Unapproved apps already open when a session starts: offer to let the user close them, or minimize and mute them? Lockd In never closes them itself. | M2 design |
| U3 | App Control policies are machine-wide: during a session, prevented apps would also be blocked for other Windows accounts on the same PC. Acceptable, or use per-user AppLocker rules (weaker: an admin can stop its service)? | After VM tests 10/20; not now |
| U4 | Emergency recovery is immediate. Should it also require a typed confirmation and notify paired devices? | M2 design |
| U5 | Override countdown length (recommended 90 s) and whether "end whole session" has a longer delay | M2 design |
| U6 | No accounts / device-group pairing (ADR-0005) vs accounts | M5 |
| U7 | Service mode may set browser policies that make Chrome/Edge show "managed by your organization". Acceptable? | After VM test 30 |
| U8 | Android: offer an accessibility mode (Play review risk), or require it if usage access proves insufficient? | After Android probe |
| U9 | Support Windows 10? | M3 |
| U10 | Android: request notification access to hide restricted apps' notifications during focus? | M4 |

## Withdrawn

| Date | Statement | Why |
|---|---|---|
| 2026-10-29 | "Lockd In is friction, never a lock" | Owner did not approve; replaced by R2 |
| 2026-10-29 | "Proposed decisions stand unless you object" | Silence is not approval (owner) |
| 2026-10-29 | Probe messages saying a covered or minimized app is "paused" | Inaccurate (R3) |
| 2026-10-29 | Former U3 ("may Lockd In close a restricted app seconds after launch?") | Superseded by R12: Lockd In never terminates real apps |
| 2026-10-29 | "Launch gating" by detect-and-terminate | Not prevention (R12) |
| 2026-10-29 | "App Control authoring not on Home, so impractical" | Wrong: enforcement works on all editions; authoring can be done in-app (see comparison) |
| 2026-10-29 | "Service reports tamper events" | An abrupt stop can't self-report (R16) |
