#Requires -RunAsAdministrator
<#
.SYNOPSIS
  VM-ONLY. Removes ONLY what the Lockd In experiments recorded in the ownership
  manifest (C:\LockdInProbeVM\owned.json). Never resets or clears anything that
  is not recorded there. Safe to run repeatedly. Restoring the VM checkpoint is
  still the cleanest reset.

.PARAMETER Only
  Restrict cleanup to one kind: appcontrol_policy, applocker_rules, registry, firewall_group.
#>
param(
    [ValidateSet('appcontrol_policy', 'applocker_rules', 'registry', 'firewall_group')][string]$Only,
    [switch]$ConfirmDisposableVM
)
. "$PSScriptRoot\Common.ps1"
Assert-DisposableVM -Confirmed:$ConfirmDisposableVM
$exp = 'cleanup'
$report = [ordered]@{}

$owned = @(Get-Owned | Where-Object { -not $Only -or $_.kind -eq $Only })
if ($owned.Count -eq 0) {
    Write-Result $exp 'nothing_owned' @{ only = "$Only" }
    Write-Host 'Nothing recorded as owned by the experiments. Nothing was changed.'
    return
}

foreach ($e in $owned) {
    switch ($e.kind) {
        'appcontrol_policy' {
            $list = (& CiTool.exe --list-policies -json) | ConvertFrom-Json
            $id = $e.policy_id.Trim('{}')
            $present = @($list.Policies | Where-Object { $_.PolicyID.Trim('{}') -eq $id })
            if ($present.Count -gt 0) { & CiTool.exe --remove-policy "{$id}" -json | Out-Null; $report["appcontrol $id"] = 'removed' }
            else { $report["appcontrol $id"] = 'not present' }
            Remove-OwnedWhere { param($x) $x.kind -eq 'appcontrol_policy' -and $x.policy_id -eq $e.policy_id }
        }
        'applocker_rules' {
            # Re-read the CURRENT local policy and remove only our rule IDs, so
            # rules anyone else added meanwhile are kept.
            [xml]$current = Get-AppLockerPolicy -Local -Xml
            $removed = 0
            foreach ($coll in @($current.SelectNodes('/AppLockerPolicy/RuleCollection'))) {
                foreach ($rule in @($coll.SelectNodes("*[contains(local-name(),'Rule')]"))) {
                    if (@($e.rule_ids) -contains $rule.GetAttribute('Id')) { [void]$coll.RemoveChild($rule); $removed++ }
                }
                # Our merge enabled EXE enforcement on an empty collection; undo that only if nothing else is there now.
                if ($coll.GetAttribute('Type') -eq 'Exe' -and $coll.SelectNodes("*[contains(local-name(),'Rule')]").Count -eq 0) {
                    $coll.SetAttribute('EnforcementMode', 'NotConfigured')
                }
            }
            $file = Join-Path $WorkDir 'applocker-after-removal.xml'
            $current.Save($file)
            Set-AppLockerPolicy -XmlPolicy $file
            $report['applocker rules removed'] = $removed
            if ($e.started_appidsvc) {
                [xml]$after = Get-AppLockerPolicy -Effective -Xml
                $remaining = $after.SelectNodes("/AppLockerPolicy/RuleCollection/*[contains(local-name(),'Rule')]").Count
                if ($remaining -eq 0) { Stop-Service AppIDSvc -ErrorAction SilentlyContinue; $report['appidsvc'] = 'stopped (we started it)' }
                else { $report['appidsvc'] = 'left running (other rules exist)' }
            }
            Remove-OwnedWhere { param($x) $x.kind -eq 'applocker_rules' -and (@($x.rule_ids) -join ',') -eq (@($e.rule_ids) -join ',') }
        }
        'registry' {
            $report["$($e.path)\$($e.name)"] = Restore-OwnedRegistryValue $e
        }
        'firewall_group' {
            Remove-NetFirewallRule -Group $e.group -ErrorAction SilentlyContinue
            $report["firewall $($e.group)"] = 'removed'
            Remove-OwnedWhere { param($x) $x.kind -eq 'firewall_group' -and $x.group -eq $e.group }
        }
    }
}
Write-Result $exp 'done' $report
$report.GetEnumerator() | ForEach-Object { Write-Host "$($_.Key): $($_.Value)" }
if (($report.Values | Where-Object { $_ -eq 'conflict' }).Count -gt 0) {
    Write-Host "Some values were changed by something else after the experiment set them. They were left alone; review them manually."
}
