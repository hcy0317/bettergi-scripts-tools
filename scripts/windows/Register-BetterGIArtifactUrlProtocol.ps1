[CmdletBinding()]
param(
    [Parameter(Mandatory)]
    [string]$BetterGIRoot,

    [string]$HandlerPath = (Join-Path $PSScriptRoot 'Invoke-BetterGIArtifactUrl.ps1')
)

$ErrorActionPreference = 'Stop'

$resolvedBetterGIRoot = (Resolve-Path -LiteralPath $BetterGIRoot -ErrorAction Stop).Path
$resolvedHandlerPath = (Resolve-Path -LiteralPath $HandlerPath -ErrorAction Stop).Path
$betterGiExe = Join-Path $resolvedBetterGIRoot 'BetterGI.exe'
$pwshPath = (Get-Command pwsh.exe -ErrorAction Stop).Source
$backupRoot = Join-Path $resolvedBetterGIRoot 'backup\url-protocol'

foreach ($requiredFile in @($betterGiExe, $resolvedHandlerPath, $pwshPath)) {
    if (-not (Test-Path -LiteralPath $requiredFile -PathType Leaf)) {
        throw "Required BetterGI artifact protocol file not found: $requiredFile"
    }
}

$protocolKey = 'Registry::HKEY_CURRENT_USER\Software\Classes\BetterGIArtifact'
$commandKey = Join-Path $protocolKey 'shell\open\command'
$previousCommand = $null
if (Test-Path -LiteralPath $commandKey) {
    $previousCommand = (Get-ItemProperty -LiteralPath $commandKey).'(default)'
}

New-Item -ItemType Directory -Force -Path $backupRoot | Out-Null
$backupPath = Join-Path $backupRoot ("artifact-url-protocol-{0}.json" -f (Get-Date -Format 'yyyyMMdd-HHmmss'))
[ordered]@{
    capturedAt = [DateTimeOffset]::Now.ToString('o')
    protocol = 'BetterGIArtifact'
    command = $previousCommand
} | ConvertTo-Json | Set-Content -LiteralPath $backupPath -Encoding UTF8

New-Item -Path $commandKey -Force | Out-Null
Set-ItemProperty -LiteralPath $protocolKey -Name '(default)' -Value 'URL:BetterGI Artifact Analysis Protocol'
Set-ItemProperty -LiteralPath $protocolKey -Name 'URL Protocol' -Value ''
$handlerCommand = '"{0}" -NoLogo -NoProfile -NonInteractive -WindowStyle Hidden -ExecutionPolicy Bypass -File "{1}" -BetterGIRoot "{2}" -Uri "%1"' -f `
    $pwshPath,
    $resolvedHandlerPath,
    $resolvedBetterGIRoot
Set-ItemProperty -LiteralPath $commandKey -Name '(default)' -Value $handlerCommand

[pscustomobject]@{
    protocol = 'BetterGIArtifact'
    command = (Get-ItemProperty -LiteralPath $commandKey).'(default)'
    handlerPath = $resolvedHandlerPath
    betterGiRoot = $resolvedBetterGIRoot
    backupPath = $backupPath
} | ConvertTo-Json -Depth 4
