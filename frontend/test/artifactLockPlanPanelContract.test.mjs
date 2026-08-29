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
  assert.match(panelSource, /attempts:\s*null/)
  assert.match(panelSource, /shouldContinue:/)
  assert.match(panelSource, /executionWatchGeneration\+\+/)
  assert.match(panelSource, /const selectedJobId = jobId\.value/)
  assert.match(panelSource, /const isCurrentWatch = \(\) =>/)
  assert.match(panelSource, /jobId\.value === selectedJobId/)
  assert.match(panelSource, /current\?\.id === jobId\.value/)
  assert.match(panelSource, /await load\(true\)\s*\n\s*if \(!isCurrentWatch\(\)\) return/)
})
