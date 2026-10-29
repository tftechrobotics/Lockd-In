# Lockd In probe for Windows — tester instructions

**What this is:** an experiment, not the Lockd In app. It checks whether an
ordinary Windows program, without administrator rights, can keep a
restricted app out of the way. It **minimizes** windows and **mutes** sound.
The app keeps running underneath: nothing is paused or closed. The probe stops by itself.

**In the folder**

| File | Purpose |
|---|---|
| `LockdInProbe.exe` | The probe (console window). Starts a small hidden helper copy of itself, the *guardian*, that restores sound if the probe crashes. |
| `LockdInTarget.exe` | A harmless test app: a small window and a quiet tone. Holds no data. |

The policy experiments are a **separate download** for a disposable virtual
machine only. Never run them on this PC.

**Before you start**

- Windows 11 (any edition), x64 or ARM64. Use the matching download.
- **Not code-signed yet.** SmartScreen may warn ("More info → Run anyway").
  If **Smart App Control** is On (Settings → Privacy & security → Windows
  Security → App & browser control), Windows may refuse to run these files.
  Record that and stop; don't turn Smart App Control off for this.
- Logs go to `%LOCALAPPDATA%\LockdInProbe\` (`probe-*`, `guardian-*`,
  `target-*`). They contain program names and timings, which can reveal what
  you use. Nothing is uploaded; review before sharing.

## Step 1 — Smoke test (5 minutes, harmless target only)

1. Double-click `LockdInTarget.exe`. You should hear a quiet tone.
2. Open Terminal in the folder and run `.\LockdInProbe.exe --block LockdInTarget.exe --minutes 3`
3. Click the LockdInTarget window to bring it to the front.

**Pass if all are true:**

- [ ] S1 LockdInTarget is minimized within about a second, and a message explains it is restricted, still open and may still be running.
- [ ] S2 The tone goes silent while the probe runs (LockdInTarget's log shows `own_mute_state` → `true`).
- [ ] S3 Restoring LockdInTarget from the taskbar minimizes it again.
- [ ] S4 After 3 minutes the probe prints "Released", the tone is audible again (restore the window to hear it), and LockdInTarget is still open.
- [ ] S5 Nothing from the probe is left running: no `LockdInProbe.exe` in Task Manager, including the guardian.

If anything fails, stop and send the newest `probe-*`, `guardian-*` and `target-*` logs.

## Step 2 — Recovery tests (one at a time, harmless target only)

Start each with LockdInTarget's tone playing and
`.\LockdInProbe.exe --block LockdInTarget.exe --minutes 10`. Wait until the tone
is muted, then do the action.

| # | Action | What should restore sound | Expected |
|---|---|---|---|
| R1 | Press Ctrl+C | The probe itself | Sound back immediately |
| R2 | Close the probe's console window | The probe itself | Sound back immediately |
| R3 | Task Manager → Details → right-click the **interactive** `LockdInProbe.exe` (the one with a console window) → End task | **The guardian** | Sound back within ~2 s; `guardian-*` log shows `guardian_restored` |
| R4 | In an admin-free terminal: `taskkill /F /T /IM LockdInProbe.exe` (kills probe **and** guardian) | Nothing, immediately | Sound **stays muted**. This is the honest limit. Then run `.\LockdInProbe.exe --recover`: sound back |
| R5 | Like R4, but close and reopen LockdInTarget **before** `--recover` | Nothing | Record whether the new LockdInTarget starts muted (does Windows remember the mute?). Then `--recover` |
| R6 | Restart Windows during a session | Nothing | After sign-in, start LockdInTarget: muted or not? Then `--recover` |

## Step 3 — Extended tests (only after Steps 1–2 pass)

Use `--minutes 10` unless noted.

| # | Scenario | Expected / record |
|---|---|---|
| W1 | Restricted window on a second monitor, not focused | Minimized by the 2 s sweep |
| W2 | `--test-close-target-after-launch`, then start LockdInTarget during the probe | **Measurement only:** the probe detects the new LockdInTarget and closes it; record `age_ms`. This is detection followed by closing, not prevention; the app did start. Lockd In will never do this to real apps. |
| W3 | Run LockdInTarget **as administrator** | Expected **failure**: the probe can't minimize or mute it; the message should say so |
| W4 | Sleep the PC mid-session and wake it | Log shows `resume_detected`; restriction continues and ends on time |
| W5 | Sign out mid-session and back in | Is LockdInTarget muted when started again? Then `--recover` |
| W6 | A real distracting app (e.g. `--block msedge.exe`) | Record behavior, including full-screen video. Nothing is closed. |
| W7 | A Store app (e.g. `--block CalculatorApp.exe`) | Identified through ApplicationFrameHost; record result |

**Removal:** delete the folder and `%LOCALAPPDATA%\LockdInProbe\`. Run
`.\LockdInProbe.exe --recover` first if any app is still muted.

**Known limitations, being measured rather than hidden:**
- Cannot act on apps running as administrator.
- Minimizing and muting do not stop an app's background work.
- New audio sessions are muted within about 2 s.
- If probe and guardian are both killed, sound stays muted until the next run or `--recover`.
- Recovery may also unmute that app if you muted it yourself.
- Apps are identified by file name only; the product will use stronger identity.
