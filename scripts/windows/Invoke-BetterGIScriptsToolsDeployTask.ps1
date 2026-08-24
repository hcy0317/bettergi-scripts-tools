[CmdletBinding()]
param(
    [string]$BetterGIRoot = 'C:\Users\hcy\Programs\Genshin Tools\BetterGI',
    [string]$ExpectedBranch = 'master'
)

$ErrorActionPreference = 'Stop'

$repositoryRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$deploymentScript = Join-Path $BetterGIRoot 'scripts\bettergi-scheduler\Deploy-BetterGIScriptsToolsAutoPlan.ps1'
$logRoot = Join-Path $BetterGIRoot 'scripts\bettergi-scripts-tools\logs'
$toolchains = Join-Path $BetterGIRoot 'toolchains'

foreach ($requiredPath in @($repositoryRoot, $deploymentScript, $toolchains)) {
    if (-not (Test-Path -LiteralPath $requiredPath)) {
        throw "Required deployment path is missing: $requiredPath"
    }
}

$dirty = @(git -C $repositoryRoot status --porcelain)
if ($LASTEXITCODE -ne 0) {
    throw 'Unable to inspect the bettergi-scripts-tools working tree.'
}
if ($dirty.Count -gt 0) {
    throw 'Refusing to deploy from a dirty bettergi-scripts-tools working tree.'
}

$branch = (git -C $repositoryRoot rev-parse --abbrev-ref HEAD).Trim()
if ($LASTEXITCODE -ne 0 -or $branch -ne $ExpectedBranch) {
    throw "Refusing to deploy branch '$branch'; expected '$ExpectedBranch'."
}

$jdk = Get-ChildItem -LiteralPath $toolchains -Directory -Filter 'jdk-*' |
    Sort-Object Name -Descending |
    Select-Object -First 1
$maven = Get-ChildItem -LiteralPath $toolchains -Directory -Filter 'apache-maven-*' |
    Sort-Object Name -Descending |
    Select-Object -First 1
if ($null -eq $jdk -or $null -eq $maven) {
    throw 'BetterGI JDK or Maven toolchain is missing.'
}
$mavenCommand = Join-Path $maven.FullName 'bin\mvn.cmd'
if (-not (Test-Path -LiteralPath $mavenCommand -PathType Leaf)) {
    throw "Maven command is missing: $mavenCommand"
}

New-Item -ItemType Directory -Force -Path $logRoot | Out-Null
$logPath = Join-Path $logRoot ("deployment-task-{0}.log" -f (Get-Date -Format 'yyyyMMdd-HHmmss'))
Start-Transcript -LiteralPath $logPath -Force | Out-Null
try {
    $env:JAVA_HOME = $jdk.FullName
    $mavenArguments = @('-pl', 'bgi-tools', '-am', 'package')
    & $mavenCommand @mavenArguments
    if ($LASTEXITCODE -ne 0) {
        throw "Maven package failed with exit code $LASTEXITCODE."
    }

    $jarPath = Join-Path $repositoryRoot 'bgi-tools\target\bgi-tools-v0.1.8.jar'
    if (-not (Test-Path -LiteralPath $jarPath -PathType Leaf)) {
        throw "Built JAR is missing: $jarPath"
    }
    $jarHash = (Get-FileHash -LiteralPath $jarPath -Algorithm SHA256).Hash

    & $deploymentScript `
        -BetterGIRoot $BetterGIRoot `
        -LocalJarPath $jarPath `
        -ExpectedLocalJarHash $jarHash
    if ($LASTEXITCODE -ne 0) {
        throw "Deployment script failed with exit code $LASTEXITCODE."
    }
}
finally {
    Stop-Transcript | Out-Null
}
