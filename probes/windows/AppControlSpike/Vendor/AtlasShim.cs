// Lockd In shim for the few members of AppControl Manager's "Atlas" class that
// the vendored SiPolicy code uses. Values copied from AppControl Manager
// (MIT, see Vendor/HotCakeX/LICENSE); GetStr returns the resource key itself.
namespace AppControlManager;

internal static class Atlas
{
    internal const string SiPolicyNamespace = "urn:schemas-microsoft-com:sipolicy";
    internal const string DefaultMaxVersion = "65535.65535.65535.65535";
    internal static string GetStr(string key) => key;
}
