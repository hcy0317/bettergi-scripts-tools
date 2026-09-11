export const preferenceOptions=[{value:'balanced',label:'均衡',description:'最大化去重场景的加权总每秒伤害'}, {value:'peak',label:'保尖',description:'按角色和方案权重保障个人目标'}, {value:'fallback',label:'兜底',description:'依次减少最严重的加权目标短缺'}]
export const levelCapOptions=Object.freeze(Array.from({length:9},(_,i)=>(i+1)*10))
export function ascensionDescription(catalog,key,cap,weapon=false){
  if(!Number.isInteger(cap))return '待填写'
  const data=(weapon?catalog?.weapons:catalog?.characters)?.find(c=>c.key===key)
  const rows=(weapon?data?.base_stats:data?.stats)?.promo_data
  if(!rows?.length)return '由计算引擎的突破表判定'
  let index=0;rows.forEach((row,i)=>{if(cap>=row.max_level)index=i})
  return index===0?'未突破':`已突破 ${index} 次`
}
export const slotOptions=[['flower','生之花'],['plume','死之羽'],['sands','时之沙'],['goblet','空之杯'],['circlet','理之冠']]
export const statOptions=[['hp','生命值'],['hp_','生命值 %'],['atk','攻击力'],['atk_','攻击力 %'],['def','防御力'],['def_','防御力 %'],['eleMas','元素精通'],['enerRech_','元素充能效率 %'],['critRate_','暴击率 %'],['critDMG_','暴击伤害 %'],['heal_','治疗加成 %'],...['pyro','hydro','cryo','electro','anemo','geo','dendro','physical'].map((s,i)=>[`${s}_dmg_`,`${['火','水','冰','雷','风','岩','草','物理'][i]}伤加成 %`])]
export function newCharacter(key,name=key){return {key,name,level:90,maxLevel:90,constellation:0,talents:[6,6,6],weapon:'',weaponLevel:90,weaponMaxLevel:90,refinement:1,tags:[],weight:1,protected:false,builds:[],minimumStats:{},mainStats:{},requiredSets:{},fixedSlots:{}}}
export function normalizeBuild(build){
  if(build.stopMode&&build.stopMode!=='loop_count')build.legacyStopMode ??= build.stopMode
  build.stopMode='loop_count'
  if(build.roundCount===undefined)build.roundCount=3
  build.targets ??= Array.from({length:Math.max(1,Math.min(10,build.enemyCount??1))},()=>({level:build.enemyLevel??100,resistance:build.resistance??0.1,radius:1,x:0,y:0,hp:null}))
  build.swapDelay ??= 1
  build.energy ??= {enabled:false,mode:'every',start:480,end:720,amount:1}
  build.roundPolicy ??= {mode:build.rounds?.length?'legacy':'auto',warmup:0,loopIndex:0}
  if(build.rounds?.length)build.legacyRounds ??= JSON.parse(JSON.stringify(build.rounds))
  build.roundPolicy.mode='auto'
  build.roundPolicy.warmup ??= 0
  build.roundPolicy.loopIndex ??= 0
  build.rounds=[]
  build.scriptPrelude ??= ''
  build.scriptPreludeEnabled ??= true
  build.rounds ??= [];build.constraints ??= [];build.buffs ??= [];build.members ??= []
  return build
}
export function newBuild(id=crypto.randomUUID()){return normalizeBuild({id,name:'新的配队方案',weight:1,roundCount:3,members:[],rotation:'',rounds:[],constraints:[],buffs:[],allowPartial:false})}
export function selectedScenarioIds(characters,selected){return [...new Set(characters.filter(c=>selected.includes(c.key)).flatMap(c=>(c.builds||[]).map(b=>b.id)))]}
function linkBuild(character,id){character.builds ||= [];if(!character.builds.some(b=>b.id===id))character.builds.push({id,weight:1,metric:'damage_per_round',reference:0})}
export function updateBuildMembers(workspace,id,keys,selected){
  const build=workspace.builds.find(b=>b.id===id)
  if(!build)throw new Error('配队方案不存在')
  if(keys.length>4||new Set(keys).size!==keys.length)throw new Error('每队最多四名不同队员')
  if(keys.some(key=>!workspace.characters.some(c=>c.key===key)))throw new Error('请先建立所选队员的个人档案')
  const previous=new Map(build.members.map(m=>[m.character,m]))
  build.members=keys.map(key=>previous.get(key)||{character:key,kind:'real_fixed'})
  for(const c of workspace.characters){if(keys.includes(c.key))linkBuild(c,id);else if(previous.has(c.key))c.builds=(c.builds||[]).filter(b=>b.id!==id)}
  return [...new Set([...selected,...keys.filter(k=>!previous.has(k))])].filter(k=>!previous.has(k)||keys.includes(k)||workspace.characters.find(c=>c.key===k)?.builds?.length)
}
export function selectBuildMembers(workspace,id,selected){
  const build=workspace.builds.find(b=>b.id===id)
  if(!build)throw new Error('配队方案不存在')
  for(const member of build.members){const character=workspace.characters.find(c=>c.key===member.character);if(character)linkBuild(character,id)}
  return [...new Set([...selected,...build.members.map(m=>m.character)])]
}
export function mergeEnkaPreview(current,incoming,acceptedKeys){
  const result=JSON.parse(JSON.stringify(current))
  for(const imported of incoming){
    if(!acceptedKeys.includes(imported.key))continue
    const index=result.findIndex(c=>c.key===imported.key)
    if(index<0)result.push({...newCharacter(imported.key,imported.name),...structuredClone(imported)})
    else {const old=result[index];result[index]={...old,...structuredClone(imported),tags:old.tags,weight:old.weight,protected:old.protected,builds:old.builds}}
  }
  return result
}
export function metric(value){return Number.isFinite(value)?value.toLocaleString('zh-CN',{maximumFractionDigits:1}):'未知'}
export const resultLabels={feasible_recommendation:'已找到合格方案',feasible_baseline:'保留合格基线',feasible_simplification:'指标未退化，流程已简化',feasible_uncertain:'可行，改善尚不确定',budget_no_feasible:'预算内未找到可行方案',proven_infeasible:'已证明约束无法满足',indeterminate:'缺少必要计算证据',validation_failed:'最终独立验证未通过',cancelled:'已取消'}
