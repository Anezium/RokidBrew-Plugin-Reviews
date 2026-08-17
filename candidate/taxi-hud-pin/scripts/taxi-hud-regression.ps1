param(
    [switch]$Install,
    [string]$PhoneSerial
)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
$env:ANDROID_HOME = Join-Path $env:LOCALAPPDATA 'Android\Sdk'
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
$env:Path = "$env:JAVA_HOME\bin;$env:Path"

Set-Location $projectRoot

$outputDir = Join-Path $projectRoot 'build\outputs\taxi-hud'
if (Test-Path -LiteralPath $outputDir) {
    Remove-Item -LiteralPath $outputDir -Recurse -Force
}

& .\gradlew.bat clean :phone:testDebugUnitTest :phone:lintDebug packageTaxiHudDebug
if ($LASTEXITCODE -ne 0) {
    throw "Taxi Plate Nexus regression failed with exit code $LASTEXITCODE"
}

$pluginApk = Join-Path $outputDir 'Taxi-Plate-debug.apk'
if (-not (Test-Path -LiteralPath $pluginApk -PathType Leaf)) {
    throw "Named plugin APK is missing: $pluginApk"
}

$legacyArtifacts = @(
    (Join-Path $outputDir 'Taxi-HUD-phone-debug.apk'),
    (Join-Path $outputDir 'Taxi-HUD-glasses-debug.apk')
)
foreach ($legacyArtifact in $legacyArtifacts) {
    if (Test-Path -LiteralPath $legacyArtifact) {
        throw "Legacy two-APK artifact must not be produced: $legacyArtifact"
    }
}

$hash = Get-FileHash -Algorithm SHA256 -LiteralPath $pluginApk
Write-Host "$($hash.Hash)  $(Split-Path -Leaf $hash.Path)"

if ($Install) {
    $adbCommand = Get-Command adb -ErrorAction SilentlyContinue
    if ($null -eq $adbCommand) {
        throw 'adb.exe is not available on PATH.'
    }
    $adb = $adbCommand.Source

    $deviceLines = @(& $adb devices | Select-Object -Skip 1 | Where-Object {
        $_ -match "\tdevice$"
    })
    if ($LASTEXITCODE -ne 0) {
        throw "adb devices failed with exit code $LASTEXITCODE"
    }

    $connectedSerials = @($deviceLines | ForEach-Object {
        ($_ -split '\s+')[0]
    })
    if ($PhoneSerial) {
        if ($PhoneSerial -notin $connectedSerials) {
            throw "PhoneSerial '$PhoneSerial' is not connected. Connected: $($connectedSerials -join ', ')"
        }
        $selectedSerial = $PhoneSerial
    }
    elseif ($connectedSerials.Count -eq 1) {
        $selectedSerial = $connectedSerials[0]
    }
    elseif ($connectedSerials.Count -eq 0) {
        throw 'Install requested, but no authorized ADB phone is connected.'
    }
    else {
        throw "Install requested with multiple ADB devices; specify -PhoneSerial. Connected: $($connectedSerials -join ', ')"
    }

    & $adb -s $selectedSerial install --user 0 -r $pluginApk
    if ($LASTEXITCODE -ne 0) {
        throw "Plugin APK install failed with exit code $LASTEXITCODE"
    }
    Write-Host "Taxi Plate Nexus plugin installed for personal user 0 on $selectedSerial. Approve it in Rokid Nexus -> Plugin access."
}
