using Microsoft.UI;
using Microsoft.UI.Windowing;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Media;
using Microsoft.UI.Xaml.Media.Imaging;
using Microsoft.Web.WebView2.Core;
using Windows.Graphics;
using Windows.UI;
using Windows.UI.ViewManagement;

namespace LockdIn.Desktop;

public sealed partial class MainWindow : Window
{
    private const string Host = "app.lockdin.local";
    private static readonly string Version = typeof(MainWindow).Assembly.GetName().Version?.ToString(3) ?? "0.0.0";
    private static readonly bool Smoke = Environment.GetEnvironmentVariable("LOCKDIN_SMOKE") == "1";

    private readonly Tray tray;
    private WebView2? View;
    private readonly ShellSettings settings = ShellSettings.Load();
    private bool quitting;
    private UiMessage.SessionState? session;

    public MainWindow()
    {
        InitializeComponent();
        Log.Write("window xaml loaded");
        ExtendsContentIntoTitleBar = true;
        SetTitleBar(TitleBarArea);
        string assets = Path.Combine(AppContext.BaseDirectory, "Assets");
        AppWindow.SetIcon(Path.Combine(assets, "lockdin.ico"));
        TitleIcon.Source = new BitmapImage(new Uri(Path.Combine(assets, "lock-mark-32.png")));
        AppWindow.Title = "Lockd In";
        double scale = (Content?.XamlRoot?.RasterizationScale) ?? 1.0;
        AppWindow.Resize(new SizeInt32((int)(640 * scale), (int)(860 * scale)));
        AppWindow.Closing += OnClosing;

        ApplyTheme(SystemIsDark());

        nint hwnd = WinRT.Interop.WindowNative.GetWindowHandle(this);
        tray = new Tray(hwnd, Path.Combine(assets, "lockdin.ico"), ShowFromTray, StartQuickFromTray, Quit);
        try
        {
            View = new WebView2();
            ViewHost.Child = View;
            View.DefaultBackgroundColor = ((SolidColorBrush)Root.Background).Color;
            _ = InitWebViewAsync();
        }
        catch (Exception ex)
        {
            ShowLoadError(ex);
        }
        if (Smoke) _ = SmokeTimeoutAsync();

        // Keep the tray text honest after a session's end time passes, even with the window hidden.
        var timer = DispatcherQueue.CreateTimer();
        timer.Interval = TimeSpan.FromSeconds(30);
        timer.Tick += (_, _) => RefreshTray();
        timer.Start();
    }

    // ---- Window lifetime: closing hides to the tray; it never ends a session. ----
    private void OnClosing(AppWindow sender, AppWindowClosingEventArgs e)
    {
        if (quitting) return;
        e.Cancel = true;
        sender.Hide();
        Log.Write("window hidden to tray");
        if (!settings.ToldAboutTray)
        {
            tray.Notify("Lockd In is still here", "Closing the window doesn’t end your session. Open Lockd In or quit it from the icon in the notification area.");
            settings.ToldAboutTray = true;
            settings.Save();
        }
    }

    public void ShowFromTray()
    {
        AppWindow.Show();
        Activate();
    }

    private void StartQuickFromTray()
    {
        ShowFromTray();
        View?.CoreWebView2?.PostWebMessageAsJson(Bridge.Command("start_quick"));
    }

    private void Quit()
    {
        quitting = true;
        Log.Write("quit");
        tray.Dispose();
        Close();
        Application.Current.Exit();
    }

    // ---- WebView2 hosting the bundled interface ----
    private async Task InitWebViewAsync()
    {
        try
        {
            string data = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "LockdIn", "WebView2");
            var env = await CoreWebView2Environment.CreateWithOptionsAsync(null, data, new CoreWebView2EnvironmentOptions());
            await View!.EnsureCoreWebView2Async(env);
        }
        catch (Exception ex)
        {
            ShowLoadError(ex);
            return;
        }

        var core = View!.CoreWebView2;
        string ui = Path.Combine(AppContext.BaseDirectory, "ui");
        core.SetVirtualHostNameToFolderMapping(Host, ui, CoreWebView2HostResourceAccessKind.Deny);

        var s = core.Settings;
        s.AreDefaultContextMenusEnabled = false;
        s.AreDevToolsEnabled = System.Diagnostics.Debugger.IsAttached;
        s.AreBrowserAcceleratorKeysEnabled = false;
        s.IsZoomControlEnabled = false;
        s.IsPinchZoomEnabled = false;
        s.IsSwipeNavigationEnabled = false;
        s.IsStatusBarEnabled = false;
        s.IsGeneralAutofillEnabled = false;
        s.IsPasswordAutosaveEnabled = false;

        // Only the bundled interface runs inside the window; links open in the browser.
        core.NavigationStarting += (_, e) =>
        {
            if (!IsAppUri(e.Uri)) { e.Cancel = true; OpenExternal(e.Uri); }
        };
        core.NewWindowRequested += (_, e) => { e.Handled = true; OpenExternal(e.Uri); };
        core.WebMessageReceived += OnWebMessage;
        core.ProcessFailed += (_, e) =>
        {
            Log.Write($"webview process failed: {e.ProcessFailedKind}");
            if (e.ProcessFailedKind == CoreWebView2ProcessFailedKind.RenderProcessExited) core.Reload();
        };
        core.Navigate($"https://{Host}/index.html");
    }

    private void ShowLoadError(Exception ex)
    {
        Log.Write($"webview2 unavailable: {ex}");
        LoadErrorText.Text = "This app needs the Microsoft Edge WebView2 Runtime, which normally comes with Windows 11. Install it, then open Lockd In again.";
        ViewHost.Visibility = Visibility.Collapsed;
        LoadError.Visibility = Visibility.Visible;
        if (Smoke) Environment.Exit(4);
    }

    private static bool IsAppUri(string uri) =>
        Uri.TryCreate(uri, UriKind.Absolute, out var u) && u.Scheme == "https" && u.Host == Host;

    private static void OpenExternal(string uri)
    {
        if (Uri.TryCreate(uri, UriKind.Absolute, out var u) && (u.Scheme == "https" || u.Scheme == "http"))
            _ = Windows.System.Launcher.LaunchUriAsync(u);
    }

    private void OnWebMessage(CoreWebView2 sender, CoreWebView2WebMessageReceivedEventArgs e)
    {
        if (!IsAppUri(e.Source)) return;
        switch (Bridge.Parse(e.WebMessageAsJson))
        {
            case UiMessage.Ready:
                Log.Write("ui.ready");
                sender.PostWebMessageAsJson(Bridge.Hello(Version, DetectAgent()));
                if (Smoke) _ = SmokeExitAsync();
                break;
            case UiMessage.SessionState st:
                session = st;
                RefreshTray();
                break;
            case UiMessage.Theme th:
                ApplyTheme(th.Dark);
                break;
        }
    }

    private void RefreshTray() =>
        tray.SetStatus(Bridge.TrayStatus(session, DateTimeOffset.Now, t => t.ToLocalTime().ToString("t")));

    /// <summary>The blocking agent/service (ADR-0007) exposes this pipe when running. Not built yet.</summary>
    private static AgentState DetectAgent()
    {
        try
        {
            return Directory.EnumerateFiles(@"\\.\pipe\").Any(p => p.EndsWith(@"\lockdin-agent", StringComparison.OrdinalIgnoreCase))
                ? AgentState.Running : AgentState.NotInstalled;
        }
        catch (IOException) { return AgentState.Unknown; }
        catch (UnauthorizedAccessException) { return AgentState.Unknown; }
    }

    // ---- Cream & Ink chrome colors (design/tokens.json) ----
    private void ApplyTheme(bool dark)
    {
        Color ground = dark ? Rgb(0x1C1814) : Rgb(0xF5F0E6);
        Color ink = dark ? Rgb(0xF0E8DB) : Rgb(0x302A25);
        Color hover = dark ? Rgb(0x322B24) : Rgb(0xEBE2D4);
        Root.Background = new SolidColorBrush(ground);
        TitleText.Foreground = new SolidColorBrush(ink);
        if (View is not null) View.DefaultBackgroundColor = ground; // no white flash before the page paints
        var tb = AppWindow.TitleBar;
        tb.ButtonBackgroundColor = Colors.Transparent;
        tb.ButtonInactiveBackgroundColor = Colors.Transparent;
        tb.ButtonForegroundColor = ink;
        tb.ButtonHoverBackgroundColor = hover;
        tb.ButtonHoverForegroundColor = ink;
        tb.ButtonPressedBackgroundColor = hover;
        tb.ButtonInactiveForegroundColor = dark ? Rgb(0x8F8170) : Rgb(0x8F8270);
        ((FrameworkElement)Content).RequestedTheme = dark ? ElementTheme.Dark : ElementTheme.Light;
    }

    private static bool SystemIsDark()
    {
        var bg = new UISettings().GetColorValue(UIColorType.Background);
        return bg.R + bg.G + bg.B < 384;
    }

    private static Color Rgb(int hex) => Color.FromArgb(255, (byte)(hex >> 16), (byte)(hex >> 8), (byte)hex);

    // ---- CI smoke test: LOCKDIN_SMOKE=1 exits 0 once the interface has loaded and spoken. ----
    private async Task SmokeExitAsync()
    {
        await Task.Delay(1500);
        Log.Write("smoke: ok");
        Quit();
    }

    private static async Task SmokeTimeoutAsync()
    {
        await Task.Delay(TimeSpan.FromSeconds(60));
        Log.Write("smoke: timed out waiting for ui.ready");
        Environment.Exit(3);
    }
}
