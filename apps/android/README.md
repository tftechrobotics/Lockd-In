# Lockd In for Android (app shell)

**Status:** implemented; built and tested only in CI (unit tests on the JVM, UI
tests on an API 35 emulator). Not yet on a phone (needs Google Play internal
testing, `docs/play/README.md`). **No blocking yet**: the app says "Blocking not
set up". Blocking (usage access + block screen) moves here from
`probes/android` once the probe has passed on a real phone (M1).

- Jetpack Compose (BOM 2026.09.00), Kotlin 2.2.20, AGP 8.13.0, minSdk 29, targetSdk 36.
- Bottom navigation: Focus, Schedule, Profiles. Settings in the top bar;
  Devices from the device row on Focus. Edge-to-edge with safe-drawing
  insets, IME padding, 48 dp targets, sp text, scrolling screens.
- `engine/` — Kotlin port of the core's session evaluation (checked against
  the 773 Rust parity samples) and weekly schedule rules (java.time, DST).
- `state/` — versioned JSON state in private SharedPreferences, excluded from
  backup and device transfer.
- Design tokens, fonts and vectors are generated: `python3 design/export-android.py`.

Known limits: session timing uses the wall clock (core `clock` module not
ported yet); no notification while backgrounded; no paper texture or library
drawings on the phone (kept for larger screens).

Tests:
- Unit (no Android SDK needed beyond Gradle): `./gradlew testDebugUnitTest`.
  The engine/state tests also run with plain kotlinc (see HANDOFF).
- UI: `./gradlew connectedDebugAndroidTest` with an emulator or device.

Third-party: Outfit and Figtree fonts, SIL Open Font License 1.1.
