# Device test checklist (short)

Do these in order. Stop at the first failure and send the logs.

| Step | Where | Time | What | Instructions |
|---|---|---|---|---|
| 1 | Windows PC | 5 min | **Smoke test**: harmless FocusTarget, 3-minute auto-expiry, checks S1–S5 | `focus-probe-windows-*/TESTER.md` §1 |
| 2 | Android phone | 5 min | **Smoke test**: harmless Focus Probe Target, 3-minute auto-expiry, checks S1–S5 | `focus-probe-android/TESTER.md` §1 |
| 3 | Windows PC | ~45 min | Extended W1–W9 (W3 and W8 use real apps; nothing is closed) | TESTER.md §2 |
| 4 | Android phone | ~60 min | Extended A1–A9, once per detector | TESTER.md §2 |
| 5 | Android phone | 2 × 60 min | Battery: baseline run, then detection run, same conditions | TESTER.md §3 |
| 6 | **Disposable VM only** | ~60 min | Policy experiments 10, 20, 30, 40, then 99-Cleanup | `vm-only-policy-experiments/` (each script refuses to run outside a VM) |

**Always record:** device model, OS version and build, CPU type (Windows),
manufacturer skin and Advanced Protection on/off (Android), and whether Smart
App Control is on (Windows).

**Logs** stay on the device. They list app/program names and timings, which
can reveal what you use. Review before sharing:

- Windows: `%LOCALAPPDATA%\FocusProbe\`
- Android: "Share log" in each app
- VM: `C:\FocusProbeVM\results.jsonl`
