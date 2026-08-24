$ErrorActionPreference = 'Stop'

function Assert-True {
    param(
        [Parameter(Mandatory)]
        [bool]$Condition,
        [Parameter(Mandatory)]
        [string]$Message
    )

    if (-not $Condition) {
        throw $Message
    }
}

$commonPath = Join-Path $PSScriptRoot 'BetterGIScriptsToolsDeployment.Common.ps1'
$taskRunnerPath = Join-Path $PSScriptRoot 'Invoke-BetterGIScriptsToolsDeployTask.ps1'
$taskRegistrationPath = Join-Path $PSScriptRoot 'Register-BetterGIScriptsToolsDeploymentTask.ps1'
$localOpsRegistrationPath = Join-Path $PSScriptRoot 'Register-BetterGIScriptsToolsLocalOps.ps1'

foreach ($requiredPath in @(
    $commonPath,
    $taskRunnerPath,
    $taskRegistrationPath,
    $localOpsRegistrationPath
)) {
    Assert-True (Test-Path -LiteralPath $requiredPath -PathType Leaf) "Missing deployment control script: $requiredPath"
}

. $commonPath

$attempts = 0
$delays = [System.Collections.Generic.List[int]]::new()
Invoke-BetterGIScriptsToolsRetry -Operation 'transient test' -MaxAttempts 3 `
    -InitialDelaySeconds 1 -MaxDelaySeconds 4 `
    -Action {
        $script:attempts++
        if ($script:attempts -lt 3) { return 17 }
        return 0
    } `
    -SleepAction { param([int]$Seconds) $script:delays.Add($Seconds) }
Assert-True ($attempts -eq 3) 'Retry helper must stop after the first successful attempt'
Assert-True (($delays -join ',') -eq '1,2') 'Retry helper must use bounded exponential delays'

$failedAttempts = 0
$failure = $null
try {
    Invoke-BetterGIScriptsToolsRetry -Operation 'permanent test' -MaxAttempts 2 `
        -InitialDelaySeconds 0 -MaxDelaySeconds 0 `
        -Action { $script:failedAttempts++; return 23 } `
        -SleepAction { param([int]$Seconds) }
}
catch {
    $failure = $_.Exception.Message
}
Assert-True ($failedAttempts -eq 2) 'Retry helper must honor MaxAttempts on permanent failures'
Assert-True ($failure -like '*permanent test*23*') 'Retry failure must identify the operation and exit code'

$runnerSource = Get-Content -LiteralPath $taskRunnerPath -Raw -Encoding UTF8
Assert-True ($runnerSource.Contains("status --porcelain")) 'Task runner must reject a dirty source checkout'
Assert-True ($runnerSource.Contains("rev-parse --abbrev-ref HEAD")) 'Task runner must verify the deployment branch'
Assert-True ($runnerSource.Contains("-ExpectedLocalJarHash")) 'Task runner must pass the exact JAR hash to deployment'

$taskSource = Get-Content -LiteralPath $taskRegistrationPath -Raw -Encoding UTF8
Assert-True ($taskSource.Contains("BetterGI-ScriptsTools-Deploy")) 'Task registration must use the governed task name'
Assert-True ($taskSource.Contains("MultipleInstances IgnoreNew")) 'Task registration must prevent overlapping deploys'
Assert-True (-not $taskSource.Contains('New-ScheduledTaskTrigger')) 'Deployment task must remain manual-only'

$localOpsSource = Get-Content -LiteralPath $localOpsRegistrationPath -Raw -Encoding UTF8
Assert-True ($localOpsSource.Contains('control-credential.json')) 'LocalOps registration must use the protected CLI bearer'
Assert-True ($localOpsSource.Contains('scheduledTaskPath')) 'LocalOps registration must create a scheduled-task card'
Assert-True ($localOpsSource.Contains('BetterGI · AutoPlan 部署')) 'LocalOps registration must use a dedicated deployment card'

Write-Host 'bettergi-scripts-tools deployment control tests passed.'
