#Requires -RunAsAdministrator
<#
.SYNOPSIS
  VM-ONLY. App Control for Business (WDAC) as TRUE launch prevention for one
  harmless executable: the process never starts. Measures apply/remove timing,
  effect on an already-running copy, and (with -SimulateCrash) what persists
  when no Focus component is left to clean up.

.DESCRIPTION
  Builds an "allow everything" base policy plus a hash deny rule for the
  target, deploys it with CiTool, measures, then removes ONLY this policy
  (recorded in the ownership manifest). Building the policy here uses the
  ConfigCI PowerShell module, which exists on Pro/Enterprise/Education but not
  Home. The resulting .cip is kept so 11-AppControl-DeployOnly.ps1 can test
  deployment and enforcement on a Home VM separately from authoring.

.PARAMETER SimulateCrash
  Deploy, measure, and exit WITHOUT removing - as if Focus crashed and never
  restarted. Then reboot the VM and run 98-AfterReboot.ps1, then 99-Cleanup.ps1.

.EXAMPLE
  .\10-AppControl.ps1 -TargetExe C:\FocusProbe\FocusTarget.exe -ConfirmDisposableVM
#>
param(
    [Parameter(Mandatory)][string]$TargetExe,
    [switch]$SimulateCrash,
    [switch]$ConfirmDisposableVM
)
. "$PSScriptRoot\Common.ps1"
Assert-DisposableVM -Confirmed:$ConfirmDisposableVM
$exp = 'app_control'
$TargetExe = (Resolve-Path $TargetExe).Path

Write-Result $exp 'precheck' @{ edition = (Get-WindowsEdition); smart_app_control = (Get-SmartAppControlState); target_launch = (Test-Launch $TargetExe) }

# 1. Build policy: AllowAll example + deny the target by hash.
$xml = Join-Path $WorkDir 'focus-deny-target.xml'
Copy-Item "$env:windir\schemas\CodeIntegrity\ExamplePolicies\AllowAll.xml" $xml -Force
$rules = New-CIPolicyRule -DriverFilePath $TargetExe -Level Hash -Deny
Merge-CIPolicy -PolicyPaths $xml -OutputFilePath $xml -Rules $rules | Out-Null
$policyId = Set-CIPolicyIdInfo -FilePath $xml -PolicyName 'FocusProbe deny FocusTarget' -ResetPolicyID
$policyId = ($policyId -replace '.*(\{[0-9A-Fa-f-]+\}).*', '$1')
Set-RuleOption -FilePath $xml -Option 3 -Delete   # remove Audit Mode -> enforce
$cip = Join-Path $WorkDir "$policyId.cip"
ConvertFrom-CIPolicy -XmlFilePath $xml -BinaryFilePath $cip | Out-Null
Write-Result $exp 'policy_built' @{ policy_id = $policyId; cip = $cip }

# 2. A copy started BEFORE deploying shows what happens to already-running apps.
$running = Start-Process -FilePath $TargetExe -PassThru
Start-Sleep -Seconds 2

# 3. Record ownership first, then deploy and measure.
Add-Owned @{ kind = 'appcontrol_policy'; experiment = $exp; policy_id = $policyId; name = 'FocusProbe deny FocusTarget' }
$sw = [Diagnostics.Stopwatch]::StartNew()
& CiTool.exe --update-policy $cip -json | Out-Null
$deployMs = [int]$sw.Elapsed.TotalMilliseconds
$blockMs = Measure-UntilLaunch -Path $TargetExe
Write-Result $exp 'deployed' @{
    citool_ms               = $deployMs
    ms_until_launch_blocked = $blockMs
    already_running_alive   = -not $running.HasExited
    smart_app_control       = (Get-SmartAppControlState)
}

if ($SimulateCrash) {
    Write-Result $exp 'simulated_crash_left_policy_in_place' @{ policy_id = $policyId }
    Write-Host 'Policy left in place on purpose. Reboot the VM, run 98-AfterReboot.ps1, then 99-Cleanup.ps1.'
    return
}

Read-Host 'Record what the user sees when the target is blocked, then press Enter to remove the policy'

# 4. Remove only our policy and measure time until launches work again (no reboot).
$sw.Restart()
& CiTool.exe --remove-policy $policyId -json | Out-Null
$removeMs = [int]$sw.Elapsed.TotalMilliseconds
$allowMs = Measure-UntilLaunch -Path $TargetExe -ExpectAllowed
Remove-OwnedWhere { param($e) $e.kind -eq 'appcontrol_policy' -and $e.policy_id -eq $policyId }
Write-Result $exp 'removed' @{
    citool_ms               = $removeMs
    ms_until_launch_allowed = $allowMs
    needs_reboot_to_release = ($allowMs -lt 0)
    smart_app_control       = (Get-SmartAppControlState)
}
if (-not $running.HasExited) { Stop-Process -Id $running.Id -Force }
