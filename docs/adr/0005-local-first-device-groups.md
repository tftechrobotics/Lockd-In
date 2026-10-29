# ADR-0005: Local-first, no Focus accounts, device-group pairing

**Status:** Proposed (2026-10-29) — owner may override

## Decision
- **Local-only mode is the default.** One device works fully with no sign-up
  and no network.
- **No usernames or passwords.** Pairing creates a *device group*: each device
  has an Ed25519 key pair in platform secure storage; pairing uses a QR code
  carrying a one-time, short-lived (≤ 5 min), single-use token and is
  confirmed on both screens.
- Group documents (schedules, profiles, acknowledgments) are versioned,
  signed by the authoring device and end-to-end encrypted with a group key.
- A small **relay** stores and forwards ciphertext. It cannot read schedules
  and is never needed for a device to run or end an accepted session.
- Revoking a lost device rotates the group key.

## Why
Removes account takeover, password storage and account recovery from the
threat model entirely; fits a two-device personal tool; keeps working on
networks that block device-to-device traffic (e.g. campus Wi-Fi), which rules
out LAN-only sync.

## Trade-offs
- Losing every device means re-pairing from scratch (only schedules/profiles
  are lost; nothing else is stored).
- No web dashboard or account-level history.
- Relay hosting is an ongoing cost (owner prerequisite, Milestone 5).
