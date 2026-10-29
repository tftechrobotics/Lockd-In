# Lockd In for Windows (native shell)

**Status:** implemented; built and launch-tested only in CI (windows-2025). Not
yet run on a person's PC (needs signing, `docs/windows-signing.md`). Blocking is
**not** part of this app: it belongs to the separate agent/service (ADR-0007),
which doesn't exist yet, so the app says "Blocking not set up".

- `LockdIn.Desktop/` — WinUI 3 (Windows App SDK 2.5.1, .NET 10), unpackaged,
  self-contained. Window with minimal title bar and system caption buttons,
  tray icon + native menu (Open, Start a 10-minute focus, Quit), close hides to
  the tray (never ends a session), single instance, minimum size 480×600,
  `--background` starts in the tray. Hosts `apps/web/dist` from
  `https://app.lockdin.local/` (bundled, offline); other links open in the
  browser; DevTools, zoom, context menu and browser shortcuts are off.
- `LockdIn.Desktop.Tests/` — bridge contract tests; run anywhere:
  `dotnet run -c Release --project apps/windows/LockdIn.Desktop.Tests`.

Build (Windows): `cd apps/web && npm ci && npm run build`, then
`dotnet publish apps/windows/LockdIn.Desktop -c Release -r win-x64 -p:Platform=x64 -o out`.
Logs: `%LOCALAPPDATA%\LockdIn\logs\shell.log` (no app names or user content).
