# End-to-end check of the Windows blocking agent on a CI runner (milestone M1.11–M1.13,
# environment E-win-ci). RUNNER ONLY: not evidence about a person's PC.
#   1. writes a plan that blocks the harmless LockdInTarget.exe for a short interval;
#   2. starts the agent (it must keep working with no app window open);
#   3. checks the target's window is minimized and the agent reports "protecting";
#   4. checks the target was not closed;
#   5. after the interval: agent reports idle and exits; restoring the window is allowed.
param([string]$Agent, [string]$Target, [int]$Seconds = 40)
$ErrorActionPreference = 'Stop'
Add-Type @'
using System; using System.Runtime.InteropServices;
public static class W {
  [DllImport("user32.dll")] public static extern bool IsIconic(IntPtr h);
  [DllImport("user32.dll")] public static extern bool ShowWindow(IntPtr h, int c);
  [DllImport("user32.dll")] public static extern bool SetForegroundWindow(IntPtr h);
}
'@
$dir = Join-Path $env:LOCALAPPDATA 'LockdIn\agent'
New-Item -ItemType Directory -Force $dir | Out-Null
Remove-Item "$dir\status.json", "$dir\agent.log" -ErrorAction SilentlyContinue
$results = [System.Collections.Generic.List[string]]::new(); $failed = $false
function Record($id, $ok, $detail) { $script:results.Add("$id $(if ($ok) {'pass'} else {'FAIL'}) $detail"); if (-not $ok) { $script:failed = $true } }
function Status { try { Get-Content "$dir\status.json" -Raw | ConvertFrom-Json } catch { $null } }
function NowMs { [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds() }

$t = Start-Process $Target -ArgumentList '--silent' -PassThru
for ($i = 0; $i -lt 50 -and $t.MainWindowHandle -eq 0; $i++) { Start-Sleep -Milliseconds 200; $t.Refresh() }
$h = $t.MainWindowHandle
Record 'target-window' ($h -ne 0) "hwnd=$h"

$start = NowMs; $end = $start + $Seconds * 1000
@{ v = 1; sessionId = 'ci-agent'; issuedAt = $start; intervals = @(, @($start, $end)); mode = 'block_list'; apps = @('LockdInTarget.exe') } |
  ConvertTo-Json -Depth 4 -Compress | Set-Content "$dir\plan.json" -Encoding utf8NoBOM
$a = Start-Process $Agent -PassThru

# 3. minimized + protecting
$sw = [Diagnostics.Stopwatch]::StartNew(); $min = $false
while ($sw.ElapsedMilliseconds -lt 5000 -and -not $min) { Start-Sleep -Milliseconds 50; $min = [W]::IsIconic($h) }
Record 'M1.12-minimized-at-start' $min "already-open window minimized after $($sw.ElapsedMilliseconds) ms"
Start-Sleep -Seconds 2
$s = Status
Record 'agent-reports-protecting' ($s -and $s.protection -eq 'protecting' -and $s.sessionId -eq 'ci-agent') "status=$($s | ConvertTo-Json -Compress)"

# Restore it by hand: the agent should minimize it again quickly (re-open during focus).
$lat = @()
foreach ($n in 1..10) {
  [W]::ShowWindow($h, 9) | Out-Null; [W]::SetForegroundWindow($h) | Out-Null
  $sw.Restart(); $min = $false
  while ($sw.ElapsedMilliseconds -lt 3000 -and -not $min) { Start-Sleep -Milliseconds 10; $min = [W]::IsIconic($h) }
  if ($min) { $lat += $sw.ElapsedMilliseconds }
  Start-Sleep -Milliseconds 300
}
$sorted = $lat | Sort-Object
$p50 = if ($sorted) { $sorted[[int][Math]::Floor(($sorted.Count - 1) / 2)] } else { 'n/a' }
Record 'M1.12-reminimized' ($lat.Count -eq 10) "$($lat.Count)/10 restores re-minimized; p50 $p50 ms, max $(if ($sorted) { $sorted[-1] } else { 'n/a' }) ms (incl. polling)"

# 4. never closed
$t.Refresh()
Record 'M1.10-not-closed' (-not $t.HasExited) 'target process still running'

# 5. release at the end
while ((NowMs) -lt $end + 1500) { Start-Sleep -Milliseconds 250 }
$exited = $a.WaitForExit(5000)
$s = Status
Record 'release-at-end' ($exited -and $s.protection -eq 'idle') "agent exited=$exited, status=$($s.protection)"
[W]::ShowWindow($h, 9) | Out-Null; Start-Sleep -Seconds 2
Record 'window-stays-restored' (-not [W]::IsIconic($h)) 'target can be restored after the session'
$t.Refresh()
Record 'M1.10-still-not-closed' (-not $t.HasExited) 'target still running after the session'
if (-not $t.HasExited) { Stop-Process -Id $t.Id -Force }

$log = if (Test-Path "$dir\agent.log") { (Get-Content "$dir\agent.log" -Tail 25) -join '%0A' } else { 'no agent.log' }
$pass = ($results | Where-Object { $_ -match ' pass ' }).Count; $fail = ($results | Where-Object { $_ -match ' FAIL ' }).Count
Write-Host "::notice title=Windows agent E2E (RUNNER ONLY) — $pass pass, $fail fail::$($results -join '%0A')"
Write-Host "::notice title=Agent log::$log"
if ($failed) { exit 1 }
