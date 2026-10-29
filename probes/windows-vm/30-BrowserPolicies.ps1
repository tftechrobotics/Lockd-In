#Requires -RunAsAdministrator
<#
.SYNOPSIS
  VM-ONLY. Checks which Chrome/Edge policies take effect on an UNMANAGED
  (not domain-joined, not MDM-enrolled) Windows PC: disabling private
  browsing and force-installing a Web Store extension so it cannot be
  disabled. Then removes them.

.DESCRIPTION
  Policies are written under HKLM\SOFTWARE\Policies. Chrome/Edge show
  "managed by your organization" while they are present — record that.
  The extension used is a harmless Google-published one by default.

.EXAMPLE
  .\30-BrowserPolicies.ps1 -ConfirmDisposableVM            # apply, then follow prompts
#>
param(
    [string]$ExtensionId = 'ghbmnnjooekpmoecnnnilnnbdlolhkhi', # Google Docs Offline (harmless)
    [switch]$ConfirmDisposableVM
)
. "$PSScriptRoot\Common.ps1"
Assert-DisposableVM -Confirmed:$ConfirmDisposableVM
$exp = 'browser_policies'

$chrome = 'HKLM:\SOFTWARE\Policies\Google\Chrome'
$edge = 'HKLM:\SOFTWARE\Policies\Microsoft\Edge'
$settings = "{`"$ExtensionId`":{`"installation_mode`":`"force_installed`",`"update_url`":`"https://clients2.google.com/service/update2/crx`"}}"

foreach ($k in $chrome, $edge) { New-Item -Path $k -Force | Out-Null }
Set-ItemProperty -Path $chrome -Name IncognitoModeAvailability -Value 1 -Type DWord
Set-ItemProperty -Path $chrome -Name ExtensionSettings -Value $settings -Type String
Set-ItemProperty -Path $edge -Name InPrivateModeAvailability -Value 1 -Type DWord
Set-ItemProperty -Path $edge -Name ExtensionSettings -Value $settings -Type String
Write-Result $exp 'applied' @{ extension_id = $ExtensionId }

Write-Host @"
Now, in the VM:
  1. Restart Chrome and Edge completely.
  2. Open chrome://policy and edge://policy. For each policy note Status (OK / Error / Ignored).
  3. Try Ctrl+Shift+N (Chrome) / Ctrl+Shift+N (Edge). Is private browsing blocked?
  4. Open chrome://extensions and edge://extensions. Is the extension installed and impossible to disable/remove?
  5. Note whether a 'managed by your organization' message appears.
"@
$answers = @{}
foreach ($q in 'chrome_incognito_blocked', 'chrome_extension_forced', 'edge_inprivate_blocked', 'edge_extension_forced', 'managed_banner_shown') {
    $answers[$q] = Read-Host "$q (y/n)"
}
Write-Result $exp 'observed' $answers

foreach ($k in $chrome, $edge) {
    Remove-ItemProperty -Path $k -Name IncognitoModeAvailability, InPrivateModeAvailability, ExtensionSettings -ErrorAction SilentlyContinue
}
Write-Result $exp 'removed'
Write-Host 'Policies removed. Restart both browsers and confirm chrome://policy is empty.'
