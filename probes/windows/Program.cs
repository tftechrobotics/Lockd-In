// Focus Windows enforcement probe — EXPERIMENT ONLY.
//
// Question it answers: can an ordinary (unelevated, per-user) process detect
// a restricted app's window coming to the foreground and move it out of the
// way quickly and reliably, without closing it or touching its data?
//
// What it does: listens for foreground changes (SetWinEventHook), sweeps all
// visible windows every 2 s (catches other monitors), and minimizes windows
// whose process image name is on --block. It never terminates processes,
// never elevates, and stops itself after --minutes (default 5) or on Ctrl+C.
//
// It logs only executable names, AppUserModelIDs, timings and outcomes —
// never window titles or content.

using System.Diagnostics;
using System.Runtime.InteropServices;
using System.Text;
using System.Text.Json;

namespace FocusProbe;

internal static class Program
{
    private static HashSet<string> _blocked = new(StringComparer.OrdinalIgnoreCase);
    private static StreamWriter? _log;
    private static uint _mainThreadId;
    private static readonly object LogLock = new();
    private static int _shieldOpen; // 0/1, one explanation box at a time
    private static Native.WinEventDelegate? _hookProc; // kept alive for the hook's lifetime
    private static DateTime _endsAtUtc;

    private static int Main(string[] args)
    {
        if (!OperatingSystem.IsWindows())
        {
            Console.Error.WriteLine("This probe only runs on Windows.");
            return 1;
        }

        double minutes = 5;
        for (int i = 0; i < args.Length; i++)
        {
            switch (args[i])
            {
                case "--block" when i + 1 < args.Length:
                    foreach (var name in args[++i].Split(',', StringSplitOptions.RemoveEmptyEntries | StringSplitOptions.TrimEntries))
                        _blocked.Add(name.EndsWith(".exe", StringComparison.OrdinalIgnoreCase) ? name : name + ".exe");
                    break;
                case "--minutes" when i + 1 < args.Length:
                    minutes = Math.Clamp(double.Parse(args[++i], System.Globalization.CultureInfo.InvariantCulture), 0.5, 60);
                    break;
                default:
                    Console.Error.WriteLine("Usage: FocusProbe --block notepad.exe,msedge.exe [--minutes 5]");
                    return 2;
            }
        }
        if (_blocked.Count == 0)
        {
            Console.Error.WriteLine("Nothing to block. Usage: FocusProbe --block notepad.exe [--minutes 5]");
            return 2;
        }
        // Never act on the shell or on ourselves, even if listed.
        foreach (var safe in new[] { "explorer.exe", "taskmgr.exe", "dotnet.exe", Path.GetFileName(Environment.ProcessPath ?? "") })
            _blocked.Remove(safe);

        var dir = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "FocusProbe");
        Directory.CreateDirectory(dir);
        var logPath = Path.Combine(dir, $"probe-{DateTime.UtcNow:yyyyMMdd-HHmmss}.jsonl");
        _log = new StreamWriter(logPath, append: false, Encoding.UTF8) { AutoFlush = true };

        _endsAtUtc = DateTime.UtcNow.AddMinutes(minutes);
        _mainThreadId = Native.GetCurrentThreadId();
        Log("start", new { blocked = _blocked.ToArray(), minutes, os = Environment.OSVersion.VersionString, arch = RuntimeInformation.OSArchitecture.ToString(), elevated = Native.IsCurrentProcessElevated() });
        Console.WriteLine($"Probe running until {_endsAtUtc.ToLocalTime():HH:mm:ss}. Blocking: {string.Join(", ", _blocked)}");
        Console.WriteLine($"Log: {logPath}");
        Console.WriteLine("Press Ctrl+C to stop early.");

        Console.CancelKeyPress += (_, e) => { e.Cancel = true; Stop("ctrl_c"); };

        _hookProc = OnWinEvent;
        var hooks = new[]
        {
            Native.SetWinEventHook(Native.EVENT_SYSTEM_FOREGROUND, Native.EVENT_SYSTEM_FOREGROUND, IntPtr.Zero, _hookProc, 0, 0, Native.WINEVENT_OUTOFCONTEXT | Native.WINEVENT_SKIPOWNPROCESS),
            Native.SetWinEventHook(Native.EVENT_SYSTEM_MINIMIZEEND, Native.EVENT_SYSTEM_MINIMIZEEND, IntPtr.Zero, _hookProc, 0, 0, Native.WINEVENT_OUTOFCONTEXT | Native.WINEVENT_SKIPOWNPROCESS),
        };
        if (hooks.Any(h => h == IntPtr.Zero))
        {
            Log("hook_failed", new { error = Marshal.GetLastWin32Error() });
            Console.Error.WriteLine("Could not install event hooks.");
            return 3;
        }

        using var sweep = new Timer(_ => Sweep(), null, TimeSpan.Zero, TimeSpan.FromSeconds(2));
        using var deadline = new Timer(_ => Stop("time_up"), null, _endsAtUtc - DateTime.UtcNow, Timeout.InfiniteTimeSpan);

        // Message loop: out-of-context hooks are delivered here.
        while (Native.GetMessage(out var msg, IntPtr.Zero, 0, 0) > 0)
        {
            Native.TranslateMessage(ref msg);
            Native.DispatchMessage(ref msg);
        }

        foreach (var h in hooks) Native.UnhookWinEvent(h);
        Log("stop", new { });
        Console.WriteLine("Stopped. Nothing remains running; minimized windows were left intact.");
        return 0;
    }

    private static void Stop(string reason)
    {
        Log("stopping", new { reason });
        Native.PostThreadMessage(_mainThreadId, Native.WM_QUIT, IntPtr.Zero, IntPtr.Zero);
    }

    private static void OnWinEvent(IntPtr hook, uint evt, IntPtr hwnd, int idObject, int idChild, uint thread, uint eventTimeMs)
    {
        if (idObject != 0 || hwnd == IntPtr.Zero) return; // OBJID_WINDOW only
        var trigger = evt == Native.EVENT_SYSTEM_FOREGROUND ? "foreground" : "restored";
        Handle(hwnd, trigger, eventTimeMs);
    }

    private static void Sweep()
    {
        Native.EnumWindows((hwnd, _) =>
        {
            if (Native.IsWindowVisible(hwnd) && !Native.IsIconic(hwnd) && Native.GetWindow(hwnd, Native.GW_OWNER) == IntPtr.Zero)
                Handle(hwnd, "sweep", null);
            return true;
        }, IntPtr.Zero);
    }

    private static void Handle(IntPtr hwnd, string trigger, uint? eventTimeMs)
    {
        var id = Identify(hwnd);
        if (id is null) return;
        if (!_blocked.Contains(id.Value.Exe)) return;

        var started = Stopwatch.GetTimestamp();
        Native.ShowWindowAsync(hwnd, Native.SW_MINIMIZE);
        // Verify instead of assuming: give the target time to process the request.
        bool minimized = false;
        for (int i = 0; i < 10 && !minimized; i++)
        {
            Thread.Sleep(20);
            minimized = Native.IsIconic(hwnd) || !Native.IsWindowVisible(hwnd);
        }
        var actionMs = Stopwatch.GetElapsedTime(started).TotalMilliseconds;
        long? detectLatencyMs = eventTimeMs is uint t ? unchecked((uint)Environment.TickCount - t) : null;

        Log("restricted_window", new
        {
            trigger,
            exe = id.Value.Exe,
            aumid = id.Value.Aumid,
            via_frame_host = id.Value.ViaFrameHost,
            minimized,
            detect_latency_ms = detectLatencyMs,
            action_ms = Math.Round(actionMs, 1),
            note = minimized ? null : "minimize had no effect (elevated target, fullscreen exclusive, or app refused)",
        });

        if (Interlocked.Exchange(ref _shieldOpen, 1) == 0)
        {
            var until = _endsAtUtc.ToLocalTime().ToString("h:mm tt");
            var text = minimized
                ? $"{id.Value.Exe} is paused for this focus session.\n\nIt was minimized, not closed — your work is safe. It will be available again at {until}."
                : $"{id.Value.Exe} is on your restricted list, but Focus could not move it out of the way.\n\nThis is a probe limitation being measured.";
            new Thread(() =>
            {
                Native.MessageBoxW(IntPtr.Zero, text, "Focus probe", Native.MB_OK | Native.MB_ICONINFORMATION | Native.MB_TOPMOST | Native.MB_SETFOREGROUND);
                Interlocked.Exchange(ref _shieldOpen, 0);
            }) { IsBackground = true }.Start();
        }
    }

    private readonly record struct AppIdentity(string Exe, string? Aumid, bool ViaFrameHost);

    private static AppIdentity? Identify(IntPtr hwnd)
    {
        Native.GetWindowThreadProcessId(hwnd, out var pid);
        if (pid == 0 || pid == (uint)Environment.ProcessId) return null;
        var path = Native.ProcessImagePath(pid);
        if (path is null) return null;
        var exe = Path.GetFileName(path);

        // Store/UWP apps are hosted by ApplicationFrameHost.exe; the real app
        // owns a child CoreWindow. Look through to it.
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
                return new AppIdentity(Path.GetFileName(childPath), Native.ProcessAumid(childPid), true);
        }
        return new AppIdentity(exe, Native.ProcessAumid(pid), false);
    }

    private static void Log(string evt, object data)
    {
        var line = JsonSerializer.Serialize(new { t = DateTime.UtcNow.ToString("O"), evt, data });
        lock (LogLock) _log?.WriteLine(line);
    }
}
