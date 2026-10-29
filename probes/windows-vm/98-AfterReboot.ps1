#Requires -RunAsAdministrator
<#
.SYNOPSIS
  VM-ONLY. Run after rebooting a VM that was left with a policy in place by
  -SimulateCrash. Answers: when no Focus component restarts, does the block
  persist across a reboot (fail-closed)? Then run 99-Cleanup.ps1.
#>
param(
    [Parameter(Mandatory)][string]$TargetExe,
    [switch]$ConfirmDisposableVM
)
. "$PSScriptRoot\Common.ps1"
Assert-DisposableVM -Confirmed:$ConfirmDisposableVM
$owned = @(Get-Owned)
Write-Result 'after_reboot' 'check' @{
    owned_items       = @($owned | ForEach-Object { $_.kind })
    target_launch     = (Test-Launch (Resolve-Path $TargetExe).Path)
    smart_app_control = (Get-SmartAppControlState)
}
Write-Host 'If target_launch.started is false, the block survived the reboot with nothing left to remove it. Now run 99-Cleanup.ps1.'
