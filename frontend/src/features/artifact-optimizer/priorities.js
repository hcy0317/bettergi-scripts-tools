function collection(workspace,scope){
  if(scope.kind==='characters')return workspace.characters
  if(scope.kind==='builds')return workspace.builds
  if(scope.kind==='bindings'){
    const character=workspace.characters.find(c=>c.key===scope.character)
    if(character)return character.builds||[]
  }
  throw new Error('优先级所属档案已改变，请重新操作')
}
const itemKey=(item,scope)=>scope.kind==='characters'?item.key:item.id
function weight(item){
  const value=item.weight===undefined?1:item.weight
  if(typeof value!=='number'||!Number.isFinite(value)||value<0||value>1000)throw new Error('权重须为0至1000的有效数字')
  return value
}
function entries(workspace,scope){
  const items=collection(workspace,scope),ids=new Set()
  if(!Array.isArray(items))throw new Error('优先级列表格式无效')
  return items.map(item=>{
    const id=itemKey(item,scope)
    if(typeof id!=='string'||!id||ids.has(id))throw new Error('优先级列表有重复或无效标识')
    ids.add(id)
    return {id,item,weight:weight(item)}
  })
}
export function priorityRows(workspace,scope){return entries(workspace,scope).sort((a,b)=>b.weight-a.weight)}
export function priorityStamp(workspace,scope){
  return JSON.stringify([scope.kind,scope.character||'',entries(workspace,scope).map(({id,item})=>[id,Object.hasOwn(item,'weight'),item.weight])])
}
function begin(workspace,scope,expected){
  if(priorityStamp(workspace,scope)!==expected)throw new Error('拖动期间该组权重或成员已改变，请重新操作')
  return {workspace,scope:{...scope},before:entries(workspace,scope).map(({id,item})=>({id,present:Object.hasOwn(item,'weight'),value:item.weight}))}
}
export function reorderPriorities(workspace,scope,ids,expected){
  const action=begin(workspace,scope,expected),rows=priorityRows(workspace,scope).filter(row=>row.weight>0)
  if(!Array.isArray(ids)||ids.length!==rows.length||new Set(ids).size!==ids.length||ids.some(id=>!rows.some(row=>row.id===id)))throw new Error('拖动结果不完整或来自其他分组，未修改权重')
  if(ids.every((id,i)=>id===rows[i].id))return null
  const values=new Map(ids.map((id,i)=>[id,ids.length-i]))
  for(const {id,item} of rows)item.weight=values.get(id)
  action.after=priorityStamp(workspace,scope)
  return action
}
export function setPriorityWeight(workspace,scope,id,value,expected){
  const action=begin(workspace,scope,expected),row=entries(workspace,scope).find(row=>row.id===id)
  weight({weight:value===undefined?NaN:value})
  if(!row)throw new Error('该优先级条目已不存在')
  if(row.weight===value)return null
  row.item.weight=value
  action.after=priorityStamp(workspace,scope)
  return action
}
export function undoPriorities(workspace,action){
  if(!action||workspace!==action.workspace||priorityStamp(workspace,action.scope)!==action.after)throw new Error('此组已有后续编辑，不能覆盖；请重新调整')
  const rows=new Map(entries(workspace,action.scope).map(row=>[row.id,row.item]))
  for(const previous of action.before){
    if(previous.present)rows.get(previous.id).weight=previous.value
    else delete rows.get(previous.id).weight
  }
}
