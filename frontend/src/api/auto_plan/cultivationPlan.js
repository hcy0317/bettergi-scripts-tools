import service from '@utils/request.js'

export async function previewCultivationPlan(uid, file) {
  const body = new FormData()
  body.append('file', file)
  const {data} = await service.post('/jwt/auto/plan/cultivation/import/preview', body, {
    params: {uid},
    headers: {'Content-Type': 'multipart/form-data'}
  })
  return data
}

export async function confirmCultivationPlan(payload) {
  const {data} = await service.post('/jwt/auto/plan/cultivation/import/confirm', payload)
  return data
}

export async function getLatestCultivationPlan(uid, {silentError = false} = {}) {
  const {data} = await service.get('/jwt/auto/plan/cultivation/plan/latest', {
    params: {uid},
    silentError
  })
  return data
}

export async function getCultivationExecutionProjection(uid) {
  const {data} = await service.get('/jwt/auto/plan/cultivation/execution/projection', {
    params: {uid}
  })
  return data
}

export async function getCultivationExecutionPreferences(uid) {
  const {data} = await service.get('/jwt/auto/plan/cultivation/execution/preferences', {
    params: {uid}
  })
  return data
}

export async function saveCultivationExecutionPreferences(payload) {
  const {data} = await service.post('/jwt/auto/plan/cultivation/execution/preferences', payload)
  return data
}

export async function getCultivationExecutionModules(uid) {
  const {data} = await service.get('/jwt/auto/plan/cultivation/execution/modules', {
    params: {uid}
  })
  return data
}

export async function saveCultivationExecutionModule(uid, moduleId, payload) {
  const {data} = await service.put(`/jwt/auto/plan/cultivation/execution/modules/${moduleId}`, payload, {
    params: {uid}
  })
  return data
}

export async function syncCultivationExecutionModule(uid, moduleId) {
  const {data} = await service.post(`/jwt/auto/plan/cultivation/execution/modules/${moduleId}/sync`, null, {
    params: {uid}
  })
  return data
}

export async function prepareCultivationOneStop(uid) {
  const {data} = await service.post('/jwt/auto/plan/cultivation/execution/one-stop/prepare', null, {
    params: {uid}
  })
  return data
}

export async function syncCultivationOneStop(uid) {
  const {data} = await service.post('/jwt/auto/plan/cultivation/execution/one-stop/sync', null, {
    params: {uid}
  })
  return data
}

export async function startCultivationOneStop(uid) {
  const {data} = await service.post('/jwt/auto/plan/cultivation/execution/one-stop/start', null, {
    params: {uid}
  })
  return data
}
