# Remora 1-Click Auto Launcher
# Automatically launches:
# 1. Pixel 7 Android Emulator with GUI (if not already running)
# 2. Waits for boot completion
# 3. Installs & grants permissions for Remora app
# 4. Launches Remora app on the emulator

$ErrorActionPreference = "Continue"

$adb = "D:\Programs\Sdk\platform-tools\adb.exe"
$emulator = "D:\Programs\Sdk\emulator\emulator.exe"
$apkPath = "$PSScriptRoot\..\app\build\outputs\apk\debug\app-debug.apk"

Write-Host "==========================================" -ForegroundColor Cyan
Write-Host "  REMORA - ONE-CLICK AUTO LAUNCHER       " -ForegroundColor Yellow
Write-Host "==========================================" -ForegroundColor Cyan

# Step 1: Check running devices & visible GUI windows
Write-Host "[1/5] Checking connected devices & GUI window..." -ForegroundColor Green
$emulatorProcs = Get-Process | Where-Object { $_.Name -match "emulator|qemu" }
$hasVisibleWindow = $false
foreach ($p in $emulatorProcs) {
    if ($p.MainWindowHandle -ne [IntPtr]::Zero) {
        $hasVisibleWindow = $true
        break
    }
}

$devicesOutput = & $adb devices
$hasAdbDevice = $false
foreach ($line in ($devicesOutput -split "`n")) {
    if ($line -match "emulator-\d+\s+device") {
        $hasAdbDevice = $true
        break
    }
}

# If adb sees device but no GUI window exists on desktop, kill headless zombie so user sees the phone!
if ($hasAdbDevice -and -not $hasVisibleWindow) {
    Write-Host "Detected background/headless emulator without visible window. Restarting with GUI..." -ForegroundColor Yellow
    Get-Process | Where-Object { $_.Name -match "emulator|qemu" } | Stop-Process -Force -ErrorAction SilentlyContinue
    & $adb kill-server
    Start-Sleep -Seconds 2
    $hasAdbDevice = $false
}

# Step 2: Start emulator if not running
if (-not $hasAdbDevice) {
    Write-Host "[2/5] Launching Pixel 7 Android Emulator window..." -ForegroundColor Green
    Start-Process -FilePath $emulator -ArgumentList "-avd", "Pixel_7"
    Write-Host "Waiting for emulator window to load and connect to ADB..." -ForegroundColor Yellow
    & $adb wait-for-device
} else {
    Write-Host "[2/5] Emulator is already running with visible window." -ForegroundColor Green
}

# Step 3: Wait for boot to finish
Write-Host "[3/5] Waiting for Android OS boot completion..." -ForegroundColor Yellow
$booted = $false
for ($i = 0; $i -lt 45; $i++) {
    $bootProp = (& $adb shell getprop sys.boot_completed 2>$null)
    if ($bootProp -and $bootProp.Trim() -eq "1") {
        $booted = $true
        Write-Host "Android system booted successfully!" -ForegroundColor Green
        break
    }
    Start-Sleep -Seconds 2
}

if (-not $booted) {
    Write-Host "WARNING: Boot is taking longer than usual, proceeding with installation attempt..." -ForegroundColor Yellow
}

# Step 4: Install APK & grant permissions
Write-Host "[4/5] Installing Remora APK..." -ForegroundColor Green
if (Test-Path $apkPath) {
    & $adb install -r $apkPath
    Write-Host "Granting runtime permissions (READ_MEDIA_IMAGES, POST_NOTIFICATIONS)..." -ForegroundColor Green
    & $adb shell pm grant com.remora android.permission.READ_MEDIA_IMAGES 2>$null
    & $adb shell pm grant com.remora android.permission.POST_NOTIFICATIONS 2>$null
} else {
    Write-Host "ERROR: APK not found at $apkPath. Please run gradlew assembleDebug first." -ForegroundColor Red
}

# Step 5: Start Remora App
Write-Host "[5/5] Launching Remora on Pixel 7..." -ForegroundColor Green
& $adb shell am start -n com.remora/.MainActivity

Write-Host "==========================================" -ForegroundColor Cyan
Write-Host "  REMORA IS READY TO TEST!               " -ForegroundColor Yellow
Write-Host "==========================================" -ForegroundColor Cyan
