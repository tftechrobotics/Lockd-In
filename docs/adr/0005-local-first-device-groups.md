# ADR-0005: Local-first, no Lockd In accounts, device-group pairing

**Status:** **Recommendation — awaiting owner decision (U6).** Not accepted.

## Change log
- 2026-10-29: status corrected. The first version said proposed decisions
  stand "unless you object". The owner has not approved this design, and
  silence is not approval.

## Recommendation
- Local-only mode: one device works fully with no sign-up and no network.
- No usernames or passwords. Pairing creates a device group:
  - each device has an Ed25519 key pair in platform secure storage;
  - pairing uses a QR code with a one-time, single-use token that expires
    within minutes, confirmed on both screens.
- Group documents (schedules, profiles, status reports) are versioned, signed
  by the authoring device, and end-to-end encrypted.
- A small relay stores and forwards ciphertext. It is never needed for a
  device to run or end an accepted session.
- Revoking a lost device rotates the group key.

## Why
- Removes account takeover, password storage and account recovery from the
  threat model.
- Works on networks that block device-to-device traffic (common on campus Wi-Fi).

## Trade-offs
- Losing every device means re-pairing from scratch.
- No web dashboard.
- Relay hosting is an ongoing cost.
