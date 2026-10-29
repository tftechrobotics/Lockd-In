using Microsoft.UI.Xaml;

namespace LockdIn.Desktop;

public partial class App : Application
{
    private MainWindow? window;

    public App() => InitializeComponent();

    protected override void OnLaunched(LaunchActivatedEventArgs args)
    {
        window = new MainWindow();
        // `--background` (used by launch at sign-in) starts in the tray only.
        if (!Environment.GetCommandLineArgs().Contains("--background")) window.Activate();
        Log.Write($"launched {string.Join(' ', Environment.GetCommandLineArgs().Skip(1))}");
    }

    /// <summary>A second launch brings the existing window forward instead of opening another.</summary>
    public void ShowMainWindow() => window?.DispatcherQueue.TryEnqueue(() => window.ShowFromTray());
}
