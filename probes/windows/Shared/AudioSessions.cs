using System.Runtime.InteropServices;

namespace FocusProbe;

/// <summary>
/// Per-process audio session control through Windows Core Audio (the same
/// sessions the Volume Mixer shows). Works for the current user's processes
/// without administrator rights. Unverified on hardware until probe W-S2.
/// </summary>
internal static class AudioSessions
{
    public sealed record Session(uint Pid, string InstanceId, bool Muted);

    /// <summary>All audio sessions on all active output devices.</summary>
    public static List<Session> List()
    {
        var result = new List<Session>();
        ForEach((pid, id, vol) =>
        {
            vol.GetMute(out var muted);
            result.Add(new Session(pid, id, muted));
            return false;
        });
        return result;
    }

    /// <summary>Sets mute on one session. Returns the previous state, or null if not found.</summary>
    public static bool? SetMute(string instanceId, bool mute)
    {
        bool? previous = null;
        ForEach((_, id, vol) =>
        {
            if (id != instanceId) return false;
            vol.GetMute(out var was);
            previous = was;
            if (was != mute)
            {
                var ctx = Guid.Empty;
                vol.SetMute(mute, ref ctx);
            }
            return true;
        });
        return previous;
    }

    /// <summary>Mute state of the first session owned by <paramref name="pid"/>, or null if it has none.</summary>
    public static bool? MuteStateOf(uint pid)
    {
        bool? state = null;
        ForEach((p, _, vol) =>
        {
            if (p != pid) return false;
            vol.GetMute(out var m);
            state = m;
            return true;
        });
        return state;
    }

    /// <summary>Visits sessions until <paramref name="visit"/> returns true.</summary>
    private static void ForEach(Func<uint, string, ISimpleAudioVolume, bool> visit)
    {
        var enumerator = (IMMDeviceEnumerator)new MMDeviceEnumeratorComObject();
        try
        {
            Check(enumerator.EnumAudioEndpoints(EDataFlow.eRender, DEVICE_STATE_ACTIVE, out var devices));
            Check(devices.GetCount(out var deviceCount));
            for (uint d = 0; d < deviceCount; d++)
            {
                Check(devices.Item(d, out var device));
                var iid = typeof(IAudioSessionManager2).GUID;
                Check(device.Activate(ref iid, CLSCTX_ALL, IntPtr.Zero, out var managerObj));
                var manager = (IAudioSessionManager2)managerObj;
                Check(manager.GetSessionEnumerator(out var sessions));
                Check(sessions.GetCount(out var count));
                for (int i = 0; i < count; i++)
                {
                    Check(sessions.GetSession(i, out var control));
                    var control2 = (IAudioSessionControl2)control;
                    if (control2.GetProcessId(out var pid) != 0) continue; // e.g. multi-process session
                    if (control2.GetSessionInstanceIdentifier(out var instanceId) != 0) continue;
                    var volume = (ISimpleAudioVolume)control;
                    if (visit(pid, instanceId, volume)) return;
                }
            }
        }
        finally
        {
            Marshal.ReleaseComObject(enumerator);
        }
    }

    private static void Check(int hr)
    {
        if (hr != 0) Marshal.ThrowExceptionForHR(hr);
    }

    private const int CLSCTX_ALL = 23;
    private const uint DEVICE_STATE_ACTIVE = 0x1;

    private enum EDataFlow { eRender = 0, eCapture = 1, eAll = 2 }

    [ComImport, Guid("BCDE0395-E52F-467C-8E3D-C4579291692E")]
    private class MMDeviceEnumeratorComObject { }

    [ComImport, Guid("A95664D2-9614-4F35-A746-DE8DB63617E6"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
    private interface IMMDeviceEnumerator
    {
        [PreserveSig] int EnumAudioEndpoints(EDataFlow dataFlow, uint stateMask, out IMMDeviceCollection devices);
        [PreserveSig] int GetDefaultAudioEndpoint(EDataFlow dataFlow, int role, out IMMDevice endpoint);
        [PreserveSig] int GetDevice([MarshalAs(UnmanagedType.LPWStr)] string id, out IMMDevice device);
        [PreserveSig] int RegisterEndpointNotificationCallback(IntPtr client);
        [PreserveSig] int UnregisterEndpointNotificationCallback(IntPtr client);
    }

    [ComImport, Guid("0BD7A1BE-7A1A-44DB-8397-CC5392387B5E"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
    private interface IMMDeviceCollection
    {
        [PreserveSig] int GetCount(out uint count);
        [PreserveSig] int Item(uint index, out IMMDevice device);
    }

    [ComImport, Guid("D666063F-1587-4E43-81F1-B948E807363F"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
    private interface IMMDevice
    {
        [PreserveSig] int Activate(ref Guid iid, int clsCtx, IntPtr activationParams, [MarshalAs(UnmanagedType.IUnknown)] out object instance);
        [PreserveSig] int OpenPropertyStore(int access, out IntPtr properties);
        [PreserveSig] int GetId([MarshalAs(UnmanagedType.LPWStr)] out string id);
        [PreserveSig] int GetState(out uint state);
    }

    [ComImport, Guid("77AA99A0-1BD6-484F-8BC7-2C654C9A9B6F"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
    private interface IAudioSessionManager2
    {
        // IAudioSessionManager
        [PreserveSig] int GetAudioSessionControl(IntPtr sessionGuid, uint streamFlags, out IntPtr sessionControl);
        [PreserveSig] int GetSimpleAudioVolume(IntPtr sessionGuid, uint streamFlags, out IntPtr audioVolume);
        // IAudioSessionManager2
        [PreserveSig] int GetSessionEnumerator(out IAudioSessionEnumerator sessionEnum);
        [PreserveSig] int RegisterSessionNotification(IntPtr notification);
        [PreserveSig] int UnregisterSessionNotification(IntPtr notification);
        [PreserveSig] int RegisterDuckNotification([MarshalAs(UnmanagedType.LPWStr)] string sessionId, IntPtr notification);
        [PreserveSig] int UnregisterDuckNotification(IntPtr notification);
    }

    [ComImport, Guid("E2F5BB11-0570-40CA-ACDD-3AA01277DEE8"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
    private interface IAudioSessionEnumerator
    {
        [PreserveSig] int GetCount(out int count);
        [PreserveSig] int GetSession(int index, out IAudioSessionControl session);
    }

    [ComImport, Guid("F4B1A599-7266-4319-A8CA-E70ACB11E8CD"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
    private interface IAudioSessionControl
    {
        [PreserveSig] int GetState(out int state);
        [PreserveSig] int GetDisplayName(out IntPtr name);
        [PreserveSig] int SetDisplayName([MarshalAs(UnmanagedType.LPWStr)] string name, ref Guid eventContext);
        [PreserveSig] int GetIconPath(out IntPtr path);
        [PreserveSig] int SetIconPath([MarshalAs(UnmanagedType.LPWStr)] string path, ref Guid eventContext);
        [PreserveSig] int GetGroupingParam(out Guid groupingParam);
        [PreserveSig] int SetGroupingParam(ref Guid groupingParam, ref Guid eventContext);
        [PreserveSig] int RegisterAudioSessionNotification(IntPtr notifications);
        [PreserveSig] int UnregisterAudioSessionNotification(IntPtr notifications);
    }

    [ComImport, Guid("BFB7FF88-7239-4FC9-8FA2-07C950BE9C6D"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
    private interface IAudioSessionControl2
    {
        // IAudioSessionControl (repeated: COM interop needs the full vtable)
        [PreserveSig] int GetState(out int state);
        [PreserveSig] int GetDisplayName(out IntPtr name);
        [PreserveSig] int SetDisplayName([MarshalAs(UnmanagedType.LPWStr)] string name, ref Guid eventContext);
        [PreserveSig] int GetIconPath(out IntPtr path);
        [PreserveSig] int SetIconPath([MarshalAs(UnmanagedType.LPWStr)] string path, ref Guid eventContext);
        [PreserveSig] int GetGroupingParam(out Guid groupingParam);
        [PreserveSig] int SetGroupingParam(ref Guid groupingParam, ref Guid eventContext);
        [PreserveSig] int RegisterAudioSessionNotification(IntPtr notifications);
        [PreserveSig] int UnregisterAudioSessionNotification(IntPtr notifications);
        // IAudioSessionControl2
        [PreserveSig] int GetSessionIdentifier([MarshalAs(UnmanagedType.LPWStr)] out string id);
        [PreserveSig] int GetSessionInstanceIdentifier([MarshalAs(UnmanagedType.LPWStr)] out string id);
        [PreserveSig] int GetProcessId(out uint pid);
        [PreserveSig] int IsSystemSoundsSession();
        [PreserveSig] int SetDuckingPreference(bool optOut);
    }

    [ComImport, Guid("87CE5498-68D6-44E5-9215-6DA47EF883D8"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
    private interface ISimpleAudioVolume
    {
        [PreserveSig] int SetMasterVolume(float level, ref Guid eventContext);
        [PreserveSig] int GetMasterVolume(out float level);
        [PreserveSig] int SetMute([MarshalAs(UnmanagedType.Bool)] bool mute, ref Guid eventContext);
        [PreserveSig] int GetMute([MarshalAs(UnmanagedType.Bool)] out bool mute);
    }
}
