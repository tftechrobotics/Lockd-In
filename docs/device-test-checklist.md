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

## Battery measurement (release readiness; repeat after study audio)

These need no computer. Charge the phone above 80% and close other heavy apps first.

1. **Baseline:** start a 60-minute session with blocking (Accessibility on) and no sound. Use the phone normally for a few minutes, then leave it locked for the rest. Afterwards, open Android Settings › Battery › Battery usage, choose "Since last full charge" or "Past 24 hours", find Lockd In and note its percentage. Note the phone's total battery drop too.
2. **With ambient sound:** same session, but with a profile that plays Rain at the default level, screen locked. Note both numbers again.
3. **With a playlist:** same, with a Spotify/YouTube Music playlist profile. Lockd In's share should match the baseline; the music app's share is that app's own.

Report the three Lockd In percentages and total drops. Targets: blocking alone ≤ 2% per hour; ambient sound adds ≤ 3% per hour on the S24+.


## New in the 2026-11-02 build (roadmap steps 2–7)

18. **History:** finish a session and check it on the History tab and in the week card. Delete one entry. Export a CSV from Settings › History.
19. **Presets:** in New session, set up a session and tap "Save as preset". Start it from Home in one tap. Add steps in Profiles › Presets, then check they show under Details.
20. **Sound:** in Profiles › a profile › Sound, try Rain with Preview. Then:
    - Start focus and check that the sound plays with the screen locked, pauses at the break and stops at the end.
    - Paste a Spotify or YouTube Music playlist link and start focus. The app opens, then Lockd In comes back. Play/pause and skip control it.
21. **Calendar:** on Schedule, tap "Use my calendar". The free times should match your Google Calendar. Tap one and check the editor is filled in.
22. **Widget:** add the Lockd In widget to the home screen. It should show the next session, or a live countdown during focus.
23. **Look:** in Settings › Look, choose a background and turn on quotes. The lock and text stay clear. Try your own photo.
24. **Coaching:**
    - Set "My own words" and add a line.
    - Add a message for one app and open that app during focus.
    - Time a few questions.
    - Set check-ins to 10 min and leave the phone untouched during focus. One silent "Still working?" should appear.
25. **Places:** add a place where you are, set it to remind, and allow location "All the time". Leave and come back during its hours. A reminder should come once.
26. **Battery:** follow the battery protocol above.

27. **Music control (R47).** For each music app you use (Spotify, YouTube Music, Apple Music, SoundCloud):
    - Save a playlist and choose it in a profile.
    - Without music control: start focus. The lock animation plays and the app doesn't switch. Note what plays.
    - Allow music control (Sound tab or session). Start focus with the music app closed, then again with it open. Does that exact playlist start? Note any messages under the controls.
    - In a session, use Change to switch playlists. Check that "Open in …" appears only when that app isn't blocked.
