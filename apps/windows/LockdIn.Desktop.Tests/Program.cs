using LockdIn.Desktop;

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

Console.WriteLine(failed == 0 ? "ALL SHELL BRIDGE TESTS PASSED" : $"{failed} FAILED");
return failed == 0 ? 0 : 1;
