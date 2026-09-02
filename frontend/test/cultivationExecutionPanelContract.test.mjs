import test from 'node:test'
import assert from 'node:assert/strict'
import {readFileSync} from 'node:fs'
import path from 'node:path'

const panelSource = readFileSync(
  path.resolve(import.meta.dirname, '../src/components/CultivationExecutionPanel.vue'),
  'utf8',
)
const planViewSource = readFileSync(
  path.resolve(import.meta.dirname, '../src/views/CultivationPlanView.vue'),
  'utf8',
)
const apiSource = readFileSync(
  path.resolve(import.meta.dirname, '../src/api/auto_plan/cultivationPlan.js'),
  'utf8',
)
const progressSource = readFileSync(
  path.resolve(import.meta.dirname, '../src/components/cultivation/CultivationMaterialProgress.vue'),
  'utf8',
)

test('cultivation execution hides the redundant crafting card', () => {
  assert.doesNotMatch(panelSource, /const craftingActions =/)
  assert.doesNotMatch(panelSource, /<h3>材料合成<\/h3>/)
  assert.doesNotMatch(panelSource, /class="action-card crafting-card"/)

  const progressUsages = panelSource.match(/<CultivationMaterialProgress\b/g) || []
  assert.equal(progressUsages.length, 6)
})

test('cultivation progress distinguishes owned completion from pending crafting', () => {
  assert.match(progressSource, /Number\(item\.required \|\| 0\) - Number\(item\.currentOwned \|\| 0\)/)
  assert.match(progressSource, /还需 \$\{formatCount\(gap\)\}（待合成）/)
  assert.match(progressSource, /'is-complete': isOwnedComplete\(item\)/)
  assert.doesNotMatch(progressSource, /'is-complete': item\.remaining <= 0/)
  assert.match(progressSource, /经验书分级/)
  assert.match(progressSource, /item\.valuePerItem/)
  assert.match(progressSource, /折合还需/)
})

test('effective ledger is refreshed automatically after execution writeback', () => {
  assert.match(planViewSource, /AUTO_LEDGER_REFRESH_INTERVAL_MS = 5000/)
  assert.match(planViewSource, /await loadLatest\(true\)/)
  assert.match(planViewSource, /getLatestCultivationPlan\(normalizedUid, \{silentError: silent\}\)/)
  assert.match(apiSource, /silentError = false/)
  assert.match(planViewSource, /window\.setInterval\([^)]*autoRefreshLedger/)
  assert.match(planViewSource, /window\.clearInterval\(ledgerRefreshTimer\)/)
})

test('cultivation settings keep top-level sync and persist module switches immediately', () => {
  const headerCommands = panelSource.match(/<div class="header-command-row">[\s\S]*?<\/div>/)?.[0] || ''
  assert.match(
    headerCommands,
    /生成一条龙配置[\s\S]*?@click="syncOneStop"[\s\S]*?>\s*同步\s*<\/el-button>[\s\S]*?同步并启动/,
  )
  assert.match(apiSource, /execution\/one-stop\/sync/)
  assert.match(panelSource, /const toggleModule = async/)
  assert.match(panelSource, /saveCultivationExecutionModule\(props\.uid\.trim\(\), module\.module\.moduleId/)
  assert.match(panelSource, /enabled: nextEnabled/)
  assert.match(panelSource, /settings: null/)
  assert.match(panelSource, /catch \(error\)[\s\S]*?await load\(\)/)
  assert.match(panelSource, /await load\(\)/)
  assert.match(panelSource, /:model-value="module\.enabled"/)
  assert.match(panelSource, /@change="value => toggleModule\(module, value\)"/)
  assert.match(panelSource, /savingModuleId === module\.module\.moduleId/)

  for (const removedText of [
    '保存启停',
    '同步到 BetterGI 脚本组',
    '保存体力开关',
    '天赋书秘境',
    '武器突破秘境',
    '摩拉地脉',
    '大英雄经验地脉',
  ]) {
    assert.doesNotMatch(panelSource, new RegExp(removedText))
  }
  assert.doesNotMatch(panelSource, /v-model="module\.enabled"/)
  assert.doesNotMatch(panelSource, /const syncModule =/)
})
