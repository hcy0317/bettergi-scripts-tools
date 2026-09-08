export function createCommunityRequestScope(){
  let generation=0,controller=null
  return {
    begin(){
      controller?.abort()
      controller=new AbortController()
      const current=++generation,signal=controller.signal
      return {signal,isCurrent:()=>current===generation&&!signal.aborted}
    },
    cancel(){generation++;controller?.abort();controller=null}
  }
}
export function communityErrorMessage(error,fallback='社区请求失败，请稍后重试'){
  const message=error?.response?.data?.message||error?.message
  if(message==='Network Error')return '社区连接失败，请稍后重试，或直接粘贴脚本'
  if(error?.code==='ECONNABORTED'||error?.code==='ETIMEDOUT')return '社区请求超时，请稍后重试，或直接粘贴脚本'
  return typeof message==='string'&&message.trim()?message:fallback
}
export function communityFilterIssue(filters,label=key=>key){
  const overlap=(filters.include||[]).filter(key=>(filters.exclude||[]).includes(key))
  if(overlap.length)return '不能同时包含和排除角色：'+overlap.map(label).join('、')
  if((filters.tags||[]).some(tag=>(filters.excludedTags||[]).includes(tag)))return '同一标签不能同时包含和排除，请调整标签筛选'
  return ''
}
export function communityParts(preview){
  return {
    script:Boolean(preview?.rotation?.trim()||preview?.scriptPrelude?.trim()),
    settings:Object.keys(preview?.settings||{}).length>0,
    sampling:Number.isInteger(preview?.validationSamples)&&preview.validationSamples>0
  }
}
export function hasCommunitySelection(preview,choices){
  const available=communityParts(preview)
  return Boolean(preview?.importable&&Object.keys(available).some(key=>available[key]&&choices[key]))
}

export function applyCommunityPreview(build,preview,{script=true,settings=true}={}){
  if(!preview?.importable)throw new Error('仍有不能等价处理的语句，请先修正后重新预览')
  const clone=value=>JSON.parse(JSON.stringify(value))
  if(script){
    if(build.rounds?.length)build.legacyRounds=clone(build.rounds)
    build.rotation=preview.rotation;build.scriptPrelude=preview.scriptPrelude||'';build.scriptPreludeEnabled=true
    build.rounds=[];build.roundPolicy={mode:'auto',warmup:0,loopIndex:0}
  }
  if(settings)for(const key of ['stopMode','duration','swapDelay','targets','energy','hitlag','defhalt'])if(Object.hasOwn(preview.settings||{},key))build[key]=clone(preview.settings[key])
  build.communityReference={url:preview.sourceUrl||'',note:'仅借用循环和已确认的场景设置，个人条件与背包未从社区覆盖'}
}
export function describeCommunitySettings(settings={}){
  const rows=[]
  if(settings.stopMode)rows.push({label:'单次停止方式',value:settings.stopMode==='target_or_script'?'敌人被击败或脚本动作结束':'达到固定游戏内时长'})
  if(settings.duration!==undefined&&settings.stopMode!=='target_or_script')rows.push({label:'单次战斗时长',value:`${settings.duration} 秒`})
  if(settings.swapDelay!==undefined)rows.push({label:'切人延迟',value:`${settings.swapDelay} 帧`})
  for(const [i,t] of (settings.targets||[]).entries())rows.push({label:`敌人 ${i+1}`,value:`等级 ${t.level}，抗性 ${t.resistance*100}%，半径 ${t.radius}，位置 (${t.x}, ${t.y})${settings.stopMode==='target_or_script'?`，血量 ${t.hp}`:''}`})
  if(settings.energy?.enabled){const e=settings.energy;rows.push({label:'外部掉球',value:`${e.mode==='once'?`第 ${e.start/60} 秒一次`:`每 ${e.start/60} 至 ${e.end/60} 秒`}，${e.amount} 颗无元素微粒`})}
  if(settings.hitlag!==undefined||settings.defhalt!==undefined)rows.push({label:'原生停顿选项',value:'按原脚本保留，上游设置未丢弃'})
  return rows
}
