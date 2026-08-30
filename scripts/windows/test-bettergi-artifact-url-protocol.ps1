[CmdletBinding()]
param()

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

$handlerPath = Join-Path $PSScriptRoot 'Invoke-BetterGIArtifactUrl.ps1'
$registrationPath = Join-Path $PSScriptRoot 'Register-BetterGIArtifactUrlProtocol.ps1'
Assert-True (Test-Path -LiteralPath $handlerPath -PathType Leaf) "Missing artifact URL handler: $handlerPath"
Assert-True (Test-Path -LiteralPath $registrationPath -PathType Leaf) "Missing artifact URL registration script: $registrationPath"

$registrationSource = Get-Content -Raw -LiteralPath $registrationPath -Encoding UTF8
$handlerSource = Get-Content -Raw -LiteralPath $handlerPath -Encoding UTF8
Assert-True ($registrationSource.Contains('HKEY_CURRENT_USER')) 'Artifact protocol must be registered per-user'
Assert-True ($registrationSource.Contains('-WindowStyle Hidden')) 'Artifact protocol handler must run without a visible console window'
Assert-True ($registrationSource.Contains('-NoProfile -NonInteractive')) 'Artifact protocol handler must use a non-interactive PowerShell process'
Assert-True (-not $registrationSource.Contains('cmd.exe')) 'Artifact protocol registration must not introduce a cmd.exe launcher'
Assert-True (-not $handlerSource.Contains('Get-Process -Name BetterGI')) `
    'Artifact protocol handler must always dispatch through BetterGI activation forwarding'
Assert-True (-not $handlerSource.Contains('existingInstance = $true')) `
    'Artifact protocol handler must not acknowledge a request before dispatching it'

$tempRoot = Join-Path ([System.IO.Path]::GetTempPath()) ("bettergi-artifact-protocol-{0}" -f [guid]::NewGuid())
$requestRoot = Join-Path $tempRoot 'User\launch-requests\artifact-analysis'
New-Item -ItemType Directory -Path $requestRoot -Force | Out-Null
New-Item -ItemType File -Path (Join-Path $tempRoot 'BetterGI.exe') -Force | Out-Null

try {
    $cases = @(
        @{ HostName = 'analysis'; Operation = 'ANALYZE' },
        @{ HostName = 'characters'; Operation = 'SCAN_CHARACTER_ROSTER' },
        @{ HostName = 'execute'; Operation = 'EXECUTE_LOCK_PLAN' },
        @{ HostName = 'native-sync'; Operation = 'REBUILD_NATIVE_PLANS' }
    )

    foreach ($case in $cases) {
        $token = [guid]::NewGuid().ToString()
        $requestPath = Join-Path $requestRoot "$token.json"
        [ordered]@{
            version = 1
            kind = 'artifact-analysis'
            uid = '102550550'
            jobId = "job-$token"
            operation = $case.Operation
            createdAtUtc = [DateTimeOffset]::UtcNow.AddSeconds(-5).ToString('o')
            expiresAtUtc = [DateTimeOffset]::UtcNow.AddMinutes(5).ToString('o')
            sourceArtifactCount = if ($case.Operation -eq 'EXECUTE_LOCK_PLAN') { 1 } else { $null }
            targets = if ($case.Operation -eq 'EXECUTE_LOCK_PLAN') {
                @(@{ scanIndex = 0; expectedFingerprint = ('a' * 64); expectedLocked = $false })
            } else { @() }
            nativeCapacity = if ($case.Operation -eq 'REBUILD_NATIVE_PLANS') { 100 } else { $null }
            nativePlanDigest = if ($case.Operation -eq 'REBUILD_NATIVE_PLANS') { 'b' * 64 } else { $null }
            characterLevelThreshold = if ($case.Operation -eq 'SCAN_CHARACTER_ROSTER') { 80 } else { $null }
            favoriteOverride = if ($case.Operation -eq 'SCAN_CHARACTER_ROSTER') { $true } else { $null }
        } | ConvertTo-Json | Set-Content -LiteralPath $requestPath -Encoding UTF8

        $plan = & $handlerPath `
            -Uri "BetterGIArtifact://$($case.HostName)?request=$token" `
            -BetterGIRoot $tempRoot `
            -NoLaunch | ConvertFrom-Json

        Assert-True ($plan.requestToken -eq $token) 'Handler returned the wrong request token'
        Assert-True ($plan.operation -eq $case.Operation) 'Handler returned the wrong operation'
        Assert-True ($plan.argumentList[0] -eq '--artifact-host-request') 'Handler must use the BetterGI artifact host command'
        Assert-True ($plan.argumentList[1] -eq $requestPath) 'Handler must pass the controlled request file path'
        Assert-True (Test-Path -LiteralPath $requestPath -PathType Leaf) 'Handler must not consume the one-time request before the host API does'
    }

    $mismatchToken = [guid]::NewGuid().ToString()
    $mismatchPath = Join-Path $requestRoot "$mismatchToken.json"
    [ordered]@{
        version = 1
        kind = 'artifact-analysis'
        uid = '102550550'
        jobId = 'job-mismatch'
        operation = 'EXECUTE_LOCK_PLAN'
        createdAtUtc = [DateTimeOffset]::UtcNow.AddSeconds(-5).ToString('o')
        expiresAtUtc = [DateTimeOffset]::UtcNow.AddMinutes(5).ToString('o')
    } | ConvertTo-Json | Set-Content -LiteralPath $mismatchPath -Encoding UTF8

    $mismatchRejected = $false
    try {
        & $handlerPath -Uri "BetterGIArtifact://analysis?request=$mismatchToken" -BetterGIRoot $tempRoot -NoLaunch | Out-Null
    }
    catch {
        $mismatchRejected = $_.Exception.Message -like '*operation*'
    }
    Assert-True $mismatchRejected 'Handler must reject a URI host that does not match the request operation'

    $expiredToken = [guid]::NewGuid().ToString()
    $expiredPath = Join-Path $requestRoot "$expiredToken.json"
    [ordered]@{
        version = 1
        kind = 'artifact-analysis'
        uid = '102550550'
        jobId = 'job-expired'
        operation = 'ANALYZE'
        createdAtUtc = [DateTimeOffset]::UtcNow.AddMinutes(-10).ToString('o')
        expiresAtUtc = [DateTimeOffset]::UtcNow.AddSeconds(-1).ToString('o')
    } | ConvertTo-Json | Set-Content -LiteralPath $expiredPath -Encoding UTF8

    $expiredRejected = $false
    try {
        & $handlerPath -Uri "BetterGIArtifact://analysis?request=$expiredToken" -BetterGIRoot $tempRoot -NoLaunch | Out-Null
    }
    catch {
        $expiredRejected = $_.Exception.Message -like '*expired*'
    }
    Assert-True $expiredRejected 'Handler must reject expired artifact requests'
}
finally {
    Remove-Item -LiteralPath $tempRoot -Recurse -Force
}

Write-Host 'BetterGI artifact URL protocol tests passed.'
