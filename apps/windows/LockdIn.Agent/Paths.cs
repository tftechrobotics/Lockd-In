namespace LockdIn.Agent;

/// <summary>Files shared with the app, under %LOCALAPPDATA%\LockdIn\agent (this user only).</summary>
public static class AgentPaths
{
    public static string Dir => Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "LockdIn", "agent");
    public static string Plan => Path.Combine(Dir, "plan.json");
    public static string Status => Path.Combine(Dir, "status.json");
    public static string Ledger => Path.Combine(Dir, "mute-ledger.json");
    public static string Log => Path.Combine(Dir, "agent.log");
    /// <summary>Held while the agent runs; the app checks it to know the agent is alive.</summary>
    public const string MutexName = @"Local\LockdIn.Agent";
}
