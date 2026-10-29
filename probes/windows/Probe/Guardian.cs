using System.Diagnostics;

namespace LockdInProbe;

/// <summary>
/// Crash-recovery guardian. A second, windowless LockdInProbe process that waits
/// for the main probe to exit and then restores any audio the probe muted
/// (from the shared ledger). It enforces nothing and holds no restriction
/// itself: if the probe ends cleanly, the ledger is already empty.
///
/// Limits (tested separately in TESTER.md): if the guardian and the probe are
/// both killed (e.g. "End process tree", power loss), nothing restores audio
/// until the next probe start or `LockdInProbe --recover`.
/// </summary>
internal static class Guardian
{
    /// <summary>Starts the guardian for the current process. Returns null if it could not start.</summary>
    public static Process? Start()
    {
        try
        {
            var self = Process.GetCurrentProcess();
            var exe = Environment.ProcessPath!;
            var args = new List<string>();
            // Under `dotnet run`/`dotnet LockdInProbe.dll` the host is dotnet.exe; pass the DLL too.
            if (Path.GetFileNameWithoutExtension(exe).Equals("dotnet", StringComparison.OrdinalIgnoreCase))
                args.Add(typeof(Guardian).Assembly.Location);
            args.Add("--guardian");
            args.Add(self.Id.ToString(System.Globalization.CultureInfo.InvariantCulture));
            args.Add(self.StartTime.ToUniversalTime().Ticks.ToString(System.Globalization.CultureInfo.InvariantCulture));
            var psi = new ProcessStartInfo(exe) { UseShellExecute = false, CreateNoWindow = true };
            foreach (var a in args) psi.ArgumentList.Add(a);
            return Process.Start(psi);
        }
        catch
        {
            return null;
        }
    }

    /// <summary>Guardian entry point: wait for the probe, then restore.</summary>
    public static int Run(int parentPid, long parentStartTicksUtc)
    {
        using var log = new JsonLog("guardian");
        var ledger = new MuteLedger(Path.Combine(JsonLog.Directory, "mute-ledger.json"));
        log.Write("guardian_watching", new { parent_pid = parentPid });
        try
        {
            using var parent = Process.GetProcessById(parentPid);
            // Guard against PID reuse: only watch the exact process that started us.
            if (parent.StartTime.ToUniversalTime().Ticks != parentStartTicksUtc)
            {
                log.Write("guardian_parent_mismatch");
            }
            else
            {
                parent.WaitForExit();
            }
        }
        catch (ArgumentException)
        {
            // Parent already gone.
        }
        catch (Exception e)
        {
            log.Write("guardian_wait_error", new { error = e.GetType().Name });
        }

        var sw = Stopwatch.StartNew();
        int restored = 0;
        try { restored = ledger.RestoreAll(); }
        catch (Exception e) { log.Write("guardian_restore_error", new { error = e.GetType().Name, e.Message }); }
        log.Write("guardian_restored", new { restored_sessions = restored, ms_after_probe_exit = sw.ElapsedMilliseconds });
        return 0;
    }
}
