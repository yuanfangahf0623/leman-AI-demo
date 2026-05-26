$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
$configPath = Join-Path $repoRoot "deploy\caddy\Caddyfile.n8n"
$logDir = Join-Path $repoRoot "logs"
$outLog = Join-Path $logDir "caddy-n8n.out.log"
$errLog = Join-Path $logDir "caddy-n8n.err.log"

New-Item -ItemType Directory -Force -Path $logDir | Out-Null

$env:Path = [System.Environment]::GetEnvironmentVariable("Path", "Machine") + ";" +
    [System.Environment]::GetEnvironmentVariable("Path", "User")
$caddyCommand = Get-Command caddy -ErrorAction SilentlyContinue
if ($caddyCommand) {
    $caddy = $caddyCommand.Source
} else {
    $caddy = Get-ChildItem -Path "$env:LOCALAPPDATA\Microsoft\WinGet\Packages" -Recurse -Filter caddy.exe -ErrorAction SilentlyContinue |
        Select-Object -First 1 -ExpandProperty FullName
}
if (-not $caddy) {
    throw "caddy.exe not found. Install Caddy first: winget install --id CaddyServer.Caddy --exact"
}

$running = Get-CimInstance Win32_Process |
    Where-Object { $_.Name -eq "caddy.exe" -and $_.CommandLine -like "*Caddyfile.n8n*" }

if ($running) {
    Write-Host "Caddy n8n proxy is already running. PID: $($running.ProcessId -join ', ')"
    return
}

Start-Process `
    -FilePath $caddy `
    -ArgumentList @("run", "--config", $configPath) `
    -RedirectStandardOutput $outLog `
    -RedirectStandardError $errLog `
    -WindowStyle Hidden

Start-Sleep -Seconds 2

Get-CimInstance Win32_Process |
    Where-Object { $_.Name -eq "caddy.exe" -and $_.CommandLine -like "*Caddyfile.n8n*" } |
    Select-Object ProcessId, CommandLine
