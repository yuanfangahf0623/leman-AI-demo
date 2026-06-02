param(
    [string]$TunnelName = "leman-n8n"
)

$ErrorActionPreference = "Stop"

$configPath = Join-Path $env:USERPROFILE ".cloudflared\config.yml"
if (-not (Test-Path $configPath)) {
    throw "cloudflared config not found: $configPath"
}

$candidatePaths = @(
    (Join-Path $env:LOCALAPPDATA "Microsoft\WinGet\Packages\Cloudflare.cloudflared_Microsoft.Winget.Source_8wekyb3d8bbwe\cloudflared.exe"),
    "cloudflared.exe"
)

$cloudflared = $candidatePaths |
    ForEach-Object { Get-Command $_ -ErrorAction SilentlyContinue } |
    Select-Object -First 1 -ExpandProperty Source
if (-not $cloudflared) {
    throw "cloudflared.exe not found. Install it first: winget install --id Cloudflare.cloudflared --exact"
}

$oldProcesses = Get-CimInstance Win32_Process |
    Where-Object {
        $_.Name -like "cloudflared*" -and
        $_.CommandLine -like "*tunnel*" -and
        $_.CommandLine -like "*run*" -and
        $_.CommandLine -like "*$TunnelName*"
    }
foreach ($process in $oldProcesses) {
    Stop-Process -Id $process.ProcessId -Force -ErrorAction SilentlyContinue
}

Start-Process -FilePath $cloudflared `
    -ArgumentList @("tunnel", "--config", "`"$configPath`"", "run", $TunnelName) `
    -WindowStyle Hidden
