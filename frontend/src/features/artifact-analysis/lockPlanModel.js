const asNumber = value => Number.isFinite(Number(value)) ? Number(value) : 0

export const DEFAULT_ARTIFACT_ANALYSIS_THRESHOLDS = Object.freeze({
  unfinishedPotentialThreshold: 75,
  finishedScoreThreshold: 80,
})

const asThreshold = (value, fallback) => {
  if (value === null || value === undefined || value === '') return fallback
  const threshold = Number(value)
  return Number.isFinite(threshold) && threshold >= 0 && threshold <= 100
    ? threshold
    : fallback
}

export const artifactDecisionRows = job => {
  const artifacts = new Map((job?.snapshot?.artifacts || []).map(artifact => [artifact.scanIndex, artifact]))
  const buildIds = job?.analysisResult?.buildIds || []
  return (job?.analysisResult?.decisions || []).map(decision => ({
    ...decision,
    buildIds,
    artifact: artifacts.get(decision.scanIndex) || null,
  }))
}

export const artifactLockPlanFilterOptions = rows => ({
  setKeys: [...new Set(rows.map(row => row.artifact?.setKey).filter(Boolean))].sort(),
  slotKeys: [...new Set(rows.map(row => row.artifact?.slotKey).filter(Boolean))].sort(),
  levels: [...new Set(rows.map(row => row.artifact?.level).filter(Number.isInteger))].sort((left, right) => left - right),
})

const compareNumber = (left, right, direction) =>
  (asNumber(left) - asNumber(right)) * (direction === 'asc' ? 1 : -1)

export const filterAndSortArtifactDecisionRows = (rows, filters = {}) => {
  const view = filters.view || 'all'
  const setKey = filters.setKey || 'all'
  const slotKey = filters.slotKey || 'all'
  const levelRange = Array.isArray(filters.levelRange) && filters.levelRange.length === 2
    ? filters.levelRange.map(asNumber)
    : [0, 20]
  const [sortKey, direction = 'desc'] = String(filters.sort || 'potential-desc').split('-')
  return [...(rows || [])]
    .filter(row => view === 'all' || (view === 'recommended' ? row.kind === 'KEEP' : row.kind !== 'KEEP'))
    .filter(row => setKey === 'all' || row.artifact?.setKey === setKey)
    .filter(row => slotKey === 'all' || row.artifact?.slotKey === slotKey)
    .filter(row => row.artifact?.level >= levelRange[0] && row.artifact?.level <= levelRange[1])
    .sort((left, right) => {
      let compared = 0
      if (sortKey === 'score') compared = compareNumber(left.currentScore, right.currentScore, direction)
      else if (sortKey === 'potential') compared = compareNumber(left.potentialScore, right.potentialScore, direction)
      else if (sortKey === 'level') compared = compareNumber(left.artifact?.level, right.artifact?.level, direction)
      else compared = compareNumber(left.scanIndex, right.scanIndex, direction)
      return compared || left.scanIndex - right.scanIndex
    })
}

export const artifactDecisionEvaluation = row => {
  if (!row || row.kind === 'UNSCORED') return {label: '未评分', type: 'info'}
  if (!row.preferredMain) return {label: '主属性不匹配', type: 'danger'}
  const score = row.artifact?.level >= 20 ? asNumber(row.currentScore) : asNumber(row.potentialScore)
  if (row.kind !== 'KEEP') return {label: '分数不足', type: 'info'}
  if (score >= 90) return {label: '极品', type: 'success'}
  if (score >= 80) return {label: '优秀', type: 'success'}
  return {label: '值得保留', type: 'primary'}
}

export const artifactHasDormantSubstat = row => Boolean(
  row?.artifact?.substats?.some(substat => substat?.dormant === true)
)

export const artifactExecutionTargets = rows => (rows || [])
  .filter(row => row.expectedLocked !== row.desiredLocked)

export const artifactExecutionSummary = rows => {
  let lock = 0
  let unlock = 0
  for (const row of artifactExecutionTargets(rows)) {
    if (row.desiredLocked) lock++
    else unlock++
  }
  return {lock, unlock, total: lock + unlock}
}

export const artifactDecisionScores = (row, policy = {}) => {
  const buildIds = row?.buildIds || []
  const matrixComplete = buildIds.length > 0
    && row?.buildCurrentScores?.length === buildIds.length
    && row?.buildPotentialScores?.length === buildIds.length
    && row?.buildPreferredMains?.length === buildIds.length
    && row?.buildSetMatches?.length === buildIds.length
  const scores = (matrixComplete ? buildIds : []).map((buildId, index) => ({
    buildId,
    currentScore: asNumber(row?.buildCurrentScores?.[index]),
    potentialScore: asNumber(row?.buildPotentialScores?.[index]),
    preferredMain: Boolean(row?.buildPreferredMains?.[index]),
    setFit: row?.buildSetMatches?.[index] ? 'SET_MATCH' : 'OFF_PIECE_CANDIDATE',
  }))
  const normalized = scores.length ? scores : row?.bestBuildId ? [{
    buildId: row.bestBuildId,
    currentScore: asNumber(row.currentScore),
    potentialScore: asNumber(row.potentialScore),
    preferredMain: Boolean(row.preferredMain),
    setFit: row.setFit || '',
  }] : []
  const unfinished = row?.artifact?.level < 20
  const threshold = unfinished
    ? asThreshold(
        policy.unfinishedPotentialThreshold,
        DEFAULT_ARTIFACT_ANALYSIS_THRESHOLDS.unfinishedPotentialThreshold)
    : asThreshold(
        policy.finishedScoreThreshold,
        DEFAULT_ARTIFACT_ANALYSIS_THRESHOLDS.finishedScoreThreshold)
  return [...normalized]
    .filter(score => score.preferredMain
      && asNumber(unfinished ? score.potentialScore : score.currentScore) >= threshold)
    .sort((left, right) =>
    asNumber(unfinished ? right.potentialScore : right.currentScore)
      - asNumber(unfinished ? left.potentialScore : left.currentScore)
      || String(left.buildId).localeCompare(String(right.buildId))
    )
}

export const preferredArtifactJobId = (jobs, currentJobId, previousNewestId) => {
  const analyzable = (jobs || []).filter(job => job.analysisResult)
  const newestId = analyzable[0]?.id || ''
  if (!newestId) return ''
  if (!analyzable.some(job => job.id === currentJobId)) return newestId
  if (previousNewestId && currentJobId === previousNewestId && newestId !== previousNewestId) return newestId
  return currentJobId
}
