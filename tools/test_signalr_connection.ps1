param(
    [string]$BaseUrl = "https://test1.bitcode.com.co",
    [int]$TimeoutSeconds = 30
)

# Ignore SSL certificate errors
[System.Net.ServicePointManager]::ServerCertificateValidationCallback = {$true}

$signalrJs = "$BaseUrl/signalr/js"
$signalrHubs = "$BaseUrl/signalr/hubs"
$signalrEndpoint = "$BaseUrl/signalr"

Write-Host "=== PRUEBA DE CONEXION SIGNALR ===" -ForegroundColor Cyan
Write-Host "Servidor: $BaseUrl" -ForegroundColor Yellow
Write-Host "Timeout: $TimeoutSeconds segundos" -ForegroundColor Yellow

# 1. Test server availability
Write-Host "`n[1] Verificando disponibilidad del servidor..." -ForegroundColor Green
try {
    $response = Invoke-WebRequest -Uri $BaseUrl -Method GET -UseBasicParsing -TimeoutSec $TimeoutSeconds
    Write-Host "OK - Servidor accesible - Status: $($response.StatusCode)" -ForegroundColor Green
} catch {
    Write-Host "ERROR - No se puede conectar: $($_.Exception.Message)" -ForegroundColor Red
    exit 1
}

# 2. Test /signalr/js endpoint
Write-Host "`n[2] Obteniendo script de SignalR (/signalr/js)..." -ForegroundColor Green
try {
    $response = Invoke-WebRequest -Uri $signalrJs -Method GET -UseBasicParsing -TimeoutSec $TimeoutSeconds
    Write-Host "OK - Script accesible - Status: $($response.StatusCode)" -ForegroundColor Green
    Write-Host "  Tamano: $($response.Content.Length) bytes" -ForegroundColor Cyan
} catch {
    Write-Host "ERROR - /signalr/js: $($_.Exception.Message)" -ForegroundColor Red
}

# 3. Test /signalr/hubs endpoint
Write-Host "`n[3] Obteniendo definicion de hubs (/signalr/hubs)..." -ForegroundColor Green
try {
    $response = Invoke-WebRequest -Uri $signalrHubs -Method GET -UseBasicParsing -TimeoutSec $TimeoutSeconds
    Write-Host "OK - Hubs accesibles - Status: $($response.StatusCode)" -ForegroundColor Green
    Write-Host "  Tamano: $($response.Content.Length) bytes" -ForegroundColor Cyan
    
    # Save response
    $hubsPath = "$(Split-Path -Parent $PSCommandPath)\signalr_hubs_response.js"
    $response.Content | Out-File -FilePath $hubsPath -Encoding UTF8 -Force
    Write-Host "  Guardado en: $hubsPath" -ForegroundColor Cyan
} catch {
    Write-Host "ERROR - /signalr/hubs: $($_.Exception.Message)" -ForegroundColor Red
}

# 4. Test /signalr endpoint
Write-Host "`n[4] Verificando endpoint /signalr..." -ForegroundColor Green
try {
    $response = Invoke-WebRequest -Uri $signalrEndpoint -Method GET -UseBasicParsing -TimeoutSec $TimeoutSeconds
    Write-Host "OK - Endpoint accesible - Status: $($response.StatusCode)" -ForegroundColor Green
    Write-Host "  Content-Type: $($response.Headers['Content-Type'])" -ForegroundColor Cyan
} catch {
    Write-Host "ERROR - /signalr: $($_.Exception.Message)" -ForegroundColor Red
}

Write-Host "`n=== PRUEBA COMPLETADA ===" -ForegroundColor Cyan
Write-Host "Si todos los endpoints muestran OK con Status 200, el servidor funciona correctamente." -ForegroundColor Yellow
