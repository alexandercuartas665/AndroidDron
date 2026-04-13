param(
    [string]$ApiKey = "bitcode-dev-2026",
    [string]$Ip,
    [int]$Port = 8080,
    [string]$Url = "https://example.com"
)

if ([string]::IsNullOrWhiteSpace($Ip)) {
    throw "Debes pasar la IP de la tablet con -Ip"
}

$navigate = "http://$Ip:$Port/navigate?url=$([uri]::EscapeDataString($Url))&key=$([uri]::EscapeDataString($ApiKey))"
Invoke-RestMethod -Method Get -Uri $navigate

$script = "document.title"
$execute = "http://$Ip:$Port/execute?script=$([uri]::EscapeDataString($script))&key=$([uri]::EscapeDataString($ApiKey))"
Invoke-RestMethod -Method Get -Uri $execute
