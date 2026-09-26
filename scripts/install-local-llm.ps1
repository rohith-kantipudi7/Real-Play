# Installs the on-device local LLM (Qwen2.5-1.5B) for RealPlay's AI composer.
# Prereq: device connected + USB debugging authorized; model already downloaded to
#   %USERPROFILE%\realplay-models\qwen2.5-1.5b-instruct.task
# Usage:  powershell -ExecutionPolicy Bypass -File scripts\install-local-llm.ps1

$ErrorActionPreference = "Stop"
$adb   = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
$local = "$env:USERPROFILE\realplay-models\qwen2.5-1.5b-instruct.task"
$remoteDir = "/sdcard/realplay/models"
$remote = "$remoteDir/qwen2.5-1.5b-instruct.task"

if (-not (Test-Path $adb))   { throw "adb not found at $adb" }
if (-not (Test-Path $local)) { throw "model not found at $local (run the download first)" }

Write-Host "== Devices =="
& $adb devices
$state = (& $adb get-state) 2>&1
if ($state -ne "device") { throw "No authorized device (state=$state). Plug in the phone and tap 'Allow USB debugging'." }

Write-Host "== Creating $remoteDir on device =="
& $adb shell "mkdir -p $remoteDir"

$mb = [math]::Round((Get-Item $local).Length/1MB,1)
Write-Host "== Pushing model ($mb MB) -> $remote (this can take a couple of minutes) =="
& $adb push $local $remote

Write-Host "== Verifying on device =="
& $adb shell "ls -la $remote"

Write-Host "== Done. In the app: Settings -> AI composer -> 'Use Gemma to compose games' (toggle ON). =="
