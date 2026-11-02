# Device test checklist (short)

Do these in order. Stop at the first failure and send the logs.

| Step | Where | Time | What | Instructions |
|---|---|---|---|---|
| 1 | Windows PC | 5 min | **Smoke test**: harmless LockdInTarget, 3-minute auto-expiry, checks S1–S5 | `lockdin-probe-windows-*/TESTER.md` Step 1 |
| 1b | Windows PC | 15 min | **Recovery tests** R1–R6, one at a time, harmless target only | TESTER.md Step 2 |
| 2 | Android phone | 5 min | **Smoke test**: harmless Lockd In Probe Target, 3-minute auto-expiry, checks S1–S5 | `lockdin-probe-android/TESTER.md` Step 1 |
| 3 | Windows PC | ~45 min | Extended W1–W7 (W6 uses a real app; nothing is closed) | TESTER.md Step 3 |
| 4 | Android phone | ~60 min | Extended A1–A11, once per detector | TESTER.md Step 2 |
| 5 | Android phone | 2 × 60 min | Battery: baseline run, then detection run, same conditions | TESTER.md Step 3 |
| 6 | **Disposable VM only** (separate download `lockdin-vm-policy-experiments-DISPOSABLE-VM-ONLY`) | ~90 min | Windows 11 Pro VM: 10, 20, 30, 40; then 10 and 20 again with `-SimulateCrash`, reboot, 98, 99. Windows 11 Home VM: 11. | Script headers (each refuses to run outside a VM) |

**Always record:** device model, OS version and build, CPU type (Windows),
manufacturer skin and Advanced Protection on/off (Android), and whether Smart
App Control is on (Windows).

**Logs** stay on the device. They list app/program names and timings, which
can reveal what you use. Review before sharing:

- Windows: `%LOCALAPPDATA%\LockdInProbe\`
- Android: "Share log" in each app
- VM: `C:\LockdInProbeVM\results.jsonl`

## Pending phone checks (2026-11-02, roadmap step 1)

Run these on the Galaxy S24+ with the latest test APK. Answer each with pass or fail plus what you saw. A screenshot helps for any failure.

**Blocking**
1. With Accessibility on, open a blocked app three times quickly. It should be covered every time.
2. Block youtube.com, start focus, and open it in Chrome from a link (e.g. in Messages). It should be covered.
3. With "Allow only these" websites and one site added, the allowed site opens and any other site is covered.
4. Open a browser that isn't in the supported list (if you have one) during allow-only focus. It should be blocked.
5. During focus, swipe up on the home screen (Wallet card). The home screen should keep working.
6. Phone, camera, clock and Settings open normally during focus.

**Start sequence**
7. Start focus. The dial clicks round with the hands (small hand counter-clockwise), the hands fade into the time, and the shackle drops after "Blocking active" appears. Haptic clicks are felt.
8. Open a blocked app, tap "Return to work", end the session, and start another. The sequence plays again. Then try "Same again" on the summary screen.
9. Leaving and reopening the app, or rotating the phone, doesn't replay it.
10. A profile with nothing blocked ("Start timer"): the dial turns, but the shackle stays open.

**Milestone A**
11. Plan a session 15 minutes ahead with a 10-minute reminder. The reminder arrives and its buttons work.
12. Plan an automatic session 3 minutes ahead and close the app. It starts on time, blocks, and doesn't open the app.
13. "Skip this time" moves Home's next session to the following occurrence.
14. Finish a session. The "Last session" card stays on Home until dismissed.

**Settings and look**
15. Settings › Notifications: "Allow notifications" off hides the switches and stops reminders. Turning it on brings them back.
16. The new padlock-with-clock icon is on the home screen and in the app drawer. With themed icons on, it turns one colour.
17. The Profiles list is readable, and app logos show in the editor.
