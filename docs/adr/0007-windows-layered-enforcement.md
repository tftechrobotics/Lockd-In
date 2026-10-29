# ADR-0007: Windows — privileged service plus per-user agent

**Status:** **Proposed — not approved; evidence pending** (VM experiments and probe W1–W9)
**Supersedes:** ADR-0003 · **Comparison:** `docs/enforcement-comparison.md`

## Context
The owner wants meaningful enforcement that resists casual bypass, preserves
work, and recovers safely, on personal Windows PCs, without a VPN. A per-user
process alone can be ended from Task Manager, cannot touch elevated apps, and
cannot stop launches cleanly.

## Proposed decision
Two layers.
1. **Per-user agent:** detects restricted windows, minimizes them, mutes their
   audio sessions with a crash-safe ledger, explains what happened, owns the UI
   and tray.
2. **Focus service (LocalSystem):**
   - real-time launch gating of restricted programs, including elevated ones;
   - supervising and relaunching the agent;
   - session-scoped browser policies and per-app firewall rules, removed at
     break and end, with reconciliation at every service start;
   - tamper events (stopped, uninstalled, clock jump) reported to paired devices;
   - authenticated, ACL-restricted local IPC, with every request validated.

AppLocker and App Control for Business are **not used** in v1. They may become
a "strict mode" only if VM evidence meets the go criteria (rebootless apply and
remove, no Smart App Control conflict, workable on Home, cleanup proven after
simulated crashes).

## Consequences
- Installation needs administrator consent. That is a product change from
  ADR-0003 and is **owner decision U1**.
- An administrator can still stop or remove Focus after a UAC prompt. This is
  detected and reported, not prevented.
- Launch gating stops a program shortly after it starts. How short is to be
  measured (probe W2; service timings in M3).
- Already-running apps are minimized and muted, not paused. Pre-session
  handling is **owner decision U2**.

## Change log
- 2026-10-29: created after the owner rejected "friction, never a lock" and
  asked for a comparison of stronger options.
