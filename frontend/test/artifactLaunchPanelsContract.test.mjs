import test from 'node:test'
import assert from 'node:assert/strict'
import {readFileSync} from 'node:fs'
import path from 'node:path'

const source = name => readFileSync(
  path.resolve(import.meta.dirname, `../src/components/artifact/${name}`),
  'utf8',
)

test('manual BetterGI connection resumes analysis observation', () => {
  const panel = source('ArtifactJobsPanel.vue')
  assert.match(panel, /const continueAnalysis =/)
  assert.match(panel, /watchActiveJob\(pendingLaunchJobId\.value\)/)
  assert.match(panel, /@launched="continueAnalysis"/)
})

test('manual BetterGI connection resumes native-plan completion observation', () => {
  const panel = source('ArtifactNativeSyncPanel.vue')
  assert.match(panel, /waitForArtifactJobCompletion/)
  assert.match(panel, /const continueNativeSync =/)
  assert.match(panel, /@launched="continueNativeSync"/)
})
