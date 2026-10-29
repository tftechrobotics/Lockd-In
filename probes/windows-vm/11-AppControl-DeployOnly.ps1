#Requires -RunAsAdministrator
<#
.SYNOPSIS
  VM-ONLY. Tests App Control DEPLOYMENT and ENFORCEMENT on an edition that
  lacks the policy-authoring PowerShell cmdlets (Windows 11 Home), using a .cip
  built elsewhere (by 10-AppControl.ps1 on a Pro VM, from the SAME target file).

.DESCRIPTION
  Missing authoring cmdlets on Home does not mean enforcement is unsupported:
  Microsoft states policies are effective on all editions. This script checks
  whether CiTool exists and works on Home, whether the policy blocks the
  target, and whether it can be removed without a reboot.
  Separately, Lockd In would need its own way to author policies on Home (e.g. an
  in-app XML-to-binary converter); see docs/enforcement-comparison.md.

.EXAMPLE
  .\11-AppControl-DeployOnly.ps1 -PolicyBinary C:\LockdInProbeVM\{GUID}.cip -TargetExe C:\LockdInProbe\LockdInTarget.exe -ConfirmDisposableVM
#>
param(
    [Parameter(Mandatory)][string]$PolicyBinary,
    [Parameter(Mandatory)][string]$TargetExe,
    [switch]$ConfirmDisposableVM
)
. "$PSScriptRoot\Common.ps1"
Assert-DisposableVM -Confirmed:$ConfirmDisposableVM
$exp = 'app_control_deploy_only'
$PolicyBinary = (Resolve-Path $PolicyBinary).Path
$TargetExe = (Resolve-Path $TargetExe).Path
$policyId = [IO.Path]::GetFileNameWithoutExtension($PolicyBinary)
if ($policyId -notmatch '^\{[0-9A-Fa-f-]{36}\}$') { throw 'PolicyBinary must be named {GUID}.cip as produced by 10-AppControl.ps1.' }

$citool = Get-Command CiTool.exe -ErrorAction SilentlyContinue
Write-Result $exp 'precheck' @{
    edition           = (Get-WindowsEdition)
    citool_present    = ($null -ne $citool)
    configci_present  = ($null -ne (Get-Command New-CIPolicy -ErrorAction SilentlyContinue))
    smart_app_control = (Get-SmartAppControlState)
    target_launch     = (Test-Launch $TargetExe)
}
if ($null -eq $citool) { Write-Host 'CiTool not present: record this and stop.'; return }

Add-Owned @{ kind = 'appcontrol_policy'; experiment = $exp; policy_id = $policyId; name = 'LockdInProbe (deploy-only)' }
$sw = [Diagnostics.Stopwatch]::StartNew()
$out = & CiTool.exe --update-policy $PolicyBinary -json 2>&1
$blockMs = Measure-UntilLaunch -Path $TargetExe
Write-Result $exp 'deployed' @{ citool_output = "$out"; ms_until_launch_blocked = $blockMs; deploy_total_ms = [int]$sw.Elapsed.TotalMilliseconds }

Read-Host 'Record what the user sees, then press Enter to remove the policy'
$sw.Restart()
& CiTool.exe --remove-policy $policyId -json | Out-Null
$allowMs = Measure-UntilLaunch -Path $TargetExe -ExpectAllowed
Remove-OwnedWhere { param($e) $e.kind -eq 'appcontrol_policy' -and $e.policy_id -eq $policyId }
Write-Result $exp 'removed' @{ ms_until_launch_allowed = $allowMs; needs_reboot_to_release = ($allowMs -lt 0) }
