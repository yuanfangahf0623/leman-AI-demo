param(
    [switch]$BuildBackend,
    [switch]$RestartBackend,
    [switch]$RestartFrontend,
    [switch]$RestartCaddy,
    [switch]$SkipDocker,
    [switch]$SkipMiddleware,
    [switch]$SkipFastGpt,
    [switch]$SkipN8n,
    [switch]$SkipBackend,
    [switch]$SkipFrontend,
    [switch]$SkipCaddy,
    [int]$DockerWaitSeconds = 180,
    [int]$ServiceWaitSeconds = 120
)

$ErrorActionPreference = "Stop"

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$backendScript = Join-Path $PSScriptRoot "start-yudao-server.ps1"
$middlewareCompose = Join-Path $repoRoot "deploy\dev\docker-compose.middleware.yml"
$fastGptCompose = Join-Path $repoRoot "deploy\fastgpt\docker-compose.yml"
$fastGptEnv = Join-Path $repoRoot "deploy\fastgpt\.env"
$frontendDir = Join-Path $repoRoot "frontend\yudao-ui-admin-vue3"
$logDir = Join-Path $repoRoot "logs"
$runLog = Join-Path $logDir "local-stack-startup.log"
$backendJar = Join-Path $repoRoot "yudao-server\target\yudao-server-1.0.0-SNAPSHOT.jar"
$backendBuildWatchPaths = @(
    (Join-Path $repoRoot "pom.xml"),
    (Join-Path $repoRoot "yudao-server\pom.xml"),
    (Join-Path $repoRoot "yudao-server\src"),
    (Join-Path $repoRoot "yudao-module-ai\yudao-module-ai-api\pom.xml"),
    (Join-Path $repoRoot "yudao-module-ai\yudao-module-ai-api\src"),
    (Join-Path $repoRoot "yudao-module-ai\yudao-module-ai-biz\pom.xml"),
    (Join-Path $repoRoot "yudao-module-ai\yudao-module-ai-biz\src")
)

New-Item -ItemType Directory -Force -Path $logDir | Out-Null

function Write-StackLog {
    param([string]$Message)

    $line = "$(Get-Date -Format 'yyyy-MM-dd HH:mm:ss') $Message"
    Write-Host $line
    Add-Content -Path $runLog -Value $line -Encoding UTF8
}

function Test-TcpPort {
    param(
        [string]$HostName = "127.0.0.1",
        [Parameter(Mandatory = $true)]
        [int]$Port
    )

    $client = [System.Net.Sockets.TcpClient]::new()
    try {
        $connect = $client.BeginConnect($HostName, $Port, $null, $null)
        if (-not $connect.AsyncWaitHandle.WaitOne(1000, $false)) {
            return $false
        }
        $client.EndConnect($connect)
        return $true
    } catch {
        return $false
    } finally {
        $client.Close()
    }
}

function Wait-TcpPort {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Name,
        [Parameter(Mandatory = $true)]
        [int]$Port,
        [int]$Seconds = $ServiceWaitSeconds
    )

    $deadline = (Get-Date).AddSeconds($Seconds)
    while ((Get-Date) -lt $deadline) {
        if (Test-TcpPort -Port $Port) {
            Write-StackLog "[OK] $Name is reachable on port $Port."
            return
        }
        Start-Sleep -Seconds 3
    }

    throw "$Name did not become reachable on port $Port within $Seconds seconds."
}

function Invoke-Docker {
    param([Parameter(Mandatory = $true)][string[]]$DockerArguments)

    & docker @DockerArguments
    if ($LASTEXITCODE -ne 0) {
        throw "Docker command failed: docker $($DockerArguments -join ' ')"
    }
}

function Test-DockerReady {
    if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
        return $false
    }

    try {
        & docker info *> $null
        return $LASTEXITCODE -eq 0
    } catch {
        return $false
    }
}

function Test-BackendJarStale {
    if (-not (Test-Path $backendJar)) {
        return $true
    }

    $jarWriteTime = (Get-Item -LiteralPath $backendJar).LastWriteTimeUtc
    foreach ($path in $backendBuildWatchPaths) {
        if (-not (Test-Path -LiteralPath $path)) {
            continue
        }

        $item = Get-Item -LiteralPath $path
        if (-not $item.PSIsContainer) {
            if ($item.LastWriteTimeUtc -gt $jarWriteTime) {
                return $true
            }
            continue
        }

        $newerFile = Get-ChildItem -LiteralPath $item.FullName -Recurse -File -ErrorAction SilentlyContinue |
            Where-Object { $_.LastWriteTimeUtc -gt $jarWriteTime } |
            Select-Object -First 1
        if ($newerFile) {
            return $true
        }
    }

    return $false
}

function Start-DockerDesktop {
    if ($SkipDocker) {
        if ($SkipMiddleware -and $SkipFastGpt -and $SkipN8n) {
            Write-StackLog "Docker startup skipped."
            return
        }
        if (-not (Test-DockerReady)) {
            throw "Docker startup was skipped, but Docker Engine is not ready."
        }
        Write-StackLog "Docker startup skipped; Docker Engine is already ready."
        return
    }

    if (Test-DockerReady) {
        Write-StackLog "Docker Engine is ready."
        return
    }

    $dockerDesktop = "C:\Program Files\Docker\Docker\Docker Desktop.exe"
    if (-not (Test-Path $dockerDesktop)) {
        throw "Docker Desktop executable was not found: $dockerDesktop"
    }

    Start-Service -Name "com.docker.service" -ErrorAction SilentlyContinue
    Write-StackLog "Starting Docker Desktop."
    Start-Process -FilePath $dockerDesktop -WindowStyle Hidden

    $deadline = (Get-Date).AddSeconds($DockerWaitSeconds)
    while ((Get-Date) -lt $deadline) {
        if (Test-DockerReady) {
            Write-StackLog "[OK] Docker Engine is ready."
            return
        }
        Start-Sleep -Seconds 5
    }

    throw "Docker Engine was not ready within $DockerWaitSeconds seconds. Check Docker Desktop and WSL status."
}

function Start-Middleware {
    if ($SkipMiddleware) {
        Write-StackLog "Middleware startup skipped."
        return
    }
    if (-not (Test-Path $middlewareCompose)) {
        throw "Middleware compose file not found: $middlewareCompose"
    }

    Write-StackLog "Creating or starting development middleware."
    Invoke-Docker -DockerArguments @("compose", "-f", $middlewareCompose, "--profile", "local-minio", "up", "-d")

    Wait-TcpPort -Name "MySQL" -Port 3306 -Seconds 90
    Wait-TcpPort -Name "Redis" -Port 6379 -Seconds 60
    Wait-TcpPort -Name "Nacos" -Port 8848 -Seconds 120
    Wait-TcpPort -Name "PostgreSQL/pgvector" -Port 5432 -Seconds 90
    Wait-TcpPort -Name "MinIO API" -Port 9000 -Seconds 90
    Wait-TcpPort -Name "Qdrant HTTP" -Port 6333 -Seconds 90
    Wait-TcpPort -Name "RabbitMQ AMQP" -Port 5672 -Seconds 90
}

function Start-FastGpt {
    if ($SkipFastGpt) {
        Write-StackLog "FastGPT startup skipped."
        return
    }
    if (-not (Test-Path $fastGptCompose)) {
        throw "FastGPT compose file not found: $fastGptCompose"
    }
    if (-not (Test-Path $fastGptEnv)) {
        throw "FastGPT environment file not found: $fastGptEnv. Create it from .env.example without committing secrets."
    }

    Write-StackLog "Creating or starting FastGPT."
    Invoke-Docker -DockerArguments @("compose", "--env-file", $fastGptEnv, "-f", $fastGptCompose, "up", "-d")
    Wait-TcpPort -Name "FastGPT" -Port 13000 -Seconds 180
}

function Start-N8n {
    if ($SkipN8n) {
        Write-StackLog "n8n startup skipped."
        return
    }

    $containerId = (& docker ps -a --filter "name=^/n8n$" --format "{{.ID}}" | Select-Object -First 1)
    if ($LASTEXITCODE -ne 0) {
        throw "Unable to inspect the n8n container."
    }

    if (-not $containerId) {
        Write-StackLog "n8n container does not exist; creating it with a persistent Docker volume."
        Invoke-Docker -DockerArguments @("volume", "create", "leman_n8n_data")
        Invoke-Docker -DockerArguments @(
            "run", "-d", "--name", "n8n", "--restart", "unless-stopped",
            "-p", "5678:5678", "-v", "leman_n8n_data:/home/node/.n8n", "n8nio/n8n"
        )
    } else {
        $running = (& docker ps --filter "name=^/n8n$" --format "{{.ID}}" | Select-Object -First 1)
        if (-not $running) {
            Write-StackLog "Starting existing n8n container."
            Invoke-Docker -DockerArguments @("start", "n8n")
        } else {
            Write-StackLog "n8n container is already running."
        }
    }

    Wait-TcpPort -Name "n8n" -Port 5678 -Seconds 120
}

function Start-Backend {
    if ($SkipBackend) {
        Write-StackLog "Backend startup skipped."
        return
    }
    $backendJarStale = Test-BackendJarStale
    if ($backendJarStale) {
        Write-StackLog "Backend JAR is missing or older than source files; rebuilding with scripts/start-yudao-server.ps1."
    }
    if ((Test-TcpPort -Port 48080) -and -not $RestartBackend -and -not $BuildBackend -and -not $backendJarStale) {
        Write-StackLog "[OK] Backend is already reachable on port 48080."
        return
    }
    if (-not (Test-Path $backendScript)) {
        throw "Backend startup script not found: $backendScript"
    }

    Write-StackLog "Starting yudao-server."
    if ($BuildBackend -or $backendJarStale) {
        & $backendScript -Build
    } else {
        & $backendScript
    }
    Wait-TcpPort -Name "yudao-server" -Port 48080 -Seconds 180
}

function Stop-Frontend {
    $connection = Get-NetTCPConnection -LocalPort 80 -State Listen -ErrorAction SilentlyContinue |
        Select-Object -First 1
    if (-not $connection) {
        return
    }

    $process = Get-Process -Id $connection.OwningProcess -ErrorAction SilentlyContinue
    if ($process -and $process.ProcessName -eq "node") {
        Write-StackLog "Stopping frontend process PID=$($process.Id)."
        Stop-Process -Id $process.Id -Force
        return
    }

    throw "Port 80 is occupied by PID=$($connection.OwningProcess), which is not the frontend Node process."
}

function Start-Frontend {
    if ($SkipFrontend) {
        Write-StackLog "Frontend startup skipped."
        return
    }
    if ($RestartFrontend) {
        Stop-Frontend
    } elseif (Test-TcpPort -Port 80) {
        Write-StackLog "[OK] Frontend is already reachable on port 80."
        return
    }
    if (-not (Test-Path (Join-Path $frontendDir "package.json"))) {
        throw "Frontend project not found: $frontendDir"
    }
    if (-not (Test-Path (Join-Path $frontendDir "node_modules"))) {
        throw "Frontend dependencies are not installed. Run pnpm install in $frontendDir first."
    }

    $pnpm = Get-Command pnpm.cmd -ErrorAction SilentlyContinue
    if (-not $pnpm) {
        throw "pnpm.cmd was not found in PATH."
    }

    $timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
    $stdout = Join-Path $logDir "frontend-vite-$timestamp.out.log"
    $stderr = Join-Path $logDir "frontend-vite-$timestamp.err.log"

    Write-StackLog "Starting frontend with pnpm dev."
    Start-Process `
        -FilePath $env:ComSpec `
        -ArgumentList @("/d", "/s", "/c", "`"$($pnpm.Source)`" dev") `
        -WorkingDirectory $frontendDir `
        -RedirectStandardOutput $stdout `
        -RedirectStandardError $stderr `
        -WindowStyle Hidden | Out-Null

    Write-StackLog "Frontend logs: $stdout and $stderr"
    Wait-TcpPort -Name "Frontend" -Port 80 -Seconds 180
}

function Start-Caddy {
    if ($SkipCaddy) {
        Write-StackLog "Caddy startup skipped."
        return
    }

    $running = Get-CimInstance Win32_Process |
        Where-Object { $_.Name -eq "caddy.exe" -and $_.CommandLine -like "*Caddyfile.n8n*" }
    if ($running -and $RestartCaddy) {
        Write-StackLog "Restarting Caddy proxy."
        foreach ($process in $running) {
            Stop-Process -Id $process.ProcessId -Force -ErrorAction SilentlyContinue
        }
        Start-Sleep -Seconds 1
    } elseif ($running) {
        Write-StackLog "Caddy proxy is already running."
    }

    if (-not $running -or $RestartCaddy) {
        & (Join-Path $PSScriptRoot "start-caddy-n8n.ps1") | Out-Null
    }

    Wait-TcpPort -Name "Caddy frontend proxy" -Port 8080 -Seconds 60
    Wait-TcpPort -Name "Caddy HTTPS proxy" -Port 443 -Seconds 60
    Wait-TcpPort -Name "Caddy n8n HTTPS proxy" -Port 8443 -Seconds 60
}

try {
    Write-StackLog "Starting local stack from $repoRoot."
    Set-Location $repoRoot

    Start-DockerDesktop
    Start-Middleware
    Start-FastGpt
    Start-N8n
    Start-Backend
    Start-Frontend
    Start-Caddy

    Write-StackLog "Local stack startup completed successfully."
    Write-StackLog "Frontend: http://localhost/ or http://192.168.19.36/"
    Write-StackLog "Backend: http://localhost:48080/"
    Write-StackLog "FastGPT: http://localhost:13000/"
    Write-StackLog "n8n: http://localhost:5678/ or https://192.168.19.36:8443/"
} catch {
    Write-StackLog "[FAILED] $($_.Exception.Message)"
    Write-StackLog "See component logs under $logDir."
    exit 1
}
