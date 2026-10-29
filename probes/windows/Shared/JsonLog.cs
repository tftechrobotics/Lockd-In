using System.Text;
using System.Text.Json;

namespace LockdInProbe;

/// <summary>
/// Local JSON-lines log under %LOCALAPPDATA%\LockdInProbe. Nothing is uploaded.
/// Entries contain executable names, timings and outcomes — never window
/// titles or content. Executable names can still reveal what someone uses.
/// </summary>
internal sealed class JsonLog : IDisposable
{
    private readonly StreamWriter _writer;
    private readonly object _lock = new();

    public string Path { get; }

    public static string Directory
    {
        get
        {
            var dir = System.IO.Path.Combine(
                Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "LockdInProbe");
            System.IO.Directory.CreateDirectory(dir);
            return dir;
        }
    }

    public JsonLog(string prefix)
    {
        Path = System.IO.Path.Combine(Directory, $"{prefix}-{DateTime.UtcNow:yyyyMMdd-HHmmss}.jsonl");
        _writer = new StreamWriter(Path, append: false, Encoding.UTF8) { AutoFlush = true };
    }

    public void Write(string evt, object? data = null)
    {
        var line = JsonSerializer.Serialize(new
        {
            t = DateTime.UtcNow.ToString("O"),
            mono_ms = Environment.TickCount64,
            evt,
            data,
        });
        lock (_lock)
        {
            if (_disposed) return; // late timer callbacks after release are dropped, not crashes
            _writer.WriteLine(line);
        }
    }

    private bool _disposed;

    public void Dispose()
    {
        lock (_lock)
        {
            if (_disposed) return;
            _disposed = true;
            _writer.Dispose();
        }
    }
}
