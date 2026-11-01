using LockdIn.Desktop;
using LockdIn.Enforcement;

int failed = 0;
void Check(string name, bool ok) { Console.WriteLine($"{(ok ? "pass" : "FAIL")}  {name}"); if (!ok) failed++; }

Check("ready", Bridge.Parse("""{"v":1,"type":"ui.ready"}""") is UiMessage.Ready);
Check("theme", Bridge.Parse("""{"v":1,"type":"ui.theme","dark":true}""") is UiMessage.Theme { Dark: true });
var s = Bridge.Parse("""{"v":1,"type":"ui.session_state","active":true,"endsAt":1793300000000,"label":"TAM 212"}""") as UiMessage.SessionState;
Check("session state", s is { Active: true, Label: "TAM 212" } && s.EndsAt == DateTimeOffset.FromUnixTimeMilliseconds(1793300000000));
Check("session state with null end", Bridge.Parse("""{"v":1,"type":"ui.session_state","active":false,"endsAt":null,"label":""}""") is UiMessage.SessionState { Active: false, EndsAt: null });
Check("wrong version", Bridge.Parse("""{"v":2,"type":"ui.ready"}""") is null);
Check("unknown type", Bridge.Parse("""{"v":1,"type":"ui.run_command","cmd":"calc"}""") is null);
Check("not an object", Bridge.Parse("""["ui.ready"]""") is null);
Check("malformed", Bridge.Parse("""{"v":1,"type":""") is null);
Check("label too long", Bridge.Parse($$"""{"v":1,"type":"ui.session_state","active":true,"label":"{{new string('x', 121)}}"}""") is null);
Check("negative time", Bridge.Parse("""{"v":1,"type":"ui.session_state","active":true,"endsAt":-5,"label":""}""") is null);
Check("string time", Bridge.Parse("""{"v":1,"type":"ui.session_state","active":true,"endsAt":"soon","label":""}""") is null);
Check("theme not bool", Bridge.Parse("""{"v":1,"type":"ui.theme","dark":"yes"}""") is null);
Check("oversized message", Bridge.Parse("{\"v\":1,\"type\":\"ui.ready\",\"pad\":\"" + new string('x', 5000) + "\"}") is null);
Check("deep nesting", Bridge.Parse("""{"v":1,"type":"ui.ready","a":{"b":{"c":{"d":{"e":1}}}}}""") is null);
Check("hello shape", Bridge.Hello("0.1.0", AgentState.NotInstalled) == """{"v":1,"type":"host.hello","appVersion":"0.1.0","agent":"not_installed"}""");
Check("tray: idle", Bridge.TrayStatus(null, DateTimeOffset.UnixEpoch, _ => "") == "No session running");
Check("tray: running", Bridge.TrayStatus(new UiMessage.SessionState(true, DateTimeOffset.UnixEpoch.AddHours(1), "TAM 212"), DateTimeOffset.UnixEpoch, _ => "2:59 PM") == "TAM 212 until 2:59 PM");
Check("tray: past its end", Bridge.TrayStatus(new UiMessage.SessionState(true, DateTimeOffset.UnixEpoch, "TAM 212"), DateTimeOffset.UnixEpoch.AddMinutes(1), _ => "") == "No session running");

Check("command shape", Bridge.Command("start_quick") == """{"v":1,"type":"host.command","command":"start_quick"}""");
bool threw = false; try { Bridge.Command("shutdown"); } catch (ArgumentOutOfRangeException) { threw = true; }
Check("unknown command refused", threw);

// ---- Agent rules (LockdIn.Enforcement) ----
var t0 = DateTimeOffset.FromUnixTimeMilliseconds(1_793_300_000_000);
long Ms(DateTimeOffset t) => t.ToUnixTimeMilliseconds();
string PlanJson(string intervals, string mode = "block_list", string apps = "[\"Discord.exe\"]", string sid = "s1") =>
    $$"""{"v":1,"sessionId":"{{sid}}","issuedAt":{{Ms(t0)}},"intervals":{{intervals}},"mode":"{{mode}}","apps":{{apps}}}""";
var twoRounds = $"[[{Ms(t0)},{Ms(t0.AddMinutes(25))}],[{Ms(t0.AddMinutes(30))},{Ms(t0.AddMinutes(55))}]]";
var plan = PlanCodec.Parse(PlanJson(twoRounds));
Check("plan parses", plan is { SessionId: "s1", Mode: RuleMode.BlockList } && plan.Intervals.Count == 2);
Check("plan round-trips", plan is not null && PlanCodec.Parse(PlanCodec.Serialize(plan)) is { } p2 && p2.Intervals.SequenceEqual(plan.Intervals) && p2.Apps.SetEquals(plan.Apps));
Check("restricting in focus", plan is not null && Policy.Restricting(plan, t0.AddMinutes(10)));
Check("released in the break", plan is not null && !Policy.Restricting(plan, t0.AddMinutes(27)));
Check("released at the end", plan is not null && !Policy.Restricting(plan, t0.AddMinutes(55)) && Policy.Finished(plan, t0.AddMinutes(55)));
Check("next change is the break", plan is not null && Policy.NextChange(plan, t0.AddMinutes(1)) == t0.AddMinutes(25));
Check("block list match ignores case", plan is not null && Policy.IsRestricted("discord.EXE", plan) && !Policy.IsRestricted("notepad.exe", plan));
var approved = PlanCodec.Parse(PlanJson(twoRounds, "approved_only", "[\"Code.exe\"]"))!;
Check("approved only", Policy.IsRestricted("Discord.exe", approved) && !Policy.IsRestricted("code.exe", approved));
Check("shell, prompts, Task Manager, Settings and Lockd In never restricted",
    new[] { "explorer.exe", "consent.exe", "Taskmgr.exe", "SystemSettings.exe", "LockdIn.exe", "LockdIn.Agent.exe", "Narrator.exe" }
        .All(x => !Policy.IsRestricted(x, approved)));
Check("unknown process not restricted", !Policy.IsRestricted(null, approved) && !Policy.IsRestricted("", approved));
Check("empty block list restricts nothing", PlanCodec.Parse(PlanJson(twoRounds, apps: "[]")) is { } e && !Policy.Restricting(e, t0.AddMinutes(1)) && Policy.Finished(e, t0));
Check("overlapping intervals refused", PlanCodec.Parse(PlanJson($"[[{Ms(t0)},{Ms(t0.AddMinutes(30))}],[{Ms(t0.AddMinutes(20))},{Ms(t0.AddMinutes(40))}]]")) is null);
Check("empty interval refused", PlanCodec.Parse(PlanJson($"[[{Ms(t0)},{Ms(t0)}]]")) is null);
Check("over 12 h refused", PlanCodec.Parse(PlanJson($"[[{Ms(t0)},{Ms(t0.AddHours(12).AddMinutes(1))}]]")) is null);
Check("bad app id refused", PlanCodec.Parse(PlanJson(twoRounds, apps: "[\"C:\\\\x\\\\evil.exe\"]")) is null
    && PlanCodec.Parse(PlanJson(twoRounds, apps: "[\"notepad\"]")) is null);
Check("unknown mode refused", PlanCodec.Parse(PlanJson(twoRounds, "kill_all")) is null);
Check("wrong version refused", PlanCodec.Parse(PlanJson(twoRounds).Replace("\"v\":1", "\"v\":2")) is null);
Check("garbage refused", PlanCodec.Parse("{") is null && PlanCodec.Parse("[]") is null && PlanCodec.Parse("") is null);

var st = new AgentStatus("s1", AgentProtection.Protecting, t0, 1, 3, 2);
Check("status round-trips", StatusCodec.Parse(StatusCodec.Serialize(st)) == st);
Check("status freshness", st.IsFresh(t0.AddSeconds(4)) && !st.IsFresh(t0.AddSeconds(6)) && !st.IsFresh(t0.AddSeconds(-6)));
Check("status garbage refused", StatusCodec.Parse("""{"v":1,"protection":"maybe","at":1}""") is null);

var ledgerPath = Path.Combine(Path.GetTempPath(), $"lockdin-ledger-{Guid.NewGuid():N}.json");
var ledger = new MuteLedger(ledgerPath);
Check("ledger add", ledger.Add("sess-a", "Discord.exe", t0) && ledger.Add("sess-b", "Spotify.exe", t0) && ledger.Add("sess-a", "Discord.exe", t0));
Check("ledger survives restart", new MuteLedger(ledgerPath).Entries.Select(x => x.InstanceId).Order().SequenceEqual(["sess-a", "sess-b"]));
var unmuted = new List<string>();
// Discord's session was re-created with a new id (app restarted); Spotify isn't running.
var live = new List<(string, string?, bool)> { ("sess-new", "discord.exe", true), ("user-muted", "Teams.exe", true) };
var n = new MuteLedger(ledgerPath).RestoreAll(live, id => { unmuted.Add(id); return true; }, t0.AddMinutes(5));
Check("ledger restores by app when the session was re-created", n == 1 && unmuted.SequenceEqual(["sess-new"]));
Check("ledger never unmutes what it didn't mute", !unmuted.Contains("user-muted"));
Check("ledger keeps entries for apps not running", new MuteLedger(ledgerPath).Entries.Select(x => x.InstanceId).SequenceEqual(["sess-b"]));
Check("ledger drops stale entries after 24 h", new MuteLedger(ledgerPath).RestoreAll([], _ => true, t0.AddHours(25)) == 0 && new MuteLedger(ledgerPath).Entries.Count == 0);
File.WriteAllText(ledgerPath, "not json");
Check("corrupt ledger reads empty", new MuteLedger(ledgerPath).Entries.Count == 0);
File.Delete(ledgerPath);

Console.WriteLine(failed == 0 ? "ALL SHELL BRIDGE TESTS PASSED" : $"{failed} FAILED");
return failed == 0 ? 0 : 1;
