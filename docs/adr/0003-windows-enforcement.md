# ADR-0003: Windows enforcement by per-user window restriction

**Status:** **Superseded by ADR-0007** (2026-10-29)

## Change log
- 2026-10-29 (owner correction): window minimization was presented as the
  proposed architecture. It is now only the **experimental baseline** (layer 1
  of ADR-0007). The claim that the restricted app was "paused" was wrong —
  minimizing leaves it running and audible — and has been removed from the probe.

## Original decision (kept for history)
An unelevated per-user agent listens for foreground and restore events and
sweeps visible windows every ~2 s, minimizing non-approved windows. It never
terminates processes. Identity: packaged apps by Package Family Name/AUMID;
Win32 apps by normalized path plus Authenticode publisher; never display names.
