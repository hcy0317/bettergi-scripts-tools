const statusMap = {
  WAITING_FOR_HOST: {label: '等待 BetterGI', type: 'info'},
  HOST_CLAIMED: {label: 'BetterGI 扫描中', type: 'primary'},
  READY_FOR_REVIEW: {label: '等待审核', type: 'warning'},
  APPROVED: {label: '已批准', type: 'success'},
  RESCAN_REQUIRED: {label: '需要重新扫描', type: 'warning'},
  READY_TO_EXECUTE: {label: '预检通过', type: 'success'},
  STALE_ABORT: {label: '数据已变化', type: 'danger'},
  COMPLETED: {label: '已完成', type: 'success'},
  FAILED: {label: '失败', type: 'danger'}
}

const operationMap = {
  ANALYZE: {label: '扫描并分析', launchHost: 'analysis'},
  SCAN_CHARACTER_ROSTER: {label: '检测角色并更新配装', launchHost: 'characters'},
  EXECUTE_LOCK_PLAN: {label: '执行锁定方案', launchHost: 'execute'},
  REBUILD_NATIVE_PLANS: {label: '重建原神方案', launchHost: 'native-sync'}
}

export const artifactNonFiveStarCountStorageKey = uid =>
  `artifact-analysis:non-five-star:${String(uid || '').trim()}`

export const normalizeArtifactNonFiveStarCount = value => {
  if (value === null || value === undefined || value === '') return null
  const count = Number(value)
  return Number.isInteger(count) && count >= 0 ? count : null
}

export const artifactCountReconciliation = (job, manualNonFiveStarCount) => {
  const nonFiveStarCount = normalizeArtifactNonFiveStarCount(manualNonFiveStarCount)
  const totalCount = Number(job?.snapshot?.artifactCount)
  const artifacts = job?.snapshot?.artifacts
  const analyzableCount = Number(job?.snapshot?.analyzableArtifactCount
    ?? (Array.isArray(artifacts) ? artifacts.length : Number.NaN))
  if (nonFiveStarCount === null || !Number.isInteger(totalCount)
    || !Number.isInteger(analyzableCount)) return null
  const combinedCount = analyzableCount + nonFiveStarCount
  return {
    matches: combinedCount === totalCount,
    totalCount,
    analyzableCount,
    nonFiveStarCount,
    combinedCount,
  }
}

export const shouldRefreshArtifactJobs = ({silent = false, documentHidden = false, refreshing = false}) =>
  !refreshing && (!silent || !documentHidden)

export const hasActiveArtifactJobs = jobs => (jobs || []).some(job =>
  ['WAITING_FOR_HOST', 'HOST_CLAIMED', 'READY_TO_EXECUTE'].includes(job?.status)
)

export const artifactCharacterScanStatusMeta = job => {
  if (!job) return null
  if (job.status === 'WAITING_FOR_HOST') return {title: '等待 BetterGI 接收角色检测任务', type: 'info'}
  if (job.status === 'HOST_CLAIMED') return {title: 'BetterGI 正在检测游戏角色', type: 'info'}
  if (job.status === 'COMPLETED') return {title: '游戏角色检测与配装更新已完成', type: 'success'}
  if (job.status === 'FAILED') {
    const detail = /object reference|空引用/i.test(String(job.errorMessage || ''))
      ? 'BetterGI 角色检测发生内部错误，请重试或查看日志。'
      : (/[一-鿿]/.test(String(job.errorMessage || ''))
        ? String(job.errorMessage)
        : '请查看 BetterGI 日志。')
    return {title: `角色检测失败：${detail}`, type: 'error'}
  }
  return {title: '角色检测任务状态已更新', type: 'info'}
}

export const artifactJobWasStopped = job => Boolean(
  job?.status === 'FAILED' && /停止|取消/.test(String(job?.errorMessage || ''))
)
export const artifactJobStatusMeta = (status, errorMessage = '') =>
  status === 'FAILED' && /停止|取消/.test(String(errorMessage))
    ? {label: '已停止', type: 'info'}
    : statusMap[status] || {label: '状态未知', type: 'info'}
export const artifactOperationMeta = operation => operationMap[operation] || {label: '未知任务', launchHost: ''}
export const canApproveArtifactJob = job => job?.status === 'READY_FOR_REVIEW'
export const canExecuteArtifactJob = job => ['APPROVED', 'COMPLETED', 'FAILED', 'STALE_ABORT'].includes(job?.status)
  && job?.decisionPlan?.approved === true
export const canDeleteArtifactJob = job => Boolean(job
  && !['HOST_CLAIMED', 'READY_TO_EXECUTE'].includes(job.status))

export const artifactHostHasAcceptedJob = job => Boolean(
  job?.status && job.status !== 'WAITING_FOR_HOST'
)

export const waitForArtifactHostClaim = async (
  jobId,
  getJob,
  {attempts = 1, delay = 350, sleep = milliseconds => new Promise(resolve => setTimeout(resolve, milliseconds))} = {}
) => {
  let job = null
  for (let attempt = 0; attempt < attempts; attempt++) {
    if (attempt > 0) await sleep(delay)
    job = await getJob(jobId)
    if (artifactHostHasAcceptedJob(job)) return job
  }
  return job
}

export const waitForArtifactJobCompletion = async (
  jobId,
  getJob,
  {
    attempts = 300,
    delay = 1000,
    sleep = milliseconds => new Promise(resolve => setTimeout(resolve, milliseconds)),
    onUpdate = () => {},
    terminalStatuses = ['COMPLETED', 'FAILED'],
    shouldContinue = () => true,
  } = {}
) => {
  let job = null
  for (let attempt = 0; attempts === null || attempt < attempts; attempt++) {
    if (!shouldContinue()) return job
    if (attempt > 0) await sleep(delay)
    if (!shouldContinue()) return job
    job = await getJob(jobId)
    onUpdate(job)
    if (terminalStatuses.includes(job?.status)) return job
  }
  return job
}

export const validateArtifactLaunch = (launch, operation) => {
  const host = artifactOperationMeta(operation).launchHost
  if (!host) return false
  return new RegExp(`^BetterGIArtifact://${host}\\?request=[0-9a-f-]{36}$`, 'i')
    .test(launch?.launchUri || '')
}
