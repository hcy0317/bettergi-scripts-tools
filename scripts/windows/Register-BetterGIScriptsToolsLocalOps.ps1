[CmdletBinding()]
param(
    [string]$LocalOpsUri = 'http://127.0.0.1:9600',
    [string]$BetterGIRoot = 'C:\Users\hcy\Programs\Genshin Tools\BetterGI',
    [string]$ScheduledTaskPath = '\BetterGI-ScriptsTools-Deploy'
)

$ErrorActionPreference = 'Stop'

$repositoryRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$deploymentRoot = Join-Path $BetterGIRoot 'scripts\bettergi-scripts-tools'
$composePath = Join-Path $deploymentRoot 'docker-compose.yml'
$credentialPath = Join-Path $env:LOCALAPPDATA 'LocalOps\control-credential.json'

foreach ($requiredPath in @($repositoryRoot, $composePath, $credentialPath)) {
    if (-not (Test-Path -LiteralPath $requiredPath)) {
        throw "Required LocalOps integration path is missing: $requiredPath"
    }
}

$credential = Get-Content -LiteralPath $credentialPath -Raw -Encoding UTF8 | ConvertFrom-Json
if ([string]::IsNullOrWhiteSpace([string]$credential.token)) {
    throw 'LocalOps control credential does not contain a CLI bearer.'
}
$headers = @{ Authorization = "Bearer $($credential.token)" }
$state = Invoke-RestMethod -Uri "$LocalOpsUri/api/state" -Headers $headers -TimeoutSec 10

function Assert-UniqueCard {
    param(
        [Parameter(Mandatory)]
        [string]$Name
    )

    $matches = @($state.apps | Where-Object { $_.name -eq $Name })
    if ($matches.Count -gt 1) {
        throw "LocalOps contains duplicate cards named '$Name'."
    }
    return $matches
}

$serviceName = 'BetterGI · AutoPlan 工具集'
$serviceCards = @(Assert-UniqueCard -Name $serviceName)
if ($serviceCards.Count -eq 0) {
    $servicePayload = [ordered]@{
        name = $serviceName
        command = 'docker compose up --detach'
        cwd = $deploymentRoot
        port = $null
        glyph = 'server'
        kind = 'service'
        dockerResource = [ordered]@{
            kind = 'compose'
            projectName = 'bettergi-scripts-tools'
            workingDir = $deploymentRoot
            configFiles = @($composePath)
        }
    }
    $createdService = Invoke-RestMethod -Method Post -Uri "$LocalOpsUri/api/apps" `
        -Headers $headers -ContentType 'application/json; charset=utf-8' `
        -Body ($servicePayload | ConvertTo-Json -Depth 10)
    if (-not $createdService.id) {
        throw 'LocalOps did not return the created BetterGI service card.'
    }
}
elseif ($serviceCards[0].dockerResource.projectName -ne 'bettergi-scripts-tools') {
    throw 'The existing BetterGI AutoPlan service card targets another resource.'
}

$deploymentName = 'BetterGI · AutoPlan 部署'
$deploymentCards = @(Assert-UniqueCard -Name $deploymentName)
if ($deploymentCards.Count -eq 0) {
    $taskPayload = [ordered]@{
        name = $deploymentName
        command = 'schtasks.exe /Run /TN "BetterGI-ScriptsTools-Deploy"'
        cwd = $null
        port = $null
        glyph = 'package'
        kind = 'task'
        scheduledTaskPath = $ScheduledTaskPath
    }
    $createdTask = Invoke-RestMethod -Method Post -Uri "$LocalOpsUri/api/apps" `
        -Headers $headers -ContentType 'application/json; charset=utf-8' `
        -Body ($taskPayload | ConvertTo-Json -Depth 10)
    if (-not $createdTask.id) {
        throw 'LocalOps did not return the created BetterGI deployment card.'
    }
}
elseif ($deploymentCards[0].scheduledTaskPath -ne $ScheduledTaskPath) {
    throw 'The existing BetterGI deployment card targets another scheduled task.'
}

$verifiedState = Invoke-RestMethod -Uri "$LocalOpsUri/api/state" -Headers $headers -TimeoutSec 10
$verifiedTask = @($verifiedState.apps | Where-Object { $_.name -eq $deploymentName })
if ($verifiedTask.Count -ne 1 -or $verifiedTask[0].scheduledTaskPath -ne $ScheduledTaskPath) {
    throw 'LocalOps did not persist the BetterGI deployment scheduled-task card.'
}

[pscustomobject]@{
    serviceCard = $serviceName
    deploymentCard = $deploymentName
    scheduledTaskPath = $ScheduledTaskPath
    deploymentCardId = $verifiedTask[0].id
} | ConvertTo-Json -Depth 4
