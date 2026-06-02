param(
    [string]$TaskName = "Leman AI Demo Local Stack"
)

$ErrorActionPreference = "Stop"

$task = Get-ScheduledTask -TaskName $TaskName -ErrorAction SilentlyContinue
if ($task) {
    Unregister-ScheduledTask -TaskName $TaskName -Confirm:$false
    Write-Host "Removed scheduled task: $TaskName"
}

& schtasks.exe /Query /TN $TaskName *> $null
if ($LASTEXITCODE -eq 0) {
    & schtasks.exe /Delete /TN $TaskName /F | Out-Null
    Write-Host "Removed schtasks.exe task: $TaskName"
}

$startupDir = [Environment]::GetFolderPath("Startup")
$shortcutPath = Join-Path $startupDir "$TaskName.lnk"
if (Test-Path $shortcutPath) {
    Remove-Item -LiteralPath $shortcutPath -Force
    Write-Host "Removed Startup folder shortcut: $shortcutPath"
}
