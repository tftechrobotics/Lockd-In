// Records every audio session the agent muted, on disk, BEFORE muting it, so sound can always
// be restored: by the agent at a break or the end, by its guardian process if the agent
// crashes, or at the next start / `LockdIn.Agent --recover` after power loss. Only sessions
// the agent muted itself are recorded, so a user's own mute is never undone.
using System.Text.Json;

namespace LockdIn.Enforcement;

public sealed record MuteEntry(string InstanceId, string Exe, DateTimeOffset MutedAt);

public sealed class MuteLedger(string path)
{
    /// <summary>Entries whose app is gone are kept this long, then dropped (nothing left to restore).</summary>
    public static readonly TimeSpan KeepMissing = TimeSpan.FromHours(24);
    private readonly object gate = new();

    public IReadOnlyList<MuteEntry> Entries { get { lock (gate) return Load(); } }

    /// <summary>Call before muting. False if it couldn't be written: then don't mute.</summary>
    public bool Add(string instanceId, string exe, DateTimeOffset now)
    {
        lock (gate)
        {
            var e = Load();
            if (e.Any(x => x.InstanceId == instanceId)) return true;
            e.Add(new MuteEntry(instanceId, exe, now));
            return Save(e);
        }
    }

    /// <summary>
    /// Restores sound. `unmute` gets each live session that matches an entry (same instance,
    /// or the same app if Windows re-created the session) and returns true when unmuted.
    /// Entries for apps that aren't running are kept for <see cref="KeepMissing"/>.
    /// Returns how many sessions were unmuted.
    /// </summary>
    public int RestoreAll(IReadOnlyList<(string InstanceId, string? Exe, bool Muted)> live, Func<string, bool> unmute, DateTimeOffset now)
    {
        lock (gate)
        {
            var entries = Load();
            if (entries.Count == 0) return 0;
            int restored = 0;
            var keep = new List<MuteEntry>();
            foreach (var e in entries)
            {
                var matches = live.Where(s => s.Muted && (s.InstanceId == e.InstanceId ||
                    (s.Exe is not null && s.Exe.Equals(e.Exe, StringComparison.OrdinalIgnoreCase)))).ToList();
                if (matches.Count == 0)
                {
                    if (now - e.MutedAt < KeepMissing) keep.Add(e);
                    continue;
                }
                bool all = true;
                foreach (var m in matches) { if (unmute(m.InstanceId)) restored++; else all = false; }
                if (!all) keep.Add(e);
            }
            Save(keep);
            return restored;
        }
    }

    private bool Save(List<MuteEntry> entries)
    {
        try
        {
            Directory.CreateDirectory(Path.GetDirectoryName(Path.GetFullPath(path))!);
            var tmp = path + ".tmp";
            File.WriteAllText(tmp, JsonSerializer.Serialize(entries.Select(e => new { id = e.InstanceId, exe = e.Exe, at = e.MutedAt.ToUnixTimeMilliseconds() })));
            File.Move(tmp, path, overwrite: true);
            return true;
        }
        catch (IOException) { return false; }
        catch (UnauthorizedAccessException) { return false; }
    }

    private List<MuteEntry> Load()
    {
        try
        {
            if (!File.Exists(path)) return [];
            using var doc = JsonDocument.Parse(File.ReadAllText(path));
            if (doc.RootElement.ValueKind != JsonValueKind.Array) return [];
            var list = new List<MuteEntry>();
            foreach (var x in doc.RootElement.EnumerateArray().Take(1000))
            {
                if (x.ValueKind != JsonValueKind.Object) continue;
                if (!x.TryGetProperty("id", out var id) || id.ValueKind != JsonValueKind.String) continue;
                if (!x.TryGetProperty("exe", out var exe) || exe.ValueKind != JsonValueKind.String) continue;
                if (!x.TryGetProperty("at", out var at) || !at.TryGetInt64(out var ms) || ms < 0) continue;
                list.Add(new MuteEntry(id.GetString()!, exe.GetString()!, DateTimeOffset.FromUnixTimeMilliseconds(ms)));
            }
            return list;
        }
        catch (Exception e) when (e is IOException or UnauthorizedAccessException or JsonException or InvalidOperationException) { return []; }
    }
}
