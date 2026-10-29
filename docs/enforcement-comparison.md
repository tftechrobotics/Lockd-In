# Enforcement comparison and recommendation (revised 2026-10-29)

**Status: recommendation, not a decision.** Nothing here is measured on a
device yet. Each claim is tagged **Doc** (current official documentation,
linked), **Reported** (secondary source), **Known** (general platform
knowledge, not re-checked) or **Test** (only a probe or VM run can settle it).

**Product intent this is measured against (owner, 2026-10-29):** a serious,
non-VPN focus restriction app. Meaningful enforcement and resistance to
casual bypass must coexist with preserving work and safe recovery. An owner
with administrative control can eventually bypass it; that limit is stated
plainly, not used to redefine the product.

**What changed from the first version:** window minimization is no longer
the proposed Windows architecture, only the experimental baseline. Android
no longer treats an overlay as sufficient. Wording no longer calls a covered
app "paused". The ADRs list each change.

---

## Windows

### Options compared

| | A. Per-user agent | B. Privileged service + per-user agent | C. AppLocker | D. App Control for Business |
|---|---|---|---|---|
| **What it prevents** | Using restricted windows (minimized on sight, re-minimized on restore); hearing them (per-app mute). Can close a restricted app *just after launch* (Test W2). | Everything A does, plus near-immediate stop of restricted launches including **elevated** ones; restarts the agent if killed; can apply session-scoped browser and firewall settings | Restricted programs **cannot start** (exe, scripts, installers, packaged apps) | Restricted code **cannot load** (kernel enforcement, incl. DLLs) |
| **Privileges** | None | Admin to install; service runs as SYSTEM | Admin to configure; needs Application Identity service (Doc) | Admin; CiTool on Win 11 22H2+ (Doc) |
| **Already-running apps** | Minimized and muted; keep running | Same as A | Unaffected (Known) | Unaffected (Known) |
| **Ordinary bypass routes** | End it in Task Manager; run the app as administrator (UIPI blocks the agent — Test W3); another user account; uninstall | Stop/uninstall the service — needs an admin **UAC prompt** and is detected and reported; Safe Mode; another OS | Stop AppIDSvc or edit policy (admin) — "stopping this service prevents AppLocker policies from being enforced" (Doc); copied/renamed files defeat path rules; hash rules break on app updates | Remove policy (admin); path rules are bypassable by copying; hash rules break on updates |
| **Edition/version** | All Windows 11 | All Windows 11 | All editions on Win 10 2004+ and all Win 11 after KB5024351 (Doc) | Policies effective on all editions, but the PowerShell authoring cmdlets are **not on Home** (Doc), so building a policy on a Home PC needs another route — likely impractical |
| **Policy conflicts** | None | Browser policies show "managed by your organization" and may clash with a work/school-managed PC | Replaces/merges local AppLocker policy; any exe rule switches the whole category to default-deny unless allow rules exist — easy to block unrelated apps | Interplay with Smart App Control unknown (Test); a bad policy can stop the PC booting (Doc) |
| **Install / uninstall** | Per-user, nothing system-wide | Service install and removal; must also remove anything it applied | Must restore prior policy | Must remove the policy; on Win 11 2024 update+ unsigned policies can be removed without restart via CiTool, otherwise a restart is needed (Doc) |
| **Crash-safe recovery** | Fails **open** (restriction stops). Mute ledger restores sound on exit, next start or `--recover` | Service itself fails open; SCM restarts it. Anything it applied (browser/firewall) persists until removed → needs startup reconciliation and expiry | Fails **closed**: rules persist after a crash until removed (a lock on those apps until an admin fixes it) | Fails **closed**, and removal may need a reboot on older builds |

Other approaches considered:

| Approach | Verdict |
|---|---|
| Per-app outbound firewall rule (service-applied, session-scoped) | **Candidate complement**: cuts network for unapproved browsers/apps without a VPN or interception. Test in VM (`40-Firewall.ps1`). |
| Browser policies: disable private mode, force-install the Focus extension so it can't be disabled | **Candidate**, service-applied. Whether Chrome honors these on an unmanaged PC is not confirmed (Test, `30-BrowserPolicies.ps1`). |
| Suspending a restricted process (true "pause") | Not now: no documented API for whole-process suspension of desktop apps; risk of hangs. Revisit only with evidence. |
| Kernel driver / minifilter | Rejected for v1: signing requirements and risk out of proportion. |
| Assigned Access (kiosk), Microsoft Family Safety | Rejected: kiosk replaces the desktop; Family Safety needs a child account. |
| IFEO debugger hijack, hosts/DNS tricks, VPN, root certificates | Rejected: weakens OS security or excluded by the owner. |

### Recommendation (provisional)

**B: a privileged Focus service paired with the normal per-user Focus app**,
in two layers:

1. **Per-user agent** (always): windows, audio, explanations, tray, UI.
2. **Service** (installed by default, admin consent at install):
   - real-time launch gating, including elevated apps;
   - supervising the agent;
   - session-scoped browser policy and firewall rules, removed at break and end, and reconciled on every start so a crash can't leave them behind;
   - tamper events reported to paired devices.

The service is not trusted as unbreakable. An administrator can stop it
after a UAC prompt. The design goal is that this cannot happen casually and
never happens silently.

**AppLocker and App Control are not recommended for v1.** They block
earlier and survive a killed service, but:
- they fail closed after a crash;
- they can't be authored on Home (App Control);
- they risk conflicts with existing policy;
- AppLocker can be switched off by stopping a service.

They become a possible "strict mode" only if the VM experiments meet the
go criteria in `docs/milestones.md`.

**Remaining limits, stated plainly:**
- an administrator can uninstall or stop Focus;
- Safe Mode and other accounts are outside its reach;
- already-running apps are minimized and muted, not paused, so their background work continues;
- launch gating stops an app shortly after it starts rather than preventing the start (how short is Test W2).

**Already-running apps at session start** (proposal, owner decision U2):
before a session starts, Focus lists open unapproved apps. Each can be
closed by the user, with the app's own save prompts, or left minimized and
muted. Focus never closes them itself.

---

## Android

### Detection

| | Usage access (UsageEvents polling) | AccessibilityService |
|---|---|---|
| Mechanism | Foreground service polls `ACTIVITY_RESUMED` events | Window-change events pushed by the system |
| Latency | Poll interval + event delivery (Test A1) | Event-driven, expected lower (Test A1) |
| Permissions | Usage access (special access) + foreground service `specialUse`, which needs a Play declaration with video and is reviewed (Doc) | User enables in Settings; Play requires an in-app prominent disclosure with affirmative consent plus a declaration and video; deceptive use risks suspension (Doc) |
| Advanced Protection (Android 17) | Not affected as far as reported | **Unavailable** to non-accessibility-tool apps (Reported) |
| PiP / split screen | A PiP window is not "resumed", so it is not reported (Known; Test A3) | Minimal config sees only window-state changes; seeing PiP would need more access (Known) |
| Background survival | Foreground service; OEM battery managers may still kill it (Test A5–A6) | System-bound service (Test) |

### Covering the app

| Method | What really happens | Verdict |
|---|---|---|
| Overlay only | App stays **resumed** underneath — video and audio continue. Apps can hide overlays with `HIDE_OVERLAY_WINDOWS` (Android 12+) (Doc). | Not acceptable as the primary method |
| **Block screen** (Focus activity brought to front) | App moves to the background: its screen is no longer visible, and it is not closed. Many video apps stop playback when they leave the screen; apps with their own media service keep playing. Allowed because "display over other apps" is a documented background-launch exception (Doc). | **Recommended** |
| Home screen | Same lifecycle effect, no explanation | Fallback |

**Sound:** covering doesn't stop playback. Two documented levers:
- **Audio focus.** The visible block screen can take audio focus, which asks well-behaved players to stop. Android 17 blocks focus requests from background apps (Doc), so the request must come from the visible screen.
- **Background audio hardening.** On Android 17, apps without a visible activity or a foreground service are silenced (Doc). Music apps that run a media foreground service keep playing.

Focus therefore reports what is actually happening ("Sound is still playing
from another app") and never says "paused".

### Recommendation (provisional)

- **Default:** usage access + block screen + audio-focus request + truthful
  audio status. This path works under Advanced Protection, so users are
  never asked to turn it off.
- **Accessibility:** an optional faster detector, offered only if probe data
  shows a real gain (latency or missed detections) that justifies the Play
  review risk.
- **Does the fallback deliver the product promise?** Undetermined until
  A1–A6 are measured. The go/no-go thresholds are in `docs/milestones.md`.
  If usage access misses too much, the honest options are:
  - require accessibility, which excludes Advanced Protection users; or
  - narrow the promise for those users.

  That would be an owner decision.

---

## Ordinary bypass gaps: defined behavior (proposal)

| Gap | Behavior |
|---|---|
| **Unsupported browser** (allowlist session) | Not approved = restricted like any other app. A browser can be approved "with website restriction" only if the Focus extension is installed and verified. Approving an unsupported browser requires an explicit "allow without website restriction" choice, shown on Home and to paired devices. Service mode can additionally block its network (Test). |
| **Extension disabled or removed mid-session** | Heartbeat lost for > 10 s (target) → that browser becomes restricted until the heartbeat returns; status "Degraded: website restriction off in Chrome". |
| **Private browsing** | Approval requires "Allow in Incognito/InPrivate" on the extension (checked each session start). Service mode: disable private browsing by policy during focus, if the VM test shows it works on unmanaged PCs. |
| **Elevated Windows app** | Agent-only mode: reported as "Can't restrict apps running as administrator". Service mode: launch-gated. |
| **Agent stopped** | Service relaunches it. Without the service: paired devices see **Unknown** after the freshness window, then the next start records the gap. |
| **Permission lost** (Android) | Detected within the self-check interval (target ≤ 10 s) → **Degraded**, local notification, reported to peers. |
| **Device offline** | Keeps executing the schedule it already accepted. Peers show **Unknown**, never "protected" or "stopped". Reconciles on reconnect. |
| **Clock changed by the user** | Proposal for M3/M4: within a running session, phase ends are tracked against the monotonic clock as well as wall time. A wall-clock jump is logged and does not shorten the session. Not yet implemented. |
| **Notification shortcuts** (inline reply from a restricted app's notification) | Gap: opening the app is blocked, but replying from the notification shade is not. Closing it needs notification access — owner decision U10. |

Scheduled breaks, delayed overrides, immediate emergency recovery and
enforcement failure are separate states in `focus-core` (implemented and
tested; see `core/src/session.rs` and `core/src/protection.rs`).

## Sources
- [AppLocker: removal of edition checks (KB5024351)](https://support.microsoft.com/en-us/topic/kb5024351-removal-of-windows-edition-checks-for-applocker-e3a763c9-6a3e-4d9c-8623-0ffe69046470)
- [Configure the Application Identity service](https://learn.microsoft.com/id-id/windows/security/application-security/application-control/app-control-for-business/applocker/configure-the-application-identity-service)
- [App Control and AppLocker feature availability](https://learn.microsoft.com/en-us/windows/security/application-security/application-control/app-control-for-business/feature-availability)
- [CiTool commands](https://learn.microsoft.com/windows/security/threat-protection/windows-defender-application-control/operations/citool-commands)
- [Remove App Control for Business policies](https://learn.microsoft.com/en-us/windows/security/application-security/application-control/app-control-for-business/deployment/disable-appcontrol-policies)
- [Chrome extension policies (Windows)](https://support.google.com/chrome/a/answer/7532015?hl=en)
- [ISimpleAudioVolume::SetMute](https://learn.microsoft.com/en-us/windows/win32/api/audioclient/nf-audioclient-isimpleaudiovolume-setmute)
- [Android: restrictions on starting activities from the background](https://developer.android.com/guide/components/activities/background-starts)
- [Android 17: background audio hardening](https://developer.android.com/about/versions/17/changes/bg-audio)
- [Android: secure sensitive activities (HIDE_OVERLAY_WINDOWS)](https://developer.android.com/security/fraud-prevention/activities)
- [Android 15 behavior changes](https://developer.android.com/about/versions/15/behavior-changes-15)
- [Play: AccessibilityService API policy](https://support.google.com/googleplay/android-developer/answer/10964491)
- [Play: foreground service requirements](https://support.google.com/googleplay/android-developer/answer/13392821)
- [Android 17 Advanced Protection and accessibility (The Hacker News)](https://thehackernews.com/2026/10/android-17-advanced-protection-locks.html)
