#Requires -RunAsAdministrator
<#
.SYNOPSIS
  VM-ONLY. Removes everything the Focus VM experiments could have left behind.
  Safe to run repeatedly. Restoring the VM checkpoint is still the cleanest reset.
#>
param([switch]$ConfirmDisposableVM)
. "$PSScriptRoot\Common.ps1"
Assert-DisposableVM -Confirmed:$ConfirmDisposableVM
$exp = 'cleanup'

# App Control policies created by 10-AppControl.ps1
$removed = @()
$list = (& CiTool.exe --list-policies -json) | ConvertFrom-Json
foreach ($p in $list.Policies) {
    if ($p.FriendlyName -like 'FocusProbe*') {
        & CiTool.exe --remove-policy "{$($p.PolicyID.Trim('{}'))}" -json | Out-Null
        $removed += $p.PolicyID
    }
}

# AppLocker: restore backup if present, otherwise an empty policy
$backup = Join-Path $WorkDir 'applocker-backup.xml'
if (Test-Path $backup) { Set-AppLockerPolicy -XmlPolicy $backup }
else {
    $empty = Join-Path $WorkDir 'applocker-empty.xml'
    '<AppLockerPolicy Version="1" />' | Set-Content $empty -Encoding utf8
    Set-AppLockerPolicy -XmlPolicy $empty
}

# Browser policies
foreach ($k in 'HKLM:\SOFTWARE\Policies\Google\Chrome', 'HKLM:\SOFTWARE\Policies\Microsoft\Edge') {
    Remove-ItemProperty -Path $k -Name IncognitoModeAvailability, InPrivateModeAvailability, ExtensionSettings -ErrorAction SilentlyContinue
}

# Firewall
Remove-NetFirewallRule -Group 'FocusProbeExperiment' -ErrorAction SilentlyContinue

Write-Result $exp 'done' @{ app_control_policies_removed = $removed }
Write-Host 'Cleanup complete. Reboot the VM if any App Control policy was removed on a Windows version older than 11 24H2.'
