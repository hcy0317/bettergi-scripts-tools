import test from 'node:test'
import assert from 'node:assert/strict'
import {readFileSync} from 'node:fs'
import path from 'node:path'

const panelSource = readFileSync(
  path.resolve(import.meta.dirname, '../src/components/artifact/ArtifactBuildsPanel.vue'),
  'utf8',
)
const tableSource = readFileSync(
  path.resolve(import.meta.dirname, '../src/components/artifact/ArtifactBuildTable.vue'),
  'utf8',
)
const apiSource = readFileSync(
  path.resolve(import.meta.dirname, '../src/api/artifact/artifactAnalysis.js'),
  'utf8',
)

test('artifact toggles serialize a build and reject stale UID responses', () => {
  const toggleSource = panelSource.match(/const toggle[\s\S]*?(?=\nconst clone)/)?.[0] || ''
  assert.match(panelSource, /pendingFields/)
  assert.match(panelSource, /createArtifactBuildMutationQueue/)
  assert.match(panelSource, /uidGeneration/)
  assert.match(panelSource, /buildMutationGeneration/)
  assert.match(panelSource, /loadedUid/)
  assert.match(toggleSource, /markPendingField\(build\.id, key, true\)/)
  assert.match(toggleSource, /enqueueBuildMutation/)
  assert.match(toggleSource, /saved = await updateArtifactBuildState/)
  assert.doesNotMatch(toggleSource, /saveArtifactBuild/)
  assert.match(toggleSource, /return saved/)
  assert.match(toggleSource, /requestUid === props\.uid\.trim\(\)/)
  assert.match(toggleSource, /generation === uidGeneration/)
  assert.match(toggleSource, /loadedUid\.value === requestUid/)
  assert.match(toggleSource, /replaceArtifactBuild\(builds\.value, saved\)/)
  assert.doesNotMatch(toggleSource, /await load\(\)/)
  assert.match(tableSource, /isPending\(row, 'analysisEnabled'\)/)
  assert.match(tableSource, /isPending\(row, 'nativeSyncEnabled'\)/)
  assert.match(tableSource, /isPending\(row, 'quickEquipPresetIndex'\)/)
  assert.match(tableSource, /emit\('toggle', row, 'quickEquipPresetIndex', value\)/)
  assert.match(apiSource, /service\.patch\(\s*`\/jwt\/artifacts\/builds\/\$\{encodeURIComponent\(buildId\)\}\/state`/)
  assert.match(apiSource, /presetIndex: Number\(value\)/)
})

test('UID changes hide stale rows before loading the next account', () => {
  const uidWatchSource = panelSource.match(/watch\(\(\) => props\.uid[\s\S]*?(?=\nonBeforeUnmount)/)?.[0] || ''
  assert.match(uidWatchSource, /loadedUid\.value = ''/)
  assert.match(uidWatchSource, /builds\.value = \[\]/)
  assert.match(uidWatchSource, /load\(false\)/)
})
