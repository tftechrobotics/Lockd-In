# Device test checklist (short)

Do these in order. Stop at the first failure and send the logs.

| Step | Where | Time | What | Instructions |
|---|---|---|---|---|
| 1 | Windows PC | 5 min | **Smoke test**: harmless FocusTarget, 3-minute auto-expiry, checks S1–S5 | `focus-probe-windows-*/TESTER.md` Step 1 |
| 1b | Windows PC | 15 min | **Recovery tests** R1–R6, one at a time, harmless target only | TESTER.md Step 2 |
| 2 | Android phone | 5 min | **Smoke test**: harmless Focus Probe Target, 3-minute auto-expiry, checks S1–S5 | `focus-probe-android/TESTER.md` Step 1 |
| 3 | Windows PC | ~45 min | Extended W1–W7 (W6 uses a real app; nothing is closed) | TESTER.md Step 3 |
| 4 | Android phone | ~60 min | Extended A1–A11, once per detector | TESTER.md Step 2 |
| 5 | Android phone | 2 × 60 min | Battery: baseline run, then detection run, same conditions | TESTER.md Step 3 |
| 6 | **Disposable VM only** (separate download `focus-vm-policy-experiments-DISPOSABLE-VM-ONLY`) | ~90 min | Windows 11 Pro VM: 10, 20, 30, 40; then 10 and 20 again with `-SimulateCrash`, reboot, 98, 99. Windows 11 Home VM: 11. | Script headers (each refuses to run outside a VM) |

**Always record:** device model, OS version and build, CPU type (Windows),
manufacturer skin and Advanced Protection on/off (Android), and whether Smart
App Control is on (Windows).

**Logs** stay on the device. They list app/program names and timings, which
can reveal what you use. Review before sharing:

- Windows: `%LOCALAPPDATA%\FocusProbe\`
- Android: "Share log" in each app
- VM: `C:\FocusProbeVM\results.jsonl`
