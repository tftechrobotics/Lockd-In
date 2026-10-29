# Product decisions log

Records decisions that refine or override the brief (`Focus_Project_Brief.md`).
Owner decisions override the brief and are marked **Owner**. Engineering
proposals awaiting objection are marked **Proposed**.

| Date | Decision | Source | Notes |
|---|---|---|---|
| 2026-10-29 | Local-only mode is the default; no Focus accounts; devices pair into a group | Proposed (ADR-0005) | Brief asked to evaluate local-only mode |
| 2026-10-29 | Approved apps are an allowlist with a non-removable system baseline (shell, Settings, Task Manager, dialer/emergency, assistive tech, input methods, Focus) | Proposed | Prevents locking the user out of recovery and accessibility |
| 2026-10-29 | Overrides: default 90 s countdown, cancellable, lifts only the current focus period; max countdown 15 min | Proposed | Implemented in core |
| 2026-10-29 | Emergency release: immediate, ends the whole session, recorded in history | Proposed | Implemented in core |
| 2026-10-29 | Session limits: focus 1–240 min, break 1–120 min, whole session ≤ 12 h | Proposed | Guarantees every session ends |
| 2026-10-29 | Website restriction v1 = Chrome and Edge extension only; status says "not restricted" otherwise | Proposed | Firefox later |
| 2026-10-29 | Supported OS at launch: Windows 11 25H2/26H2 (x64, ARM64); Android minSdk 29 pending owner's phone | Proposed | Windows 10 not supported unless owner needs it |
| 2026-10-29 | Calendar uses free/busy only; never reads event titles | Proposed (ADR-0006) | |
| 2026-10-29 | DST rule: nonexistent local start shifts later by the gap; repeated local start uses the first occurrence (RFC 5545) | Proposed | Implemented and tested |

## Where the brief was challenged

- **"Restrict" means add friction, not prevent.** On personal devices the
  owner can always uninstall or stop Focus. Copy will never say "impossible to
  bypass".
- **Perfect cross-device simultaneity** is not achievable; target is ≤ 2 s
  with synced clocks, with a visible warning when a clock is off.
- **Website blocking in every browser** is not realistic without invasive
  techniques the brief forbids; scope is Chrome/Edge with honest status.
