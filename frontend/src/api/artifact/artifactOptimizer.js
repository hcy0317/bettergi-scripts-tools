import service from '@utils/request.js'
const root='/jwt/artifacts/optimizer'
async function data(promise){return (await promise).data}
export const loadWorkspace=uid=>data(service.get(`${root}/workspace`,{params:{uid}}))
export const saveWorkspace=(uid,value)=>data(service.put(`${root}/workspace`,value,{params:{uid}}))
export const loadCatalog=()=>data(service.get(`${root}/catalog`))
export const enkaPreview=uid=>data(service.post(`${root}/enka-preview`,null,{params:{uid}}))
export const loadSnapshots=uid=>data(service.get(`${root}/snapshots`,{params:{uid}}))
export const loadSnapshot=(uid,id)=>data(service.get(`${root}/snapshots/${encodeURIComponent(id)}`,{params:{uid}}))
export const startOptimization=(uid,selection)=>data(service.post(`${root}/jobs`,selection,{params:{uid}}))
export const loadOptimization=(uid,id)=>data(service.get(`${root}/jobs/${encodeURIComponent(id)}`,{params:{uid}}))
export const listOptimizations=uid=>data(service.get(`${root}/jobs`,{params:{uid}}))
export const cancelOptimization=(uid,id)=>data(service.post(`${root}/jobs/${encodeURIComponent(id)}/cancel`,null,{params:{uid}}))
