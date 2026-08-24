[CmdletBinding()]
param(
    [string]$BetterGIRoot = 'C:\Users\hcy\Programs\Genshin Tools\BetterGI',
    [string]$TaskName = 'BetterGI-ScriptsTools-Deploy',
    [string]$LocalOpsUri = 'http://127.0.0.1:9600'
)

$ErrorActionPreference = 'Stop'

$repositoryRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$runnerPath = Join-Path $PSScriptRoot 'Invoke-BetterGIScriptsToolsDeployTask.ps1'
$localOpsRegistrationPath = Join-Path $PSScriptRoot 'Register-BetterGIScriptsToolsLocalOps.ps1'
$pwshPath = (Get-Command pwsh.exe -ErrorAction Stop).Source

foreach ($requiredPath in @($runnerPath, $localOpsRegistrationPath)) {
    if (-not (Test-Path -LiteralPath $requiredPath -PathType Leaf)) {
        throw "Required deployment task script is missing: $requiredPath"
    }
}

$existingTask = Get-ScheduledTask -TaskName $TaskName -TaskPath '\' -ErrorAction SilentlyContinue
if ($null -ne $existingTask) {
    $backupRoot = Join-Path $BetterGIRoot 'backup\scheduled-tasks'
    New-Item -ItemType Directory -Force -Path $backupRoot | Out-Null
    $backupPath = Join-Path $backupRoot ("{0}-{1}.xml" -f $TaskName, (Get-Date -Format 'yyyyMMdd-HHmmss'))
    Export-ScheduledTask -TaskName $TaskName -TaskPath '\' |
        Set-Content -LiteralPath $backupPath -Encoding UTF8
}

$arguments = '-NoProfile -NonInteractive -WindowStyle Hidden -ExecutionPolicy Bypass -File "{0}"' -f $runnerPath
$action = New-ScheduledTaskAction -Execute $pwshPath -Argument $arguments -WorkingDirectory $repositoryRoot
$principal = New-ScheduledTaskPrincipal `
    -UserId ([System.Security.Principal.WindowsIdentity]::GetCurrent().Name) `
    -LogonType Interactive `
    -RunLevel Limited
$settings = New-ScheduledTaskSettingsSet `
    -MultipleInstances IgnoreNew `
    -ExecutionTimeLimit (New-TimeSpan -Hours 2) `
    -AllowStartIfOnBatteries `
    -DontStopIfGoingOnBatteries `
    -StartWhenAvailable

Register-ScheduledTask `
    -TaskName $TaskName `
    -TaskPath '\' `
    -Action $action `
    -Principal $principal `
    -Settings $settings `
    -Description 'Build, verify, and deploy the local BetterGI AutoPlan toolbox.' `
    -Force | Out-Null

$task = Get-ScheduledTask -TaskName $TaskName -TaskPath '\'
$taskXml = Export-ScheduledTask -TaskName $TaskName -TaskPath '\'
if ($taskXml -notmatch '<Triggers\s*/>') {
    throw 'BetterGI deployment task must not have automatic triggers.'
}
if ($taskXml -notmatch '<MultipleInstancesPolicy>IgnoreNew</MultipleInstancesPolicy>') {
    throw 'BetterGI deployment task must ignore overlapping starts.'
}
if ($task.Actions.Execute -ne $pwshPath -or $task.Actions.Arguments -notlike "*$runnerPath*") {
    throw 'BetterGI deployment task action does not match the governed runner.'
}

$localOps = & $localOpsRegistrationPath `
    -LocalOpsUri $LocalOpsUri `
    -BetterGIRoot $BetterGIRoot `
    -ScheduledTaskPath "\$TaskName" |
    ConvertFrom-Json

[pscustomobject]@{
    taskName = $TaskName
    taskPath = "\$TaskName"
    state = $task.State.ToString()
    action = $task.Actions.Execute
    arguments = $task.Actions.Arguments
    localOpsCardId = $localOps.deploymentCardId
} | ConvertTo-Json -Depth 5
