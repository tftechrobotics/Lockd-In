using Microsoft.UI.Dispatching;
using Microsoft.UI.Xaml;
using Microsoft.Windows.AppLifecycle;

namespace LockdIn.Desktop;

/// <summary>Custom entry point: one instance per user; later launches activate it.</summary>
public static class Program
{
    [STAThread]
    private static int Main(string[] args)
    {
        Log.Write($"start {Environment.ProcessId}");
        AppDomain.CurrentDomain.UnhandledException += (_, e) => Log.Write($"fatal: {e.ExceptionObject}");
        WinRT.ComWrappersSupport.InitializeComWrappers();
        var instance = AppInstance.FindOrRegisterForKey("lockdin-main");
        if (!instance.IsCurrent)
        {
            var activation = AppInstance.GetCurrent().GetActivatedEventArgs();
            Task.Run(() => instance.RedirectActivationToAsync(activation).AsTask()).Wait();
            return 0;
        }
        instance.Activated += (_, _) => (Application.Current as App)?.ShowMainWindow();
        Application.Start(init =>
        {
            SynchronizationContext.SetSynchronizationContext(new DispatcherQueueSynchronizationContext(DispatcherQueue.GetForCurrentThread()));
            try { _ = new App(); }
            catch (Exception ex) { Log.Write($"app init failed: {ex}"); throw; }
        });
        return 0;
    }
}
