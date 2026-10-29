#Requires -RunAsAdministrator
<#
.SYNOPSIS
  VM-ONLY. AppLocker as true launch prevention for one harmless executable.
  Adds two rules to the local policy (merge), measures, then removes ONLY those
  two rules, leaving any other local rules exactly as they are at removal time.

.DESCRIPTION
  Safety rules:
    * Refuses to run if the local or effective policy already has EXE rules -
      adding an "allow all" rule would weaken an existing allowlist.
    * Never replaces the whole local policy with a backup.
    * Does not change the Application Identity service's startup type; it only
      starts the service if it was stopped, and stops it afterwards only if it
      started it and no EXE rules remain.

.PARAMETER SimulateCrash
  Apply, measure, and exit WITHOUT removing. Then reboot, run 98-AfterReboot.ps1, then 99-Cleanup.ps1.

.EXAMPLE
  .\20-AppLocker.ps1 -TargetExe C:\LockdInProbe\LockdInTarget.exe -ConfirmDisposableVM
#>
param(
    [Parameter(Mandatory)][string]$TargetExe,
    [switch]$SimulateCrash,
    [switch]$ConfirmDisposableVM
)
. "$PSScriptRoot\Common.ps1"
Assert-DisposableVM -Confirmed:$ConfirmDisposableVM
$exp = 'applocker'
$TargetExe = (Resolve-Path $TargetExe).Path

# XPath, not property access: an empty policy has no RuleCollection element,
# and strict mode would throw on a missing property.
function Get-ExeRuleCount([xml]$policy) {
    return $policy.SelectNodes("/AppLockerPolicy/RuleCollection[@Type='Exe']/*[contains(local-name(),'Rule')]").Count
}

[xml]$local = Get-AppLockerPolicy -Local -Xml
[xml]$effective = Get-AppLockerPolicy -Effective -Xml
if ((Get-ExeRuleCount $local) -gt 0 -or (Get-ExeRuleCount $effective) -gt 0) {
    throw 'This VM already has AppLocker EXE rules. This experiment will not modify an existing AppLocker configuration.'
}
$svc = Get-Service AppIDSvc
Write-Result $exp 'precheck' @{ edition = (Get-WindowsEdition); appidsvc_status = $svc.Status.ToString(); target_launch = (Test-Launch $TargetExe) }

$info = Get-AppLockerFileInformation -Path $TargetExe
$hash = ("$($info.Hash)" -split ' ')[-1]   # FileHash prints as 'SHA256 0x...'
$len = (Get-Item $TargetExe).Length
$name = [IO.Path]::GetFileName($TargetExe)
$allowId = [guid]::NewGuid().ToString()
$denyId = [guid]::NewGuid().ToString()
$policy = @"
<AppLockerPolicy Version="1">
  <RuleCollection Type="Exe" EnforcementMode="Enabled">
    <FilePathRule Id="$allowId" Name="LockdInProbe allow all" Description="LockdInProbe experiment" UserOrGroupSid="S-1-1-0" Action="Allow">
      <Conditions><FilePathCondition Path="*" /></Conditions>
    </FilePathRule>
    <FileHashRule Id="$denyId" Name="LockdInProbe deny target" Description="LockdInProbe experiment" UserOrGroupSid="S-1-1-0" Action="Deny">
      <Conditions><FileHashCondition>
        <FileHash Type="SHA256" Data="$hash" SourceFileName="$name" SourceFileLength="$len" />
      </FileHashCondition></Conditions>
    </FileHashRule>
  </RuleCollection>
</AppLockerPolicy>
"@
$policyFile = Join-Path $WorkDir 'applocker-focus-rules.xml'
$policy | Set-Content -Path $policyFile -Encoding utf8

$running = Start-Process -FilePath $TargetExe -PassThru
Start-Sleep -Seconds 2

$weStartedSvc = $svc.Status -ne 'Running'
Add-Owned @{ kind = 'applocker_rules'; experiment = $exp; rule_ids = @($allowId, $denyId); started_appidsvc = $weStartedSvc }
$sw = [Diagnostics.Stopwatch]::StartNew()
if ($weStartedSvc) { Start-Service AppIDSvc }
Set-AppLockerPolicy -XmlPolicy $policyFile -Merge
$applyMs = [int]$sw.Elapsed.TotalMilliseconds
$blockMs = Measure-UntilLaunch -Path $TargetExe
Write-Result $exp 'applied' @{ apply_ms = $applyMs; ms_until_launch_blocked = $blockMs; already_running_alive = -not $running.HasExited }

# Does stopping the service as an administrator end enforcement?
$stopError = $null
try { Stop-Service AppIDSvc -Force -ErrorAction Stop } catch { $stopError = $_.Exception.Message }
Start-Sleep -Seconds 3
Write-Result $exp 'admin_stops_appidsvc' @{ stop_error = $stopError; service_status = (Get-Service AppIDSvc).Status.ToString(); launch_after_stop = (Test-Launch $TargetExe) }
Start-Service AppIDSvc -ErrorAction SilentlyContinue

if ($SimulateCrash) {
    Write-Result $exp 'simulated_crash_left_rules_in_place'
    Write-Host 'Rules left in place on purpose. Reboot the VM, run 98-AfterReboot.ps1, then 99-Cleanup.ps1.'
    return
}

Read-Host 'Record what the user sees when the target is blocked, then press Enter to remove the two experiment rules'
$sw.Restart()
& "$PSScriptRoot\99-Cleanup.ps1" -ConfirmDisposableVM -Only applocker_rules
$allowMs = Measure-UntilLaunch -Path $TargetExe -ExpectAllowed
Write-Result $exp 'rules_removed' @{ remove_ms = [int]$sw.Elapsed.TotalMilliseconds; ms_until_launch_allowed = $allowMs }
if (-not $running.HasExited) { Stop-Process -Id $running.Id -Force }
