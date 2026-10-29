# Launches the real Lockd In Windows app and photographs its window (native
# title bar included) for design review. CI only: it changes the CURRENT USER's
# light/dark app theme on a disposable runner. Do not run on your own PC unless
# you are happy for the script to flip your app theme and restore it.
param(
  [Parameter(Mandatory)] [string] $Exe,
  [Parameter(Mandatory)] [string] $OutDir
)
$ErrorActionPreference = 'Stop'
New-Item -ItemType Directory -Force -Path $OutDir | Out-Null

Add-Type -AssemblyName System.Drawing
Add-Type @'
using System;
using System.Runtime.InteropServices;
public static class Win {
  [StructLayout(LayoutKind.Sequential)] public struct RECT { public int L, T, R, B; }
  [DllImport("dwmapi.dll")] public static extern int DwmGetWindowAttribute(IntPtr h, int attr, out RECT r, int size);
  [DllImport("user32.dll")] public static extern bool SetWindowPos(IntPtr h, IntPtr after, int x, int y, int w, int hgt, uint flags);
  [DllImport("user32.dll")] public static extern bool SetForegroundWindow(IntPtr h);
}
'@

$personalize = 'HKCU:\Software\Microsoft\Windows\CurrentVersion\Themes\Personalize'
New-Item -Force -Path $personalize | Out-Null
$previous = (Get-ItemProperty -Path $personalize -Name AppsUseLightTheme -ErrorAction SilentlyContinue).AppsUseLightTheme

function Capture([IntPtr] $h, [string] $name) {
  [Win]::SetForegroundWindow($h) | Out-Null
  Start-Sleep -Milliseconds 900
  $r = New-Object Win+RECT
  [Win]::DwmGetWindowAttribute($h, 9, [ref] $r, 16) | Out-Null   # DWMWA_EXTENDED_FRAME_BOUNDS
  $w = $r.R - $r.L; $hgt = $r.B - $r.T
  $bmp = New-Object System.Drawing.Bitmap $w, $hgt
  $g = [System.Drawing.Graphics]::FromImage($bmp)
  $g.CopyFromScreen($r.L, $r.T, 0, 0, $bmp.Size)
  $bmp.Save((Join-Path $OutDir "$name.png"), [System.Drawing.Imaging.ImageFormat]::Png)
  $g.Dispose(); $bmp.Dispose()
  Write-Host "captured $name ($w x $hgt)"
}

function Shoot([int] $light, [string] $prefix) {
  Set-ItemProperty -Path $personalize -Name AppsUseLightTheme -Value $light -Type DWord
  $log = Join-Path $env:LOCALAPPDATA 'LockdIn/logs/shell.log'
  if (Test-Path $log) { Remove-Item $log -Force }
  $p = Start-Process $Exe -PassThru
  $deadline = (Get-Date).AddSeconds(60)
  while ((Get-Date) -lt $deadline -and -not ((Test-Path $log) -and (Select-String -Path $log -SimpleMatch 'ui.ready' -Quiet))) { Start-Sleep -Milliseconds 500 }
  Start-Sleep -Seconds 3
  $p.Refresh(); $h = $p.MainWindowHandle
  if ($h -eq [IntPtr]::Zero) { throw "No window for $prefix" }
  # Runner screens are small (1024x768), so use a window that fits on screen.
  [Win]::SetWindowPos($h, [IntPtr]::Zero, 20, 10, 620, 748, 0x0004) | Out-Null
  Capture $h "$prefix-default"
  [Win]::SetWindowPos($h, [IntPtr]::Zero, 20, 10, 480, 600, 0x0004) | Out-Null   # minimum supported size
  Capture $h "$prefix-minimum"
  Stop-Process -Id $p.Id -Force
  Start-Sleep -Seconds 1
}

try {
  Shoot 1 'windows-light'
  Shoot 0 'windows-dark'
} finally {
  if ($null -ne $previous) { Set-ItemProperty -Path $personalize -Name AppsUseLightTheme -Value $previous -Type DWord }
}
