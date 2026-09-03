param(
    [switch]$SkipRuntime
)

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

$betterGiRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$deploymentRoot = Join-Path $betterGiRoot 'scripts\bettergi-scripts-tools'
$composePath = Join-Path $deploymentRoot 'docker-compose.yml'
$configPath = Join-Path $deploymentRoot 'application-prod.yml'
$scriptGroupPath = Join-Path $betterGiRoot 'User\ScriptGroup\每日任务.json'
$uid = '102550550'
$uidScriptGroupPath = Join-Path $betterGiRoot "User\ScriptGroup\养成一条龙-$uid.json"
$protocolHandlerPath = Join-Path $PSScriptRoot 'Invoke-BetterGICultivationUrl.ps1'
$scriptGroupLauncherPath = Join-Path $PSScriptRoot 'Start-BetterGI-ScriptGroup.ps1'
$deploymentScriptPath = Join-Path $PSScriptRoot 'Deploy-BetterGIScriptsToolsAutoPlan.ps1'
$hostApiBase = 'http://127.0.0.1:18081/bgi'
$expectedImage = 'bettergi-scripts-tools-local:v0.1.8'
Assert-True (Test-Path -LiteralPath $composePath) "Missing compose file: $composePath"
Assert-True (Test-Path -LiteralPath $configPath) "Missing application config: $configPath"
Assert-True (Test-Path -LiteralPath $uidScriptGroupPath) "Missing UID cultivation group: $uidScriptGroupPath"
Assert-True (Test-Path -LiteralPath $protocolHandlerPath -PathType Leaf) "Missing BetterGI cultivation protocol handler: $protocolHandlerPath"
Assert-True (Test-Path -LiteralPath $scriptGroupLauncherPath -PathType Leaf) "Missing BetterGI script-group launcher: $scriptGroupLauncherPath"
$scriptGroupLauncherSource = Get-Content -Raw -LiteralPath $scriptGroupLauncherPath -Encoding UTF8
Assert-True (-not $scriptGroupLauncherSource.Contains('--no-single')) 'Script-group launcher must rely on BetterGI named-pipe activation forwarding instead of passing --no-single as a group name'
Assert-True (Test-Path -LiteralPath $deploymentScriptPath -PathType Leaf) "Missing bettergi-scripts-tools deployment script: $deploymentScriptPath"
$deploymentScriptSource = Get-Content -Raw -LiteralPath $deploymentScriptPath -Encoding UTF8
Assert-True ($deploymentScriptSource.Contains('[string]$LocalJarPath')) 'Deployment script must support an explicit verified local JAR input'
Assert-True ($deploymentScriptSource.Contains('[string]$ExpectedLocalJarHash')) 'Deployment script must require an expected hash for a local JAR input'
Assert-True ($deploymentScriptSource.Contains('Register-BetterGICultivationUrlProtocol.ps1')) 'Deployment script must register the BetterGI cultivation host protocol'

$compose = Get-Content -Raw -LiteralPath $composePath
Assert-True ($compose.Contains($expectedImage)) "Compose must pin $expectedImage"
Assert-True ($compose.Contains('dockerfile: Dockerfile')) 'Compose must build the verified official release JAR'
Assert-True ($compose.Contains('127.0.0.1:18081:8081')) 'Compose must expose the service only on 127.0.0.1:18081'
Assert-True ($compose.Contains('./cache:/app/cache')) 'Compose must persist the SQLite cache directory'

docker compose --project-directory $deploymentRoot -f $composePath config --quiet
if ($LASTEXITCODE -ne 0) {
    throw 'docker compose config validation failed'
}

$group = Get-Content -Raw -LiteralPath $scriptGroupPath | ConvertFrom-Json
Assert-True (@($group.projects | Where-Object { $_.folderName -eq 'AutoPlan' }).Count -eq 0) '每日任务 must not own an AutoPlan project'

$uidGroup = Get-Content -Raw -LiteralPath $uidScriptGroupPath | ConvertFrom-Json
$uidAutoPlans = @($uidGroup.projects | Where-Object { $_.folderName -eq 'AutoPlan' })
$uidPlanDriven = @($uidAutoPlans | Where-Object { $_.jsScriptSettingsObject.cultivation_plan_mode -eq $true })
$uidInventoryReconcile = @($uidAutoPlans | Where-Object { $_.jsScriptSettingsObject.cultivation_inventory_reconcile_mode -eq $true })
Assert-True ($uidAutoPlans.Count -eq 1) 'UID cultivation group must contain exactly one AutoPlan project'
Assert-True ($uidPlanDriven.Count -eq 1) 'UID cultivation group must contain exactly one plan-driven AutoPlan project'
Assert-True ($uidInventoryReconcile.Count -eq 0) 'UID cultivation group must not contain a duplicate inventory-reconcile AutoPlan project'
$uidAutoPlan = $uidPlanDriven[0]
Assert-True ($null -ne $uidAutoPlan) "AutoPlan project is missing from 养成一条龙-$uid.json"
Assert-True ($uidAutoPlan.status -eq 'Enabled') 'UID AutoPlan project must be enabled'
$settings = $uidAutoPlan.jsScriptSettingsObject
Assert-True ($settings.auto_load.Count -eq 1 -and $settings.auto_load[0] -eq 'bgi_tools加载') 'UID AutoPlan must load plans from bgi_tools only'
Assert-True ($settings.bgi_tools_http_pull_json_config -eq "$hostApiBase/auto/plan/json") 'UID AutoPlan pull URL must use the host-reachable port'
Assert-True ($settings.bgi_tools_http_push_all_json_config -eq "$hostApiBase/auto/plan/domain/json/all") 'UID AutoPlan domain push URL must use the host-reachable port'
Assert-True ($settings.bgi_tools_http_push_all_country_config -eq "$hostApiBase/auto/plan/country/json/all") 'UID AutoPlan country push URL must use the host-reachable port'
Assert-True ($settings.bgi_tools_http_push_all_boss_config -eq "$hostApiBase/auto/plan/boss/json/all") 'UID AutoPlan boss push URL must use the host-reachable port'
Assert-True ($settings.cultivation_plan_mode -eq $true) 'UID AutoPlan must use plan-driven cultivation mode'
Assert-True ([string]$settings.run_config -eq '') 'UID AutoPlan must not retain a fixed-count fallback plan'
Assert-True (@($settings.auto_check).Count -eq 0) 'UID AutoPlan plan-driven mode must not run unrelated legacy checks'
$autoPlanScriptRoot = Join-Path $betterGiRoot 'User\JsScript\AutoPlan'
Assert-True (Test-Path -LiteralPath (Join-Path $autoPlanScriptRoot 'utils\cultivation_plan.js') -PathType Leaf) 'Plan-driven AutoPlan bridge is missing'
$cultivationPlan = Get-Content -Raw -LiteralPath (Join-Path $autoPlanScriptRoot 'utils\cultivation_plan.js') -Encoding UTF8
$reconcileDeclarationCount = ([regex]::Matches($cultivationPlan, 'async function runInventoryReconcileOnce')).Count
Assert-True ($reconcileDeclarationCount -eq 1) 'AutoPlan bridge must declare runInventoryReconcileOnce exactly once'
Assert-True ($cultivationPlan.Contains('GridScreenName.CharacterDevelopmentItems')) 'Cultivation inventory reconciliation must scan the Character Development Items tab'
Assert-True (-not $cultivationPlan.Contains('param.GridScreenName = GridScreenName.Materials')) 'Cultivation inventory reconciliation must not scan the generic Materials tab for ascension items'
Assert-True ($cultivationPlan.Contains('NO_PROGRESS:NO_REWARDS')) 'Plan-driven cultivation must report an empty-reward batch as no progress'
Assert-True ($cultivationPlan.Contains('import {Physical} from "./physical";')) 'Plan-driven cultivation must reuse the installed AutoPlan resin scanner'
Assert-True ($cultivationPlan.Contains('await Physical.countAllResin()')) 'Plan-driven cultivation must scan all resin before claiming work'
Assert-True ($cultivationPlan.Contains('function resinSnapshotQuery(snapshot)')) 'Plan-driven cultivation must encode the resin snapshot for the planner'
Assert-True ($cultivationPlan.Contains('action.actionType === "CRAFT_BATCH"')) 'Plan-driven cultivation must lease all current crafts as one batch'
Assert-True ($cultivationPlan.Contains('for (const craftAction of craftActions)')) 'Plan-driven cultivation must execute every craft in the leased batch'
Assert-True ($cultivationPlan.Contains('批量合成后强制完整库存复核')) 'Plan-driven cultivation must reconcile inventory once after the craft batch'
Assert-True ($cultivationPlan.Contains('行动未产生奖励，重新领取以选择批量合成或安全停止')) 'Plan-driven cultivation must replan after an empty resin action'
$autoPlanMain = Get-Content -Raw -LiteralPath (Join-Path $autoPlanScriptRoot 'main.js') -Encoding UTF8
Assert-True ($autoPlanMain.Contains('runPlanDrivenCultivation')) 'AutoPlan main entry does not load the plan-driven bridge'
Assert-True ($autoPlanMain.Contains('settings.cultivation_plan_mode')) 'AutoPlan main entry does not branch into plan-driven mode'
$autoPlanLoadCheck = Get-Content -Raw -LiteralPath (Join-Path $autoPlanScriptRoot 'utils\load_check_run.js') -Encoding UTF8
$bossRunStart = $autoPlanLoadCheck.IndexOf('"开始执行Boss任务"', [System.StringComparison]::Ordinal)
$bossParamStart = $autoPlanLoadCheck.IndexOf('let param = new AutoBossParam()', $bossRunStart, [System.StringComparison]::Ordinal)
Assert-True ($bossRunStart -ge 0 -and $bossParamStart -gt $bossRunStart) 'AutoPlan boss runner segment is missing'
$bossRunnerPrelude = $autoPlanLoadCheck.Substring($bossRunStart, $bossParamStart - $bossRunStart)
Assert-True (-not $bossRunnerPrelude.Contains('Physical.countAllResin()')) 'AutoPlan boss runner must rely on AutoBossTask resin preflight instead of scanning resin twice'

$uidFullyAuto = $uidGroup.projects | Where-Object { $_.folderName -in @('FullyAutoAndSemiAutoTools', 'HCY-FullyAutoAndSemiAutoTools') } | Select-Object -First 1
if ($null -ne $uidFullyAuto) {
    Assert-True ($uidFullyAuto.status -eq 'Enabled') 'UID FullyAutoAndSemiAutoTools project must be enabled'
    Assert-True ($uidFullyAuto.jsScriptSettingsObject.http_api -eq "$hostApiBase/cron/next-timestamp/all") 'UID FullyAutoAndSemiAutoTools http_api must use the host-reachable port'
    $monsterFamilies = @($uidFullyAuto.jsScriptSettingsObject.treeLevel_1_1)
    $monsterRouteBundles = @($uidFullyAuto.jsScriptSettingsObject.PSObject.Properties |
        Where-Object { $_.Name -like 'treeLevel_2_*' } |
        ForEach-Object { @($_.Value) } |
        Sort-Object)
    $routeBundleFamilies = @($monsterRouteBundles |
        ForEach-Object { ([string]$_ -split '@', 2)[0] } |
        Sort-Object -Unique)
    $sortedMonsterFamilies = @($monsterFamilies | Sort-Object -Unique)
    Assert-True (@($monsterRouteBundles | Where-Object { [string]$_ -notmatch '^.+@.+$' }).Count -eq 0) 'UID monster route bundles must contain both a family and a route selector'
    Assert-True ($monsterRouteBundles.Count -eq $routeBundleFamilies.Count) 'UID monster project must select at most one route bundle per target family'
    $directRootFamilies = @(
        foreach ($family in $sortedMonsterFamilies) {
            if ($routeBundleFamilies -contains $family) {
                continue
            }
            $familyRoot = Join-Path $betterGiRoot (Join-Path 'User\AutoPathing\敌人与魔物' $family)
            $routeFiles = @(Get-ChildItem -LiteralPath $familyRoot -File -Filter '*.json' -ErrorAction SilentlyContinue)
            if ($routeFiles.Count -eq 0) {
                continue
            }
            $validRoot = $true
            foreach ($routeFile in $routeFiles) {
                try {
                    $route = Get-Content -LiteralPath $routeFile.FullName -Raw -Encoding UTF8 | ConvertFrom-Json
                    $positions = @($route.positions)
                    if ($positions.Count -eq 0 -or
                        @($positions | Where-Object { $_.action -eq 'fight' }).Count -eq 0) {
                        $validRoot = $false
                        break
                    }
                }
                catch {
                    $validRoot = $false
                    break
                }
            }
            if ($validRoot) {
                $family
            }
        }
    )
    $coveredMonsterFamilies = @($routeBundleFamilies + $directRootFamilies | Sort-Object -Unique)
    Assert-True (($coveredMonsterFamilies -join ',') -eq ($sortedMonsterFamilies -join ',')) 'UID monster project families must each resolve to one route bundle or one valid direct-root route family'
}
Assert-True ($uidGroup.config.pathingConfig.autoFightConfig.burstEnabled -eq $false) 'UID pathing group must preserve BetterGI burstEnabled=false for shield slot Q'
Assert-True ($uidGroup.config.pathingConfig.autoFightConfig.pickDropsAfterFightSeconds -eq 60) 'UID pathing group must allow up to 60 seconds while reachable loot is still detected'

if ($SkipRuntime) {
    Write-Host 'bettergi-scripts-tools AutoPlan static integration tests passed.'
    exit 0
}

$container = docker inspect bettergi-scripts-tools | ConvertFrom-Json
Assert-True ($container.Count -eq 1) 'bettergi-scripts-tools container is missing'
Assert-True ($container[0].State.Running) 'bettergi-scripts-tools container is not running'
Assert-True ($container[0].Config.Image -eq $expectedImage) "Container must run $expectedImage"
Assert-True ($container[0].Config.Labels.'org.opencontainers.image.version' -eq '0.1.8-local-development') 'Container image version label mismatch'
Assert-True ($container[0].Config.Labels.'io.hcy.bettergi.artifact' -eq 'local-development-build') 'Container must identify the local development artifact'
Assert-True (($container[0].Mounts | Where-Object { $_.Destination -eq '/app/cache' }).Count -eq 1) 'Container cache is not persisted at /app/cache'

$ui = Invoke-WebRequest -UseBasicParsing -Uri 'http://127.0.0.1:18081/bgi/ui' -TimeoutSec 10
Assert-True ($ui.StatusCode -eq 200) 'AutoPlan management UI is not reachable'

$response = Invoke-RestMethod -Uri "$hostApiBase/auto/plan/json?uid=$uid&enable=true" -TimeoutSec 10
Assert-True ($response.code -eq 200) 'AutoPlan API did not return code 200'
$plans = @($response.data)
Assert-True (@($plans | Where-Object { $_.cultivate -eq $true }).Count -eq 0) 'Legacy fixed-count cultivation plans must be removed'

$projectionResponse = Invoke-RestMethod -Uri "$hostApiBase/auto/plan/cultivation/execution/projection?uid=$uid" -TimeoutSec 10
Assert-True ($projectionResponse.code -eq 200) 'Cultivation projection API did not return code 200'
Assert-True ($projectionResponse.data.uid -eq $uid) 'Cultivation projection UID mismatch'
Assert-True ($projectionResponse.data.executionMode -match '计划驱动') 'Cultivation projection is not marked as plan-driven'
$modulesResponse = Invoke-RestMethod -Uri "$hostApiBase/auto/plan/cultivation/execution/modules?uid=$uid" -TimeoutSec 10
Assert-True ($modulesResponse.code -eq 200) 'Cultivation modules API did not return code 200'
$monsterModule = $modulesResponse.data | Where-Object { $_.module.moduleId -eq 'fully-auto-and-semi-auto-tools' } | Select-Object -First 1
$expectsMonsterProject = $null -ne $monsterModule -and $monsterModule.enabled -and @($projectionResponse.data.monsterAction.targets).Count -gt 0
Assert-True ($expectsMonsterProject -eq ($null -ne $uidFullyAuto)) 'UID monster project presence must match the enabled module and current monster targets'

$smokeExecutor = 'codex-deploy-smoke'
$claimSmokeUid = '999999999999'
$claimSmoke = Invoke-RestMethod -Method Post -Uri "$hostApiBase/auto/plan/cultivation/execution/next-action?uid=$claimSmokeUid&executorId=$smokeExecutor" -TimeoutSec 10
Assert-True ($claimSmoke.code -eq 200) 'Plan-driven next-action API did not return code 200'
Assert-True ($claimSmoke.data.status -eq 'NO_PLAN') 'Plan-driven next-action smoke request unexpectedly created an action'

$uidResponse = Invoke-RestMethod -Uri "$hostApiBase/auto/plan/uid/all/mapping" -TimeoutSec 10
Assert-True ($uidResponse.code -eq 200) 'AutoPlan UID mapping API did not return code 200'
$uidMapping = @($uidResponse.data | Where-Object { $_.uid -eq $uid })
Assert-True ($uidMapping.Count -eq 1) 'AutoPlan UID 102550550 mapping is missing'
Assert-True ($uidMapping[0].as -eq 'HCY 主账号') 'AutoPlan UID 102550550 alias mismatch'

$startResponse = Invoke-RestMethod -Method Post -Uri "$hostApiBase/auto/plan/cultivation/execution/one-stop/start?uid=$uid" -TimeoutSec 30
Assert-True ($startResponse.code -eq 200) 'Cultivation one-stop start API did not return code 200'
Assert-True ($startResponse.data.launchUri -match '^BetterGICultivation://one-stop\?request=[0-9a-f-]{36}$') 'Cultivation one-stop start API did not return a valid host launch URI'
$requestToken = $startResponse.data.launchUri -replace '^BetterGICultivation://one-stop\?request=', ''
$requestPath = Join-Path $betterGiRoot "User\launch-requests\cultivation-one-stop\$requestToken.json"
Assert-True (Test-Path -LiteralPath $requestPath -PathType Leaf) 'Cultivation one-stop launch request file is missing'
$launchRequest = Get-Content -Raw -LiteralPath $requestPath -Encoding UTF8 | ConvertFrom-Json
Assert-True ($launchRequest.uid -eq $uid) 'Cultivation launch request UID mismatch'
Assert-True ($launchRequest.scriptGroupName -eq "养成一条龙-$uid") 'Cultivation launch request resolved the wrong script group'
Assert-True ([datetime]$launchRequest.expiresAtUtc -gt [datetime]::UtcNow) 'Cultivation launch request is already expired'

Write-Host 'bettergi-scripts-tools AutoPlan integration tests passed.'
