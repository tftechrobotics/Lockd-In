#Requires -RunAsAdministrator
<#
.SYNOPSIS
  VM-ONLY. Checks which Chrome/Edge policies take effect on an UNMANAGED
  (not domain-joined, not MDM-enrolled) Windows PC: disabling private browsing
  and force-installing a Web Store extension so it cannot be disabled.

.DESCRIPTION
  Each policy value is recorded in the ownership manifest before it is set,
  including whatever value was there before. Restore puts back the previous
  value (or removes ours if there was none) ONLY if the value is still the one
  this experiment set; otherwise it is reported as a conflict and left alone.
  Refuses to run if a browser already has an ExtensionSettings policy, because
  that value is a single JSON document this experiment would have to replace.

.EXAMPLE
  .\30-BrowserPolicies.ps1 -ConfirmDisposableVM
#>
param(
    [string]$ExtensionId = 'ghbmnnjooekpmoecnnnilnnbdlolhkhi', # Google Docs Offline (harmless, Google-published)
    [switch]$ConfirmDisposableVM
)
. "$PSScriptRoot\Common.ps1"
Assert-DisposableVM -Confirmed:$ConfirmDisposableVM
$exp = 'browser_policies'

$chrome = 'HKLM:\SOFTWARE\Policies\Google\Chrome'
$edge = 'HKLM:\SOFTWARE\Policies\Microsoft\Edge'
foreach ($k in $chrome, $edge) {
    if (Get-ItemProperty -Path $k -Name ExtensionSettings -ErrorAction SilentlyContinue) {
        throw "$k\ExtensionSettings already exists. This experiment will not replace an existing extension policy."
    }
}
$settings = "{`"$ExtensionId`":{`"installation_mode`":`"force_installed`",`"update_url`":`"https://clients2.google.com/service/update2/crx`"}}"

Set-OwnedRegistryValue -Experiment $exp -Path $chrome -Name IncognitoModeAvailability -Value 1 -Kind DWord
Set-OwnedRegistryValue -Experiment $exp -Path $chrome -Name ExtensionSettings -Value $settings -Kind String
Set-OwnedRegistryValue -Experiment $exp -Path $edge -Name InPrivateModeAvailability -Value 1 -Kind DWord
Set-OwnedRegistryValue -Experiment $exp -Path $edge -Name ExtensionSettings -Value $settings -Kind String
Write-Result $exp 'applied' @{ extension_id = $ExtensionId }

Write-Host @"
Now, in the VM:
  1. Fully restart Chrome and Edge.
  2. Open chrome://policy and edge://policy. Note each policy's Status (OK / Error / Ignored).
  3. Try Ctrl+Shift+N in both. Is private browsing blocked?
  4. Open chrome://extensions and edge://extensions. Is the extension installed and impossible to disable or remove?
  5. Note whether a 'managed by your organization' message appears.
"@
$answers = @{}
foreach ($q in 'chrome_incognito_blocked', 'chrome_extension_forced', 'edge_inprivate_blocked', 'edge_extension_forced', 'managed_banner_shown') {
    $answers[$q] = Read-Host "$q (y/n)"
}
Write-Result $exp 'observed' $answers

& "$PSScriptRoot\99-Cleanup.ps1" -ConfirmDisposableVM -Only registry
Write-Host 'Restart both browsers and confirm chrome://policy and edge://policy no longer list these policies.'
