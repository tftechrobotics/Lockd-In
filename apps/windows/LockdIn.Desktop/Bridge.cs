// Versioned message contract between the shell and the shared interface
// (ADR-0009). Everything from the page is untrusted: unknown versions, types or
// shapes are dropped, strings are length-limited, numbers range-checked.
// Pure code (no Windows APIs) so it is unit-tested on any OS.
using System.Text.Json;

namespace LockdIn.Desktop;

public abstract record UiMessage
{
    public sealed record Ready : UiMessage;
    public sealed record SessionState(bool Active, DateTimeOffset? EndsAt, string Label) : UiMessage;
    public sealed record Theme(bool Dark) : UiMessage;
}

public enum AgentState { NotInstalled, Stopped, Running, Unknown }

public static class Bridge
{
    public const int Version = 1;
    public const int MaxLabel = 120;
    public const int MaxMessageBytes = 4096;

    public static UiMessage? Parse(string json)
    {
        if (string.IsNullOrEmpty(json) || json.Length > MaxMessageBytes) return null;
        try
        {
            using var doc = JsonDocument.Parse(json, new JsonDocumentOptions { MaxDepth = 4 });
            var root = doc.RootElement;
            if (root.ValueKind != JsonValueKind.Object) return null;
            if (!root.TryGetProperty("v", out var v) || v.ValueKind != JsonValueKind.Number || !v.TryGetInt32(out var ver) || ver != Version) return null;
            if (!root.TryGetProperty("type", out var t) || t.ValueKind != JsonValueKind.String) return null;
            switch (t.GetString())
            {
                case "ui.ready":
                    return new UiMessage.Ready();
                case "ui.theme":
                    return root.TryGetProperty("dark", out var d) && d.ValueKind is JsonValueKind.True or JsonValueKind.False
                        ? new UiMessage.Theme(d.GetBoolean()) : null;
                case "ui.session_state":
                    if (!root.TryGetProperty("active", out var a) || a.ValueKind is not (JsonValueKind.True or JsonValueKind.False)) return null;
                    if (!root.TryGetProperty("label", out var l) || l.ValueKind != JsonValueKind.String) return null;
                    var label = l.GetString() ?? "";
                    if (label.Length > MaxLabel) return null;
                    DateTimeOffset? ends = null;
                    if (root.TryGetProperty("endsAt", out var e) && e.ValueKind == JsonValueKind.Number)
                    {
                        if (!e.TryGetInt64(out var ms) || ms < 0 || ms > 32503680000000) return null; // ≤ year 3000
                        ends = DateTimeOffset.FromUnixTimeMilliseconds(ms);
                    }
                    else if (root.TryGetProperty("endsAt", out var e2) && e2.ValueKind != JsonValueKind.Null) return null;
                    return new UiMessage.SessionState(a.GetBoolean(), ends, label);
                default:
                    return null;
            }
        }
        catch (JsonException)
        {
            return null;
        }
    }

    public static string Hello(string appVersion, AgentState agent) => JsonSerializer.Serialize(new
    {
        v = Version,
        type = "host.hello",
        appVersion,
        agent = agent switch
        {
            AgentState.NotInstalled => "not_installed",
            AgentState.Stopped => "stopped",
            AgentState.Running => "running",
            _ => "unknown",
        },
    });

    /// <summary>A request from the shell to the interface. Only listed commands exist.</summary>
    public static string Command(string command) => command switch
    {
        "start_quick" => JsonSerializer.Serialize(new { v = Version, type = "host.command", command }),
        _ => throw new ArgumentOutOfRangeException(nameof(command)),
    };

    /// <summary>Tray text for the current session; plain words, no claims about blocking.</summary>
    public static string TrayStatus(UiMessage.SessionState? s, DateTimeOffset now, Func<DateTimeOffset, string> formatTime)
    {
        if (s is null || !s.Active || (s.EndsAt is { } end && end <= now)) return "No session running";
        var until = s.EndsAt is { } t ? $" until {formatTime(t)}" : "";
        return string.IsNullOrWhiteSpace(s.Label) ? $"Session running{until}" : $"{s.Label}{until}";
    }
}
