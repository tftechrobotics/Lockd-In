#Requires -RunAsAdministrator
<#
.SYNOPSIS
  VM-ONLY. Measures AppLocker as a launch-blocking mechanism for one harmless
  executable, then restores the previous local AppLocker policy.

.DESCRIPTION
  Uses an "allow all, deny target by hash" rule set so nothing else in the VM
  is blocked. Requires the Application Identity service (AppIDSvc). Also
  records whether stopping that service as an administrator ends enforcement.

.EXAMPLE
  .\20-AppLocker.ps1 -TargetExe C:\FocusProbe\FocusTarget.exe -ConfirmDisposableVM
#>
param(
    [Parameter(Mandatory)][string]$TargetExe,
    [switch]$ConfirmDisposableVM
)
. "$PSScriptRoot\Common.ps1"
Assert-DisposableVM -Confirmed:$ConfirmDisposableVM
$exp = 'applocker'
$TargetExe = (Resolve-Path $TargetExe).Path

$backup = Join-Path $WorkDir 'applocker-backup.xml'
Get-AppLockerPolicy -Local -Xml | Set-Content -Path $backup -Encoding utf8
Write-Result $exp 'precheck' @{ backup = $backup; target_launch = (Test-Launch $TargetExe) }

$info = Get-AppLockerFileInformation -Path $TargetExe
# FileHash prints as 'SHA256 0x...'; keep the data part.
$hash = ("$($info.Hash)" -split ' ')[-1]
$len = (Get-Item $TargetExe).Length
$name = [IO.Path]::GetFileName($TargetExe)
$policy = @"
<AppLockerPolicy Version="1">
  <RuleCollection Type="Exe" EnforcementMode="Enabled">
    <FilePathRule Id="$(New-Guid)" Name="FocusProbe allow all" Description="" UserOrGroupSid="S-1-1-0" Action="Allow">
      <Conditions><FilePathCondition Path="*" /></Conditions>
    </FilePathRule>
    <FileHashRule Id="$(New-Guid)" Name="FocusProbe deny target" Description="" UserOrGroupSid="S-1-1-0" Action="Deny">
      <Conditions><FileHashCondition>
        <FileHash Type="SHA256" Data="$hash" SourceFileName="$name" SourceFileLength="$len" />
      </FileHashCondition></Conditions>
    </FileHashRule>
  </RuleCollection>
</AppLockerPolicy>
"@
$policyFile = Join-Path $WorkDir 'applocker-focus.xml'
$policy | Set-Content -Path $policyFile -Encoding utf8

$running = Start-Process -FilePath $TargetExe -PassThru
Start-Sleep -Seconds 2

$sw = [Diagnostics.Stopwatch]::StartNew()
& sc.exe config appidsvc start= auto | Out-Null
Start-Service AppIDSvc
Set-AppLockerPolicy -XmlPolicy $policyFile
$applyMs = [int]$sw.Elapsed.TotalMilliseconds
$blockMs = Measure-UntilLaunch -Path $TargetExe
Write-Result $exp 'applied' @{ apply_ms = $applyMs; ms_until_launch_blocked = $blockMs; already_running_alive = -not $running.HasExited }

# Can an administrator end enforcement just by stopping the service?
$stopError = $null
try { Stop-Service AppIDSvc -Force -ErrorAction Stop } catch { $stopError = $_.Exception.Message }
Start-Sleep -Seconds 3
Write-Result $exp 'admin_stops_appidsvc' @{ stop_error = $stopError; service_status = (Get-Service AppIDSvc).Status.ToString(); launch_after_stop = (Test-Launch $TargetExe) }
Start-Service AppIDSvc -ErrorAction SilentlyContinue

Read-Host 'Record what the user sees when the target is blocked, then press Enter to restore the previous policy'

$sw.Restart()
Set-AppLockerPolicy -XmlPolicy $backup
$allowMs = Measure-UntilLaunch -Path $TargetExe -ExpectAllowed
Write-Result $exp 'restored' @{ restore_ms = [int]$sw.Elapsed.TotalMilliseconds; ms_until_launch_allowed = $allowMs }
if (-not $running.HasExited) { Stop-Process -Id $running.Id -Force }
