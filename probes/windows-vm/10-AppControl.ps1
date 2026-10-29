#Requires -RunAsAdministrator
<#
.SYNOPSIS
  VM-ONLY. Measures App Control for Business (WDAC) as a launch-blocking
  mechanism for one harmless executable, then removes the policy.

.DESCRIPTION
  Builds an "allow everything" base policy plus a hash deny rule for the
  target, deploys it with CiTool, measures time-to-effect, checks what happens
  to an already-running copy, then removes it and measures time-to-release.
  Needs Windows 11 22H2+ (CiTool) and an edition with the ConfigCI PowerShell
  module (Pro/Enterprise/Education) to build the policy. Policies themselves
  take effect on all editions.

.EXAMPLE
  .\10-AppControl.ps1 -TargetExe C:\FocusProbe\FocusTarget.exe -ConfirmDisposableVM
#>
param(
    [Parameter(Mandatory)][string]$TargetExe,
    [switch]$ConfirmDisposableVM
)
. "$PSScriptRoot\Common.ps1"
Assert-DisposableVM -Confirmed:$ConfirmDisposableVM
$exp = 'app_control'
$TargetExe = (Resolve-Path $TargetExe).Path

Write-Result $exp 'precheck' @{ smart_app_control = (Get-SmartAppControlState); target_launch = (Test-Launch $TargetExe) }

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
Write-Result $exp 'policy_built' @{ policy_id = $policyId }

# 2. Start a copy BEFORE deploying, to see what happens to already-running apps.
$running = Start-Process -FilePath $TargetExe -PassThru
Start-Sleep -Seconds 2

# 3. Deploy and measure time until launches are blocked.
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
$events = Get-WinEvent -LogName 'Microsoft-Windows-CodeIntegrity/Operational' -MaxEvents 20 -ErrorAction SilentlyContinue |
    Where-Object { $_.Id -in 3076, 3077 } | Select-Object -First 3 | ForEach-Object { "$($_.Id): $($_.TimeCreated)" }
Write-Result $exp 'code_integrity_events' @{ recent = @($events) }

Read-Host 'Check: did Windows show a block message? Record what the user sees, then press Enter to remove the policy'

# 4. Remove and measure time until launches work again (no reboot).
$sw.Restart()
& CiTool.exe --remove-policy $policyId -json | Out-Null
$removeMs = [int]$sw.Elapsed.TotalMilliseconds
$allowMs = Measure-UntilLaunch -Path $TargetExe -ExpectAllowed
Write-Result $exp 'removed' @{
    citool_ms                  = $removeMs
    ms_until_launch_allowed    = $allowMs
    needs_reboot_to_release    = ($allowMs -lt 0)
    smart_app_control          = (Get-SmartAppControlState)
}
if (-not $running.HasExited) { Stop-Process -Id $running.Id -Force }
Write-Host "Done. If launches are still blocked, reboot the VM and run 99-Cleanup.ps1, then record that removal needed a reboot."
