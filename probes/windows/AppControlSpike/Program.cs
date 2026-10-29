// SPIKE ONLY (M1, risk R6b). Never deploys: there is no CiTool call anywhere.
//   build --deny <OriginalFileName>[=<label>] ... --out <dir> [--policy-id <guid>]
//       writes policy.xml (for ConvertFrom-CIPolicy) and lockdin.cip (our compiler)
//   describe <file.cip>   parses a binary policy and prints a JSON summary
//   selftest              runs the checks that need no Windows
using System.Text.Json;
using AppControlManager.SiPolicy;
using LockdIn.AppControlSpike;

return args.FirstOrDefault() switch
{
    "build" => Build(args[1..]),
    "describe" => Describe(args[1]),
    "selftest" => SelfTest(),
    _ => Usage(),
};

static int Usage()
{
    Console.Error.WriteLine("usage: build --deny NAME[=LABEL]... --out DIR [--policy-id GUID] | describe FILE.cip | selftest");
    return 2;
}

static int Build(string[] a)
{
    List<DenyTarget> targets = [];
    string? outDir = null;
    Guid id = Guid.NewGuid();
    for (int i = 0; i < a.Length; i++)
    {
        switch (a[i])
        {
            case "--deny":
                string[] parts = a[++i].Split('=', 2);
                targets.Add(new DenyTarget(parts[0], parts.Length > 1 ? parts[1] : parts[0]));
                break;
            case "--out": outDir = a[++i]; break;
            case "--policy-id": id = Guid.Parse(a[++i]); break;
            default: return Usage();
        }
    }
    if (outDir is null) return Usage();
    Directory.CreateDirectory(outDir);
    SiPolicy p = PolicyBuilder.Build(id, "Lockd In session (spike)", targets);
    // Serialize XML before compiling: the compiler may normalize fields in place.
    AppControlManager.SiPolicy.CustomSerialization.CreateXmlFromSiPolicy(p).Save(Path.Combine(outDir, "policy.xml"));
    byte[] bin = Compile(PolicyBuilder.Build(id, "Lockd In session (spike)", targets));
    File.WriteAllBytes(Path.Combine(outDir, "lockdin.cip"), bin);
    Console.WriteLine(JsonSerializer.Serialize(new { policy_id = id.ToString("B").ToUpperInvariant(), bytes = bin.Length, denies = targets.Select(t => t.OriginalFileName) }));
    return 0;
}

static byte[] Compile(SiPolicy p)
{
    using MemoryStream ms = new();
    BinaryOpsForward.ConvertPolicyToBinary(p, ms);
    return ms.ToArray();
}

static object Summarize(SiPolicy p) => new
{
    policy_id = p.PolicyID,
    base_policy_id = p.BasePolicyID,
    options = p.Rules.Select(r => r.Item.ToString()).OrderBy(x => x).ToArray(),
    allow_file_names = (p.FileRules ?? []).OfType<Allow>().Select(r => r.FileName).ToArray(),
    deny_file_names = (p.FileRules ?? []).OfType<Deny>().Select(r => $"{r.FileName}<={r.MinimumFileVersion}").ToArray(),
    scenarios = (p.SigningScenarios ?? []).Select(s => new { s.Value, rules = s.ProductSigners.FileRulesRef?.FileRuleRef.Count ?? 0 }).ToArray(),
};

static SiPolicy Parse(byte[] bin)
{
    using BinaryReader r = new(new MemoryStream(bin));
    return BinaryOpsReverse.ParseSiPolicy(r, static k => k);
}

static int Describe(string path)
{
    Console.WriteLine(JsonSerializer.Serialize(Summarize(Parse(File.ReadAllBytes(path)))));
    return 0;
}

static int SelfTest()
{
    int failed = 0;
    void Check(string name, bool ok) { Console.WriteLine($"{(ok ? "pass" : "FAIL")}  {name}"); if (!ok) failed++; }

    Guid id = Guid.Parse("6d6b2f1e-1c5a-4b8e-9a51-0f3c2d7e9b10");
    DenyTarget[] targets = [new("steam.exe", "Steam"), new("Discord.exe", "Discord")];
    byte[] a = Compile(PolicyBuilder.Build(id, "t", targets));
    byte[] b = Compile(PolicyBuilder.Build(id, "t", targets));
    Check("compiles to a non-empty binary", a.Length > 64);
    Check("compilation is deterministic", a.AsSpan().SequenceEqual(b));
    Check("header version is 11", BitConverter.ToUInt32(a, 0) == 11);

    SiPolicy back = Parse(a);
    var denies = (back.FileRules ?? []).OfType<Deny>().ToList();
    Check("both deny rules survive a binary round trip", denies.Select(d => d.FileName).Order().SequenceEqual(["Discord.exe", "steam.exe"]));
    Check("deny rules cover every version", denies.All(d => d.MinimumFileVersion == PolicyBuilder.AllVersions));
    Check("allow-all rules survive", (back.FileRules ?? []).OfType<Allow>().Count(x => x.FileName == "*") == 2);
    HashSet<OptionType> opts = [.. back.Rules.Select(r => r.Item)];
    Check("policy is unsigned (removable)", opts.Contains(OptionType.EnabledUnsignedSystemIntegrityPolicy));
    Check("update/remove without reboot", opts.Contains(OptionType.EnabledUpdatePolicyNoReboot));
    Check("F8 recovery menu stays enabled", opts.Contains(OptionType.EnabledAdvancedBootOptionsMenu));
    Check("not audit mode (enforces)", !opts.Contains(OptionType.EnabledAuditMode));
    Check("user-mode scenario holds allow-all + 2 denies",
        (back.SigningScenarios ?? []).Any(s => s.Value == 12 && s.ProductSigners.FileRulesRef?.FileRuleRef.Count == 3));
    Check("policy id round-trips", string.Equals(back.BasePolicyID.Trim('{', '}'), id.ToString(), StringComparison.OrdinalIgnoreCase));

    static bool Throws(Action f) { try { f(); return false; } catch (ArgumentException) { return true; } }
    Check("refuses wildcard names", Throws(() => PolicyBuilder.Build(id, "t", [new("*", "x")])));
    Check("refuses paths", Throws(() => PolicyBuilder.Build(id, "t", [new(@"C:\Windows\x.exe", "x")])));
    Check("refuses Explorer", Throws(() => PolicyBuilder.Build(id, "t", [new("EXPLORER.EXE", "x")])));
    Check("refuses Task Manager", Throws(() => PolicyBuilder.Build(id, "t", [new("Taskmgr.exe", "x")])));
    Check("refuses screen reader", Throws(() => PolicyBuilder.Build(id, "t", [new("narrator.exe", "x")])));
    Check("refuses empty list", Throws(() => PolicyBuilder.Build(id, "t", [])));

    string xml = AppControlManager.SiPolicy.CustomSerialization.CreateXmlFromSiPolicy(PolicyBuilder.Build(id, "t", targets)).OuterXml;
    Check("XML has the deny rules", xml.Contains("FileName=\"steam.exe\"") && xml.Contains("FileName=\"Discord.exe\""));
    Check("XML is in the SiPolicy namespace", xml.Contains("urn:schemas-microsoft-com:sipolicy"));

    Console.WriteLine(failed == 0 ? "ALL SPIKE SELF-TESTS PASSED" : $"{failed} FAILED");
    return failed == 0 ? 0 : 1;
}
