const favonius=new Set(['favoniussword','favoniusgreatsword','favoniuslance','favoniuscodex','favoniuswarbow'])
const keywords=new Set(['fn','let','return','if','else','while','for','switch','case'])
const identifier='[\\p{L}_][\\p{L}\\p{N}_（）()]*'
const boundary='(?<![\\p{L}\\p{N}_.])'
const waitPattern=()=>new RegExp(boundary+'while\\s+!\\s*\\.('+identifier+')\\.mods\\.favonius-cd\\s*\\{\\s*('+identifier+')\\s+attack\\s*;\\s*\\}','gu')
const actorPatterns=()=>[
  new RegExp(boundary+'active\\s+('+identifier+')\\s*;','gu'),
  new RegExp(boundary+'('+identifier+')\\s+(?:attack|skill|burst|charge|dash|jump|walk|aim|low_plunge|high_plunge)\\b','gu'),
]

// String indices and split('') intentionally retain UTF-16 offsets, like Java.
function scan(source){
  const masked=source.split(''),balance={'(':0,'[':0,'{':0},opening={')':'(',']':'[','}':'{'}
  let quote='',lineComment=false,blockComment=false,escape=false
  for(let i=0;i<source.length;i++){
    const c=source[i],next=source[i+1]
    if(lineComment){if(c==='\n')lineComment=false;else masked[i]=' ';continue}
    if(blockComment){if(c!=='\r'&&c!=='\n')masked[i]=' ';if(c==='*'&&next==='/'){masked[++i]=' ';blockComment=false}continue}
    if(quote){if(c!=='\r'&&c!=='\n')masked[i]=' ';if(escape)escape=false;else if(c==='\\')escape=true;else if(c===quote)quote='';continue}
    if(c==='#'||c==='/'&&next==='/'){masked[i]=' ';lineComment=true;if(c==='/')masked[++i]=' ';continue}
    if(c==='/'&&next==='*'){masked[i]=masked[++i]=' ';blockComment=true;continue}
    if(c==='"'||c==="'"){quote=c;masked[i]=' ';continue}
    if(c in balance)balance[c]++
    if(c in opening){balance[opening[c]]--;if(balance[opening[c]]<0)return {error:'括号未配对',at:i}}
  }
  if(quote||blockComment||Object.values(balance).some(Boolean))return {error:'脚本结尾存在未闭合的字符串、注释或括号',at:Math.max(0,source.length-1)}
  return {code:masked.join('')}
}
function issue(source,buildId,field,character,message,startOffset,endOffset){
  const line=source.slice(0,startOffset).split('\n').length
  const column=startOffset-source.lastIndexOf('\n',startOffset-1)
  return {scope:'build',character,buildId,field,message,startOffset,endOffset,line,column}
}
export function diagnoseScript(source,buildId,field,weapons={},aliases={}){
  if(typeof source!=='string'||!source.trim())return []
  const scanned=source.length>100000?{error:'脚本超过100 KB',at:source.length-1}:scan(source)
  if(scanned.error){
    const start=Math.max(0,source.lastIndexOf('\n',scanned.at-1)+1),next=source.indexOf('\n',start)
    return [issue(source,buildId,field,'',scanned.error,start,next<0?source.length:Math.max(start+1,next))]
  }
  const issues=[],code=scanned.code,resolve=token=>Object.hasOwn(aliases,token)?aliases[token]:token
  for(const match of code.matchAll(waitPattern())){
    const actor=resolve(match[1]),weapon=Object.hasOwn(weapons,actor)?weapons[actor]:undefined
    if(actor===resolve(match[2])&&weapon&&!favonius.has(weapon)){
      const start=match.index+match[0].indexOf('.'+match[1])
      issues.push(issue(source,buildId,field,actor,'当前武器 '+weapon+' 无法触发西风效果；请修正这段等待逻辑后再计算',start,start+1+match[1].length+'.mods.favonius-cd'.length))
    }
    if(issues.length>=100)break
  }
  for(const pattern of actorPatterns())for(const match of code.matchAll(pattern)){
    if(issues.length>=100)break
    const token=match[1],actor=resolve(token)
    if(keywords.has(token)||Object.hasOwn(weapons,actor))continue
    const start=match.index+match[0].indexOf(token)
    issues.push(issue(source,buildId,field,actor,'脚本引用的角色 '+token+' 不在当前配队或缺少档案，请修正后再计算',start,start+token.length))
  }
  return issues.sort((a,b)=>a.startOffset-b.startOffset)
}
export function validateBuildScripts(build,characters=[],catalog={}){
  if(!build||build.nativeRotation?.enabled)return []
  const profiles=new Map(characters.map(p=>[p.key,p])),weapons={},aliases={}
  function add(alias,key){
    if(!alias)return
    for(const value of new Set([alias,alias.replaceAll(' ','').replaceAll('(','（').replaceAll(')','）')]))
      aliases[value]=Object.hasOwn(aliases,value)&&aliases[value]!==key?'':key
  }
  for(const member of build.members||[]){
    const key=member.character,profile=member.profile||profiles.get(key)
    if(profile)weapons[key]=profile.weapon||''
    add(key,key);add(catalog.localization?.character_names?.[key],key)
    const person=catalog.characters?.find(c=>c.key===key)
    add(person?.nativeName,key);for(const alias of person?.inventoryAliases||[])add(alias,key)
  }
  return [...diagnoseScript(build.rotation,build.id,'rotation',weapons,aliases),
    ...(build.scriptPreludeEnabled===false?[]:diagnoseScript(build.scriptPrelude,build.id,'scriptPrelude',weapons,aliases))]
}
