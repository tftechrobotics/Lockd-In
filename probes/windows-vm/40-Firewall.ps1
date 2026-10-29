#Requires -RunAsAdministrator
<#
.SYNOPSIS
  VM-ONLY. Per-program outbound firewall blocking as a way to keep an
  unsupported browser or app off the internet during focus - no VPN, no
  traffic interception. Uses its own rule group; touches no existing rules.

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
if (Get-NetFirewallRule -Group $group -ErrorAction SilentlyContinue) {
    throw "Rules in group '$group' already exist. Run 99-Cleanup.ps1 first."
}

Add-Owned @{ kind = 'firewall_group'; experiment = $exp; group = $group }
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

& "$PSScriptRoot\99-Cleanup.ps1" -ConfirmDisposableVM -Only firewall_group
