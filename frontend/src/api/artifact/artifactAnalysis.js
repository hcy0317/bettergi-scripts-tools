import service from '@utils/request.js'

export async function getArtifactBuilds() {
  const {data} = await service.get('/jwt/artifacts/builds')
  return data
}

export async function saveArtifactBuild(build, uid) {
  const {data} = await service.put(`/jwt/artifacts/builds/${encodeURIComponent(build.id)}`, build, {params: {uid}})
  return data
}

export async function importArtifactBuilds(builds, uid) {
  const {data} = await service.post('/jwt/artifacts/builds/import', builds, {params: {uid}})
  return data
}

export async function updateArtifactBuildBulkState(request, uid) {
  const {data} = await service.put('/jwt/artifacts/builds/bulk-state', request, {params: {uid}})
  return data
}

export async function deleteArtifactBuild(buildId, uid) {
  const {data} = await service.delete(`/jwt/artifacts/builds/${encodeURIComponent(buildId)}`, {params: {uid}})
  return data
}

export async function getArtifactBuildAutoActivationSettings() {
  const {data} = await service.get('/jwt/artifacts/builds/auto-activation/settings')
  return data
}

export async function getArtifactBuildAutoActivationResult(uid) {
  const {data} = await service.get('/jwt/artifacts/builds/auto-activation/result', {params: {uid}})
  return data
}

export async function saveArtifactBuildAutoActivationSettings(settings) {
  const {data} = await service.put('/jwt/artifacts/builds/auto-activation/settings', settings)
  return data
}

export async function startArtifactCharacterRosterJob(uid, gameNickname = '', miliastraNickname = '') {
  const {data} = await service.post('/jwt/artifacts/builds/auto-activation/jobs', null, {
    params: {uid, gameNickname, miliastraNickname}
  })
  return data
}

export async function getArtifactSettings() {
  const {data} = await service.get('/jwt/artifacts/settings')
  return data
}

export async function saveArtifactSettings(settings, uid) {
  const {data} = await service.put('/jwt/artifacts/settings', settings, {params: {uid}})
  return data
}

export async function startArtifactJob(uid, operation = 'ANALYZE', capacity = 100, confirmReplaceAll = false, reviewedPlanDigest = '') {
  const {data} = await service.post('/jwt/artifacts/jobs', null, {
    params: {uid, operation, capacity, confirmReplaceAll, reviewedPlanDigest}
  })
  return data
}

export async function getArtifactJobs(uid) {
  const {data} = await service.get('/jwt/artifacts/jobs', {params: {uid}})
  return data
}

export async function getArtifactJob(jobId) {
  const {data} = await service.get(`/jwt/artifacts/jobs/${encodeURIComponent(jobId)}`)
  return data
}

export async function deleteArtifactJob(jobId) {
  const {data} = await service.delete(`/jwt/artifacts/jobs/${encodeURIComponent(jobId)}`)
  return data
}

export async function approveArtifactJob(jobId, snapshotDigest) {
  const {data} = await service.post(`/jwt/artifacts/jobs/${jobId}/approve`, null, {
    params: {snapshotDigest}
  })
  return data
}

export async function launchArtifactPlan(jobId, scanIndices = null) {
  const {data} = await service.post(`/jwt/artifacts/jobs/${jobId}/launch`, scanIndices, {
    params: {operation: 'EXECUTE_LOCK_PLAN'}
  })
  return data
}

export async function previewArtifactNativeSync(capacity = 100) {
  const {data} = await service.post('/jwt/artifacts/native-sync/preview', null, {params: {capacity}})
  return data
}
