# App Control policy compiler spike — no deployment

**Status: spike.** Retires (or confirms) risk R6b: Lockd In must author App
Control policies on Windows Home, where Microsoft's ConfigCI PowerShell module
(`New-CIPolicy`, `ConvertFrom-CIPolicy`) is not available. Deployment with
`CiTool` works on Home (VM test 11); authoring is the gap.

What it does:
- `PolicyBuilder` builds the policy Lockd In would deploy for one session:
  allow everything, plus a deny rule per app the user chose to prevent,
  matched by the `OriginalFileName` in the app's version resource, for every
  version. Unsigned (removable with `CiTool`), no reboot needed, F8 recovery
  menu kept, script enforcement off, enforce mode.
- Refuses wildcards, paths, an empty list, more than 200 rules, and a
  built-in list of Windows shell, recovery, security and assistive tools
  (Explorer, Task Manager, Narrator, CiTool, ...).
- Compiles to the binary `.cip` format with vendored MIT code from
  AppControl Manager (see below), with no ConfigCI dependency.
- Writes the same policy as XML so CI can compile it with Microsoft's
  `ConvertFrom-CIPolicy` and compare the two binaries.

What it does **not** do: deploy, remove, or test enforcement. There is no
`CiTool` call anywhere. Enforcement is tested only in a disposable VM
(`probes/windows-vm`).

Commands:
```
dotnet build -c Release
dotnet bin/Release/net10.0/LockdInAppControlSpike.dll selftest
dotnet bin/Release/net10.0/LockdInAppControlSpike.dll build --deny steam.exe=Steam --out out
dotnet bin/Release/net10.0/LockdInAppControlSpike.dll describe out/lockdin.cip
```

Known limits of this spike:
- Apps without a version resource need hash rules (not implemented).
- Store/packaged apps need `PackageFamilyName` rules (not implemented).
- `OriginalFileName` can be shared by unrelated apps; production should add
  publisher (signer) conditions where available.
- The binary format is undocumented; byte-equality with Microsoft's compiler
  on one Windows build is evidence, not a guarantee. Production must keep the
  round-trip test on every supported Windows build (R6b).

## Third-party code

`Vendor/HotCakeX/` contains files from AppControl Manager by Violet Hansen
(HotCakeX), MIT License (`Vendor/HotCakeX/LICENSE`), from
github.com/HotCakeX/Harden-Windows-Security, commit
1c689e2fe06f059d9f8aecd34368e0ae4ebbc092 (2026-10-29), folder
`AppControl Manager/SiPolicy/`: SiPolicy.cs, BinaryOpsForward.cs,
BinaryOpsReverse.cs, Helper.cs, CustomSerialization.cs,
ApplicationManifest.cs, CustomAppManifestLogics.cs.

Lockd In modifications: `Helper.cs` — removed the network download branch in
`RetrieveApplicationManifest`. `Vendor/AtlasShim.cs` replaces the three
members of AppControl Manager's `Atlas` class those files use.
