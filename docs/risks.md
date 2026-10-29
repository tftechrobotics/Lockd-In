# Risk register and external prerequisites

Likelihood/impact: H/M/L. Ordered by how early the risk must be retired.

| # | Risk | L | I | Mitigation | Retire by |
|---|---|---|---|---|---|
| R1 | Android detection too slow, misses apps, or is killed in background | M | H | Probe compares two detectors on the real phone before committing | M1 |
| R2 | Windows minimize fails for important app classes (elevated, Store, fullscreen) | M | M | Probe W4–W7; honest UI wording for unsupported cases | M1 |
| R3 | Play rejects `specialUse` foreground service or accessibility declaration | M | H | Disclosures done properly; keep app working with honest Degraded status if refused; sideload/limited distribution for the owner meanwhile | M4/M7 |
| R4 | Advanced Protection (Android 17) disables accessibility mode | L for owner, M for public | M | Accessibility is optional, never required | M4 |
| R5 | OEM battery management kills the service | M | H | Device testing; targeted guidance only when detected | M4 |
| R6 | Restrictions fail to release (stuck shield) | L | H | Core invariant: no restriction outside focus phases; native release on every boundary, on crash restart and on emergency release; tests in M3/M4 | M3/M4 |
| R7 | User perceives permissions as spyware | M | M | Minimal data, plain disclosures, nothing leaves the device except encrypted sync | M2 |
| R8 | Three-language build complexity (Rust/C#/Kotlin) | M | M | Small JSON-shaped FFI; fallback to shared test vectors (ADR-0001) | M3 |
| R9 | Pairing abuse / unauthorized remote changes | L | H | Physical QR + confirmation on both devices, signatures, revocation, visible notifications for remote changes | M5 |
| R10 | Clock skew between devices | M | L | Absolute instants; skew detection and warning | M5 |
| R11 | Google OAuth verification delay | M | L | Calendar is optional; Testing mode for development | M6 |
| R12 | Windows 10 users excluded | M | L | Confirm ESU status; decide with owner | M3 |
| R13 | User ends Focus via Task Manager / uninstall | H | L | Accepted: Focus is a support tool. Detect and report honestly | — |

## External prerequisites (owner involvement)

| Needed for | Item | Cost | When |
|---|---|---|---|
| M1 | Windows PC details (`winver`, CPU arch) and ~20 min to run the Windows probe | — | Now |
| M1 | Android phone model + Android version; Android Studio on the PC; USB cable; ~30 min plus one 60-min session | — | Now |
| Continuity | A private GitHub repository this project can push to | Free | Now (recommended) |
| M4/M7 | Google Play developer account | One-time fee (confirm amount) | Before Play testing |
| M5 | Relay hosting (small VM or serverless) | ~$5–10/month estimate, unverified | M5 |
| M6 | Google Cloud project + OAuth consent screen; later a domain + privacy policy for verification | Domain ~annual fee | M6 / before public release |
| M7 | Windows code signing (e.g. Microsoft's managed signing service) and/or Microsoft Store account | Recurring fee (confirm) | M7 |
| M7 | Second Android phone (different manufacturer) for compatibility testing | Varies | M4–M7 |

Nothing here has been purchased, created or assumed to exist.
