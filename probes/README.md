# Enforcement probes (Milestone 1 experiments)

These are **experiments, not product code**. Each answers one high-risk
question on a real device. Nothing here is part of the shipped app, and none
of it should be described as "working blocking" until the results below are
filled in from a real device.

Both probes:

- never terminate apps or discard data — they only move a restricted app out
  of the way (Windows: minimize; Android: cover with an overlay);
- stop themselves after a fixed time (max 60 min) and have an immediate stop;
- log only app identifiers, timings and outcomes — never titles or content.

---

## Windows probe — `probes/windows`

**Question:** Can an unelevated per-user process detect a restricted app's
window and minimize it fast enough, across Win32, Store (UWP) and browser apps,
multiple monitors and sleep/resume — and where does it fail?

**Status:** Implemented. Compiled on Linux with .NET 10 SDK (0 warnings,
warnings-as-errors). **Not yet run on Windows.**

### Run it (Windows 11, any edition)

1. Install the .NET 10 SDK: `winget install Microsoft.DotNet.SDK.10`
2. From this folder: `dotnet run -c Release -- --block notepad.exe,msedge.exe --minutes 5`
3. Work through the checklist below while it runs. Ctrl+C ends it at any time.
4. Send the log from `%LOCALAPPDATA%\FocusProbe\probe-*.jsonl`.

### Checklist (record pass/fail + notes)

| # | Scenario | Expectation to verify |
|---|----------|-----------------------|
| W1 | Open Notepad during the probe | Minimized within ~250 ms of becoming foreground; explanation shown; text you typed is still there after restoring |
| W2 | Restore Notepad from the taskbar | Minimized again (`restored` trigger) |
| W3 | Notepad on a second monitor, not focused | Minimized by `sweep` within ~2 s |
| W4 | A Store app (e.g. add `CalculatorApp.exe`) | Identified through ApplicationFrameHost with an AUMID; minimized |
| W5 | Notepad **run as administrator** | Expected to FAIL (UIPI). Confirm the log says `minimized:false` |
| W6 | Edge/Chrome in full screen video (F11) | Record behavior |
| W7 | A game in exclusive full screen (if available) | Record behavior |
| W8 | Sleep the PC mid-probe, wake it | Probe still reacting after resume |
| W9 | Ctrl+C | Exits cleanly; nothing left running (check Task Manager) |
| W10 | Let `--minutes 1` expire | Exits on its own |

Also record: Windows edition/version (`winver`), CPU architecture, display scaling.

---

## Android probe — `probes/android`

**Question:** On a personal (unmanaged) phone, which detector —
UsageEvents polling or an accessibility service — detects a restricted app
quickly and survives real-world conditions, and can a full-screen overlay
reliably cover it?

**Status:** Implemented. **Not compiled** — this session cannot reach Google's
Maven repository or the Android SDK. Expect possible small compile fixes.

### Build and install

1. Open `probes/android` in Android Studio (it will create the Gradle wrapper;
   accept any suggested AGP/Kotlin upgrade).
2. Enable USB debugging on the phone, connect, press Run.

### Checklist

Run each scenario once with **usage access** and once with **accessibility**.

| # | Scenario | Expectation to verify |
|---|----------|-----------------------|
| A1 | Open a restricted app (e.g. TikTok) | Overlay appears; note `detect_latency_ms` |
| A2 | Tap "Go to home screen" | Home screen; app still in Recents with state intact |
| A3 | Open it from Recents | Covered again |
| A4 | Screen off 10 min mid-session, then on | Still enforcing |
| A5 | Swipe Focus Probe away from Recents | Log shows `service_restarted` or enforcement continues |
| A6 | Revoke usage access / overlay mid-session | `capabilities_changed` logged within ~5 s |
| A7 | Reboot mid-session | Record what happens (no auto-restart is implemented yet) |
| A8 | Let the session time out; tap "End now" in another run | Overlay removed, `session_released` logged |
| A9 | 60-minute session with normal use | Battery % at start/end in the log |
| A10 | Picture-in-picture / split screen with a restricted app | Record behavior |

Also record: phone model, Android version, manufacturer skin (e.g. One UI),
battery-optimization setting for the app, and whether Advanced Protection is on.
