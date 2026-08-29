[CmdletBinding()]
param(
    [Parameter(Mandatory)]
    [string]$Uri,

    [Parameter(Mandatory)]
    [string]$BetterGIRoot,

    [switch]$NoLaunch
)

$ErrorActionPreference = 'Stop'

$resolvedBetterGIRoot = (Resolve-Path -LiteralPath $BetterGIRoot -ErrorAction Stop).Path
$betterGiExe = Join-Path $resolvedBetterGIRoot 'BetterGI.exe'
$requestRoot = Join-Path $resolvedBetterGIRoot 'User\launch-requests\artifact-analysis'

if (-not (Test-Path -LiteralPath $betterGiExe -PathType Leaf)) {
    throw "BetterGI executable not found: $betterGiExe"
}

try {
    $parsedUri = [System.Uri]::new($Uri, [System.UriKind]::Absolute)
}
catch {
    throw 'Invalid BetterGI artifact launch URI.'
}

if (-not [string]::Equals(
        $parsedUri.Scheme,
        'BetterGIArtifact',
        [System.StringComparison]::OrdinalIgnoreCase)) {
    throw 'Unsupported BetterGI artifact launch URI scheme.'
}

$expectedOperation = switch ($parsedUri.Host.ToLowerInvariant()) {
    'analysis' { 'ANALYZE' }
    'characters' { 'SCAN_CHARACTER_ROSTER' }
    'execute' { 'EXECUTE_LOCK_PLAN' }
    'native-sync' { 'REBUILD_NATIVE_PLANS' }
    default { throw 'Unsupported BetterGI artifact launch operation.' }
}

$query = $parsedUri.Query.TrimStart('?')
if ($query -notmatch '^request=(?<token>[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})$') {
    throw 'The BetterGI artifact launch request token is invalid.'
}

$requestToken = $Matches.token.ToLowerInvariant()
$requestPath = Join-Path $requestRoot "$requestToken.json"
if (-not (Test-Path -LiteralPath $requestPath -PathType Leaf)) {
    throw "BetterGI artifact launch request not found or already consumed: $requestToken"
}

$request = Get-Content -Raw -LiteralPath $requestPath -Encoding UTF8 | ConvertFrom-Json
if ($request.version -ne 1 -or
    -not [string]::Equals([string]$request.kind, 'artifact-analysis', [System.StringComparison]::Ordinal) -or
    [string]$request.uid -notmatch '^[0-9]{6,12}$' -or
    [string]::IsNullOrWhiteSpace([string]$request.jobId)) {
    throw 'Unsupported BetterGI artifact launch request format.'
}

if (-not [string]::Equals(
        [string]$request.operation,
        $expectedOperation,
        [System.StringComparison]::Ordinal)) {
    throw 'The BetterGI artifact launch URI does not match the request operation.'
}
if ($expectedOperation -eq 'EXECUTE_LOCK_PLAN' -and
    ($null -eq $request.sourceArtifactCount -or
        [int]$request.sourceArtifactCount -lt 0 -or
        $null -eq $request.targets)) {
    throw 'The BetterGI artifact lock request is missing its approved target binding.'
}
if ($expectedOperation -eq 'REBUILD_NATIVE_PLANS' -and
    ($null -eq $request.nativeCapacity -or
        [int]$request.nativeCapacity -lt 1 -or
        [string]$request.nativePlanDigest -notmatch '^[0-9a-f]{64}$')) {
    throw 'The BetterGI native artifact request is missing its reviewed plan binding.'
}
if ($expectedOperation -eq 'SCAN_CHARACTER_ROSTER' -and
    ($null -eq $request.characterLevelThreshold -or
        [int]$request.characterLevelThreshold -lt 0 -or
        [int]$request.characterLevelThreshold -gt 90 -or
        $null -eq $request.favoriteOverride)) {
    throw 'The BetterGI character roster request is missing its activation settings.'
}

try {
    $createdAt = ([DateTimeOffset]$request.createdAtUtc).ToUniversalTime()
    $expiresAt = ([DateTimeOffset]$request.expiresAtUtc).ToUniversalTime()
}
catch {
    throw 'The BetterGI artifact launch request contains an invalid timestamp.'
}

$now = [DateTimeOffset]::UtcNow
if ($expiresAt -le $now) {
    throw 'The BetterGI artifact launch request has expired.'
}
if ($expiresAt -le $createdAt -or
    ($expiresAt - $createdAt) -gt [TimeSpan]::FromMinutes(10) -or
    $createdAt -gt $now.AddMinutes(1)) {
    throw 'The BetterGI artifact launch request contains an invalid lifetime.'
}

$argumentList = @('--artifact-host-request', $requestPath)
if ($NoLaunch) {
    [pscustomobject]@{
        requestToken = $requestToken
        requestPath = $requestPath
        uid = [string]$request.uid
        jobId = [string]$request.jobId
        operation = $expectedOperation
        executable = $betterGiExe
        argumentList = $argumentList
        wouldLaunch = $false
        requestConsumed = $false
    } | ConvertTo-Json -Depth 6
    return
}

$startInfo = [System.Diagnostics.ProcessStartInfo]::new()
$startInfo.FileName = $betterGiExe
$startInfo.UseShellExecute = $true
$startInfo.WindowStyle = [System.Diagnostics.ProcessWindowStyle]::Hidden
$startInfo.ArgumentList.Add($argumentList[0])
$startInfo.ArgumentList.Add($argumentList[1])
$process = [System.Diagnostics.Process]::Start($startInfo)
if ($null -eq $process) {
    throw 'BetterGI artifact host process could not be started.'
}

[pscustomobject]@{
    requestToken = $requestToken
    operation = $expectedOperation
    processId = $process.Id
    wouldLaunch = $true
    requestConsumed = $false
} | ConvertTo-Json -Depth 4
