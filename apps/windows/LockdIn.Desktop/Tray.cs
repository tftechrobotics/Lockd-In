// Native notification-area icon and menu (Shell_NotifyIcon + Win32 popup menu),
// so the tray behaves exactly like other Windows apps: left click or Enter opens
// the window, right click or Shift+F10 opens the menu, and the icon comes back
// if Explorer restarts. Also enforces the window's minimum size.
using System.Runtime.InteropServices;

namespace LockdIn.Desktop;

internal sealed partial class Tray : IDisposable
{
    private const uint WM_APP_TRAY = 0x8000 + 1;
    private const uint WM_GETMINMAXINFO = 0x0024;
    private const uint WM_CONTEXTMENU = 0x007B;
    private const uint WM_NULL = 0x0000;
    private const uint NIN_SELECT = 0x0400;
    private const uint NIN_KEYSELECT = 0x0401;
    private const uint NIM_ADD = 0, NIM_MODIFY = 1, NIM_DELETE = 2, NIM_SETVERSION = 4;
    private const uint NIF_MESSAGE = 0x1, NIF_ICON = 0x2, NIF_TIP = 0x4, NIF_INFO = 0x10, NIF_SHOWTIP = 0x80;
    private const uint NOTIFYICON_VERSION_4 = 4;
    private const uint MF_STRING = 0x0, MF_GRAYED = 0x1, MF_SEPARATOR = 0x800;
    private const uint TPM_RETURNCMD = 0x100, TPM_RIGHTBUTTON = 0x2, TPM_BOTTOMALIGN = 0x20;
    private const uint IMAGE_ICON = 1, LR_LOADFROMFILE = 0x10;

    private enum Cmd : uint { Open = 1, StartQuick = 2, Quit = 3 }

    private readonly nint hwnd;
    private readonly nint icon;
    private readonly uint taskbarCreated;
    private readonly SubclassProc proc; // kept alive for the lifetime of the subclass
    private readonly Action onOpen, onStartQuick, onQuit;
    private string status = "No session running";
    private bool added;

    public int MinWidth { get; init; } = 480;
    public int MinHeight { get; init; } = 600;

    public Tray(nint hwnd, string iconPath, Action onOpen, Action onStartQuick, Action onQuit)
    {
        this.hwnd = hwnd;
        this.onOpen = onOpen;
        this.onStartQuick = onStartQuick;
        this.onQuit = onQuit;
        icon = LoadImageW(0, iconPath, IMAGE_ICON, GetSystemMetrics(49 /* SM_CXSMICON */), GetSystemMetrics(50), LR_LOADFROMFILE);
        taskbarCreated = RegisterWindowMessageW("TaskbarCreated");
        proc = WndProc;
        SetWindowSubclass(hwnd, proc, 1, 0);
        Add();
    }

    public void SetStatus(string text)
    {
        status = text;
        var d = Data(NIF_TIP | NIF_SHOWTIP);
        d.szTip = Truncate($"Lockd In · {text}", 127);
        Shell_NotifyIconW(NIM_MODIFY, ref d);
    }

    /// <summary>Shows a system notification from the tray icon (used once, on first close).</summary>
    public void Notify(string title, string text)
    {
        var d = Data(NIF_INFO);
        d.szInfoTitle = Truncate(title, 63);
        d.szInfo = Truncate(text, 255);
        Shell_NotifyIconW(NIM_MODIFY, ref d);
    }

    private void Add()
    {
        var d = Data(NIF_MESSAGE | NIF_ICON | NIF_TIP | NIF_SHOWTIP);
        d.uCallbackMessage = WM_APP_TRAY;
        d.hIcon = icon;
        d.szTip = Truncate($"Lockd In · {status}", 127);
        added = Shell_NotifyIconW(NIM_ADD, ref d);
        d.uTimeoutOrVersion = NOTIFYICON_VERSION_4;
        Shell_NotifyIconW(NIM_SETVERSION, ref d);
        Log.Write($"tray added={added}");
    }

    private NOTIFYICONDATAW Data(uint flags) => new()
    {
        cbSize = (uint)Marshal.SizeOf<NOTIFYICONDATAW>(),
        hWnd = hwnd,
        uID = 1,
        uFlags = flags,
        szTip = "",
        szInfo = "",
        szInfoTitle = "",
    };

    private nint WndProc(nint h, uint msg, nint wParam, nint lParam, nuint id, nuint data)
    {
        if (msg == WM_APP_TRAY)
        {
            uint ev = (uint)(lParam.ToInt64() & 0xFFFF);
            if (ev is NIN_SELECT or NIN_KEYSELECT) onOpen();
            else if (ev == WM_CONTEXTMENU) ShowMenu((short)(wParam.ToInt64() & 0xFFFF), (short)((wParam.ToInt64() >> 16) & 0xFFFF));
            return 0;
        }
        if (msg == taskbarCreated && taskbarCreated != 0)
        {
            Add(); // Explorer restarted: put the icon back.
            return 0;
        }
        if (msg == WM_GETMINMAXINFO)
        {
            double scale = GetDpiForWindow(h) / 96.0;
            Marshal.WriteInt32(lParam, 24, (int)(MinWidth * scale));  // ptMinTrackSize.x
            Marshal.WriteInt32(lParam, 28, (int)(MinHeight * scale)); // ptMinTrackSize.y
        }
        return DefSubclassProc(h, msg, wParam, lParam);
    }

    private void ShowMenu(int x, int y)
    {
        nint menu = CreatePopupMenu();
        AppendMenuW(menu, MF_STRING | MF_GRAYED, 0, status);
        AppendMenuW(menu, MF_SEPARATOR, 0, null);
        AppendMenuW(menu, MF_STRING, (nuint)Cmd.Open, "Open Lockd In");
        AppendMenuW(menu, MF_STRING, (nuint)Cmd.StartQuick, "Start a 10-minute focus");
        AppendMenuW(menu, MF_SEPARATOR, 0, null);
        AppendMenuW(menu, MF_STRING, (nuint)Cmd.Quit, "Quit Lockd In");
        SetForegroundWindow(hwnd); // required so the menu closes when focus moves away
        uint cmd = TrackPopupMenuEx(menu, TPM_RETURNCMD | TPM_RIGHTBUTTON | TPM_BOTTOMALIGN, x, y, hwnd, 0);
        PostMessageW(hwnd, WM_NULL, 0, 0);
        DestroyMenu(menu);
        switch ((Cmd)cmd)
        {
            case Cmd.Open: onOpen(); break;
            case Cmd.StartQuick: onStartQuick(); break;
            case Cmd.Quit: onQuit(); break;
        }
    }

    public void Dispose()
    {
        if (added)
        {
            var d = Data(0);
            Shell_NotifyIconW(NIM_DELETE, ref d);
            added = false;
        }
        RemoveWindowSubclass(hwnd, proc, 1);
        if (icon != 0) DestroyIcon(icon);
    }

    private static string Truncate(string s, int max) => s.Length <= max ? s : s[..(max - 1)] + "…";

    // ---- Win32 ----
    private delegate nint SubclassProc(nint hWnd, uint uMsg, nint wParam, nint lParam, nuint uIdSubclass, nuint dwRefData);

    [StructLayout(LayoutKind.Sequential, CharSet = CharSet.Unicode)]
    private struct NOTIFYICONDATAW
    {
        public uint cbSize;
        public nint hWnd;
        public uint uID;
        public uint uFlags;
        public uint uCallbackMessage;
        public nint hIcon;
        [MarshalAs(UnmanagedType.ByValTStr, SizeConst = 128)] public string szTip;
        public uint dwState;
        public uint dwStateMask;
        [MarshalAs(UnmanagedType.ByValTStr, SizeConst = 256)] public string szInfo;
        public uint uTimeoutOrVersion;
        [MarshalAs(UnmanagedType.ByValTStr, SizeConst = 64)] public string szInfoTitle;
        public uint dwInfoFlags;
        public Guid guidItem;
        public nint hBalloonIcon;
    }

    [DllImport("shell32.dll", CharSet = CharSet.Unicode)] private static extern bool Shell_NotifyIconW(uint dwMessage, ref NOTIFYICONDATAW lpData);
    [DllImport("comctl32.dll")] private static extern bool SetWindowSubclass(nint hWnd, SubclassProc pfnSubclass, nuint uIdSubclass, nuint dwRefData);
    [DllImport("comctl32.dll")] private static extern bool RemoveWindowSubclass(nint hWnd, SubclassProc pfnSubclass, nuint uIdSubclass);
    [DllImport("comctl32.dll")] private static extern nint DefSubclassProc(nint hWnd, uint uMsg, nint wParam, nint lParam);
    [DllImport("user32.dll", CharSet = CharSet.Unicode)] private static extern uint RegisterWindowMessageW(string lpString);
    [DllImport("user32.dll")] private static extern nint CreatePopupMenu();
    [DllImport("user32.dll", CharSet = CharSet.Unicode)] private static extern bool AppendMenuW(nint hMenu, uint uFlags, nuint uIDNewItem, string? lpNewItem);
    [DllImport("user32.dll")] private static extern uint TrackPopupMenuEx(nint hMenu, uint uFlags, int x, int y, nint hwnd, nint lptpm);
    [DllImport("user32.dll")] private static extern bool DestroyMenu(nint hMenu);
    [DllImport("user32.dll")] private static extern bool SetForegroundWindow(nint hWnd);
    [DllImport("user32.dll")] private static extern bool PostMessageW(nint hWnd, uint Msg, nint wParam, nint lParam);
    [DllImport("user32.dll")] private static extern uint GetDpiForWindow(nint hwnd);
    [DllImport("user32.dll")] private static extern int GetSystemMetrics(int nIndex);
    [DllImport("user32.dll", CharSet = CharSet.Unicode)] private static extern nint LoadImageW(nint hInst, string name, uint type, int cx, int cy, uint fuLoad);
    [DllImport("user32.dll")] private static extern bool DestroyIcon(nint hIcon);
}
