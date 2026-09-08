import test from 'node:test'
import assert from 'node:assert/strict'
import {protectedInventoryKeys,setInventoryProtections,toggleCharacterProtection,isCharacterProtected} from '../src/features/artifact-optimizer/inventory.js'

const catalog={
  characters:[{key:'amber',inventoryKey:'inventory21'},{key:'aetherdendro',inventoryKey:'inventory20000000'},{key:'lumineanemo',inventoryKey:'inventory20000000'}],
  inventoryCharacters:[{key:'inventory21',nativeName:'安柏'},{key:'inventory133',nativeName:'桑多涅'},{key:'inventory20000000',nativeName:'旅行者'}]
}
test('inventory-only protection requires no invented combat profile and joins legacy protection',()=>{
  const workspace={characters:[{key:'amber',protected:true,weight:3}],protectedInventoryOwners:['inventory133']}
  assert.deepEqual(new Set(protectedInventoryKeys(workspace,catalog)),new Set(['inventory21','inventory133']))
  setInventoryProtections(workspace,catalog,['inventory133'])
  assert.equal(workspace.characters.length,1)
  assert.equal(workspace.characters[0].protected,false)
  assert.equal(workspace.characters[0].weight,3)
  toggleCharacterProtection(workspace,catalog,'amber',true)
  assert.equal(isCharacterProtected(workspace,catalog,'amber'),true)
  assert.ok(workspace.protectedInventoryOwners.includes('inventory133'))
})
test('all traveler forms show and toggle the same physical protection',()=>{
  const workspace={characters:[{key:'aetherdendro',protected:false},{key:'lumineanemo',protected:true}]}
  assert.equal(isCharacterProtected(workspace,catalog,'aetherdendro'),true)
  toggleCharacterProtection(workspace,catalog,'aetherdendro',false)
  assert.equal(workspace.characters[1].protected,false)
  assert.deepEqual(protectedInventoryKeys(workspace,catalog),[])
})
test('unverified protection choices fail without changing the workspace',()=>{
  const workspace={characters:[{key:'amber',protected:true}],protectedInventoryOwners:['inventory21']}
  const before=JSON.stringify(workspace)
  assert.throws(()=>setInventoryProtections(workspace,catalog,['unverified']),/身份/)
  assert.equal(JSON.stringify(workspace),before)
})
