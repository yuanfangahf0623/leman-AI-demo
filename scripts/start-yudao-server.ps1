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
    "SERVER_JAVA_OPTS",
    "AI_RAG_ENGINE",
    "FASTGPT_BASE_URL",
    "FASTGPT_API_KEY",
    "FASTGPT_APP_ID",
    "FASTGPT_MODEL",
    "FASTGPT_CONNECT_TIMEOUT_SECONDS",
    "FASTGPT_READ_TIMEOUT_SECONDS",
    "TWO_HAO_HR_ACCESS_TOKEN",
    "TWO_HAO_HR_CORP_ID",
    "TWO_HAO_HR_APP_ID",
    "TWO_HAO_HR_APP_SECRET",
    "TWO_HAO_HR_CALLBACK_TOKEN",
    "AI_DOCUMENT_OCR_ENABLED",
    "AI_DOCUMENT_OCR_PROVIDER",
    "AI_DOCUMENT_OCR_TESSERACT_EXECUTABLE",
    "AI_DOCUMENT_OCR_TESSDATA_DIRECTORY",
    "AI_DOCUMENT_OCR_LANGUAGE",
    "AI_DOCUMENT_OCR_DPI",
    "AI_DOCUMENT_OCR_MAX_PAGES",
    "AI_DOCUMENT_OCR_TIMEOUT_SECONDS",
    "AI_DOCUMENT_OCR_MIN_TEXT_LENGTH_TO_SKIP_OCR",
    "AI_DOCUMENT_OCR_VISION_FALLBACK_ENABLED",
    "AI_DOCUMENT_OCR_VISION_MODEL",
    "AI_DOCUMENT_OCR_VISION_FALLBACK_MAX_PAGES",
    "AI_DOCUMENT_OCR_VISION_FALLBACK_MAX_IMAGE_BYTES",
    "AI_DOCUMENT_OCR_VISION_FALLBACK_MIN_TEXT_LENGTH",
    "AI_DOCUMENT_OCR_VISION_FALLBACK_GARBLED_RATIO_THRESHOLD",
    "AI_DOCUMENT_OCR_VISION_FALLBACK_TIMEOUT_SECONDS",
    "AI_INVOICE_RECOGNITION_PROVIDER",
    "AI_INVOICE_RECOGNITION_MODEL",
    "AI_INVOICE_RECOGNITION_MAX_OCR_CHARS",
    "AI_INVOICE_RECOGNITION_MAX_TOKENS",
    "AI_INVOICE_RECOGNITION_FALLBACK_TO_MOCK"
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
$javaOpts = if ($env:SERVER_JAVA_OPTS) {
    $env:SERVER_JAVA_OPTS -split "\s+" | Where-Object { $_ }
} else {
    @("-Xms1g", "-Xmx8g")
}

$process = Start-Process -FilePath $java `
    -ArgumentList @($javaOpts + @("-jar", "`"$jarPath`"")) `
    -WorkingDirectory $repoRoot `
    -RedirectStandardOutput $stdout `
    -RedirectStandardError $stderr `
    -WindowStyle Hidden `
    -PassThru

Write-Host "Backend started, PID=$($process.Id)"
Write-Host "AI_MODEL_PROVIDER=$env:AI_MODEL_PROVIDER"
Write-Host "AI_VECTOR_STORE_TYPE=$env:AI_VECTOR_STORE_TYPE"
Write-Host "AI_RAG_ENGINE=$env:AI_RAG_ENGINE"
Write-Host "Java opts: $($javaOpts -join ' ')"
Write-Host "Log: $stdout"
