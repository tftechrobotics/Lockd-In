# ADR-0003: Windows enforcement by per-user window restriction

**Status:** Proposed — pending probe results (`probes/windows`, W1–W10)

## Decision
An unelevated per-user agent listens for foreground and restore events
(`SetWinEventHook`, out-of-context) and sweeps visible windows every ~2 s.
When a non-approved app's window is visible during a focus period, the agent
minimizes it and shows a calm explanation of when it returns. It never
terminates processes. Approved apps are an allowlist with a non-removable
system baseline (shell, Settings, Task Manager, assistive technology, input
methods, Focus itself).

App identity: packaged apps by Package Family Name / AUMID; Win32 apps by
normalized image path plus Authenticode publisher where signed. Display names
are never used as identifiers.

## Consequences
- No admin rights, installer stays simple, uninstall leaves nothing behind.
- Cannot act on elevated windows (UIPI); the UI must say "can't pause apps
  running as administrator" rather than imply it can.
- The owner can end the agent from Task Manager. Focus treats that as a
  detected stop and reports enforcement Unavailable to paired devices.
- Re-evaluate a privileged service only if real use shows a need.

## Acceptance (probe)
Foreground-triggered minimize p95 ≤ 500 ms; sweep catches off-focus windows
within 2.5 s; zero data loss; elevated-app failure detected and reported
(not silent).
