import {artifactCharacterLabels,artifactSetLabels} from '../artifact-analysis/buildCatalog.js'
const canonical=key=>String(key??'').toLowerCase().replace(/[_ -]/g,'')
const fallbackCharacters=Object.fromEntries(Object.entries(artifactCharacterLabels).map(([key,name])=>[canonical(key),name]))
const fallbackSets=Object.fromEntries(Object.entries(artifactSetLabels).map(([key,name])=>[canonical(key),name]))
const isChinese=value=>/[\u3400-\u9fff]/.test(value||'')
export function characterLabel(catalog,key){return catalog?.localization?.character_names?.[canonical(key)]||catalog?.characters?.find(c=>c.key===key)?.nativeName||fallbackCharacters[canonical(key)]||(isChinese(key)?key:'角色资料待补充')}
export function weaponLabel(catalog,key){return catalog?.localization?.weapon_names?.[canonical(key)]||(isChinese(key)?key:'武器资料待补充')}
export function setLabel(catalog,key){return catalog?.localization?.artifact_names?.[canonical(key)]||fallbackSets[canonical(key)]||(isChinese(key)?key:'套装资料待补充')}
export function profileLabel(catalog,profile){return profile?.name&&profile.name!==profile.key?profile.name:characterLabel(catalog,profile?.key)}
export function buildLabel(build){return build?.name==='新的配队 Build'?'新的配队方案':(build?.name||'未命名方案')}
export const elementLabels={pyro:'火元素',hydro:'水元素',cryo:'冰元素',electro:'雷元素',anemo:'风元素',geo:'岩元素',dendro:'草元素',physical:'物理'}
export const statLabels={hp:'生命值','hp%':'生命值百分比',atk:'攻击力','atk%':'攻击力百分比',def:'防御力','def%':'防御力百分比',em:'元素精通',er:'元素充能效率',cr:'暴击率',cd:'暴击伤害',heal:'治疗加成',...Object.fromEntries(Object.entries(elementLabels).map(([key,label])=>[`${key==='physical'?'phys':key}%`,`${label}伤害加成`]))}
export const statLabel=key=>statLabels[key]||'属性资料待补充'
export const jobLabels={QUEUED:'等待计算',RUNNING:'正在计算',COMPLETED:'计算完成',FAILED:'计算失败',CANCELLED:'已取消',INTERRUPTED:'已中断',passed:'通过',failed:'未通过',indeterminate:'无法判定',pending:'待验证'}
export const metricLabels={damage_per_round:'每轮伤害',effective_healing_per_round:'每轮有效治疗',damage:'伤害',healing:'治疗量',mean_raw_across_samples:'原始值跨样本均值',mean:'均值',per_round:'每轮',hp:'生命值',unknown:'未知'}
