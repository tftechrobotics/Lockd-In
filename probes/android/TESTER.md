# Focus probe for Android — tester instructions

**What this is:** an experiment, not the Focus app. It checks whether this
phone can detect a chosen app and block it, and what really happens to that
app (does it keep running? keep playing sound?). It never uninstalls or
force-stops apps, and every session ends by itself (max 60 minutes).

**What's in the folder**

| File | Purpose |
|---|---|
| `focus-probe.apk` | The probe |
| `focus-probe-target.apk` | "Focus Probe Target", a harmless test app (quiet tone, picture-in-picture button). Holds no data. |

**Install**

1. Copy both APKs to the phone (or use `adb install focus-probe.apk` and `adb install focus-probe-target.apk`).
2. Allow installing from your file manager when Android asks. If **Advanced Protection** is on, Android may block installing these at all — record that and stop; do not turn Advanced Protection off for this test.
3. These are debug builds signed by the CI server. Each new download has a different signature: uninstall the old version before installing a new one.

**Privacy:** the probe's log records the package names of apps you open during a
session, which can reveal what you use. It stays on the phone. "Share log"
sends it only where you choose; read it first.

## Step 1 — Smoke test (5 minutes, harmless target only)

1. Open **Focus Probe**. Grant: *Usage access*, *Display over other apps*, *Notifications* (buttons at the top; ✓ marks update when you return).
2. Tap **Run smoke test**. A "Focus probe session" notification appears.
3. Open **Focus Probe Target** from the launcher.

**Pass if all are true:**

- [ ] S1 Within about a second, a block screen says the target is blocked until a time 3 minutes away.
- [ ] S2 "Go to home screen" works; the target is still in Recents with its screen intact.
- [ ] S3 Opening the target from Recents shows the block screen again.
- [ ] S4 After 3 minutes the notification disappears and the target opens normally.
- [ ] S5 Tapping "End now" in the notification (in a second run) releases immediately.

If any fail, stop and use **Share log** in both apps.

## Step 2 — Extended tests (only after the smoke test passes)

For each row, use a custom session blocking `app.focus.probetarget` for 10 minutes.
Run the detection rows twice: **Usage access** and **Accessibility events**.

| # | Scenario | Record |
|---|---|---|
| A1 | Open target 10 times from launcher, Recents and a notification shortcut | Missed detections; latency (`detect_latency_ms`) and target `exposure_ms` |
| A2 | Target playing its tone, then blocked — with each "When a blocked app opens" option | Does the tone keep playing? (`audio_after_cover`) Repeat with "Ask other apps to pause sound" checked |
| A3 | Target in picture-in-picture before/while blocked | Does the PiP window stay visible/audible? |
| A4 | Target in split screen with another app | What gets covered? |
| A5 | Screen off 10 min mid-session, then on | Still blocking; `tick_gap` logged |
| A6 | Swipe Focus Probe away from Recents | `task_removed`; still blocking? `service_restarted_by_system`? |
| A7 | Revoke usage access, then overlay, mid-session | `capabilities_changed` within ~5 s |
| A8 | Reboot mid-session | `boot_completed`, `resume_after_boot` started? |
| A9 | Real app (e.g. TikTok `com.zhiliaoapp.musically`, YouTube `com.google.android.youtube`) | Same as A1–A3 |

## Step 3 — Battery (needs two runs; a single percentage drop means little)

Do both on the same day, same brightness, similar use, phone unplugged, starting above 80%:

1. **Baseline:** custom session, detector **None — battery baseline run**, 60 minutes.
2. **Detection:** custom session, detector **Usage access**, 500 ms, 60 minutes.

Then share the log. The comparison uses `charge_counter_uah` where the phone reports it, else percent. Also screenshot Settings → Battery → app usage for Focus Probe after each run.

**Record for every test:** phone model, Android version, manufacturer skin, whether Advanced Protection is on, and whether Focus Probe's battery setting is "Unrestricted" (don't change it unless a test says so).

**Removal:** uninstall both apps. Nothing else is left behind.
