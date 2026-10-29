# ADR-0009: Windows app = native shell + shared UI

**Status:** Accepted by the owner (R31, 2026-10-29). Supersedes the Windows
part of ADR-0002.

## Decision
- **Shell (native, C#/.NET 10, Windows App SDK / WinUI 3):** the window, a
  minimal native title bar with the standard caption buttons, Mica/backdrop,
  the system tray icon and its menu, native notifications, single-instance
  handling, launch at sign-in, and close-to-tray. Closing the window never
  ends a session; ending a session is a separate labeled action.
- **Interface (shared):** the same React components as the web app, in a
  **desktop layout** (compact Focus/Schedule switch, Menu for Profiles,
  Devices and Settings; no masthead or sidebar), rendered in WebView2.
- **Offline:** the interface files ship inside the app and are served from a
  virtual host mapped to the install folder
  (`SetVirtualHostNameToFolderMapping`). No remote content is loaded; external
  navigation is blocked and opened in the default browser instead.
- **Enforcement stays native and separate:** the agent/service from
  ADR-0007 owns blocking and runs whether or not the window is open. The UI
  talks to it over the shell's bridge; it never enforces anything itself.
- **Bridge:** `window.chrome.webview.postMessage` with a versioned JSON
  contract validated on both sides; the page gets no general-purpose host
  object.

## Why
One tested interface for web and Windows (the reference shows them as close
siblings), while title bar, tray, menus, notifications and accessibility
plumbing are genuinely native. Rewriting every screen in XAML would double UI
work and testing.

## Costs and risks
- WebView2 runtime dependency (preinstalled on Windows 11; the installer
  must check for it, Evergreen bootstrapper as fallback).
- Native feel depends on details: no text selection on chrome, native focus
  rectangles, keyboard shortcuts, system accent for focus where appropriate,
  no browser context menu or zoom gestures, instant first paint (the shell
  draws the ground color before WebView2 loads).
- Accessibility goes through WebView2's UI Automation bridge; must be
  verified with Narrator and NVDA.
- Rendering can't be inspected from the Linux sandbox; CI builds and
  launches it, the owner sees it once signed builds exist (R18).
