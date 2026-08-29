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
  assert.match(panel, /pendingLaunchJobId\.value = ''[\s\S]*?startArtifactJob/)
  assert.match(panel, /无法创建扫描任务/)
  assert.match(panel, /const continueAnalysis =/)
  assert.match(panel, /watchActiveJob\(pendingLaunchJobId\.value\)/)
  assert.match(panel, /@launched="continueAnalysis"/)
  assert.match(panel, /分析任务状态连续读取失败/)
})

test('new artifact launches never reuse an earlier pending job id after creation fails', () => {
  const lockPanel = source('ArtifactLockPlanPanel.vue')
  const nativePanel = source('ArtifactNativeSyncPanel.vue')
  const buildsPanel = source('ArtifactBuildsPanel.vue')

  assert.match(lockPanel, /pendingExecutionJobId\.value = ''[\s\S]*?launchArtifactPlan/)
  assert.match(lockPanel, /无法创建锁定执行任务/)
  assert.match(nativePanel, /pendingLaunchJobId\.value = ''[\s\S]*?startArtifactJob/)
  assert.match(nativePanel, /无法创建原神方案重建任务/)
  assert.match(buildsPanel, /characterScanJobId\.value = ''[\s\S]*?startArtifactCharacterRosterJob/)
  assert.match(buildsPanel, /无法创建角色检测任务/)
  assert.match(source('ArtifactJobsPanel.vue'), /validateArtifactLaunch\(response\.launch, 'ANALYZE'\)/)
  assert.match(lockPanel, /validateArtifactLaunch\(response\.launch, 'EXECUTE_LOCK_PLAN'\)/)
  assert.match(nativePanel, /validateArtifactLaunch\(response\.launch, 'REBUILD_NATIVE_PLANS'\)/)
  assert.match(buildsPanel, /validateArtifactLaunch\(response\.launch, 'SCAN_CHARACTER_ROSTER'\)/)
})

test('manual BetterGI connection resumes native-plan completion observation', () => {
  const panel = source('ArtifactNativeSyncPanel.vue')
  assert.match(panel, /waitForArtifactJobCompletion/)
  assert.match(panel, /attempts:\s*null/)
  assert.match(panel, /const continueNativeSync =/)
  assert.match(panel, /@launched="continueNativeSync"/)
  assert.match(panel, /onBeforeUnmount/)
  assert.match(panel, /requestedUid !== props\.uid\.trim\(\)/)
  assert.match(panel, /requestedCapacity !== capacity\.value/)
  assert.match(panel, /requestGeneration !== previewRequestGeneration/)
  assert.match(panel, /watch\(\(\) => props\.uid,[\s\S]*?loading\.value = false/)
  assert.match(panel, /原神方案任务状态连续读取失败/)
})
