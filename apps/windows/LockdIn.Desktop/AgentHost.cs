// The shell's side of the blocking agent: hands it the plan, starts it, reads its status.
// The agent is a separate process (LockdIn.Agent.exe next to LockdIn.exe), so blocking keeps
// running when this window is closed or the app quits.
using System.Diagnostics;
using LockdIn.Enforcement;

namespace LockdIn.Desktop;

internal static class AgentHost
{
    private static string Dir => Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "LockdIn", "agent");
    private static string PlanPath => Path.Combine(Dir, "plan.json");
    private static string StatusPath => Path.Combine(Dir, "status.json");
    private static string Exe => Path.Combine(AppContext.BaseDirectory, "LockdIn.Agent.exe");
    private const string MutexName = @"Local\LockdIn.Agent";

    public static bool Installed => File.Exists(Exe);

    public static bool Running
    {
        get
        {
            try { if (Mutex.TryOpenExisting(MutexName, out var m)) { m.Dispose(); return true; } }
            catch (UnauthorizedAccessException) { return true; }
            return false;
        }
    }

    /// <summary>Writes the plan (atomically) and makes sure the agent is running if it has work.</summary>
    public static void Apply(Plan plan)
    {
        try
        {
            Directory.CreateDirectory(Dir);
            var tmp = PlanPath + ".tmp";
            File.WriteAllText(tmp, PlanCodec.Serialize(plan));
            File.Move(tmp, PlanPath, overwrite: true);
            Log.Write($"plan written: session={plan.SessionId} intervals={plan.Intervals.Count} apps={plan.Apps.Count}");
        }
        catch (Exception e) when (e is IOException or UnauthorizedAccessException)
        {
            Log.Write($"plan write failed: {e.GetType().Name}");
            return;
        }
        if (!Policy.Finished(plan, DateTimeOffset.UtcNow)) EnsureRunning();
    }

    /// <summary>
    /// Starts the agent if it isn't running. With no plan to enforce it only restores any sound
    /// a crashed earlier run left muted, then exits. Called at app start, too.
    /// </summary>
    public static void EnsureRunning()
    {
        if (!Installed || Running) return;
        try
        {
            Process.Start(new ProcessStartInfo(Exe) { UseShellExecute = false, CreateNoWindow = true, WorkingDirectory = AppContext.BaseDirectory })?.Dispose();
            Log.Write("agent started");
        }
        catch (Exception e) when (e is System.ComponentModel.Win32Exception or InvalidOperationException or IOException)
        {
            Log.Write($"agent start failed: {e.GetType().Name}");
        }
    }

    public static AgentStatus? ReadStatus()
    {
        try { return File.Exists(StatusPath) ? StatusCodec.Parse(File.ReadAllText(StatusPath)) : null; }
        catch (IOException) { return null; }
        catch (UnauthorizedAccessException) { return null; }
    }

    /// <summary>Apps with a visible window right now, for choosing apps in a profile.</summary>
    public static IEnumerable<(string Exe, string Name)> OpenApps()
    {
        foreach (var p in Process.GetProcesses())
        {
            using (p)
            {
                string? exe = null, name = null;
                try
                {
                    if (p.MainWindowHandle == IntPtr.Zero || p.SessionId != Process.GetCurrentProcess().SessionId) continue;
                    var path = p.MainModule?.FileName;
                    if (path is null) continue;
                    exe = Path.GetFileName(path);
                    var desc = FileVersionInfo.GetVersionInfo(path).FileDescription;
                    name = string.IsNullOrWhiteSpace(desc) ? Path.GetFileNameWithoutExtension(path) : desc.Trim();
                }
                catch (Exception e) when (e is System.ComponentModel.Win32Exception or InvalidOperationException or NotSupportedException)
                {
                    continue; // elevated or exited
                }
                if (exe is not null && name is not null) yield return (exe, name);
            }
        }
    }
}
