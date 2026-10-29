// FocusProbe — Windows per-user enforcement experiment. EXPERIMENT ONLY.
//
// Measures what an ordinary (unelevated) per-user process can do against a
// restricted app, without closing it or touching its data:
//   * detect its windows (foreground/restore events + 2 s sweep) and minimize them;
//   * mute its audio sessions (they keep running — they are NOT paused);
//   * notice launches. With --test-close-target-after-launch, and ONLY for the
//     harmless FocusTarget.exe, it closes a copy shortly after it starts. That is
//     detection followed by termination — NOT launch prevention (the process does
//     start, briefly). It exists to measure timing, never to be applied to real
//     apps, which can hold valuable state from the moment they start.
//
// Recovery of audio it muted (every mute is recorded in a ledger first):
//   * normal end, Ctrl+C, console closed      -> restored immediately by the probe;
//   * probe crashes or is killed              -> restored by a separate guardian
//                                                process that waits on the probe;
//   * probe AND guardian killed / power loss  -> NOT immediate: restored on the next
//                                                probe start or `FocusProbe --recover`.

using System.Diagnostics;
using System.Runtime.InteropServices;
using System.Text.Json;

namespace FocusProbe;

internal static class Program
{
    private const string HarmlessTarget = "FocusTarget.exe";

    private static readonly HashSet<string> Blocked = new(StringComparer.OrdinalIgnoreCase);
    private static readonly HashSet<string> NeverTouch = new(StringComparer.OrdinalIgnoreCase)
    {
        "explorer.exe", "taskmgr.exe", "dotnet.exe", "FocusProbe.exe", "Narrator.exe", "Magnify.exe",
        "osk.exe", "TextInputHost.exe", "ShellExperienceHost.exe", "StartMenuExperienceHost.exe",
        "SearchHost.exe", "LockApp.exe", "ctfmon.exe", "SystemSettings.exe",
    };

    private static JsonLog _log = null!;
    private static MuteLedger _ledger = null!;
    private static uint _mainThreadId;
    private static DateTime _endsAtUtc;
    private static bool _mute = true;
    private static bool _testCloseTarget;
    private static Process? _guardian;
    private static int _released;
    private static int _shieldOpen;
    private static HashSet<int> _initialPids = new();
    private static readonly HashSet<int> SeenLaunches = new();
    private static long _lastTickWallMs = DateTimeOffset.UtcNow.ToUnixTimeMilliseconds();
    private static Native.WinEventDelegate? _hookProc;
    private static Native.ConsoleCtrlHandler? _ctrlHandler;

    private static int Main(string[] args)
    {
        if (!OperatingSystem.IsWindows())
        {
            Console.Error.WriteLine("FocusProbe only runs on Windows.");
            return 1;
        }

        double minutes = 5;
        bool recoverOnly = false;
        for (int i = 0; i < args.Length; i++)
        {
            switch (args[i])
            {
                case "--block" when i + 1 < args.Length:
                    foreach (var n in args[++i].Split(',', StringSplitOptions.RemoveEmptyEntries | StringSplitOptions.TrimEntries))
                        Blocked.Add(n.EndsWith(".exe", StringComparison.OrdinalIgnoreCase) ? n : n + ".exe");
                    break;
                case "--minutes" when i + 1 < args.Length:
                    if (!double.TryParse(args[++i], System.Globalization.NumberStyles.Float, System.Globalization.CultureInfo.InvariantCulture, out minutes))
                        return Usage("--minutes must be a number");
                    minutes = Math.Clamp(minutes, 0.5, 60);
                    break;
                case "--no-mute": _mute = false; break;
                case "--test-close-target-after-launch": _testCloseTarget = true; break;
                case "--recover": recoverOnly = true; break;
                case "--guardian" when i + 2 < args.Length:
                    return Guardian.Run(int.Parse(args[i + 1], System.Globalization.CultureInfo.InvariantCulture),
                        long.Parse(args[i + 2], System.Globalization.CultureInfo.InvariantCulture));
                default: return Usage($"Unknown argument: {args[i]}");
            }
        }

        _log = new JsonLog("probe");
        _ledger = new MuteLedger(Path.Combine(JsonLog.Directory, "mute-ledger.json"));

        // Always undo anything a previous run (or crash) left muted.
        var recovered = _ledger.RestoreAll();
        _log.Write("startup_recovery", new { restored_sessions = recovered });
        if (recovered > 0) Console.WriteLine($"Restored sound for {recovered} app session(s) left muted by an earlier run.");
        if (recoverOnly)
        {
            Console.WriteLine("Recovery complete. Nothing is restricted.");
            return 0;
        }

        Blocked.ExceptWith(NeverTouch);
        if (Blocked.Count == 0) return Usage("Nothing to restrict. Example: --block FocusTarget.exe");
        if (_testCloseTarget && !(Blocked.Count == 1 && Blocked.Contains(HarmlessTarget)))
            return Usage("--test-close-target-after-launch is only allowed with --block FocusTarget.exe. Focus never closes real apps.");

        _endsAtUtc = DateTime.UtcNow.AddMinutes(minutes);
        _mainThreadId = Native.GetCurrentThreadId();
        _initialPids = BlockedPids();

        _log.Write("start", new
        {
            blocked = Blocked.ToArray(),
            minutes,
            mute = _mute,
            test_close_target_after_launch = _testCloseTarget,
            os = Environment.OSVersion.VersionString,
            arch = RuntimeInformation.OSArchitecture.ToString(),
            probe_elevated = Native.IsCurrentProcessElevated(),
            already_running_pids = _initialPids.Count,
        });

        Console.WriteLine($"Focus probe running until {_endsAtUtc.ToLocalTime():h:mm:ss tt}.");
        Console.WriteLine($"Restricting: {string.Join(", ", Blocked)}");
        if (_initialPids.Count > 0)
            Console.WriteLine($"{_initialPids.Count} restricted process(es) were already running; they will be minimized/muted, not closed.");
        _guardian = Guardian.Start();
        _log.Write("guardian_started", new { ok = _guardian is not null });
        if (_guardian is null) Console.WriteLine("Warning: crash-recovery guardian could not start; recovery after a crash would wait for the next run.");
        Console.WriteLine($"Log: {_log.Path}");
        Console.WriteLine("Press Ctrl+C to stop early. Closing this window also stops it.");

        Console.CancelKeyPress += (_, e) => { e.Cancel = true; RequestStop("ctrl_c"); };
        _ctrlHandler = ctrl =>
        {
            if (ctrl == Native.CTRL_CLOSE_EVENT) { Release("console_closed"); return true; }
            return false;
        };
        Native.SetConsoleCtrlHandler(_ctrlHandler, true);
        AppDomain.CurrentDomain.ProcessExit += (_, _) => Release("process_exit");

        _hookProc = OnWinEvent;
        const uint flags = Native.WINEVENT_OUTOFCONTEXT | Native.WINEVENT_SKIPOWNPROCESS;
        var hooks = new[]
        {
            Native.SetWinEventHook(Native.EVENT_SYSTEM_FOREGROUND, Native.EVENT_SYSTEM_FOREGROUND, IntPtr.Zero, _hookProc, 0, 0, flags),
            Native.SetWinEventHook(Native.EVENT_SYSTEM_MINIMIZEEND, Native.EVENT_SYSTEM_MINIMIZEEND, IntPtr.Zero, _hookProc, 0, 0, flags),
        };
        if (hooks.Any(h => h == IntPtr.Zero))
        {
            _log.Write("hook_failed", new { error = Marshal.GetLastWin32Error() });
            Console.Error.WriteLine("Could not install window event hooks.");
            Release("hook_failed");
            return 3;
        }

        using var sweep = new Timer(_ => Safe("sweep", Sweep), null, TimeSpan.Zero, TimeSpan.FromSeconds(2));
        using var launches = new Timer(_ => Safe("launch_poll", PollLaunches), null, TimeSpan.Zero, TimeSpan.FromMilliseconds(250));
        using var heartbeat = new Timer(_ => Safe("heartbeat", Heartbeat), null, TimeSpan.FromSeconds(10), TimeSpan.FromSeconds(10));
        using var deadline = new Timer(_ => RequestStop("time_up"), null, _endsAtUtc - DateTime.UtcNow, Timeout.InfiniteTimeSpan);

        while (Native.GetMessageW(out var msg, IntPtr.Zero, 0, 0) > 0)
        {
            Native.TranslateMessage(ref msg);
            Native.DispatchMessageW(ref msg);
        }

        foreach (var h in hooks) Native.UnhookWinEvent(h);
        Release("loop_exit");
        return 0;
    }

    private static int Usage(string problem)
    {
        Console.Error.WriteLine(problem);
        Console.Error.WriteLine("Usage: FocusProbe --block FocusTarget.exe [--minutes 3] [--no-mute] [--test-close-target-after-launch]");
        Console.Error.WriteLine("       FocusProbe --recover     (restore sound for anything an earlier run muted)");
        return 2;
    }

    private static void RequestStop(string reason)
    {
        _log.Write("stop_requested", new { reason });
        Native.PostThreadMessageW(_mainThreadId, Native.WM_QUIT, IntPtr.Zero, IntPtr.Zero);
    }

    /// <summary>Undo everything the probe changed. Idempotent and crash-tolerant.</summary>
    private static void Release(string reason)
    {
        if (Interlocked.Exchange(ref _released, 1) == 1) return;
        int restored = 0;
        try { restored = _ledger.RestoreAll(); }
        catch (Exception e) { _log.Write("release_error", new { error = e.GetType().Name, e.Message }); }
        // After a clean release the ledger is empty, so the guardian has nothing left to do.
        try { if (_guardian is { HasExited: false }) _guardian.Kill(); }
        catch (Exception e) { _log.Write("guardian_stop_error", new { error = e.GetType().Name }); }
        _log.Write("released", new { reason, restored_sessions = restored });
        Console.WriteLine($"Released ({reason}). Restored sound for {restored} session(s). Minimized windows were left open and unchanged.");
        _log.Dispose();
    }

    private static void Safe(string where, Action action)
    {
        if (Volatile.Read(ref _released) == 1) return;
        try { action(); }
        catch (Exception e) { _log.Write("error", new { where, error = e.GetType().Name, e.Message }); }
    }

    private static void OnWinEvent(IntPtr hook, uint evt, IntPtr hwnd, int idObject, int idChild, uint thread, uint eventTimeMs)
    {
        if (idObject != 0 || hwnd == IntPtr.Zero) return; // OBJID_WINDOW only
        var trigger = evt == Native.EVENT_SYSTEM_FOREGROUND ? "foreground" : "restored";
        Safe("win_event", () => HandleWindow(hwnd, trigger, eventTimeMs));
    }

    private static void Sweep()
    {
        Native.EnumWindows((hwnd, _) =>
        {
            if (Native.IsWindowVisible(hwnd) && !Native.IsIconic(hwnd) && Native.GetWindow(hwnd, Native.GW_OWNER) == IntPtr.Zero)
                HandleWindow(hwnd, "sweep", null);
            return true;
        }, IntPtr.Zero);
        if (_mute) MuteRestrictedAudio();
    }

    private static void Heartbeat()
    {
        var now = DateTimeOffset.UtcNow.ToUnixTimeMilliseconds();
        var gap = now - Interlocked.Exchange(ref _lastTickWallMs, now);
        // A gap far beyond the 10 s period means the PC slept or the process was frozen.
        _log.Write(gap > 30_000 ? "resume_detected" : "heartbeat", new { gap_ms = gap });
    }

    private static HashSet<int> BlockedPids()
    {
        var set = new HashSet<int>();
        foreach (var name in Blocked)
            foreach (var p in Process.GetProcessesByName(Path.GetFileNameWithoutExtension(name)))
                using (p) set.Add(p.Id);
        return set;
    }

    private static readonly object LaunchLock = new();

    private static void PollLaunches()
    {
        // Timer callbacks can overlap if one run is slow; serialize them.
        if (!Monitor.TryEnter(LaunchLock)) return;
        try { PollLaunchesCore(); }
        finally { Monitor.Exit(LaunchLock); }
    }

    private static void PollLaunchesCore()
    {
        foreach (var name in Blocked)
        {
            foreach (var p in Process.GetProcessesByName(Path.GetFileNameWithoutExtension(name)))
            {
                using (p)
                {
                    if (_initialPids.Contains(p.Id) || !SeenLaunches.Add(p.Id)) continue;
                    double? ageMs = null;
                    try { ageMs = (DateTime.Now - p.StartTime).TotalMilliseconds; } catch { /* elevated or exited */ }
                    var elevated = Native.IsProcessElevated((uint)p.Id);
                    bool? closed = null;
                    string? error = null;
                    if (_testCloseTarget && name.Equals(HarmlessTarget, StringComparison.OrdinalIgnoreCase))
                    {
                        try { p.Kill(); closed = true; }
                        catch (Exception e) { closed = false; error = e.GetType().Name; }
                    }
                    // age_ms = how long the process had already been running when detected.
                    _log.Write("launch_detected", new { exe = name, age_ms = ageMs, elevated, test_closed_after_launch = closed, error });
                    if (closed == true)
                        Explain($"{name} started and was then closed by the probe (test app only). This is detection followed by closing, not prevention: it did run briefly.");
                }
            }
        }
    }

    private static void HandleWindow(IntPtr hwnd, string trigger, uint? eventTimeMs)
    {
        var id = Identify(hwnd);
        if (id is null || !Blocked.Contains(id.Value.Exe)) return;

        var started = Stopwatch.GetTimestamp();
        Native.ShowWindowAsync(hwnd, Native.SW_MINIMIZE);
        bool minimized = false;
        for (int i = 0; i < 10 && !minimized; i++)
        {
            Thread.Sleep(20);
            minimized = Native.IsIconic(hwnd) || !Native.IsWindowVisible(hwnd);
        }
        var actionMs = Stopwatch.GetElapsedTime(started).TotalMilliseconds;
        long? detectLatencyMs = eventTimeMs is uint t ? unchecked((uint)Environment.TickCount - t) : null;
        int muted = _mute ? MuteRestrictedAudio() : 0;
        var elevated = Native.IsProcessElevated(id.Value.Pid);

        _log.Write("restricted_window", new
        {
            trigger,
            exe = id.Value.Exe,
            aumid = id.Value.Aumid,
            via_frame_host = id.Value.ViaFrameHost,
            target_elevated = elevated,
            minimized,
            newly_muted_sessions = muted,
            detect_latency_ms = detectLatencyMs,
            action_ms = Math.Round(actionMs, 1),
        });

        var until = _endsAtUtc.ToLocalTime().ToString("h:mm tt");
        string text = minimized
            ? $"{id.Value.Exe} is restricted until {until}.\n\nFocus minimized it{(_mute ? " and muted its sound" : "")}. It is still open and may still be running — nothing was closed."
            : $"{id.Value.Exe} is restricted, but Focus could not minimize it{(elevated == null ? " (it may be running as administrator)" : "")}.\n\nThis is a limitation being measured.";
        Explain(text);
    }

    private static void Explain(string text)
    {
        if (Interlocked.Exchange(ref _shieldOpen, 1) == 1) return;
        new Thread(() =>
        {
            Native.MessageBoxW(IntPtr.Zero, text, "Focus probe",
                Native.MB_OK | Native.MB_ICONINFORMATION | Native.MB_TOPMOST | Native.MB_SETFOREGROUND);
            Interlocked.Exchange(ref _shieldOpen, 0);
        }) { IsBackground = true }.Start();
    }

    /// <summary>Mutes any unmuted audio session owned by a restricted process. Returns how many it muted.</summary>
    private static int MuteRestrictedAudio()
    {
        int count = 0;
        foreach (var s in AudioSessions.List())
        {
            if (s.Muted) continue; // already muted (possibly by the user) — never record or later unmute it
            var exe = Native.ProcessImagePath(s.Pid) is string path ? Path.GetFileName(path) : null;
            if (exe is null || !Blocked.Contains(exe)) continue;
            _ledger.Record(s.InstanceId, exe); // record BEFORE muting so a crash can always be undone
            if (AudioSessions.SetMute(s.InstanceId, true) == false) count++;
        }
        return count;
    }

    private readonly record struct AppIdentity(uint Pid, string Exe, string? Aumid, bool ViaFrameHost);

    private static AppIdentity? Identify(IntPtr hwnd)
    {
        Native.GetWindowThreadProcessId(hwnd, out var pid);
        if (pid == 0 || pid == (uint)Environment.ProcessId) return null;
        var path = Native.ProcessImagePath(pid);
        if (path is null) return null;
        var exe = Path.GetFileName(path);

        // Store/UWP apps are hosted by ApplicationFrameHost.exe; the real app
        // owns a child CoreWindow in another process. Look through to it.
        if (exe.Equals("ApplicationFrameHost.exe", StringComparison.OrdinalIgnoreCase))
        {
            uint childPid = 0;
            Native.EnumChildWindows(hwnd, (child, _) =>
            {
                Native.GetWindowThreadProcessId(child, out var p);
                if (p != pid) { childPid = p; return false; }
                return true;
            }, IntPtr.Zero);
            if (childPid != 0 && Native.ProcessImagePath(childPid) is string childPath)
                return new AppIdentity(childPid, Path.GetFileName(childPath), Native.ProcessAumid(childPid), true);
        }
        return new AppIdentity(pid, exe, Native.ProcessAumid(pid), false);
    }
}

/// <summary>
/// Persistent record of audio sessions the probe muted, written before each
/// mute so that a crash, power loss or forced close can always be undone.
/// </summary>
internal sealed class MuteLedger(string path)
{
    private readonly object _lock = new();

    private sealed record Entry(string InstanceId, string Exe, DateTime MutedAtUtc);

    public void Record(string instanceId, string exe)
    {
        lock (_lock)
        {
            var entries = Load();
            if (entries.Any(e => e.InstanceId == instanceId)) return;
            entries.Add(new Entry(instanceId, exe, DateTime.UtcNow));
            Save(entries);
        }
    }

    /// <summary>
    /// Unmutes every recorded session. A session is matched by its instance ID,
    /// or — if Windows re-created it, e.g. after a crash or app restart — by
    /// the same executable. Entries whose app is not running are kept (for up
    /// to 24 h) so the next run or `--recover` can still restore them.
    /// </summary>
    public int RestoreAll()
    {
        lock (_lock)
        {
            var entries = Load();
            if (entries.Count == 0) return 0;
            int restored = 0;
            var keep = new List<Entry>();
            var sessions = AudioSessions.List();
            foreach (var e in entries)
            {
                bool found = false;
                if (AudioSessions.SetMute(e.InstanceId, false) is bool wasMuted)
                {
                    found = true;
                    if (wasMuted) restored++;
                }
                foreach (var s in sessions.Where(s => s.Muted && s.InstanceId != e.InstanceId))
                {
                    var exe = Native.ProcessImagePath(s.Pid) is string p ? Path.GetFileName(p) : null;
                    if (!string.Equals(exe, e.Exe, StringComparison.OrdinalIgnoreCase)) continue;
                    if (AudioSessions.SetMute(s.InstanceId, false) == true) { restored++; found = true; }
                }
                if (!found && DateTime.UtcNow - e.MutedAtUtc < TimeSpan.FromHours(24)) keep.Add(e);
            }
            Save(keep);
            return restored;
        }
    }

    private List<Entry> Load()
    {
        try { return File.Exists(path) ? JsonSerializer.Deserialize<List<Entry>>(File.ReadAllText(path)) ?? new() : new(); }
        catch (JsonException) { return new(); }
    }

    private void Save(List<Entry> entries)
    {
        var tmp = path + ".tmp";
        File.WriteAllText(tmp, JsonSerializer.Serialize(entries));
        File.Move(tmp, path, overwrite: true);
    }
}
