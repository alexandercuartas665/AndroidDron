param(
    [string]$ApiKey = "bitcode-dev-2026"
)

$ErrorActionPreference = "Stop"
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
$apk = Join-Path $PSScriptRoot "..\app\build\outputs\apk\debug\app-debug.apk"

if (!(Test-Path $adb)) { throw "No se encontro adb en $adb" }
if (!(Test-Path $apk)) { throw "No se encontro APK en $apk. Ejecuta .\\gradlew.bat assembleDebug primero." }

& $adb start-server | Out-Null
$devices = & $adb devices
if ($devices -notmatch "\tdevice") {
    throw "No hay dispositivos ADB autorizados. Verifica cable USB y depuracion USB."
}

Push-Location (Join-Path $PSScriptRoot "..")
try {
    & .\gradlew.bat assembleDebug -PremoteApiKey="$ApiKey"
    & $adb install -r "$apk"
    Write-Host "Instalacion completada." -ForegroundColor Green
}
finally {
    Pop-Location
}
