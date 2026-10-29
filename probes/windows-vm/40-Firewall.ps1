#Requires -RunAsAdministrator
<#
.SYNOPSIS
  VM-ONLY. Measures per-program outbound firewall blocking as a way to keep an
  unsupported browser or app from reaching the internet during focus, without
  a VPN or traffic interception. Then removes the rule.

.EXAMPLE
  .\40-Firewall.ps1 -ProgramPath "C:\Program Files\Mozilla Firefox\firefox.exe" -ConfirmDisposableVM
#>
param(
    [Parameter(Mandatory)][string]$ProgramPath,
    [switch]$ConfirmDisposableVM
)
. "$PSScriptRoot\Common.ps1"
Assert-DisposableVM -Confirmed:$ConfirmDisposableVM
$exp = 'firewall'
$group = 'FocusProbeExperiment'

$sw = [Diagnostics.Stopwatch]::StartNew()
New-NetFirewallRule -DisplayName "FocusProbe block $([IO.Path]::GetFileName($ProgramPath))" -Group $group `
    -Direction Outbound -Program $ProgramPath -Action Block -Profile Any | Out-Null
Write-Result $exp 'rule_added' @{ add_ms = [int]$sw.Elapsed.TotalMilliseconds; program = [IO.Path]::GetFileName($ProgramPath) }

Write-Host 'Open the program and try to load a website (including one already open in a tab). Record what happens.'
$answers = @{}
foreach ($q in 'new_pages_blocked', 'already_open_connections_kept_working', 'error_shown_to_user_is_clear') {
    $answers[$q] = Read-Host "$q (y/n)"
}
Write-Result $exp 'observed' $answers

Remove-NetFirewallRule -Group $group
Write-Result $exp 'rule_removed'
