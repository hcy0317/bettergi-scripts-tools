import test from 'node:test'
import assert from 'node:assert/strict'
import {priorityRows,priorityStamp,reorderPriorities,setPriorityWeight,undoPriorities} from '../src/features/artifact-optimizer/priorities.js'

const fixture=()=>({version:7,characters:[
  {key:'furina',builds:[{id:'water',weight:1},{id:'mixed',weight:1}],protected:true},
  {key:'jean',weight:1,builds:[{id:'water',weight:3}]},
  {key:'zhongli',weight:0,builds:[]},
],builds:[{id:'water',weight:1,members:[{character:'furina'}],roundCount:3},{id:'mixed',weight:1,rotation:'original'}]})
const chars={kind:'characters'},teams={kind:'builds'},bindings={kind:'bindings',character:'furina'}
test('opening priority overview is read-only and missing weights mean one',()=>{
  const w=fixture(),before=structuredClone(w)
  assert.deepEqual(priorityRows(w,chars).map(r=>[r.id,r.weight]),[['furina',1],['jean',1],['zhongli',0]])
  assert.deepEqual(w,before)
})
test('dragging equal weights changes real priority but preserves zero and other settings',()=>{
  const w=fixture(),before=structuredClone(w)
  const action=reorderPriorities(w,chars,['jean','furina'],priorityStamp(w,chars))
  assert.deepEqual(w.characters.map(c=>[c.key,c.weight]),[['furina',1],['jean',2],['zhongli',0]])
  assert.deepEqual(w.builds,before.builds)
  assert.deepEqual(w.characters[0].builds,before.characters[0].builds)
  assert.equal(w.characters[0].protected,true)
  undoPriorities(w,action)
  assert.deepEqual(w,before)
  assert.equal(Object.hasOwn(w.characters[0],'weight'),false)
})
test('teams and character-specific team preferences have separate targets',()=>{
  const w=fixture()
  reorderPriorities(w,teams,['mixed','water'],priorityStamp(w,teams))
  assert.equal(w.builds[1].weight,2);assert.equal(w.characters[0].builds[1].weight,1)
  reorderPriorities(w,bindings,['mixed','water'],priorityStamp(w,bindings))
  assert.equal(w.characters[0].builds[1].weight,2);assert.equal(w.characters[1].builds[0].weight,3)
  assert.deepEqual(JSON.parse(JSON.stringify(w)),w)
})
test('unknown duplicate omitted and zero-weight IDs reject atomically',()=>{
  for(const ids of [['jean','bad'],['jean','jean'],['jean'],['jean','furina','zhongli']]){
    const w=fixture(),before=structuredClone(w)
    assert.throws(()=>reorderPriorities(w,chars,ids,priorityStamp(w,chars)))
    assert.deepEqual(w,before)
  }
})
test('dropping at the original position does not normalize existing weights',()=>{
  const w=fixture();w.characters[0].weight=12.5
  const before=structuredClone(w)
  assert.equal(reorderPriorities(w,chars,['furina','jean'],priorityStamp(w,chars)),null)
  assert.deepEqual(w,before)
})
test('stale drops and undo never overwrite later edits',()=>{
  const w=fixture(),stamp=priorityStamp(w,chars);w.characters[1].weight=7
  assert.throws(()=>reorderPriorities(w,chars,['jean','furina'],stamp))
  assert.equal(w.characters[1].weight,7)
  const action=setPriorityWeight(w,chars,'furina',8,priorityStamp(w,chars));w.characters[1].weight=9
  assert.throws(()=>undoPriorities(w,action));assert.equal(w.characters[1].weight,9)
})
test('numeric editing rejects invalid values and can explicitly enable a zero-weight item',()=>{
  const w=fixture()
  for(const value of [-1,Infinity,NaN,null,undefined,'2',1001])assert.throws(()=>setPriorityWeight(w,chars,'zhongli',value,priorityStamp(w,chars)))
  setPriorityWeight(w,chars,'zhongli',1,priorityStamp(w,chars))
  assert.equal(w.characters[2].weight,1)
})
