export function nativeImportTicket(uid,build,source){
  const snapshot=JSON.stringify(build),id=build?.id
  return {matches:(currentUid,currentBuild,currentSource)=>uid===currentUid&&id===currentBuild?.id&&source===currentSource&&snapshot===JSON.stringify(currentBuild)}
}
export function applyNativeImport(build,parsed,sourceName='粘贴的策略'){
  const p=parsed?.program
  if(!parsed?.supported||parsed.mode!=='native_flow'||p?.schemaVersion!=='native-flow-v1'||typeof p.source!=='string'||!p.source.trim()||!p.blocks||!Array.isArray(p.root))throw new Error('原生流程尚未通过完整检查')
  const blocks=Object.entries(p.blocks),macros=blocks.filter(([,b])=>b.macro)
  if(macros.some(([,b])=>b.macro!=='neuvillette_charge_v1'))throw new Error('此输入宏还没有可确认的模拟映射')
  build.nativeRotation={enabled:true,source:p.source,sourceName,macroMapping:macros.length?'neuvillette_charge_v1':'',summary:{rootCount:p.root.length,blocks:blocks.map(([name,b])=>({name,atomic:b.declaration?.options?.atomic==='true',macro:Boolean(b.macro)}))}}
  if(build.scriptPrelude?.trim())build.scriptPreludeEnabled=false
}
export function nativeAssumptionLabel(value){
  if(typeof value==='string'&&/^removed_impossible_favonius_wait:[a-z0-9]{1,40}$/.test(value))return `${value.split(':')[1]} 未装备西风武器：已从计算副本移除无法结束的触发等待，原文保留`
  if(value==='native_movement_timing_only')return '移动保留gcsim动作与耗时，但不模拟实机方向、站位和聚怪效果'
  if(value==='native_standard_charge')return '重击执行gcsim完整标准动作；按键秒数作为最短时段，不截断动作或伪造命中'
  return ({native_flow_model:'原生控制流程试算：使用模拟状态，视觉观测保持未知；不等价于实机执行',native_macro_neuvillette_charge:'喷射宏按gcsim标准重击试算，水滴/命座影响由引擎计算，不模拟镜头扫射命中',auxiliary_logic_disabled:'gcsim辅助逻辑已明确停用',external_energy_schedule:'启用了外部掉球供能假设',ignore_burst_energy:'已忽略爆发能量要求'})[value]||value
}
