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

test('lock plan performs one initial load and does not duplicate it through visibility observation', () => {
  assert.doesNotMatch(panelSource, /IntersectionObserver/)
  assert.doesNotMatch(panelSource, /visibilityObserver/)
  assert.match(panelSource, /watch\(\(\) => props\.uid,[\s\S]*?void load\(\)/)
  assert.match(panelSource, /artifactLoadSettlement/)
  assert.match(panelSource, /loading\.value = settlement\.loading/)
  assert.match(panelSource, /if \(!requestedUid\)[\s\S]*?loading\.value = false/)
})
