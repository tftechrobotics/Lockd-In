using System.Text.Json;

namespace LockdIn.Desktop;

/// <summary>Local, human-readable shell log (no app names or user content).</summary>
internal static class Log
{
    private static readonly string Dir = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "LockdIn", "logs");
    private static readonly object Gate = new();

    public static void Write(string line)
    {
        try
        {
            lock (Gate)
            {
                Directory.CreateDirectory(Dir);
                var file = Path.Combine(Dir, "shell.log");
                if (File.Exists(file) && new FileInfo(file).Length > 1_000_000) File.Move(file, file + ".old", true);
                File.AppendAllText(file, $"{DateTimeOffset.Now:O} {line}{Environment.NewLine}");
            }
        }
        catch (IOException) { /* logging must never break the app */ }
        catch (UnauthorizedAccessException) { }
    }
}

/// <summary>Small per-user shell preferences.</summary>
internal sealed class ShellSettings
{
    private static readonly string FilePath = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "LockdIn", "shell.json");
    public bool ToldAboutTray { get; set; }

    public static ShellSettings Load()
    {
        try { return JsonSerializer.Deserialize<ShellSettings>(File.ReadAllText(FilePath)) ?? new(); }
        catch (Exception e) when (e is IOException or UnauthorizedAccessException or JsonException) { return new(); }
    }

    public void Save()
    {
        try
        {
            Directory.CreateDirectory(Path.GetDirectoryName(FilePath)!);
            File.WriteAllText(FilePath, JsonSerializer.Serialize(this));
        }
        catch (Exception e) when (e is IOException or UnauthorizedAccessException) { Log.Write($"settings not saved: {e.GetType().Name}"); }
    }
}
