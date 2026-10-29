// LockdInTarget — a harmless app for testing LockdInProbe. EXPERIMENT ONLY.
//
// It holds no user data. It shows a small window, optionally plays a quiet
// looping tone, and logs from its own point of view what happened to it:
// when it was activated, minimized, restored, muted or closed. That gives an
// independent measurement of how long a restricted app stayed usable
// ("exposure") and whether its sound really stopped.

using System.Runtime.InteropServices;

namespace LockdInProbe;

internal static class Program
{
    private static JsonLog _log = null!;
    private static Native.WndProc? _wndProc;
    private static long? _usableSinceMs;
    private static bool? _lastMute;
    private static GCHandle _wavHandle;

    [STAThread]
    private static int Main(string[] args)
    {
        if (!OperatingSystem.IsWindows()) return 1;
        bool silent = args.Contains("--silent");
        _log = new JsonLog("target");
        _log.Write("target_start", new { pid = Environment.ProcessId, silent });

        _wndProc = WindowProc;
        var hInstance = Native.GetModuleHandleW(null);
        var wc = new Native.WNDCLASSEX
        {
            cbSize = (uint)Marshal.SizeOf<Native.WNDCLASSEX>(),
            lpfnWndProc = Marshal.GetFunctionPointerForDelegate(_wndProc),
            hInstance = hInstance,
            hCursor = Native.LoadCursorW(IntPtr.Zero, 32512), // IDC_ARROW
            hbrBackground = Native.COLOR_WINDOW + 1,
            lpszClassName = "LockdInTargetWindow",
        };
        if (Native.RegisterClassExW(ref wc) == 0) return 3;

        var hwnd = Native.CreateWindowExW(0, "LockdInTargetWindow", "Lockd In test target (harmless)",
            Native.WS_OVERLAPPEDWINDOW | Native.WS_VISIBLE, Native.CW_USEDEFAULT, Native.CW_USEDEFAULT, 520, 200,
            IntPtr.Zero, IntPtr.Zero, hInstance, IntPtr.Zero);
        if (hwnd == IntPtr.Zero) return 4;
        Native.CreateWindowExW(0, "STATIC",
            "This is a harmless test app for the Lockd In probe.\r\nIt holds no data. Close it any time.\r\n" +
            (silent ? "Sound: off (--silent)." : "Sound: a quiet tone plays while it runs."),
            Native.WS_CHILD | Native.WS_VISIBLE, 16, 16, 480, 80, hwnd, IntPtr.Zero, hInstance, IntPtr.Zero);
        Native.ShowWindow(hwnd, Native.SW_SHOW);
        Native.UpdateWindow(hwnd);

        if (!silent) StartTone();
        using var muteWatch = new Timer(_ => WatchMute(), null, TimeSpan.FromSeconds(1), TimeSpan.FromSeconds(1));

        while (Native.GetMessageW(out var msg, IntPtr.Zero, 0, 0) > 0)
        {
            Native.TranslateMessage(ref msg);
            Native.DispatchMessageW(ref msg);
        }
        Native.PlaySoundW(IntPtr.Zero, IntPtr.Zero, 0);
        if (_wavHandle.IsAllocated) _wavHandle.Free();
        _log.Write("target_exit");
        _log.Dispose();
        return 0;
    }

    private static IntPtr WindowProc(IntPtr hWnd, uint msg, IntPtr wParam, IntPtr lParam)
    {
        switch (msg)
        {
            case Native.WM_ACTIVATE:
                bool active = (wParam.ToInt64() & 0xFFFF) != 0;
                if (active) MarkUsable("activated"); else EndUsable("deactivated");
                break;
            case Native.WM_SIZE:
                int kind = (int)wParam.ToInt64();
                if (kind == Native.SIZE_MINIMIZED) EndUsable("minimized");
                else if (kind == Native.SIZE_RESTORED) _log.Write("restored");
                break;
            case Native.WM_DESTROY:
                EndUsable("closing");
                Native.PostQuitMessage(0);
                return IntPtr.Zero;
        }
        return Native.DefWindowProcW(hWnd, msg, wParam, lParam);
    }

    private static void MarkUsable(string why)
    {
        _usableSinceMs ??= Environment.TickCount64;
        _log.Write(why);
    }

    /// <summary>Logs how long the window was usable before it lost activation or was minimized.</summary>
    private static void EndUsable(string why)
    {
        long? exposure = _usableSinceMs is long since ? Environment.TickCount64 - since : null;
        _usableSinceMs = null;
        _log.Write(why, new { exposure_ms = exposure });
    }

    private static void WatchMute()
    {
        try
        {
            var muted = AudioSessions.MuteStateOf((uint)Environment.ProcessId);
            if (muted != _lastMute)
            {
                _log.Write("own_mute_state", new { muted });
                _lastMute = muted;
            }
        }
        catch (Exception e)
        {
            _log.Write("mute_check_error", new { error = e.GetType().Name });
        }
    }

    /// <summary>Loops a quiet 440 Hz tone so the probe has real audio to act on.</summary>
    private static void StartTone()
    {
        const int rate = 44100;
        const int samples = rate; // 1 second, loops seamlessly at 440 Hz
        var data = new byte[44 + samples * 2];
        void W32(int o, int v) => BitConverter.GetBytes(v).CopyTo(data, o);
        void W16(int o, short v) => BitConverter.GetBytes(v).CopyTo(data, o);
        "RIFF"u8.ToArray().CopyTo(data, 0); W32(4, 36 + samples * 2);
        "WAVE"u8.ToArray().CopyTo(data, 8); "fmt "u8.ToArray().CopyTo(data, 12);
        W32(16, 16); W16(20, 1); W16(22, 1); W32(24, rate); W32(28, rate * 2); W16(32, 2); W16(34, 16);
        "data"u8.ToArray().CopyTo(data, 36); W32(40, samples * 2);
        for (int i = 0; i < samples; i++)
            W16(44 + i * 2, (short)(Math.Sin(2 * Math.PI * 440 * i / rate) * 0.04 * short.MaxValue));
        _wavHandle = GCHandle.Alloc(data, GCHandleType.Pinned); // must stay valid while playing
        bool ok = Native.PlaySoundW(_wavHandle.AddrOfPinnedObject(), IntPtr.Zero,
            Native.SND_MEMORY | Native.SND_ASYNC | Native.SND_LOOP);
        _log.Write("tone_started", new { ok });
    }
}
