// Lockd In blocking agent (Windows, per user).
//
// What it does: while the plan written by the app says a focus period is running, every
// visible top-level window that belongs to a restricted app is minimized as soon as it comes
// to the front (WinEvent hook) and on a 1-second sweep, and that app's audio sessions are
// muted. Every mute is recorded in a ledger first and undone at breaks, at the end, by a
// guardian process if the agent crashes, or at the next start after power loss.
//
// What it can't do (and the app says so): stop an app from starting; minimize windows of
// apps running as administrator (Windows blocks that from a normal process; counted and
// reported as Degraded); keep working if the user ends it in Task Manager. Windows are left
// minimized at the end, never closed. Nothing is ever terminated.
using System.Diagnostics;
using LockdIn.Enforcement;

namespace LockdIn.Agent;

internal static class Program
{
    private static Plan? plan;
    private static DateTime planStamp;
    private static MuteLedger ledger = null!;
    private static uint mainThread;
    private static bool restricting;
    private static int minimized, muted, elevatedSkipped;
    private static DateTimeOffset lastDegradedAt = DateTimeOffset.MinValue;
    private static Process? guardian;
    private static Native.WinEventDelegate? hookProc;
    private static readonly object Gate = new();

    [STAThread]
    private static int Main(string[] args)
    {
        if (!OperatingSystem.IsWindows()) return 1;
        Directory.CreateDirectory(AgentPaths.Dir);
        ledger = new MuteLedger(AgentPaths.Ledger);

        if (args is ["--guardian", var pidText, var ticksText]) return Guardian.Run(pidText, ticksText);
        if (args is ["--recover"]) { Log($"recover: restored {RestoreAudio()}"); return 0; }

        using var mutex = new Mutex(true, AgentPaths.MutexName, out var first);
        if (!first) return 0; // already running; it picks up plan changes itself

        Log($"start {Environment.OSVersion.VersionString}");
        Log($"startup recovery: restored {RestoreAudio()}");
        mainThread = Native.GetCurrentThreadId();
        guardian = Guardian.Start();
        AppDomain.CurrentDomain.ProcessExit += (_, _) => Release("process_exit");

        hookProc = OnWinEvent;
        const uint flags = Native.WINEVENT_OUTOFCONTEXT | Native.WINEVENT_SKIPOWNPROCESS;
        var hooks = new[]
        {
            Native.SetWinEventHook(Native.EVENT_SYSTEM_FOREGROUND, Native.EVENT_SYSTEM_FOREGROUND, IntPtr.Zero, hookProc, 0, 0, flags),
            Native.SetWinEventHook(Native.EVENT_SYSTEM_MINIMIZEEND, Native.EVENT_SYSTEM_MINIMIZEEND, IntPtr.Zero, hookProc, 0, 0, flags),
        };

        Tick();
        using var timer = new System.Threading.Timer(_ => Safe(Tick), null, 250, 500);
        while (Native.GetMessageW(out var msg, IntPtr.Zero, 0, 0) > 0)
        {
            Native.TranslateMessage(ref msg);
            Native.DispatchMessageW(ref msg);
        }
        foreach (var h in hooks) if (h != IntPtr.Zero) Native.UnhookWinEvent(h);
        Release("finished");
        return 0;
    }

    /// <summary>Re-reads the plan, applies or releases restriction, sweeps, reports status.</summary>
    private static void Tick()
    {
        lock (Gate)
        {
            var now = DateTimeOffset.UtcNow;
            LoadPlan();
            if (plan is null || Policy.Finished(plan, now))
            {
                WriteStatus(plan?.SessionId, AgentProtection.Idle, now);
                Native.PostThreadMessageW(mainThread, Native.WM_QUIT, IntPtr.Zero, IntPtr.Zero);
                return;
            }
            var nowRestricting = Policy.Restricting(plan, now);
            if (restricting && !nowRestricting) Log($"released for break/unlock: restored {RestoreAudio()}");
            restricting = nowRestricting;
            if (restricting)
            {
                Sweep();
                MuteRestrictedAudio();
            }
            var degraded = restricting && now - lastDegradedAt < TimeSpan.FromSeconds(10);
            WriteStatus(plan.SessionId, !restricting ? AgentProtection.NotRestricting : degraded ? AgentProtection.Degraded : AgentProtection.Protecting, now);
        }
    }

    private static void LoadPlan()
    {
        try
        {
            var stamp = File.Exists(AgentPaths.Plan) ? File.GetLastWriteTimeUtc(AgentPaths.Plan) : DateTime.MinValue;
            if (stamp == planStamp && plan is not null) return;
            planStamp = stamp;
            var next = stamp == DateTime.MinValue ? null : PlanCodec.Parse(File.ReadAllText(AgentPaths.Plan));
            if (next?.SessionId != plan?.SessionId || next?.Intervals.Count != plan?.Intervals.Count)
                Log($"plan {(next is null ? "none" : $"{next.SessionId} intervals={next.Intervals.Count} mode={next.Mode} apps={next.Apps.Count}")}");
            plan = next;
        }
        catch (IOException) { /* being written; next tick */ }
        catch (UnauthorizedAccessException) { plan = null; }
    }

    private static void OnWinEvent(IntPtr hook, uint evt, IntPtr hwnd, int idObject, int idChild, uint thread, uint time)
    {
        if (idObject != 0 || hwnd == IntPtr.Zero) return;
        Safe(() => { lock (Gate) { if (restricting && plan is not null) Handle(hwnd, plan); } });
    }

    private static void Sweep()
    {
        var p = plan!;
        Native.EnumWindows((hwnd, _) =>
        {
            if (Native.IsWindowVisible(hwnd) && !Native.IsIconic(hwnd) && Native.GetWindow(hwnd, Native.GW_OWNER) == IntPtr.Zero)
                Handle(hwnd, p);
            return true;
        }, IntPtr.Zero);
    }

    private static void Handle(IntPtr hwnd, Plan p)
    {
        var exe = Identify(hwnd, out var pid);
        if (!Policy.IsRestricted(exe, p) || Native.IsIconic(hwnd)) return;
        Native.ShowWindowAsync(hwnd, Native.SW_MINIMIZE);
        bool ok = false;
        for (int i = 0; i < 10 && !ok; i++) { Thread.Sleep(15); ok = Native.IsIconic(hwnd) || !Native.IsWindowVisible(hwnd); }
        if (ok) { minimized++; Log($"minimized {exe}"); }
        else
        {
            // Usually an app running as administrator: Windows doesn't let us touch it.
            elevatedSkipped++;
            lastDegradedAt = DateTimeOffset.UtcNow;
            Log($"could not minimize {exe} (elevated={Native.IsProcessElevated(pid)?.ToString() ?? "unknown"})");
        }
    }

    private static void MuteRestrictedAudio()
    {
        foreach (var s in AudioSessions.List())
        {
            if (s.Muted) continue; // muted by the user or already by us: never record, never later unmute
            var exe = Native.ProcessImagePath(s.Pid) is string path ? Path.GetFileName(path) : null;
            if (!Policy.IsRestricted(exe, plan!)) continue;
            if (!ledger.Add(s.InstanceId, exe!, DateTimeOffset.UtcNow)) continue; // couldn't record: don't mute
            if (AudioSessions.SetMute(s.InstanceId, true) == false) { muted++; Log($"muted {exe}"); }
        }
    }

    internal static int RestoreAudio()
    {
        var live = AudioSessions.List()
            .Select(s => (s.InstanceId, Native.ProcessImagePath(s.Pid) is string p ? Path.GetFileName(p) : null, s.Muted))
            .ToList();
        return ledger.RestoreAll(live, id => AudioSessions.SetMute(id, false) == true, DateTimeOffset.UtcNow);
    }

    private static int released;
    private static void Release(string why)
    {
        if (Interlocked.Exchange(ref released, 1) == 1) return;
        int restored = 0;
        try { restored = RestoreAudio(); } catch (Exception e) { Log($"release error {e.GetType().Name}"); }
        try { if (guardian is { HasExited: false }) guardian.Kill(); } catch (Exception) { /* already gone */ }
        WriteStatus(plan?.SessionId, AgentProtection.Idle, DateTimeOffset.UtcNow);
        Log($"released ({why}): restored {restored}; minimized windows left open");
    }

    private static string? Identify(IntPtr hwnd, out uint pid)
    {
        Native.GetWindowThreadProcessId(hwnd, out pid);
        if (pid == 0 || pid == (uint)Environment.ProcessId) return null;
        var path = Native.ProcessImagePath(pid);
        if (path is null) return null;
        var exe = Path.GetFileName(path);
        // Store apps are hosted by ApplicationFrameHost.exe; look through to the app's own process.
        if (exe.Equals("ApplicationFrameHost.exe", StringComparison.OrdinalIgnoreCase))
        {
            uint host = pid, child = 0;
            Native.EnumChildWindows(hwnd, (c, _) =>
            {
                Native.GetWindowThreadProcessId(c, out var p);
                if (p != host) { child = p; return false; }
                return true;
            }, IntPtr.Zero);
            if (child != 0 && Native.ProcessImagePath(child) is string cp) { pid = child; return Path.GetFileName(cp); }
        }
        return exe;
    }

    private static void WriteStatus(string? sessionId, AgentProtection p, DateTimeOffset now)
    {
        try
        {
            var tmp = AgentPaths.Status + ".tmp";
            File.WriteAllText(tmp, StatusCodec.Serialize(new AgentStatus(sessionId, p, now, elevatedSkipped, minimized, muted)));
            File.Move(tmp, AgentPaths.Status, overwrite: true);
        }
        catch (IOException) { }
        catch (UnauthorizedAccessException) { }
    }

    private static void Safe(Action a)
    {
        if (Volatile.Read(ref released) == 1) return;
        try { a(); } catch (Exception e) { Log($"error {e.GetType().Name}: {e.Message}"); }
    }

    private static readonly object LogGate = new();
    internal static void Log(string line)
    {
        lock (LogGate)
        {
            try
            {
                var fi = new FileInfo(AgentPaths.Log);
                if (fi.Exists && fi.Length > 512 * 1024) File.Move(AgentPaths.Log, AgentPaths.Log + ".1", overwrite: true);
                // App names and times only; no window titles or contents.
                File.AppendAllText(AgentPaths.Log, $"{DateTimeOffset.Now:O} {line}{Environment.NewLine}");
            }
            catch (IOException) { }
            catch (UnauthorizedAccessException) { }
        }
    }
}
