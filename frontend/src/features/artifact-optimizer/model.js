export const preferenceOptions=[{value:'balanced',label:'均衡',description:'最大化去重场景的加权总每秒伤害'}, {value:'peak',label:'保尖',description:'按角色和方案权重保障个人目标'}, {value:'fallback',label:'兜底',description:'依次减少最严重的加权目标短缺'}]
export const slotOptions=[['flower','生之花'],['plume','死之羽'],['sands','时之沙'],['goblet','空之杯'],['circlet','理之冠']]
export const statOptions=[['hp','生命值'],['hp_','生命值 %'],['atk','攻击力'],['atk_','攻击力 %'],['def','防御力'],['def_','防御力 %'],['eleMas','元素精通'],['enerRech_','元素充能效率 %'],['critRate_','暴击率 %'],['critDMG_','暴击伤害 %'],['heal_','治疗加成 %'],...['pyro','hydro','cryo','electro','anemo','geo','dendro','physical'].map((s,i)=>[`${s}_dmg_`,`${['火','水','冰','雷','风','岩','草','物理'][i]}伤加成 %`])]
export function newCharacter(key,name=key){return {key,name,level:90,maxLevel:90,constellation:0,talents:[6,6,6],weapon:'',weaponLevel:90,weaponMaxLevel:90,refinement:1,tags:[],weight:1,protected:false,builds:[],minimumStats:{},mainStats:{},requiredSets:{},fixedSlots:{}}}
export function newBuild(id=crypto.randomUUID()){return {id,name:'新的配队方案',weight:1,duration:60,enemyLevel:100,resistance:0.1,enemyCount:1,members:[],rotation:'',rounds:[],constraints:[],buffs:[],allowPartial:false}}
export function selectedScenarioIds(characters,selected){return [...new Set(characters.filter(c=>selected.includes(c.key)).flatMap(c=>(c.builds||[]).map(b=>b.id)))]}
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
export const resultLabels={feasible_recommendation:'已找到合格方案',feasible_baseline:'保留合格旧装',feasible_uncertain:'可行，改善尚不确定',budget_no_feasible:'预算内未找到可行方案',proven_infeasible:'已证明约束无法满足',indeterminate:'缺少必要计算证据',validation_failed:'最终独立验证未通过',cancelled:'已取消'}
