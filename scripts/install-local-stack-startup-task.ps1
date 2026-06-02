param(
    [string]$TaskName = "Leman AI Demo Local Stack",
    [switch]$RunNow
)

$ErrorActionPreference = "Stop"

$startupScript = Join-Path $PSScriptRoot "start-local-stack.ps1"
if (-not (Test-Path $startupScript)) {
    throw "Startup script not found: $startupScript"
}

$userId = "$env:USERDOMAIN\$env:USERNAME"
$argument = "-NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -File `"$startupScript`""

function New-StartupShortcut {
    $startupDir = [Environment]::GetFolderPath("Startup")
    $shortcutPath = Join-Path $startupDir "$TaskName.lnk"
    $shell = New-Object -ComObject WScript.Shell
    $shortcut = $shell.CreateShortcut($shortcutPath)
    $shortcut.TargetPath = "powershell.exe"
    $shortcut.Arguments = $argument
    $shortcut.WorkingDirectory = Split-Path $startupScript -Parent
    $shortcut.WindowStyle = 7
    $shortcut.Description = "Starts Leman AI demo local stack after user logon."
    $shortcut.Save()
    return $shortcutPath
}

$registered = $false

try {
    $action = New-ScheduledTaskAction -Execute "powershell.exe" -Argument $argument
    $trigger = New-ScheduledTaskTrigger -AtLogOn
    $trigger.Delay = "PT45S"
    $principal = New-ScheduledTaskPrincipal -UserId $userId -LogonType Interactive -RunLevel Limited
    $settings = New-ScheduledTaskSettingsSet `
        -AllowStartIfOnBatteries `
        -DontStopIfGoingOnBatteries `
        -StartWhenAvailable `
        -MultipleInstances IgnoreNew `
        -ExecutionTimeLimit (New-TimeSpan -Hours 2)

    Register-ScheduledTask `
        -TaskName $TaskName `
        -Action $action `
        -Trigger $trigger `
        -Principal $principal `
        -Settings $settings `
        -Description "Starts Leman AI demo backend, frontend, Caddy proxy, and Docker containers after user logon." `
        -Force | Out-Null

    Write-Host "Registered scheduled task: $TaskName"
    $registered = $true
} catch {
    Write-Warning "Register-ScheduledTask failed: $($_.Exception.Message)"
}

if (-not $registered) {
    $taskRun = "powershell.exe $argument"
    $previousErrorActionPreference = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    try {
        & schtasks.exe /Create /TN $TaskName /SC ONLOGON /TR $taskRun /F *> $null
        if ($LASTEXITCODE -eq 0) {
            Write-Host "Registered scheduled task with schtasks.exe: $TaskName"
            $registered = $true
        } else {
            Write-Warning "schtasks.exe registration failed. Falling back to Startup folder shortcut."
        }
    } finally {
        $ErrorActionPreference = $previousErrorActionPreference
    }
}

if (-not $registered) {
    $shortcutPath = New-StartupShortcut
    Write-Host "Created Startup folder shortcut: $shortcutPath"
}

Write-Host "User: $userId"
Write-Host "Script: $startupScript"

if ($RunNow) {
    Start-ScheduledTask -TaskName $TaskName
    Write-Host "Started scheduled task: $TaskName"
}
