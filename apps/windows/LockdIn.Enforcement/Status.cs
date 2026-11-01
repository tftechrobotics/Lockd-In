// What the agent reports back (status.json), read by the shell and passed to the interface.
// The interface shows "Blocking active" only for a fresh Protecting report for its session.
using System.Text.Json;

namespace LockdIn.Enforcement;

public enum AgentProtection { Idle, NotRestricting, Protecting, Degraded }

public sealed record AgentStatus(
    string? SessionId,
    AgentProtection Protection,
    DateTimeOffset At,
    int ElevatedSkipped,
    int Minimized,
    int Muted)
{
    /// <summary>The agent writes at least every second; older than this means it isn't running.</summary>
    public static readonly TimeSpan Fresh = TimeSpan.FromSeconds(5);

    public bool IsFresh(DateTimeOffset now) => now - At <= Fresh && At - now <= Fresh;
}

public static class StatusCodec
{
    public static string Serialize(AgentStatus s) => JsonSerializer.Serialize(new
    {
        v = 1,
        sessionId = s.SessionId,
        protection = s.Protection switch
        {
            AgentProtection.Protecting => "protecting",
            AgentProtection.NotRestricting => "not_restricting",
            AgentProtection.Degraded => "degraded",
            _ => "idle",
        },
        at = s.At.ToUnixTimeMilliseconds(),
        elevatedSkipped = s.ElevatedSkipped,
        minimized = s.Minimized,
        muted = s.Muted,
    });

    public static AgentStatus? Parse(string json)
    {
        if (string.IsNullOrEmpty(json) || json.Length > 4096) return null;
        try
        {
            using var doc = JsonDocument.Parse(json, new JsonDocumentOptions { MaxDepth = 3 });
            var r = doc.RootElement;
            if (r.ValueKind != JsonValueKind.Object || !r.TryGetProperty("v", out var v) || !v.TryGetInt32(out var ver) || ver != 1) return null;
            string? sid = r.TryGetProperty("sessionId", out var s) && s.ValueKind == JsonValueKind.String ? s.GetString() : null;
            var p = r.TryGetProperty("protection", out var pr) && pr.ValueKind == JsonValueKind.String ? pr.GetString() switch
            {
                "protecting" => AgentProtection.Protecting,
                "not_restricting" => AgentProtection.NotRestricting,
                "degraded" => AgentProtection.Degraded,
                "idle" => AgentProtection.Idle,
                _ => (AgentProtection?)null,
            } : null;
            if (p is null || !r.TryGetProperty("at", out var at) || !at.TryGetInt64(out var ms) || ms < 0) return null;
            int N(string k) => r.TryGetProperty(k, out var x) && x.TryGetInt32(out var n) && n >= 0 ? n : 0;
            return new AgentStatus(sid, p.Value, DateTimeOffset.FromUnixTimeMilliseconds(ms), N("elevatedSkipped"), N("minimized"), N("muted"));
        }
        catch (JsonException) { return null; }
        catch (InvalidOperationException) { return null; }
    }
}
