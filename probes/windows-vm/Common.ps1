# Shared helpers for the VM-only policy experiments. EXPERIMENT ONLY.
# These scripts change machine-wide security policy. They refuse to run unless
# the machine looks like a virtual machine AND -ConfirmDisposableVM is given.
# Take a VM checkpoint/snapshot before running anything here.

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$script:WorkDir = 'C:\FocusProbeVM'
$script:ResultsFile = Join-Path $script:WorkDir 'results.jsonl'
New-Item -ItemType Directory -Force -Path $script:WorkDir | Out-Null

function Assert-DisposableVM {
    param([switch]$Confirmed)
    $cs = Get-CimInstance Win32_ComputerSystem
    $looksVirtual = "$($cs.Manufacturer) $($cs.Model)" -match 'Virtual|VMware|VirtualBox|KVM|QEMU|HVM|Parallels|Xen'
    if (-not $looksVirtual) {
        throw "Refusing to run: this does not look like a virtual machine ($($cs.Manufacturer) $($cs.Model)). These experiments must not run on an everyday PC."
    }
    if (-not $Confirmed) {
        throw 'Refusing to run: pass -ConfirmDisposableVM after taking a VM checkpoint.'
    }
    $principal = [Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()
    if (-not $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) {
        throw 'Run this from an elevated (Administrator) PowerShell inside the VM.'
    }
}

function Write-Result {
    param([Parameter(Mandatory)][string]$Experiment, [Parameter(Mandatory)][string]$Step, [hashtable]$Data = @{})
    $os = Get-CimInstance Win32_OperatingSystem
    $entry = [ordered]@{
        t          = (Get-Date).ToUniversalTime().ToString('o')
        experiment = $Experiment
        step       = $Step
        os         = "$($os.Caption) $($os.Version)"
        data       = $Data
    }
    ($entry | ConvertTo-Json -Compress -Depth 5) | Add-Content -Path $script:ResultsFile -Encoding utf8
    Write-Host "[$Experiment] $Step $(($Data | ConvertTo-Json -Compress -Depth 5))"
}

# Tries to start an executable and reports whether Windows allowed it.
function Test-Launch {
    param([Parameter(Mandatory)][string]$Path)
    try {
        $p = Start-Process -FilePath $Path -PassThru -ErrorAction Stop
        Start-Sleep -Milliseconds 1500
        $alive = -not $p.HasExited
        if ($alive) { Stop-Process -Id $p.Id -Force -ErrorAction SilentlyContinue }
        return @{ started = $true; alive_after_1_5s = $alive; error = $null }
    } catch {
        return @{ started = $false; alive_after_1_5s = $false; error = $_.Exception.Message }
    }
}

# Polls until a launch is blocked (or allowed, with -ExpectAllowed) and returns elapsed ms, or -1 on timeout.
function Measure-UntilLaunch {
    param([Parameter(Mandatory)][string]$Path, [switch]$ExpectAllowed, [int]$TimeoutSeconds = 120)
    $sw = [Diagnostics.Stopwatch]::StartNew()
    while ($sw.Elapsed.TotalSeconds -lt $TimeoutSeconds) {
        $r = Test-Launch -Path $Path
        $ok = if ($ExpectAllowed) { $r.started -and $r.alive_after_1_5s } else { -not ($r.started -and $r.alive_after_1_5s) }
        if ($ok) { return [int]$sw.Elapsed.TotalMilliseconds }
        Start-Sleep -Milliseconds 500
    }
    return -1
}

function Get-SmartAppControlState {
    $v = Get-ItemProperty -Path 'HKLM:\SYSTEM\CurrentControlSet\Control\CI\Policy' -Name VerifiedAndReputablePolicyState -ErrorAction SilentlyContinue
    if ($null -eq $v) { return 'unknown' }
    switch ($v.VerifiedAndReputablePolicyState) { 0 { 'off' } 1 { 'on' } 2 { 'evaluation' } default { "value:$($v.VerifiedAndReputablePolicyState)" } }
}
