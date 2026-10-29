# Shared helpers for the VM-only policy experiments. EXPERIMENT ONLY.
#
# These scripts change machine-wide settings. They refuse to run unless the
# machine looks like a virtual machine AND -ConfirmDisposableVM is given.
# Take a VM checkpoint/snapshot first.
#
# Rollback model (ownership manifest, C:\LockdInProbeVM\owned.json):
#   * Before changing anything, a script records exactly what it is about to
#     change and the value that was there before (or that nothing was there).
#   * Restore touches ONLY recorded items, and only if they still hold the
#     value this experiment set. If someone else changed it meanwhile, it is
#     left alone and reported as a conflict.
#   * Nothing is ever cleared or reset just because a backup is missing.

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

# FOCUSPROBE_VM_WORKDIR exists only so the bookkeeping can be unit-tested off Windows.
$script:WorkDir = if ($env:FOCUSPROBE_VM_WORKDIR) { $env:FOCUSPROBE_VM_WORKDIR } else { 'C:\LockdInProbeVM' }
$script:ResultsFile = Join-Path $script:WorkDir 'results.jsonl'
$script:OwnedFile = Join-Path $script:WorkDir 'owned.json'
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
    ($entry | ConvertTo-Json -Compress -Depth 6) | Add-Content -Path $script:ResultsFile -Encoding utf8
    Write-Host "[$Experiment] $Step $(($Data | ConvertTo-Json -Compress -Depth 6))"
}

# ---------- ownership manifest ----------

function Get-Owned {
    if (-not (Test-Path $script:OwnedFile)) { return @() }
    $raw = Get-Content -Raw -Path $script:OwnedFile
    if ([string]::IsNullOrWhiteSpace($raw)) { return @() }
    return @($raw | ConvertFrom-Json)
}

function Save-Owned([object[]]$Entries) {
    $tmp = "$($script:OwnedFile).tmp"
    (ConvertTo-Json -InputObject @($Entries) -Depth 6) | Set-Content -Path $tmp -Encoding utf8
    Move-Item -Force -Path $tmp -Destination $script:OwnedFile
}

function Add-Owned([hashtable]$Entry) {
    $all = @(Get-Owned) + [pscustomobject]$Entry
    Save-Owned $all
}

function Remove-OwnedWhere([scriptblock]$Predicate) {
    Save-Owned @(Get-Owned | Where-Object { -not (& $Predicate $_) })
}

# ---------- registry values (browser policies) ----------

function Set-OwnedRegistryValue {
    param([string]$Experiment, [string]$Path, [string]$Name, $Value, [Microsoft.Win32.RegistryValueKind]$Kind)
    if (@(Get-Owned | Where-Object { $_.kind -eq 'registry' -and $_.path -eq $Path -and $_.name -eq $Name }).Count -gt 0) {
        throw "An earlier experiment still owns $Path\$Name. Run 99-Cleanup.ps1 first."
    }
    $keyExisted = Test-Path $Path
    $prop = if ($keyExisted) { Get-ItemProperty -Path $Path -Name $Name -ErrorAction SilentlyContinue } else { $null }
    $had = $null -ne $prop
    $prevKind = if ($had) { (Get-Item $Path).GetValueKind($Name).ToString() } else { $null }
    Add-Owned @{
        kind = 'registry'; experiment = $Experiment; path = $Path; name = $Name
        key_existed = $keyExisted; had_previous = $had
        previous_value = if ($had) { $prop.$Name } else { $null }; previous_kind = $prevKind
        set_value = $Value; set_kind = $Kind.ToString()
    }
    if (-not $keyExisted) { New-Item -Path $Path -Force | Out-Null }
    New-ItemProperty -Path $Path -Name $Name -Value $Value -PropertyType $Kind -Force | Out-Null
}

# Restores one owned registry value. Returns 'restored', 'removed', 'conflict' or 'missing'.
function Restore-OwnedRegistryValue($Entry) {
    $prop = Get-ItemProperty -Path $Entry.path -Name $Entry.name -ErrorAction SilentlyContinue
    if ($null -eq $prop) {
        $result = if ($Entry.had_previous) { 'conflict' } else { 'missing' }  # someone removed it; don't recreate blindly
    } elseif ("$($prop.($Entry.name))" -ne "$($Entry.set_value)") {
        $result = 'conflict'   # changed by someone else since we set it: leave it
    } elseif ($Entry.had_previous) {
        New-ItemProperty -Path $Entry.path -Name $Entry.name -Value $Entry.previous_value -PropertyType $Entry.previous_kind -Force | Out-Null
        $result = 'restored'
    } else {
        Remove-ItemProperty -Path $Entry.path -Name $Entry.name
        # Remove the key only if we created it and it is now empty.
        if (-not $Entry.key_existed) {
            $k = Get-Item $Entry.path -ErrorAction SilentlyContinue
            if ($k -and $k.ValueCount -eq 0 -and $k.SubKeyCount -eq 0) { Remove-Item $Entry.path }
        }
        $result = 'removed'
    }
    Remove-OwnedWhere { param($e) $e.kind -eq 'registry' -and $e.path -eq $Entry.path -and $e.name -eq $Entry.name }
    return $result
}

# ---------- launch tests ----------

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

# Polls until a launch is blocked (or allowed, with -ExpectAllowed); returns elapsed ms, or -1 on timeout.
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

function Get-WindowsEdition { (Get-CimInstance Win32_OperatingSystem).Caption }
