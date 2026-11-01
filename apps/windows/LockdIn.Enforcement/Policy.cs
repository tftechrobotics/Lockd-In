namespace LockdIn.Enforcement;

/// <summary>What the agent should do with a window, decided from pure inputs.</summary>
public static class Policy
{
    /// <summary>
    /// Never minimized or muted, whatever the rules: the Windows shell and its system
    /// surfaces, sign-in/UAC/credential prompts, accessibility tools, Task Manager and
    /// Settings (so the user keeps control of their PC), and Lockd In itself.
    /// </summary>
    public static readonly IReadOnlySet<string> AlwaysAllowed = new HashSet<string>(StringComparer.OrdinalIgnoreCase)
    {
        "explorer.exe", "ShellExperienceHost.exe", "StartMenuExperienceHost.exe", "SearchHost.exe", "SearchApp.exe",
        "TextInputHost.exe", "LockApp.exe", "ShellHost.exe", "sihost.exe", "dwm.exe", "csrss.exe", "winlogon.exe",
        "consent.exe", "CredentialUIBroker.exe", "LogonUI.exe", "Taskmgr.exe", "SystemSettings.exe",
        "ApplicationFrameHost.exe", "SystemSettingsBroker.exe", "SecHealthUI.exe",
        "Narrator.exe", "Magnify.exe", "osk.exe", "TabTip.exe", "ctfmon.exe",
        "LockdIn.exe", "LockdIn.Agent.exe", "msedgewebview2.exe",
    };

    /// <summary>An app is identified by its executable file name, e.g. "Discord.exe".</summary>
    public static bool IsValidAppId(string s) =>
        s.Length is > 4 and <= 128 && s.EndsWith(".exe", StringComparison.OrdinalIgnoreCase)
        && s.IndexOfAny(['\\', '/', ':', '*', '?', '"', '<', '>', '|']) < 0 && s.Trim() == s;

    public static bool IsRestricted(string? exeName, Plan plan)
    {
        if (string.IsNullOrEmpty(exeName) || AlwaysAllowed.Contains(exeName)) return false;
        return plan.Mode switch
        {
            RuleMode.BlockList => plan.Apps.Contains(exeName),
            RuleMode.ApprovedOnly => !plan.Apps.Contains(exeName),
            _ => false,
        };
    }

    /// <summary>True while `now` falls inside a restriction interval.</summary>
    public static bool Restricting(Plan plan, DateTimeOffset now) =>
        plan.RestrictsAnything && plan.Intervals.Any(i => i.Start <= now && now < i.End);

    /// <summary>The next instant restriction starts or stops, or null when the plan is over.</summary>
    public static DateTimeOffset? NextChange(Plan plan, DateTimeOffset now)
    {
        DateTimeOffset? best = null;
        foreach (var i in plan.Intervals)
            foreach (var t in new[] { i.Start, i.End })
                if (t > now && (best is null || t < best)) best = t;
        return best;
    }

    /// <summary>The agent may exit: nothing left to enforce.</summary>
    public static bool Finished(Plan plan, DateTimeOffset now) => !plan.Intervals.Any(i => i.End > now) || !plan.RestrictsAnything;
}
