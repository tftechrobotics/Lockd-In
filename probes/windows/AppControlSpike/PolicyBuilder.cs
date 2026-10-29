// SPIKE ONLY. Builds the App Control policy Lockd In would deploy for one
// session: allow everything, deny only the apps the user chose to prevent.
// Nothing here deploys a policy; see README.md.
using System.Text.RegularExpressions;
using AppControlManager.SiPolicy;

namespace LockdIn.AppControlSpike;

/// <summary>One app to prevent from starting, identified by the OriginalFileName
/// in its version resource (survives renaming the .exe). Files without a
/// version resource need a hash rule instead (not in this spike).</summary>
internal sealed record DenyTarget(string OriginalFileName, string FriendlyName);

internal static partial class PolicyBuilder
{
    /// Platform GUID used by Microsoft's example policies.
    internal const string PlatformId = "{2E07F7E4-194C-4D20-B7C9-6F44A6C5A234}";

    /// For deny rules, this "minimum" version means every version is denied
    /// (as in Microsoft's recommended block rules).
    internal const string AllVersions = "65535.65535.65535.65535";

    /// <summary>The option set and why each is there.</summary>
    internal static readonly OptionType[] Options =
    [
        OptionType.EnabledUMCI,                          // apply to user-mode apps (the point)
        OptionType.EnabledUnsignedSystemIntegrityPolicy, // unsigned: removable with CiTool, no lock-in (R6)
        OptionType.EnabledUpdatePolicyNoReboot,          // apply and remove without a reboot
        OptionType.EnabledAdvancedBootOptionsMenu,       // keep F8 recovery available (safety)
        OptionType.DisabledScriptEnforcement,            // don't restrict the user's scripts
    ];

    [GeneratedRegex(@"^[A-Za-z0-9 ._()+\-]{1,128}\.(exe|com)$", RegexOptions.CultureInvariant)]
    private static partial Regex SafeFileName();

    internal static SiPolicy Build(Guid policyId, string friendlyName, IReadOnlyList<DenyTarget> targets)
    {
        if (targets.Count == 0) throw new ArgumentException("At least one app to deny is required.");
        if (targets.Count > 200) throw new ArgumentException("Too many deny rules for one session.");
        foreach (DenyTarget t in targets)
        {
            // Wildcards or paths here could deny far more than intended.
            if (!SafeFileName().IsMatch(t.OriginalFileName))
                throw new ArgumentException($"Refusing unsafe file name: '{t.OriginalFileName}'.");
            if (IsProtected(t.OriginalFileName))
                throw new ArgumentException($"Refusing to deny a Windows or recovery component: '{t.OriginalFileName}'.");
        }

        string id = policyId.ToString("B").ToUpperInvariant();
        List<RuleType> rules = [.. Options.Select(o => new RuleType(o))];
        SiPolicy p = new(
            versionEx: "10.0.0.0",
            platformID: PlatformId,
            policyID: id,
            basePolicyID: id,
            rules: rules,
            policyType: PolicyType.BasePolicy)
        {
            FriendlyName = friendlyName,
        };

        Allow allowKm = new("ID_ALLOW_ALL_KM") { FriendlyName = "Allow all kernel-mode files", FileName = "*" };
        Allow allowUm = new("ID_ALLOW_ALL_UM") { FriendlyName = "Allow all user-mode files", FileName = "*" };
        List<Deny> denies = [.. targets.Select((t, i) => new Deny($"ID_DENY_LOCKDIN_{i + 1}")
        {
            FriendlyName = $"Lockd In session: {t.FriendlyName}",
            FileName = t.OriginalFileName,
            MinimumFileVersion = AllVersions,
        })];

        p.FileRules = [allowKm, allowUm, .. denies];
        p.Signers = [];
        p.UpdatePolicySigners = [];
        p.CiSigners = [];
        p.HvciOptions = 0;
        p.Settings =
        [
            new Setting(new SettingValueType(friendlyName), "PolicyInfo", "Information", "Name"),
            new Setting(new SettingValueType("lockdin-session"), "PolicyInfo", "Information", "Id"),
        ];
        p.SigningScenarios =
        [
            new SigningScenario("ID_SIGNINGSCENARIO_KMCI", 131, new ProductSigners
            {
                FileRulesRef = new FileRulesRef([new FileRuleRef(allowKm.ID)]),
            }) { FriendlyName = "Kernel mode" },
            new SigningScenario("ID_SIGNINGSCENARIO_UMCI", 12, new ProductSigners
            {
                FileRulesRef = new FileRulesRef([new FileRuleRef(allowUm.ID), .. denies.Select(d => new FileRuleRef(d.ID))]),
            }) { FriendlyName = "User mode" },
        ];
        return p;
    }

    /// Files Lockd In must never deny: the shell, recovery and security tools,
    /// and itself. A real product would check signer identity too.
    internal static bool IsProtected(string fileName) => Protected.Contains(fileName);

    private static readonly HashSet<string> Protected = new(StringComparer.OrdinalIgnoreCase)
    {
        "explorer.exe", "taskmgr.exe", "cmd.exe", "powershell.exe", "pwsh.exe", "regedit.exe",
        "mmc.exe", "control.exe", "systemsettings.exe", "msconfig.exe", "citool.exe", "sfc.exe",
        "dism.exe", "rstrui.exe", "recoverydrive.exe", "winlogon.exe", "logonui.exe", "sihost.exe",
        "dwm.exe", "csrss.exe", "lsass.exe", "services.exe", "svchost.exe", "smss.exe", "wininit.exe",
        "narrator.exe", "magnify.exe", "osk.exe", "utilman.exe", "atbroker.exe", "nvda.exe",
        "lockdinprobe.exe", "lockdin.exe", "lockdinservice.exe", "lockdinagent.exe",
    };
}
