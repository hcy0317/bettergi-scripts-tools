function inventoryKey(catalog,key){
  if(catalog.inventoryCharacters?.some(c=>c.key===key))return key
  return catalog.characters?.find(c=>c.key===key)?.inventoryKey||
    catalog.inventoryCharacters?.find(c=>c.simulationKeys?.includes(key))?.key||''
}
export function protectedInventoryKeys(workspace,catalog={}){
  const keys=new Set(Array.isArray(workspace.protectedInventoryOwners)?workspace.protectedInventoryOwners.filter(k=>typeof k==='string'):[])
  for(const profile of workspace.characters||[])if(profile.protected){
    const key=inventoryKey(catalog,profile.key)
    if(key)keys.add(key)
  }
  return [...keys]
}
export function setInventoryProtections(workspace,catalog,keys){
  const next=[...new Set(keys)]
  if(next.some(key=>!catalog.inventoryCharacters?.some(c=>c.key===key)))throw new Error('所选角色身份未核实，请重新载入角色目录')
  workspace.protectedInventoryOwners=next
  for(const profile of workspace.characters||[]){
    const key=inventoryKey(catalog,profile.key)
    if(key)profile.protected=next.includes(key)
  }
}
export function isCharacterProtected(workspace,catalog,key){
  const identity=inventoryKey(catalog,key)
  return Boolean(workspace.characters?.find(c=>c.key===key)?.protected||
    identity&&protectedInventoryKeys(workspace,catalog).includes(identity))
}
export function toggleCharacterProtection(workspace,catalog,key,value){
  const identity=inventoryKey(catalog,key)
  if(!identity){
    const profile=workspace.characters?.find(c=>c.key===key)
    if(profile)profile.protected=value
    return
  }
  const current=protectedInventoryKeys(workspace,catalog)
  setInventoryProtections(workspace,catalog,value?[...current,identity]:current.filter(k=>k!==identity))
}
