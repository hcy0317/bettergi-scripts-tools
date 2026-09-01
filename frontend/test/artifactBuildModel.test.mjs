import test from 'node:test'
import assert from 'node:assert/strict'

import {
  artifactAlternativeTone,
  applyArtifactBuildBulkState,
  artifactSetLabel,
  artifactBuildPayload,
  artifactCharacterAvatarUrl,
  artifactCharacterLabel,
  artifactHostErrorLabel,
  artifactNativeSyncStatusMeta,
  artifactPageSize,
  artifactEquivalentSetKeys,
  artifactRecipeLabel,
  artifactSlotLabel,
  artifactStatLabel,
  cloneArtifactBuild,
  createArtifactBuildMutationQueue,
  filterArtifactBuilds,
  normalizeArtifactRecipe,
  normalizeArtifactAutoActivationSettings,
  prepareArtifactBuilds,
  replaceArtifactBuild,
  summarizeArtifactBuild,
} from '../src/features/artifact-analysis/buildModel.js'

test('artifact build mutations for one build are serialized', async () => {
  const enqueue = createArtifactBuildMutationQueue()
  const events = []
  let releaseFirst
  const firstGate = new Promise(resolve => { releaseFirst = resolve })

  const first = enqueue('uid-1:build-1', async () => {
    events.push('first:start')
    await firstGate
    events.push('first:end')
  })
  const second = enqueue('uid-1:build-1', async () => {
    events.push('second:start')
    events.push('second:end')
  })

  await new Promise(resolve => setImmediate(resolve))
  assert.deepEqual(events, ['first:start'])
  releaseFirst()
  await Promise.all([first, second])
  assert.deepEqual(events, ['first:start', 'first:end', 'second:start', 'second:end'])
})

test('artifact build mutation queue passes the authoritative saved row forward', async () => {
  const enqueue = createArtifactBuildMutationQueue()
  const initial = {id: 'build-1', analysisEnabled: true, nativeSyncEnabled: false}
  const first = enqueue('uid-1:build-1', async () => ({...initial, analysisEnabled: false}))
  const second = enqueue('uid-1:build-1', async previousSaved => ({
    ...previousSaved,
    nativeSyncEnabled: true,
  }))

  assert.deepEqual(await first, {id: 'build-1', analysisEnabled: false, nativeSyncEnabled: false})
  assert.deepEqual(await second, {id: 'build-1', analysisEnabled: false, nativeSyncEnabled: true})
})

const upstreamBuild = {
  id: 'furina-off-field',
  name: '后台C',
  characterKey: 'Furina',
  sets: [{setKey: 'GoldenTroupe', pieces: 4}],
  alternativeSetRecipes: [[
    {setKey: 'MarechausseeHunter', pieces: 2},
    {setKey: 'GoldenTroupe', pieces: 2},
  ]],
  mainStatsBySlot: {
    flower: ['hp'],
    plume: ['atk'],
    sands: ['enerRech_', 'hp_'],
    goblet: ['hydro_dmg_'],
    circlet: ['critRate_', 'critDMG_'],
  },
  substatWeights: {critDMG_: 1, hp_: 0.6, critRate_: 1, enerRech_: 0.5},
  analysisEnabled: true,
  nativeSyncEnabled: false,
  sourceVersion: 'genshin-artifact-analyzer@766b1a6a',
}

test('artifact build labels expose localized sets, slots, and stats', () => {
  assert.equal(artifactSetLabel('GoldenTroupe'), '黄金剧团')
  assert.equal(artifactSetLabel('ADayCarvedFromRisingWinds'), '风起之日')
  assert.equal(artifactSetLabel('UnknownSet'), '未知套装')
  assert.equal(artifactSlotLabel('circlet'), '理之冠')
  assert.equal(artifactStatLabel('hydro_dmg_'), '水元素伤害加成')
})

test('character labels and avatars stay readable without leaking English keys', () => {
  assert.equal(artifactCharacterLabel('Furina'), '芙宁娜')
  assert.equal(artifactCharacterLabel('RaidenShogun'), '雷电将军')
  assert.equal(artifactCharacterLabel('UnknownCharacter'), '未知角色')
  assert.match(artifactCharacterAvatarUrl('Furina'), /UI_AvatarIcon_Furina\.png$/)
  assert.match(artifactCharacterAvatarUrl('RaidenShogun'), /UI_AvatarIcon_Shougun\.png$/)
  assert.match(artifactCharacterAvatarUrl('HuTao'), /UI_AvatarIcon_Hutao\.png$/)
  assert.match(artifactCharacterAvatarUrl('Thoma'), /UI_AvatarIcon_Tohma\.png$/)
  assert.match(artifactCharacterAvatarUrl('Yanfei'), /UI_AvatarIcon_Feiyan\.png$/)
})

test('alternate recipes cycle through stable tones and page size supports all rows', () => {
  assert.equal(artifactAlternativeTone(1), 'amber')
  assert.equal(artifactAlternativeTone(4), 'rose')
  assert.equal(artifactAlternativeTone(5), 'amber')
  assert.equal(artifactPageSize(25, 158), 25)
  assert.equal(artifactPageSize(0, 158), 158)
  assert.equal(artifactPageSize(0, 0), 1)
})

test('recipe pieces follow selected set count and two-piece effects expose equivalents', () => {
  assert.deepEqual(normalizeArtifactRecipe([
    {setKey: 'MaidenBeloved', pieces: 4},
    {setKey: 'TenacityOfTheMillelith', pieces: 4},
  ]), [
    {setKey: 'MaidenBeloved', pieces: 2},
    {setKey: 'TenacityOfTheMillelith', pieces: 2},
  ])
  assert.deepEqual(normalizeArtifactRecipe([
    {setKey: 'TenacityOfTheMillelith', pieces: 2},
  ]), [{setKey: 'TenacityOfTheMillelith', pieces: 4}])
  const sameEffectRecipe = normalizeArtifactRecipe([
    {setKey: 'ShimenawasReminiscence', pieces: 2},
    {setKey: 'ShimenawasReminiscence', pieces: 2},
  ])
  assert.equal(new Set(sameEffectRecipe.map(rule => rule.setKey)).size, 2)
  assert.equal(artifactRecipeLabel(sameEffectRecipe), '任选两套攻击力+18%套装（2+2）')
  assert.equal(artifactRecipeLabel([
    {setKey: 'ShimenawasReminiscence', pieces: 2},
    {setKey: 'EmblemOfSeveredFate', pieces: 2},
  ]), '攻击力+18%套装 + 元素充能效率+20%套装')
  assert.deepEqual(artifactEquivalentSetKeys('TenacityOfTheMillelith'), [
    'TenacityOfTheMillelith', 'VourukashasGlow',
  ])
  assert.equal(
    artifactRecipeLabel([
      {setKey: 'MaidenBeloved', pieces: 4},
      {setKey: 'TenacityOfTheMillelith', pieces: 4},
    ]),
    '被怜爱的少女 / 海染砗磲 / 昔时之歌 2件 + 千岩牢固 / 花海甘露之光 2件'
  )
  assert.equal(
    artifactRecipeLabel([{setKey: 'TenacityOfTheMillelith', pieces: 2}]),
    '千岩牢固 4件'
  )
})

test('native sync and host errors are translated without exposing backend messages', () => {
  assert.deepEqual(artifactNativeSyncStatusMeta('READY'), {
    title: '预检通过，可以同步',
    description: '套装锁定与角色快速装备方案都能按 Build 表达。',
    type: 'success',
  })
  assert.equal(artifactNativeSyncStatusMeta('NO_GO_CAPACITY').title, '方案容量不足')
  assert.equal(artifactNativeSyncStatusMeta('UNKNOWN').title, '预检状态未知')
  assert.equal(
    artifactHostErrorLabel('artifact count changed; full rescan and approval are required'),
    '圣遗物数量已变化，需要重新扫描并重新批准方案。'
  )
  assert.equal(artifactHostErrorLabel('unmapped backend detail'), '任务执行失败，请查看 BetterGI 日志。')
})

test('build summaries preserve recipes, main stats, and descending weights', () => {
  const summary = summarizeArtifactBuild(upstreamBuild)

  assert.equal(summary.sourceKind, 'upstream')
  assert.equal(summary.primaryRecipe, '黄金剧团 4件')
  assert.equal(summary.alternativeRecipeCount, 1)
  assert.deepEqual(summary.mainStats.circlet, ['暴击率', '暴击伤害'])
  assert.deepEqual(summary.topSubstats.map(item => [item.key, item.weight]), [
    ['critDMG_', 1],
    ['critRate_', 1],
    ['hp_', 0.6],
    ['enerRech_', 0.5],
  ])
})

test('build filters match localized content and management states', () => {
  const custom = {
    ...structuredClone(upstreamBuild),
    id: 'custom-noelle',
    name: '女仆主C',
    characterKey: 'Noelle',
    sourceVersion: 'custom',
    analysisEnabled: false,
    nativeSyncEnabled: true,
  }
  const builds = [upstreamBuild, custom]

  assert.deepEqual(filterArtifactBuilds(builds, {query: '黄金剧团'}).map(item => item.id), [
    'furina-off-field',
    'custom-noelle',
  ])
  assert.deepEqual(filterArtifactBuilds(builds, {character: 'Furina'}).map(item => item.id), [
    'furina-off-field',
  ])
  assert.deepEqual(filterArtifactBuilds(builds, {source: 'custom'}).map(item => item.id), [
    'custom-noelle',
  ])
  assert.deepEqual(filterArtifactBuilds(builds, {status: 'native'}).map(item => item.id), [
    'custom-noelle',
  ])
  assert.deepEqual(filterArtifactBuilds(builds, {status: 'disabled'}).map(item => item.id), [])
})

test('cloning creates an independent custom build', () => {
  const clone = cloneArtifactBuild(upstreamBuild, 1724688000000)

  assert.equal(clone.id, 'custom-1724688000000')
  assert.equal(clone.name, '后台C 副本')
  assert.equal(clone.sourceVersion, 'custom')
  assert.equal(clone.quickEquipPresetIndex, 0)
  assert.notEqual(clone.sets, upstreamBuild.sets)
  assert.notEqual(clone.alternativeSetRecipes[0], upstreamBuild.alternativeSetRecipes[0])
  clone.sets[0].pieces = 2
  assert.equal(upstreamBuild.sets[0].pieces, 4)
})

test('view summaries never leak into persisted build payloads', () => {
  const payload = artifactBuildPayload({...upstreamBuild, summary: {primaryRecipe: 'view only'}})

  assert.equal('summary' in payload, false)
  assert.deepEqual(payload.sets, upstreamBuild.sets)
  assert.notEqual(payload.sets, upstreamBuild.sets)
})

test('bulk state updates support symmetric enable and disable scopes', () => {
  const custom = {
    ...structuredClone(upstreamBuild),
    id: 'custom-noelle',
    sourceVersion: 'custom',
    analysisEnabled: true,
    nativeSyncEnabled: true,
  }

  const disabledUpstream = applyArtifactBuildBulkState(
    [upstreamBuild, custom],
    {scope: 'upstream', key: 'analysisEnabled', value: false},
  )
  assert.equal(disabledUpstream[0].analysisEnabled, false)
  assert.equal(disabledUpstream[1], custom)

  const disabledAll = applyArtifactBuildBulkState(
    disabledUpstream,
    {scope: 'all', key: 'nativeSyncEnabled', value: false},
  )
  assert.deepEqual(disabledAll.map(build => build.nativeSyncEnabled), [false, false])
})

test('auto activation settings preserve the inclusive level threshold', () => {
  assert.deepEqual(normalizeArtifactAutoActivationSettings(), {
    levelThreshold: 80,
    favoriteOverride: true,
  })
  assert.deepEqual(normalizeArtifactAutoActivationSettings({
    levelThreshold: 95,
    favoriteOverride: false,
  }), {
    levelThreshold: 90,
    favoriteOverride: false,
  })
})

test('prepared builds reuse one summary across filtering and pagination', () => {
  const prepared = prepareArtifactBuilds([upstreamBuild])
  const filtered = filterArtifactBuilds(prepared, {query: '芙宁娜'})

  assert.equal(filtered[0], prepared[0])
  assert.equal(filtered[0].summary, prepared[0].summary)
  assert.equal(filtered[0].summary.primaryRecipe, '黄金剧团 4件')
})

test('saved build replaces only its matching row without a full reload', () => {
  const other = {...structuredClone(upstreamBuild), id: 'other'}
  const saved = {...structuredClone(upstreamBuild), analysisEnabled: false}

  const updated = replaceArtifactBuild([upstreamBuild, other], saved)

  assert.equal(updated[0], saved)
  assert.equal(updated[1], other)
})
