import {
  artifactCharacterAvatarAliases,
  artifactCharacterLabels,
  artifactSetLabels,
  artifactSetTwoPieceEffectGroups,
  artifactSlotOptions,
  artifactStatOptions,
} from './buildCatalog.js'

const statLabels = new Map(artifactStatOptions)
const slotLabels = new Map(artifactSlotOptions)
const alternativeTones = Object.freeze(['amber', 'teal', 'violet', 'rose'])

const nativeSyncStatusMap = Object.freeze({
  READY: {
    title: '预检通过，可以同步',
    description: '套装锁定与角色快速装备方案都能按 Build 表达。',
    type: 'success',
  },
  NO_GO_EMPTY: {
    title: '没有可同步的配装',
    description: '请先在配装管理中启用至少一个“参与原神同步”的配装。',
    type: 'warning',
  },
  NO_GO_CAPACITY: {
    title: '方案容量不足',
    description: '某个套装超过 3 个 Build、某个角色超过 2 个快速装备 Build，或总套装容量不足。',
    type: 'error',
  },
})

export const normalizeArtifactAutoActivationSettings = settings => ({
  levelThreshold: Math.min(90, Math.max(0, Math.round(Number(settings?.levelThreshold ?? 80)))),
  favoriteOverride: settings?.favoriteOverride ?? true,
})

const pascalToSnake = value => String(value || '')
  .replace(/([A-Z]+)([A-Z][a-z])/g, '$1_$2')
  .replace(/([a-z0-9])([A-Z])/g, '$1_$2')
  .replace(/[-\s]+/g, '_')
  .toLowerCase()

export const humanizeArtifactKey = value => String(value || '')
  .replace(/([a-z0-9])([A-Z])/g, '$1 $2')
  .replace(/[_-]+/g, ' ')
  .replace(/\s+/g, ' ')
  .trim()

export const artifactSetLabel = setKey =>
  artifactSetLabels[pascalToSnake(setKey)] || '未知套装'

export const artifactStatLabel = statKey => statLabels.get(statKey) || '未知属性'
export const artifactSlotLabel = slotKey => slotLabels.get(slotKey) || '未知部位'
export const artifactCharacterLabel = characterKey => artifactCharacterLabels[characterKey] || '未知角色'
export const artifactCharacterAvatarUrl = characterKey => {
  if (!artifactCharacterLabels[characterKey]) return ''
  const avatarKey = artifactCharacterAvatarAliases[characterKey] || characterKey
  return `https://enka.network/ui/UI_AvatarIcon_${encodeURIComponent(avatarKey)}.png`
}
export const artifactAlternativeTone = index => alternativeTones[(Math.max(1, Number(index)) - 1) % alternativeTones.length]
export const artifactPageSize = (selected, total) => selected === 0 ? Math.max(1, total) : selected
export const artifactNativeSyncStatusMeta = status => nativeSyncStatusMap[status] || {
  title: '预检状态未知',
  description: '无法识别服务端返回的预检状态，请刷新后重试。',
  type: 'error',
}
export const artifactTranslationModeLabel = mode =>
  mode === 'BUILD_SCOPED_LOCK_AND_QUICK_EQUIP_V1'
    ? 'Build 独立锁定与快速装备'
    : '未知转换方式'
export const artifactHostErrorLabel = message => {
  if (/[\u4e00-\u9fff]/.test(String(message || ''))) return String(message)
  const value = String(message || '').toLocaleLowerCase()
  if (value.includes('artifact count changed')) return '圣遗物数量已变化，需要重新扫描并重新批准方案。'
  if (value.includes('fingerprint changed')) return '目标圣遗物内容已变化，任务已安全中止。'
  if (value.includes('lock state changed')) return '目标圣遗物锁定状态已变化，任务已安全中止。'
  if (value.includes('target index is missing')) return '目标圣遗物已不在原扫描位置，任务已安全中止。'
  if (value.includes('uid does not match')) return '当前账号与已批准方案不一致，任务已安全中止。'
  if (value.includes('not approved')) return '方案尚未批准，不能执行锁定操作。'
  return '任务执行失败，请查看 BetterGI 日志。'
}
export const formatArtifactDate = value => {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return '时间未知'
  return new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit',
    hour12: false,
  }).format(date)
}

export const artifactEquivalentSetKeys = setKey =>
  artifactSetTwoPieceEffectGroups.find(group => group.setKeys.includes(setKey))?.setKeys || [setKey]

export const normalizeArtifactRecipe = recipe => {
  const rules = recipe || []
  if (rules.length > 2) throw new Error('一个套装方案最多选择两个套装')
  const pieces = rules.length === 1 ? 4 : 2
  const used = new Set()
  return rules.map(rule => {
    let setKey = rule.setKey
    if (used.has(setKey)) {
      setKey = artifactEquivalentSetKeys(setKey).find(candidate => !used.has(candidate))
      if (!setKey) throw new Error('同一套装不能在 2+2 方案中重复选择')
    }
    used.add(setKey)
    return {...rule, setKey, pieces}
  })
}

const artifactSetRuleLabel = rule => {
  if (rule.pieces !== 2) return `${artifactSetLabel(rule.setKey)} ${rule.pieces}件`
  const group = artifactSetTwoPieceEffectGroups.find(item => item.setKeys.includes(rule.setKey))
  if (!group) return `${artifactSetLabel(rule.setKey)} 2件`
  if (group.setKeys.length > 3) return `${group.label.replaceAll(' ', '')}套装`
  return `${group.setKeys.map(artifactSetLabel).join(' / ')} 2件`
}

export const artifactRecipeLabel = recipe => {
  const normalized = normalizeArtifactRecipe(recipe)
  if (normalized.length === 2) {
    const firstGroup = artifactSetTwoPieceEffectGroups.find(group => group.setKeys.includes(normalized[0].setKey))
    const secondGroup = artifactSetTwoPieceEffectGroups.find(group => group.setKeys.includes(normalized[1].setKey))
    if (firstGroup && firstGroup === secondGroup) {
      return `任选两套${firstGroup.label.replaceAll(' ', '')}套装（2+2）`
    }
  }
  return normalized.map(artifactSetRuleLabel).join(' + ')
}

export const artifactBuildSourceKind = build =>
  build?.sourceVersion?.startsWith('genshin-artifact-analyzer@') ? 'upstream' : 'custom'

export const summarizeArtifactBuild = build => {
  const mainStats = Object.fromEntries(artifactSlotOptions.map(([slot]) => [
    slot,
    (build?.mainStatsBySlot?.[slot] || []).map(artifactStatLabel),
  ]))
  const topSubstats = Object.entries(build?.substatWeights || {})
    .map(([key, weight]) => ({key, label: artifactStatLabel(key), weight}))
    .sort((left, right) => right.weight - left.weight || left.key.localeCompare(right.key))
  const alternativeRecipes = (build?.alternativeSetRecipes || []).map(artifactRecipeLabel)
  const setLabels = [build?.sets, ...(build?.alternativeSetRecipes || [])]
    .flat()
    .map(rule => artifactSetLabel(rule.setKey))
  const searchText = [
    build?.id, build?.name, build?.characterKey, artifactCharacterLabel(build?.characterKey), ...setLabels,
    ...Object.values(mainStats).flat(), ...topSubstats.map(item => item.label),
  ].filter(Boolean).join(' ').toLocaleLowerCase()

  return {
    sourceKind: artifactBuildSourceKind(build),
    primaryRecipe: artifactRecipeLabel(build?.sets),
    alternativeRecipes,
    alternativeRecipeCount: alternativeRecipes.length,
    mainStats,
    topSubstats,
    searchText,
  }
}

export const prepareArtifactBuilds = builds => (builds || []).map(build => {
  const normalized = {quickEquipSyncEnabled: false, ...build}
  return normalized?.summary
    ? normalized
    : {...normalized, summary: summarizeArtifactBuild(normalized)}
})

export const replaceArtifactBuild = (builds, saved) =>
  (builds || []).map(build => build.id === saved?.id ? saved : build)

export const createArtifactBuildMutationQueue = () => {
  const tails = new Map()
  return (queueKey, operation) => {
    if (typeof operation !== 'function') throw new TypeError('配装更新操作必须是函数')
    const previous = tails.get(queueKey) || Promise.resolve()
    const current = previous.catch(() => undefined).then(operation)
    tails.set(queueKey, current)
    const cleanup = () => {
      if (tails.get(queueKey) === current) tails.delete(queueKey)
    }
    void current.then(cleanup, cleanup)
    return current
  }
}

export const filterArtifactBuilds = (builds, filters = {}) => {
  const query = String(filters.query || '').trim().toLocaleLowerCase()
  const character = filters.character || 'all'
  const source = filters.source || 'all'
  const status = filters.status || 'all'

  return (builds || []).filter(build => {
    const summary = build.summary || summarizeArtifactBuild(build)
    if (query && !summary.searchText.includes(query)) return false
    if (character !== 'all' && build.characterKey !== character) return false
    if (source !== 'all' && summary.sourceKind !== source) return false
    if (status === 'analysis' && !build.analysisEnabled) return false
    if (status === 'native' && !build.nativeSyncEnabled) return false
    if (status === 'quick' && !build.quickEquipSyncEnabled) return false
    if (status === 'disabled'
      && (build.analysisEnabled || build.nativeSyncEnabled || build.quickEquipSyncEnabled)) return false
    return true
  })
}

export const applyArtifactBuildBulkState = (builds, {scope, key, value}) => {
  if (!['analysisEnabled', 'nativeSyncEnabled'].includes(key)) {
    throw new Error('不支持的配装批量状态字段')
  }
  return (builds || []).map(build => {
    const matchesScope = scope === 'all' || artifactBuildSourceKind(build) === scope
    return matchesScope ? {...build, [key]: Boolean(value)} : build
  })
}

export const artifactBuildPayload = build => {
  const payload = JSON.parse(JSON.stringify(
    build, (key, value) => key === 'summary' ? undefined : value
  ))
  payload.sets = normalizeArtifactRecipe(payload.sets)
  payload.alternativeSetRecipes = (payload.alternativeSetRecipes || []).map(normalizeArtifactRecipe)
  payload.quickEquipSyncEnabled = Boolean(payload.quickEquipSyncEnabled)
  return payload
}

export const cloneArtifactBuild = (build, timestamp = Date.now()) => {
  const clone = artifactBuildPayload(build)
  clone.id = `custom-${timestamp}`
  clone.name = `${build.name} 副本`
  clone.sourceVersion = 'custom'
  clone.quickEquipSyncEnabled = false
  return clone
}
