# ADR-0002: Native client frameworks

**Status:** Proposed (2026-10-29)

## Decision
- **Windows:** WinUI 3 (Windows App SDK) on .NET 10, C#. Native Fluent
  controls, UI Automation for screen readers, Mica, system theme, tray via
  Win32 interop. Full-trust desktop apps can use the Win32 window APIs the
  enforcer needs.
- **Android:** Kotlin + Jetpack Compose with Material 3; platform services
  (foreground service, overlay, alarms) written directly against the SDK.

## Rejected
- Electron/Tauri/web UI: the brief rules out a packaged web page as proof of
  native behavior, and native conventions (tray, notifications, accessibility)
  are easier to get right natively.
- Flutter / Compose Multiplatform for both: one UI codebase, but weaker
  Windows conventions, and enforcement would still be native per platform.

## Open
- Windows packaging: MSIX (clean install/update/uninstall, App Installer
  updates, Store-ready) vs. unpackaged + MSI/installer. Leaning MSIX; confirm
  in Milestone 3 that the enforcer and native-messaging registration work from
  a packaged app.
- Android `minSdk`: proposed 29 (Android 10) pending the owner's phone model.
