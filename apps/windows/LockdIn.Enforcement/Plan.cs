// The restriction plan: what the interface tells the Windows agent to enforce.
// Written by the desktop shell to %LOCALAPPDATA%\LockdIn\agent\plan.json and read by the
// agent, which keeps enforcing it when the window is closed. It contains only absolute
// restriction intervals (computed by the shared session rules in the interface, so breaks,
// unlocked rounds and emergency recovery are already applied) and the app rules.
using System.Text.Json;

namespace LockdIn.Enforcement;

public enum RuleMode { BlockList, ApprovedOnly }

public sealed record Interval(DateTimeOffset Start, DateTimeOffset End);

public sealed record Plan(string SessionId, IReadOnlyList<Interval> Intervals, RuleMode Mode, IReadOnlySet<string> Apps, DateTimeOffset IssuedAt)
{
    /// <summary>A plan that restricts nothing (the session ended or has no rules).</summary>
    public static Plan Empty(string sessionId, DateTimeOffset now) => new(sessionId, [], RuleMode.BlockList, new HashSet<string>(), now);

    public bool RestrictsAnything => Mode == RuleMode.ApprovedOnly || Apps.Count > 0;
}

public static class PlanCodec
{
    public const int Version = 1;
    public const int MaxBytes = 64 * 1024;
    public const int MaxIntervals = 64;
    public const int MaxApps = 500;
    /// <summary>Same cap as the core: a plan never restricts for more than 12 hours in total span.</summary>
    public static readonly TimeSpan MaxSpan = TimeSpan.FromHours(12);

    /// <summary>Parses untrusted JSON. Returns null for anything invalid; never throws.</summary>
    public static Plan? Parse(string json)
    {
        if (string.IsNullOrEmpty(json) || json.Length > MaxBytes) return null;
        try
        {
            using var doc = JsonDocument.Parse(json, new JsonDocumentOptions { MaxDepth = 5 });
            var r = doc.RootElement;
            if (r.ValueKind != JsonValueKind.Object) return null;
            if (!r.TryGetProperty("v", out var v) || !v.TryGetInt32(out var ver) || ver != Version) return null;
            if (!r.TryGetProperty("sessionId", out var sid) || sid.ValueKind != JsonValueKind.String) return null;
            var sessionId = sid.GetString() ?? "";
            if (sessionId.Length is 0 or > 64) return null;
            if (!r.TryGetProperty("issuedAt", out var ia) || !TryTime(ia, out var issuedAt)) return null;

            if (!r.TryGetProperty("intervals", out var iv) || iv.ValueKind != JsonValueKind.Array || iv.GetArrayLength() > MaxIntervals) return null;
            var intervals = new List<Interval>();
            foreach (var pair in iv.EnumerateArray())
            {
                if (pair.ValueKind != JsonValueKind.Array || pair.GetArrayLength() != 2) return null;
                if (!TryTime(pair[0], out var s) || !TryTime(pair[1], out var e) || e <= s) return null;
                intervals.Add(new Interval(s, e));
            }
            intervals.Sort((a, b) => a.Start.CompareTo(b.Start));
            for (var i = 1; i < intervals.Count; i++) if (intervals[i].Start < intervals[i - 1].End) return null; // overlap
            if (intervals.Count > 0 && intervals[^1].End - intervals[0].Start > MaxSpan) return null;

            if (!r.TryGetProperty("mode", out var m) || m.ValueKind != JsonValueKind.String) return null;
            RuleMode mode = m.GetString() switch { "block_list" => RuleMode.BlockList, "approved_only" => RuleMode.ApprovedOnly, _ => (RuleMode)(-1) };
            if (!Enum.IsDefined(mode)) return null;

            if (!r.TryGetProperty("apps", out var a) || a.ValueKind != JsonValueKind.Array || a.GetArrayLength() > MaxApps) return null;
            var apps = new HashSet<string>(StringComparer.OrdinalIgnoreCase);
            foreach (var app in a.EnumerateArray())
            {
                if (app.ValueKind != JsonValueKind.String) return null;
                var name = app.GetString() ?? "";
                if (!Policy.IsValidAppId(name)) return null;
                apps.Add(name);
            }
            return new Plan(sessionId, intervals, mode, apps, issuedAt);
        }
        catch (JsonException) { return null; }
        catch (InvalidOperationException) { return null; }
    }

    public static string Serialize(Plan p) => JsonSerializer.Serialize(new
    {
        v = Version,
        sessionId = p.SessionId,
        issuedAt = p.IssuedAt.ToUnixTimeMilliseconds(),
        intervals = p.Intervals.Select(i => new[] { i.Start.ToUnixTimeMilliseconds(), i.End.ToUnixTimeMilliseconds() }),
        mode = p.Mode == RuleMode.ApprovedOnly ? "approved_only" : "block_list",
        apps = p.Apps.OrderBy(x => x, StringComparer.OrdinalIgnoreCase),
    });

    private static bool TryTime(JsonElement e, out DateTimeOffset t)
    {
        t = default;
        if (e.ValueKind != JsonValueKind.Number || !e.TryGetInt64(out var ms) || ms < 0 || ms > 32503680000000) return false;
        t = DateTimeOffset.FromUnixTimeMilliseconds(ms);
        return true;
    }
}
