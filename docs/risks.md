# Risk register and external prerequisites

Likelihood/impact: H/M/L. Ordered by when the risk must be retired.

| # | Risk | L | I | Mitigation | Retire by |
|---|---|---|---|---|---|
| R1 | Android usage-access detection too slow, misses opens, or is killed in background | M | H | Probe compares detectors; go/no-go thresholds; owner decision U8 if it fails | M1 |
| R2 | Android block screen can't be brought to front on some phones/versions | M | H | Probe measures; fallback to home screen; accessibility global action as alternative | M1 |
| R3 | Restricted app keeps playing audio/video under a cover, contradicting the promise | H | H | Block screen instead of overlay; audio focus; Android 17 background-audio hardening; Windows per-app mute; truthful status (R3) | M1 |
| R4 | Windows minimize/mute unreliable for some app classes (elevated, Store, full-screen games) | M | M | Probe W3, W8, W9; service layer for elevated apps | M1/M3 |
| R5 | Windows mute state persists after a crash (user left without sound in an app) | M | M | Ledger written before muting; guardian process restores when the probe dies; product service does the same; `--recover` and next start as last resort; tested separately in R1–R6 | M1 |
| R6 | Persistent OS policy left behind when nothing restarts (fail-closed: apps stay blocked) | M | H | Service reconciliation on start; separate scheduled cleanup task per policy; never signed policies; measured via `-SimulateCrash` + reboot; GO only if bounded | M1/M3 |
| R6b | App Control binary format is undocumented: an in-app compiler (needed on Home) could produce wrong policies after Windows changes | M | H | Round-trip tests against `CiTool` on every supported build; allow-all base + deny-only rules; refuse to deploy on validation failure | M3 |
| R6c | App Control is machine-wide: other accounts on a shared PC are affected during a session | M | M | Owner decision U3; AppLocker per-user rules as alternative | M1/M3 |
| R6d | Rollback overwrites someone else's settings | L | H | Ownership manifest: restore only values still equal to what Focus set; report conflicts; refuse when pre-existing EXE rules or extension policies exist | M1 |
| R7 | Privileged service adds attack surface (local privilege escalation via IPC), and cannot report its own abrupt stop | M | H | Authenticated ACL'd pipe, request validation, threat model, least functionality | M3/M7 |
| R8 | Play rejects `specialUse` foreground service or accessibility declaration | M | H | Proper disclosures; app keeps working with honest Degraded status; owner-device testing via sideload meanwhile | M4/M7 |
| R9 | Advanced Protection blocks sideloading (documented) and accessibility mode (reported); effect on usage access / overlay permission / background launch unverified | M | M | Default path doesn't need accessibility; probe A10 verifies the rest; Play internal testing for such phones; never ask users to disable it | M1/M4 |
| R10 | OEM battery management kills the Android service | M | H | Device testing; targeted guidance only when detected | M4 |
| R11 | Smart App Control blocks unsigned Windows test builds | M | M | Recorded as a finding; code signing is an M7 prerequisite | M1 |
| R12 | User changes the system clock to end sessions early | M | M | Monotonic tracking within running sessions (M3/M4); log clock jumps | M3/M4 |
| R13 | Three-language build complexity (Rust/C#/Kotlin) | M | M | Small JSON-shaped FFI; fallback to shared test vectors | M3 |
| R14 | Pairing abuse / unauthorized remote changes | L | H | Physical QR + confirmation, signatures, revocation, visible notifications | M5 |
| R15 | Clock skew between devices | M | L | Absolute instants; skew detection and warning | M5 |
| R16 | Google OAuth verification delay | M | L | Calendar optional; Testing mode for development | M6 |
| R17 | Owner stops/uninstalls Focus as administrator | H | L | Accepted limit (R2): detected and reported, not prevented | — |

## External prerequisites

| Needed for | Item | Cost | Status |
|---|---|---|---|
| M1 now | **Private GitHub repository** that a session can attach with push access (CI builds the Android APKs and publishes test downloads). The only remaining blocker to CI. | Free | **Requested (blocking CI)** |
| M1 now | Windows PC details (`winver`, CPU type) and ~15 min for the smoke test | — | Requested |
| M1 now | Android phone model, Android version, Advanced Protection on/off; ~15 min for the smoke test | — | Requested |
| M1 | A **disposable Windows 11 VM** for policy experiments. Hyper-V (Windows Pro) or VirtualBox, with Microsoft's evaluation image. Must be Pro/Enterprise to author App Control policies. | Free (evaluation) | Requested when the smoke tests pass |
| M4/M7 | Google Play developer account | One-time fee (confirm) | Later |
| M5 | Relay hosting | ~$5–10/month estimate, unverified | Later |
| M6 | Google Cloud project + OAuth consent; later a domain and privacy policy | Domain fee | Later |
| M7 | Windows code signing; optionally a Microsoft Store account | Recurring fee (confirm) | Later |
| M4–M7 | Second Android phone (different manufacturer) | Varies | Later |

Nothing here has been purchased, created or assumed to exist.
