# Focus probe for Windows — tester instructions

**What this is:** an experiment, not the Focus app. It checks whether an
ordinary Windows program can keep a restricted app out of the way. It
**minimizes** windows and **mutes** sound. It never closes apps, never needs
administrator rights, and stops by itself.

**What's in the folder**

| File | Purpose |
|---|---|
| `FocusProbe.exe` | The probe (console window) |
| `FocusTarget.exe` | A harmless test app: a small window and a quiet tone. Holds no data. |
| `vm-only-policy-experiments\` | Scripts for a **disposable virtual machine only**. Do not run them on your everyday PC — they refuse to unless they detect a VM. |

**Before you start**

- Windows 11 (any edition), x64 or ARM64 — use the matching download.
- These files are **not code-signed** yet. Windows SmartScreen may warn ("More info → Run anyway"). If **Smart App Control** is On (Settings → Privacy & security → Windows Security → App & browser control), Windows may refuse to run them at all. Record that and stop; do not turn Smart App Control off for this test.
- Logs are written to `%LOCALAPPDATA%\FocusProbe\`. They contain program names (e.g. `FocusTarget.exe`) and timings — program names can reveal what you use. Nothing is uploaded; review before sharing.

## Step 1 — Smoke test (5 minutes, harmless target only)

1. Open the folder, double-click `FocusTarget.exe`. You should hear a quiet tone.
2. Open Terminal in the folder and run:
   `.\FocusProbe.exe --block FocusTarget.exe --minutes 3`
3. Click the FocusTarget window to bring it to the front.

**Pass if all are true:**

- [ ] S1 FocusTarget is minimized within about a second and a message explains why.
- [ ] S2 The tone goes silent while the probe runs (FocusTarget's log records `own_mute_state: true`).
- [ ] S3 Restoring FocusTarget from the taskbar minimizes it again.
- [ ] S4 After 3 minutes the probe says "Released", the tone is audible again (restore the window to check), and FocusTarget is still open.
- [ ] S5 Nothing from the probe is left running (Task Manager).

If any fail, stop and send the two newest files from `%LOCALAPPDATA%\FocusProbe\` (`probe-*.jsonl`, `target-*.jsonl`).

## Step 2 — Extended tests (only after the smoke test passes)

Run with `--minutes 10` unless noted. Record pass/fail and anything surprising.

| # | Scenario | Expected / what to record |
|---|---|---|
| W1 | Restricted window on a second monitor, not focused | Minimized by the 2 s sweep |
| W2 | `--terminate-new-launches`, then start FocusTarget while the probe runs | New copy closes within ~1 s; record `age_ms` in the log |
| W3 | Start FocusTarget **as administrator** (right-click → Run as administrator) | Expected **failure**: probe cannot minimize/mute it. Confirm the message says so |
| W4 | Close the probe's console window mid-session | Sound restored immediately |
| W5 | End `FocusProbe.exe` in Task Manager mid-session, then run `.\FocusProbe.exe --recover` | Sound restored by `--recover` |
| W5b | As W5, but close and reopen FocusTarget **before** running `--recover` | Is the new FocusTarget still muted (Windows remembering app mute)? Does `--recover` fix it? |
| W6 | Sleep the PC mid-session and wake it | Log shows `resume_detected`; restriction continues; ends on time |
| W7 | Sign out mid-session, sign back in | Record whether FocusTarget's sound is still muted (it should not be after `--recover`) |
| W8 | Real distracting app, e.g. `--block msedge.exe` or a game | Record behavior, including full-screen video and exclusive-full-screen games |
| W9 | A Store app (e.g. `--block CalculatorApp.exe`) | Identified through ApplicationFrameHost; record result |

**Removal:** delete the folder and `%LOCALAPPDATA%\FocusProbe\`. Nothing is installed.

**Known limitations of this probe** (being measured, not hidden): cannot act on apps
running as administrator; minimizing does not stop an app's background work;
muting is per audio session and new sessions are muted within ~2 s; it
identifies apps by file name only (the product will use stronger identity).
