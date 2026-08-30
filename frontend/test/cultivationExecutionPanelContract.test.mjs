import test from 'node:test'
import assert from 'node:assert/strict'
import {readFileSync} from 'node:fs'
import path from 'node:path'

const panelSource = readFileSync(
  path.resolve(import.meta.dirname, '../src/components/CultivationExecutionPanel.vue'),
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
})
