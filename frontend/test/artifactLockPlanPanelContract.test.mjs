import test from 'node:test'
import assert from 'node:assert/strict'
import {readFileSync} from 'node:fs'
import path from 'node:path'

const panelSource = readFileSync(
  path.resolve(import.meta.dirname, '../src/components/artifact/ArtifactLockPlanPanel.vue'),
  'utf8',
)

test('manual BetterGI connection resumes lock execution observation', () => {
  assert.match(panelSource, /waitForArtifactJobCompletion/)
  assert.match(panelSource, /const continueLockExecution =/)
  assert.match(panelSource, /@launched="continueLockExecution"/)
  assert.match(panelSource, /shouldContinue:/)
  assert.match(panelSource, /executionWatchGeneration\+\+/)
})
