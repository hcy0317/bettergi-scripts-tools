import test from 'node:test'
import assert from 'node:assert/strict'
import {readFileSync} from 'node:fs'
import path from 'node:path'

const panelSource = readFileSync(
  path.resolve(import.meta.dirname, '../src/components/CultivationExecutionPanel.vue'),
  'utf8',
)

test('cultivation execution hides the redundant crafting card', () => {
  assert.doesNotMatch(panelSource, /const craftingActions =/)
  assert.doesNotMatch(panelSource, /<h3>材料合成<\/h3>/)
  assert.doesNotMatch(panelSource, /class="action-card crafting-card"/)

  const progressUsages = panelSource.match(/<CultivationMaterialProgress\b/g) || []
  assert.equal(progressUsages.length, 6)
})
