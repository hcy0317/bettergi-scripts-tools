import {profileLabel,buildLabel} from './localization.js'
import {validateBuildScripts} from './script-diagnostics.js'
const integer=(v,min,max)=>Number.isInteger(v)&&v>=min&&v<=max
const number=(v,min,max)=>Number.isFinite(v)&&v>=min&&v<=max
export function validateOptimization(workspace,selected,snapshot,catalog={},options={}){
  const issues=[],seen=new Set(),profiles=new Map((workspace.characters||[]).map(c=>[c.key,c])),builds=new Map((workspace.builds||[]).map(b=>[b.id,b])),ids=new Set()
  const add=(scope,character,buildId,field,message)=>{const key=[scope,character,buildId,field,message].join('|');if(!seen.has(key)){seen.add(key);issues.push({scope,character,buildId,field,message})}}
  if(!selected.length)add('selection','','','selection','请选择参与本次配装的角色，或使用“将本队加入配装”')
  if(selected.length>16)add('selection','','','selection','一次最多为16名角色分配装备')
  if(!snapshot?.artifacts?.length)add('selection','','','snapshot','请选择含可识别圣遗物的扫描记录')
  if(workspace.protectedInventoryOwners!==undefined){
    if(!Array.isArray(workspace.protectedInventoryOwners)||workspace.protectedInventoryOwners.length>200)add('selection','','','protections','库存角色保护列表格式无效')
    else if(workspace.protectedInventoryOwners.some(key=>typeof key!=='string'||!catalog.inventoryCharacters?.some(c=>c.key===key)))add('selection','','','protections','部分库存保护角色的身份尚未核实，请检查角色目录与保护选择')
  }
  const personal=(key,p,buildId='')=>{
    const scope=buildId?'build':'character',name=profileLabel(catalog,{...p,key})
    if(catalog.characters?.length&&!catalog.characters.some(c=>c.key===key))add(scope,key,buildId,'profile',name+'：当前引擎没有此角色的模拟实现；库存身份可用不等于可以参战计算')
    for(const [field,label,min,max] of [['level','角色等级',1,100],['maxLevel','突破等级上限',p.level||1,100],['constellation','命座',0,6],['weaponLevel','武器等级',1,90],['weaponMaxLevel','武器等级上限',p.weaponLevel||1,90],['refinement','精炼',1,5]])if(!integer(p[field],min,max))add(scope,key,buildId,field,`${name}：${label}未填写或超出范围`)
    if(!p.weapon||catalog.weapons?.length&&!catalog.weapons.some(w=>w.key===p.weapon))add(scope,key,buildId,'weapon',`${name}：请选择有效武器`)
    for(const field of ['maxLevel','weaponMaxLevel'])if(!integer(p[field],10,90)||p[field]%10!==0)add(scope,key,buildId,field,`${name}：等级上限须选择10至90之间的整十档位`)
    if(!Array.isArray(p.talents)||p.talents.length!==3||p.talents.some(v=>!integer(v,1,15)))add(scope,key,buildId,'talents',`${name}：请补全三个基础天赋等级`)
  }
  for(const key of selected){const p=profiles.get(key);if(!p){add('character',key,'','profile','所选角色的个人档案不存在');continue}personal(key,p);if(!p.builds?.length)add('character',key,'','builds',`${profileLabel(catalog,p)}：尚未关联配队方案`);for(const b of p.builds||[]){ids.add(b.id);if(!builds.has(b.id))add('character',key,'','builds','角色关联的方案已不存在')}}
  for(const id of ids){const b=builds.get(id);if(!b)continue;const name=buildLabel(b)
    issues.push(...validateBuildScripts(b,workspace.characters,catalog))
    if(!Array.isArray(b.members)||!b.members.length||b.members.length>4||new Set(b.members.map(m=>m.character)).size!==b.members.length)add('build','',id,'members',`${name}：请设置一至四名不同队员`)
    for(const m of b.members||[]){const p=m.profile||profiles.get(m.character);if(!p){add('build',m.character,id,'members',`${name}：队员缺少个人档案`);continue}if(m.profile||!selected.includes(m.character))personal(m.character,p,m.profile?id:'');if(!selected.includes(m.character)&&m.kind==='hypothetical'&&!m.stats?.trim())add('build',m.character,id,'members',`${name}：未参算队友的假设属性不能为空`)}
    for(const key of selected)if(profiles.get(key)?.builds?.some(r=>r.id===id)&&!b.members?.some(m=>m.character===key))add('build',key,id,'members',`${name}：关联该方案的角色不在队伍中`)
    if(b.nativeRotation?.enabled){
      if(typeof b.nativeRotation.source!=='string'||!b.nativeRotation.source.trim()||b.nativeRotation.source.length>100000)add('build','',id,'rotation',`${name}：原生流程为空或超过大小限制`)
      if(catalog.capabilities?.nativeFlow?.schemaVersion!=='native-flow-v1')add('build','',id,'rotation',`${name}：当前计算引擎尚未安装原生流程适配，请更新引擎`)
      if(b.scriptPrelude?.trim()&&b.scriptPreludeEnabled!==false)add('build','',id,'scriptPrelude',`${name}：请明确停用gcsim辅助逻辑，或切回gcsim循环；不能和原生流程混合运行`)
    }else if(!b.rotation?.trim())add('build','',id,'rotation',`${name}：请填写或导入循环脚本`)
    const roundCount=b.roundCount===undefined?3:b.roundCount,warmup=b.roundPolicy?.warmup??0
    if(!integer(roundCount,1,64)||!integer(warmup,0,63)||warmup>=roundCount)add('build','',id,'rounds',`${name}：循环次数须为1至64，且大于逐轮指标忽略的开场轮数`)
    if(Array.isArray(b.targets)){if(b.targets.length<1||b.targets.length>10)add('build','',id,'scene',`${name}：敌人数量应为1至10`);b.targets.forEach((t,i)=>{if(!integer(t.level,1,200)||!number(t.resistance,-1,10)||!number(t.radius,0.01,100)||!number(t.x,-1000,1000)||!number(t.y,-1000,1000))add('build','',id,'scene',`${name}：敌人${i+1}的等级、抗性、位置或半径未填对`)})}
    if(b.energy?.enabled){const e=b.energy;if(!['once','every'].includes(e.mode)||!integer(e.start,1,36000)||!integer(e.amount,1,100)||(e.mode==='every'&&(!integer(e.end,1,36000)||e.end<=e.start)))add('build','',id,'energy',`${name}：掉球时间或数量无效；周期最大间隔必须大于最小间隔`)}
  }
  if(options.searchSamples!==undefined&&!integer(options.searchSamples,1,32))add('selection','','','sampling','搜索采样次数应为1至32')
  if(options.validationSamples!==undefined&&!integer(options.validationSamples,2,1000))add('selection','','','sampling','独立验证采样次数应为2至1000')
  return issues
}

export function validateRotation(workspace,buildId,selected,snapshot,catalog={},options={}){
  const build=workspace.builds?.find(b=>b.id===buildId)
  if(!build)return [{scope:'selection',character:'',buildId:'',field:'members',message:'请选择当前配队方案'}]
  const members=new Set((build.members||[]).map(m=>m.character))
  const scopedSelection=selected.filter(key=>members.has(key))
  const characters=(workspace.characters||[]).map(profile=>{
    if(!scopedSelection.includes(profile.key))return profile
    const binding=profile.builds?.find(b=>b.id===buildId)||{id:buildId}
    return {...profile,builds:[binding]}
  })
  return validateOptimization({...workspace,characters,builds:[build]},scopedSelection,snapshot,catalog,options)
}
