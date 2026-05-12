param(
    [switch]$Build
)

$ErrorActionPreference = "Stop"

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot "..")
Set-Location $repoRoot

function Import-EnvValue {
    param([string]$Name)

    $current = [Environment]::GetEnvironmentVariable($Name, "Process")
    if ($current) {
        return
    }
    $userValue = [Environment]::GetEnvironmentVariable($Name, "User")
    if ($userValue) {
        [Environment]::SetEnvironmentVariable($Name, $userValue, "Process")
        return
    }
    $machineValue = [Environment]::GetEnvironmentVariable($Name, "Machine")
    if ($machineValue) {
        [Environment]::SetEnvironmentVariable($Name, $machineValue, "Process")
    }
}

$requiredEnvNames = @(
    "AI_MODEL_PROVIDER",
    "AI_BASE_URL",
    "AI_API_KEY",
    "AI_CHAT_MODEL",
    "AI_EMBEDDING_MODEL",
    "AI_VECTOR_STORE_TYPE",
    "AI_PGVECTOR_JDBC_URL",
    "AI_PGVECTOR_USERNAME",
    "AI_PGVECTOR_PASSWORD",
    "SPRING_DATASOURCE_PASSWORD"
)

$optionalEnvNames = @(
    "AI_DOCUMENT_OCR_ENABLED",
    "AI_DOCUMENT_OCR_PROVIDER",
    "AI_DOCUMENT_OCR_TESSERACT_EXECUTABLE",
    "AI_DOCUMENT_OCR_TESSDATA_DIRECTORY",
    "AI_DOCUMENT_OCR_LANGUAGE",
    "AI_DOCUMENT_OCR_DPI",
    "AI_DOCUMENT_OCR_MAX_PAGES",
    "AI_DOCUMENT_OCR_TIMEOUT_SECONDS"
)

foreach ($name in ($requiredEnvNames + $optionalEnvNames)) {
    Import-EnvValue $name
}

if (-not $env:AI_MODEL_PROVIDER) {
    $env:AI_MODEL_PROVIDER = "openai-compatible"
}
if (-not $env:AI_VECTOR_STORE_TYPE) {
    $env:AI_VECTOR_STORE_TYPE = "pgvector"
}

$missing = $requiredEnvNames | Where-Object { -not [Environment]::GetEnvironmentVariable($_, "Process") }
if ($missing.Count -gt 0) {
    throw "Missing required environment variables: $($missing -join ', '). Configure them in Windows user/machine env or current PowerShell. Secret values are never printed."
}

$oldServers = Get-CimInstance Win32_Process -Filter "Name = 'java.exe'" |
    Where-Object { $_.CommandLine -like "*yudao-server-1.0.0-SNAPSHOT.jar*" }
foreach ($server in $oldServers) {
    Stop-Process -Id $server.ProcessId -Force -ErrorAction SilentlyContinue
}

if ($Build) {
    mvn package -DskipTests
    if ($LASTEXITCODE -ne 0) {
        throw "Maven package failed with exit code $LASTEXITCODE"
    }
}

$jarPath = Join-Path $repoRoot "yudao-server\target\yudao-server-1.0.0-SNAPSHOT.jar"
if (-not (Test-Path $jarPath)) {
    throw "Backend JAR not found: $jarPath. Run: .\scripts\start-yudao-server.ps1 -Build"
}

$javaHomeCandidate = Join-Path $env:USERPROFILE ".codex\dev-env\tools\jdk-17\bin\java.exe"
$java = if (Test-Path $javaHomeCandidate) { $javaHomeCandidate } else { "java" }

$logDir = Join-Path $repoRoot "logs"
New-Item -ItemType Directory -Force -Path $logDir | Out-Null
$timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$stdout = Join-Path $logDir "yudao-server-$timestamp.out.log"
$stderr = Join-Path $logDir "yudao-server-$timestamp.err.log"

$process = Start-Process -FilePath $java `
    -ArgumentList @("-jar", "`"$jarPath`"") `
    -WorkingDirectory $repoRoot `
    -RedirectStandardOutput $stdout `
    -RedirectStandardError $stderr `
    -WindowStyle Hidden `
    -PassThru

Write-Host "Backend started, PID=$($process.Id)"
Write-Host "AI_MODEL_PROVIDER=$env:AI_MODEL_PROVIDER"
Write-Host "AI_VECTOR_STORE_TYPE=$env:AI_VECTOR_STORE_TYPE"
Write-Host "Log: $stdout"
