# ADR-0010: Web app reaches devices as a paired browser, no accounts

**Status:** Accepted by the owner (R32, 2026-10-29). Extends ADR-0005.

## Decision
The browser joins the device group like any device: it scans or shows a QR
code, completes the same pairing handshake, and gets its own device key. A
small hosted relay forwards end-to-end encrypted messages between group
members; it stores only ciphertext queued for offline devices, with expiry.
No accounts, emails or passwords.

## Consequences
- The relay sees metadata (device IDs, timing, sizes), not content.
- Lose every paired device and the group is gone: re-pair from scratch.
- The browser's key lives in browser storage (non-extractable WebCrypto key
  in IndexedDB); clearing site data unpairs the browser.
- A browser never enforces; it can request a session start, which each device
  shows and applies under its own rules (no silent activation on another
  device, per the brief).
- Hosting provider and monthly spend still need owner approval before
  deployment.
