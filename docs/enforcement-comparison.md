# Enforcement comparison and recommendation (revised 2026-10-29, second review)

**Status: recommendation, not a decision. Nothing here is measured on a
device yet.** Claims are tagged:
- **Doc**: current official documentation, linked;
- **Reported**: secondary source;
- **Known**: general platform knowledge, not re-checked;
- **Test**: only a device, VM or probe run can settle it.

**Measured against (owner requirements R1–R2):** a serious, non-VPN focus
restriction app. Meaningful enforcement and resistance to casual bypass must
coexist with preserving work and safe recovery. An administrator-owner can
eventually bypass it; that limit is stated plainly, and it does not redefine
the product.

## Vocabulary (used consistently in all docs)

| Term | Meaning |
|---|---|
| **Launch prevention** | The OS refuses to start the program. No process exists, so no work can be lost. |
| **Detect-and-terminate** | The program starts; Focus notices it and kills it. **Not prevention.** A process that has started can already hold valuable state: restored tabs, a reopened document, an unsaved draft, an in-progress download or sync. **Focus never does this to real apps** (requirement R12). The Windows probe does it only to the harmless FocusTarget, to measure detection timing. |
| **Window restriction** | Focus minimizes or covers the program's windows. The program keeps running. |
| **Audio restriction** | Focus mutes the program's audio sessions (Windows) or asks players to stop via audio focus (Android). Playback may continue silently or, on Android, keep playing in apps that ignore focus. |
| **Paused** | Not used. Nothing above pauses an app (requirement R3). |

---

## Windows

### Options compared

| | A. Per-user agent | B. Privileged service + agent | C. AppLocker | D. App Control for Business |
|---|---|---|---|---|
| **Kind of control** | Window + audio restriction; launch *detection* | A, plus privileged operations (below) | **Launch prevention** | **Launch prevention** (kernel code integrity) |
| **Privileges** | None | Admin to install; service runs as SYSTEM | Admin; Application Identity service must run (Doc) | Admin; `CiTool` (Win 11 22H2+) (Doc) |
| **Already-running apps** | Minimized and muted; keep running | Same | Unaffected (Known) | Unaffected (Known) |
| **Scope** | Current user | Machine (service) + current user | Per user or group (rules carry a user/group SID) | **Whole machine**: affects every account on the PC during a session |
| **Ordinary bypass routes** | End task; run app as admin (UIPI, Test W3); another account; uninstall | Stop/uninstall the service: admin + UAC prompt; Safe Mode; another OS | Admin stops AppIDSvc ("stopping this service prevents AppLocker policies from being enforced", Doc) or edits policy; renamed copies defeat path rules; hash rules break on app updates | Admin removes the policy; hash rules break on updates (publisher rules survive them for signed apps) |
| **Edition support** | All Win 11 | All Win 11 | All editions on Win 10 2004+ and all Win 11 after KB5024351 (Doc) | Enforcement on all editions (Doc). Microsoft's authoring cmdlets aren't on Home, and the MDM ApplicationControl CSP doesn't list Home (Doc). See "App Control on Windows Home" below. |
| **Policy conflicts** | None | Browser policies show "managed by your organization"; may clash with work/school management | Merges with local policy; any EXE rule makes that category default-deny unless allow rules exist | Smart App Control interplay unknown (Test); a policy blocking boot-critical drivers can stop boot (Doc). Focus would only deny user-mode apps on an allow-all base. |
| **Removal** | Nothing system-wide | Service removal + reconciliation | Remove only Focus-owned rules | `CiTool --remove-policy`: rebootless for unsigned policies on Win 11 2024 update and later (Doc); every supported Win 11 release (25H2, 26H2) qualifies. Never use signed policies. |
| **After a crash** | Fails open (restriction stops) | Service fails open; anything it applied persists until reconciled | **Fails closed**: rules persist until removed | **Fails closed**: policy persists, including across reboot (Test 98) |

### App Control on Windows Home (corrected)

Missing PowerShell authoring cmdlets do not mean enforcement is unsupported.
The pieces are:

- **Enforcement:** effective on all editions (Doc).
- **Deployment:** `CiTool` ships in Windows 11 22H2+ images (Doc); whether it is present and works on Home is **Test 11**. The MDM CSP route doesn't list Home (Doc).
- **Authoring:** the policy is a binary compiled from XML. Off Home, Microsoft's `ConvertFrom-CIPolicy` does this. An open-source, **MIT-licensed** implementation, AppControl Manager (HotCakeX/Harden-Windows-Security), contains its own XML↔binary converter in C#. We confirmed this by reading its source. Focus could therefore compile policies itself. The format is undocumented by Microsoft, so this adds a maintenance and correctness risk, mitigated by round-trip tests against `CiTool` on every supported Windows build.

Verdict: **not ruled out on Home**. It depends on Test 11 plus a correctness
spike of in-app policy compilation.

### Recommendation (provisional)

**B: a privileged service paired with the normal per-user app, with
prevention when it can be done safely, and work-preserving restriction
otherwise.** Nothing is ever terminated.

1. **Prevention (if VM go criteria pass):** for a session, the service
   deploys an unsigned App Control policy:
   - an allow-all base plus deny rules for the user's **installed, non-approved apps**;
   - publisher rules for signed apps, hash rules otherwise;
   - removed at each break and at session end.

   AppLocker is the alternative if App Control fails its tests. It has the
   advantage of per-user scope, but the drawback that an admin can stop its
   service.
2. **Work-preserving restriction (always):** the agent minimizes and mutes
   anything not prevented:
   - apps already open when the session starts;
   - portable or newly installed apps;
   - apps run as administrator, which the service handles because the agent can't;
   - the whole session, if prevention is unavailable.
3. **Before a session starts:** Focus lists open non-approved apps. Each can be
   closed by the user, with the app's own save prompts, or left minimized and
   muted. Focus never closes them.

**Limitations of this approach, stated plainly:**
- Prevention covers only apps Focus knows are installed. New, portable or renamed copies fall back to window and audio restriction.
- App Control is machine-wide: other accounts on the same PC are affected during a session (owner decision U3).
- An administrator can remove the policy or stop the service.
- Already-running apps are minimized and muted, never paused, and their background work continues.
- On Home, prevention depends on Focus compiling policies itself.

### Recovery: what restores audio and temporary policies

Audio means mutes Focus applied. Temporary policies means App Control or
AppLocker rules, browser policies and firewall rules applied for a session.

| Situation | Audio | Temporary policies | Tested? |
|---|---|---|---|
| Normal end, break, Ctrl+C, window closed | Agent restores immediately | Service removes at the boundary | Audio: probe R1, R2. Policies: VM 10/20/30/40 normal path |
| **Agent crashes or is killed**; service running | **Guardian process** (probe today) / service (product) restores within seconds | Unaffected; service still owns them | Probe R3 |
| Agent **and** its guardian killed; no service | **Not immediate**: restored on next start or `--recover` | n/a | Probe R4, R5 |
| **Service crashes** | Agent unaffected | Service Control Manager restarts the service (product, M3), which reconciles: expired → removed | Not yet; M3 |
| Service stopped by an admin | Agent restores on its own release | **Stay in force** until something removes them → fail-closed | VM 98 measures persistence |
| **Reboot** mid-session | Process memory gone. Whether Windows remembers per-app mute is **unknown** | Policies persist (Test 98). Product plan: boot-time reconciliation by the service, plus a separate Task Scheduler cleanup task registered with each policy, triggered at session end and at startup | Probe R6; VM 98 |
| **Neither component ever restarts** (service disabled/removed, task deleted) | Muted until the user unmutes in Volume Mixer or runs recovery | Policies remain: apps stay blocked. Recovery = Focus uninstaller, `CiTool --remove-policy`, or Safe Mode. This is the fail-closed risk that the go criteria must bound | VM `-SimulateCrash` + 98 + 99 |

### Tamper reporting (corrected)

A service that is abruptly stopped, crashes or loses power **cannot report its
own stop**. The design instead relies on:
- **authenticated heartbeats** from each device, signed with its device key;
- the peer marking a device **Unknown** when heartbeats expire (implemented in `focus-core`: no fresh report → Unknown);
- the device reporting the gap and its cause, if it can tell, **after** it restarts.

A clean, orderly stop may send a final message, as a courtesy only. Nothing
depends on it.

### Other approaches considered

| Approach | Verdict |
|---|---|
| Per-app outbound firewall rule (service-applied, session-scoped) | Candidate complement for unapproved browsers/apps; VM test 40 |
| Browser policies: disable private mode; force-install the Focus extension | Candidate; whether Chrome/Edge honor them on an unmanaged PC is VM test 30 |
| Process suspension (a true pause) | Not now: no documented whole-process API for desktop apps; hang risk |
| Kernel driver (process-creation callback could refuse launches) | Rejected for v1: signing burden and risk out of proportion |
| Assigned Access (kiosk), Microsoft Family Safety | Rejected: kiosk replaces the desktop; Family Safety needs a child account |
| IFEO debugger hijack, hosts/DNS tricks, VPN, root certificates | Rejected: weakens OS security or excluded by the owner |

---

## Android

### Exact permission dependencies (probe and proposed product)

| Capability | Requires | Notes |
|---|---|---|
| Detect foreground app (default) | **Usage access** (`PACKAGE_USAGE_STATS`, special access) | Polled in a foreground service |
| Keep running in background | Foreground service type `specialUse` + `FOREGROUND_SERVICE_SPECIAL_USE`; Play declaration with video (Doc) | Reviewed by Play; approval not guaranteed |
| **Bring the block screen to the front** | **`SYSTEM_ALERT_WINDOW`** ("Display over other apps"). Holding it is the documented background-activity-launch exception (Doc) | **The block-screen approach depends on it even though it draws no overlay.** Without it, the launch is silently refused (probe A11) |
| Overlay-only cover (not recommended) | `SYSTEM_ALERT_WINDOW` | App stays resumed underneath; apps can hide overlays (`HIDE_OVERLAY_WINDOWS`, Doc) |
| Ask players to stop | Audio focus, requested **while the block screen is visible**: Android 17 refuses focus requests from background apps (Doc) | Well-behaved players stop; others don't |
| Visible session + "End now" | `POST_NOTIFICATIONS` (Android 13+) | |
| Optional faster detector | User-enabled AccessibilityService; Play prominent disclosure + declaration + video (Doc) | Unavailable under Advanced Protection on Android 17 (Reported) |
| Know whether Advanced Protection is on | `QUERY_ADVANCED_PROTECTION_MODE` + `AdvancedProtectionManager` (Doc) | Read-only |

### Advanced Protection: what is known vs. assumed

- **Known (Doc):** blocks sideloading. The probe APKs may not install on such a phone; Play distribution would be needed.
- **Reported:** restricts AccessibilityService to verified accessibility tools on Android 17.
- **Not documented either way:** effects on usage access, "Display over other apps", or background activity launches. The statement that the default path "works under Advanced Protection" is a **hypothesis**, to be verified by probe row A10 on a phone that already has it enabled.
- Users are never asked to turn Advanced Protection off.

### Detection

| | Usage access (polling) | AccessibilityService |
|---|---|---|
| Latency | Poll interval + event delivery (Test A1) | Event-driven (Test A1) |
| Picture-in-picture | Not reported (Known; Test A3) | Minimal config doesn't see it either |
| Survival | Foreground service; OEM battery managers may still kill it (Test A5–A6) | System-bound (Test) |

### Covering the app

| Method | What really happens | Verdict |
|---|---|---|
| **Block screen** | App leaves the screen; not closed. Many video apps stop when they lose the screen; apps with their own media service keep playing. Requires `SYSTEM_ALERT_WINDOW` as above. | **Recommended** |
| Overlay only | App stays resumed and audible underneath; can be hidden by the app | Measured, not recommended |
| Home screen | Same lifecycle effect, no explanation | Fallback |

**Recommendation (provisional):** usage access + block screen + audio focus
from the visible block screen + truthful audio status. Accessibility is an
optional faster detector, offered only if probe data shows a meaningful gain.
Whether this delivers the product promise is undetermined until A1–A11 are
measured.

---

## Ordinary bypass gaps: defined behavior (proposal)

| Gap | Behavior |
|---|---|
| **Unsupported browser** (allowlist session) | Not approved → restricted like any app (prevented where possible, otherwise minimized/muted). Approving one requires an explicit "allow without website restriction" choice, shown on Home and to paired devices. Service mode can also block its network (VM 40). |
| **Extension disabled/removed mid-session** | Heartbeat lost > 10 s (target) → that browser becomes restricted until it returns; status "Degraded: website restriction off in Chrome". |
| **Private browsing** | A browser is approvable "with website restriction" only if the extension is allowed in private windows (checked at session start). Service mode may disable private browsing by policy (VM 30). |
| **Elevated Windows app** | Agent alone can't act; service handles it; without the service, reported as "Can't restrict apps running as administrator". |
| **Agent or service stopped** | Peers see **Unknown** when authenticated heartbeats expire. The device reports the gap after restart. |
| **Permission lost** (Android) | Detected within the self-check interval (target ≤ 10 s) → **Degraded**, local notification, reported to peers. |
| **Device offline** | Keeps executing its accepted schedule; peers show **Unknown**; reconciles on reconnect. |
| **System clock changed** | Planned (M3/M4): track phase ends against the monotonic clock too, and log jumps. Not implemented. |
| **Notification inline reply** from a restricted app | Gap. Closing it needs notification access (owner decision U10). |

## Sources
- [AppLocker: removal of edition checks (KB5024351)](https://support.microsoft.com/en-us/topic/kb5024351-removal-of-windows-edition-checks-for-applocker-e3a763c9-6a3e-4d9c-8623-0ffe69046470)
- [Configure the Application Identity service](https://learn.microsoft.com/id-id/windows/security/application-security/application-control/app-control-for-business/applocker/configure-the-application-identity-service)
- [App Control and AppLocker feature availability](https://learn.microsoft.com/en-us/windows/security/application-security/application-control/app-control-for-business/feature-availability)
- [CiTool commands](https://learn.microsoft.com/windows/security/threat-protection/windows-defender-application-control/operations/citool-commands)
- [Deploy App Control policies using script](https://learn.microsoft.com/en-us/windows/security/application-security/application-control/app-control-for-business/deployment/deploy-appcontrol-policies-with-script)
- [Remove App Control for Business policies](https://learn.microsoft.com/en-us/windows/security/application-security/application-control/app-control-for-business/deployment/disable-appcontrol-policies)
- [ApplicationControl CSP](https://learn.microsoft.com/en-us/windows/client-management/mdm/applicationcontrol-csp)
- [HotCakeX/Harden-Windows-Security (AppControl Manager, MIT)](https://github.com/HotCakeX/harden-windows-security)
- [Chrome extension policies (Windows)](https://support.google.com/chrome/a/answer/7532015?hl=en)
- [ISimpleAudioVolume::SetMute](https://learn.microsoft.com/en-us/windows/win32/api/audioclient/nf-audioclient-isimpleaudiovolume-setmute)
- [Android: restrictions on starting activities from the background](https://developer.android.com/guide/components/activities/background-starts)
- [Android 17: background audio hardening](https://developer.android.com/about/versions/17/changes/bg-audio)
- [Android: Advanced Protection Mode](https://developer.android.com/privacy-and-security/advanced-protection-mode)
- [Android: secure sensitive activities (HIDE_OVERLAY_WINDOWS)](https://developer.android.com/security/fraud-prevention/activities)
- [Play: AccessibilityService API policy](https://support.google.com/googleplay/android-developer/answer/10964491)
- [Play: foreground service requirements](https://support.google.com/googleplay/android-developer/answer/13392821)
- [Android 17 Advanced Protection and accessibility (The Hacker News)](https://thehackernews.com/2026/10/android-17-advanced-protection-locks.html)
