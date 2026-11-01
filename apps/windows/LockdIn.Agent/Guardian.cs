using System.Diagnostics;
using System.Globalization;

namespace LockdIn.Agent;

/// <summary>
/// A second, windowless copy of the agent that waits for the agent to exit and then restores
/// any sound it muted (from the ledger). It enforces nothing. If both are killed at once
/// (End process tree, power loss), sound is restored at the next agent start or
/// `LockdIn.Agent --recover`, which the app also runs at startup.
/// </summary>
internal static class Guardian
{
    public static Process? Start()
    {
        try
        {
            var self = Process.GetCurrentProcess();
            var psi = new ProcessStartInfo(Environment.ProcessPath!) { UseShellExecute = false, CreateNoWindow = true };
            psi.ArgumentList.Add("--guardian");
            psi.ArgumentList.Add(self.Id.ToString(CultureInfo.InvariantCulture));
            psi.ArgumentList.Add(self.StartTime.ToUniversalTime().Ticks.ToString(CultureInfo.InvariantCulture));
            return Process.Start(psi);
        }
        catch (Exception e) when (e is InvalidOperationException or System.ComponentModel.Win32Exception or IOException)
        {
            Program.Log($"guardian failed to start: {e.GetType().Name}");
            return null;
        }
    }

    public static int Run(string pidText, string ticksText)
    {
        if (!int.TryParse(pidText, NumberStyles.None, CultureInfo.InvariantCulture, out var pid)
            || !long.TryParse(ticksText, NumberStyles.None, CultureInfo.InvariantCulture, out var ticks)) return 2;
        try
        {
            using var p = Process.GetProcessById(pid);
            // Guard against PID reuse: only wait on the process that started us.
            if (p.StartTime.ToUniversalTime().Ticks != ticks) return 0;
            p.WaitForExit();
        }
        catch (ArgumentException) { /* already gone */ }
        catch (InvalidOperationException) { }
        var restored = Program.RestoreAudio();
        if (restored > 0) Program.Log($"guardian restored {restored} after the agent exited");
        return 0;
    }
}
