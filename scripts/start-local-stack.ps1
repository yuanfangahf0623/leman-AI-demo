param(
    [switch]$RestartBackend,
    [switch]$RestartFrontend,
    [switch]$RestartCaddy,
    [switch]$SkipDocker,
    [int]$DockerWaitSeconds = 120,
    [int]$ServiceWaitSeconds = 120
)

$ErrorActionPreference = "Stop"

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot "..")
$frontendDir = Join-Path $repoRoot "frontend\yudao-ui-admin-vue3"
$logDir = Join-Path $repoRoot "logs"
New-Item -ItemType Directory -Force -Path $logDir | Out-Null

$runLog = Join-Path $logDir "local-stack-startup.log"

function Write-StackLog {
    param([string]$Message)

    $line = "$(Get-Date -Format 'yyyy-MM-dd HH:mm:ss') $Message"
    Write-Host $line
    Add-Content -Path $runLog -Value $line
}

function Test-TcpPort {
    param(
        [string]$HostName = "127.0.0.1",
        [int]$Port
    )

    $client = [System.Net.Sockets.TcpClient]::new()
    try {
        $asyncResult = $client.BeginConnect($HostName, $Port, $null, $null)
        if (-not $asyncResult.AsyncWaitHandle.WaitOne(1000, $false)) {
            return $false
        }
        $client.EndConnect($asyncResult)
        return $true
    } catch {
        return $false
    } finally {
        $client.Close()
    }
}

function Wait-TcpPort {
    param(
        [string]$Name,
        [int]$Port,
        [int]$Seconds = $ServiceWaitSeconds
    )

    $deadline = (Get-Date).AddSeconds($Seconds)
    while ((Get-Date) -lt $deadline) {
        if (Test-TcpPort -Port $Port) {
            Write-StackLog "$Name is reachable on port $Port."
            return $true
        }
        Start-Sleep -Seconds 3
    }

    Write-StackLog "$Name did not become reachable on port $Port within $Seconds seconds."
    return $false
}

function Get-DockerCommand {
    $docker = Get-Command docker -ErrorAction SilentlyContinue
    if ($docker) {
        return $docker.Source
    }
    return $null
}

function Test-DockerReady {
    $docker = Get-DockerCommand
    if (-not $docker) {
        return $false
    }

    & $docker info *> $null
    return $LASTEXITCODE -eq 0
}

function Start-DockerDesktopIfNeeded {
    if ($SkipDocker) {
        Write-StackLog "Docker startup skipped by parameter."
        return
    }

    if (Test-DockerReady) {
        Write-StackLog "Docker is ready."
        return
    }

    $dockerDesktop = "C:\Program Files\Docker\Docker\Docker Desktop.exe"
    if (Test-Path $dockerDesktop) {
        Write-StackLog "Starting Docker Desktop."
        Start-Process -FilePath $dockerDesktop -WindowStyle Hidden
    } else {
        Write-StackLog "Docker Desktop executable was not found."
        return
    }

    $deadline = (Get-Date).AddSeconds($DockerWaitSeconds)
    while ((Get-Date) -lt $deadline) {
        if (Test-DockerReady) {
            Write-StackLog "Docker is ready."
            return
        }
        Start-Sleep -Seconds 5
    }

    Write-StackLog "Docker was not ready within $DockerWaitSeconds seconds."
}

function Ensure-DockerContainers {
    if ($SkipDocker) {
        return
    }
    if (-not (Test-DockerReady)) {
        Write-StackLog "Docker is not ready; skipping container startup."
        return
    }

    $docker = Get-DockerCommand
    $targetNames = @(
        "leman-dev-mysql",
        "leman-dev-redis",
        "leman-dev-nacos",
        "leman-dev-rabbitmq",
        "leman-dev-postgres",
        "leman-dev-qdrant",
        "leman-dev-minio",
        "fastgpt-app",
        "fastgpt-plugin",
        "fastgpt-aiproxy",
        "fastgpt-pg",
        "fastgpt-code-sandbox",
        "fastgpt-volume-manager",
        "fastgpt-redis",
        "fastgpt-opensandbox-server",
        "fastgpt-aiproxy-pg",
        "fastgpt-mcp-server",
        "fastgpt-minio",
        "fastgpt-mongo",
        "n8n"
    )

    $existingNames = @(& $docker ps -a --format "{{.Names}}")
    $runningNames = @(& $docker ps --format "{{.Names}}")

    foreach ($name in $targetNames) {
        if ($existingNames -notcontains $name) {
            continue
        }
        if ($runningNames -contains $name) {
            Write-StackLog "Container $name is already running."
            continue
        }

        Write-StackLog "Starting container $name."
        & $docker start $name | Out-Null
    }

    Wait-TcpPort -Name "MySQL" -Port 3306 -Seconds 60 | Out-Null
    Wait-TcpPort -Name "Redis" -Port 6379 -Seconds 60 | Out-Null
    Wait-TcpPort -Name "PostgreSQL" -Port 5432 -Seconds 60 | Out-Null
    Wait-TcpPort -Name "FastGPT" -Port 13000 -Seconds 90 | Out-Null
    Wait-TcpPort -Name "n8n" -Port 5678 -Seconds 90 | Out-Null
}

function Start-Backend {
    if ((Test-TcpPort -Port 48080) -and -not $RestartBackend) {
        Write-StackLog "Backend is already reachable on port 48080."
        return
    }

    Write-StackLog "Starting backend."
    & (Join-Path $PSScriptRoot "start-yudao-server.ps1")
    Wait-TcpPort -Name "Backend" -Port 48080 | Out-Null
}

function Stop-FrontendIfRequested {
    if (-not $RestartFrontend) {
        return
    }

    $portOwner = Get-NetTCPConnection -LocalPort 80 -State Listen -ErrorAction SilentlyContinue |
        Select-Object -First 1 -ExpandProperty OwningProcess
    if (-not $portOwner) {
        return
    }

    $process = Get-Process -Id $portOwner -ErrorAction SilentlyContinue
    if ($process -and $process.ProcessName -eq "node") {
        Write-StackLog "Stopping existing frontend node process PID=$portOwner."
        Stop-Process -Id $portOwner -Force -ErrorAction SilentlyContinue
    }
}

function Start-Frontend {
    Stop-FrontendIfRequested

    if ((Test-TcpPort -Port 80) -and -not $RestartFrontend) {
        Write-StackLog "Frontend is already reachable on port 80."
        return
    }

    $pnpm = Get-Command pnpm -ErrorAction SilentlyContinue
    if (-not $pnpm) {
        throw "pnpm was not found in PATH."
    }

    $timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
    $stdout = Join-Path $logDir "frontend-vite-$timestamp.out.log"
    $stderr = Join-Path $logDir "frontend-vite-$timestamp.err.log"

    Write-StackLog "Starting frontend."
    Start-Process `
        -FilePath "powershell.exe" `
        -ArgumentList @("-NoProfile", "-ExecutionPolicy", "Bypass", "-File", $pnpm.Source, "dev") `
        -WorkingDirectory $frontendDir `
        -RedirectStandardOutput $stdout `
        -RedirectStandardError $stderr `
        -WindowStyle Hidden `
        -PassThru | Out-Null

    Write-StackLog "Frontend log: $stdout"
    Wait-TcpPort -Name "Frontend" -Port 80 | Out-Null
}

function Start-CaddyProxy {
    $running = Get-CimInstance Win32_Process |
        Where-Object { $_.Name -eq "caddy.exe" -and $_.CommandLine -like "*Caddyfile.n8n*" }

    if ($running -and $RestartCaddy) {
        Write-StackLog "Restarting Caddy proxy."
        foreach ($process in $running) {
            Stop-Process -Id $process.ProcessId -Force -ErrorAction SilentlyContinue
        }
        Start-Sleep -Seconds 1
    } elseif ($running) {
        Write-StackLog "Caddy proxy is already running. PID=$($running.ProcessId -join ',')."
        return
    }

    Write-StackLog "Starting Caddy proxy."
    & (Join-Path $PSScriptRoot "start-caddy-n8n.ps1") | Out-String | ForEach-Object {
        if ($_.Trim()) {
            Write-StackLog $_.Trim()
        }
    }

    Wait-TcpPort -Name "Caddy HTTP proxy" -Port 8080 | Out-Null
    Wait-TcpPort -Name "Caddy HTTPS proxy" -Port 443 | Out-Null
    Wait-TcpPort -Name "Caddy HTTPS proxy 433" -Port 433 | Out-Null
    Wait-TcpPort -Name "Caddy HTTPS proxy 8443" -Port 8443 | Out-Null
}

Write-StackLog "Starting local stack from $repoRoot."
Set-Location $repoRoot

Start-DockerDesktopIfNeeded
Ensure-DockerContainers
Start-Backend
Start-Frontend
Start-CaddyProxy

Write-StackLog "Local stack startup finished."
Write-StackLog "Knowledge base: http://192.168.19.36/ and http://192.168.19.36:8080/"
Write-StackLog "n8n: https://192.168.19.36/ https://192.168.19.36:433/ https://192.168.19.36:8443/"
