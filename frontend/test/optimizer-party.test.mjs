import test from 'node:test'
import assert from 'node:assert/strict'
import {newCharacter,newBuild,updateBuildMembers,selectBuildMembers,selectedScenarioIds} from '../src/features/artifact-optimizer/model.js'

test('multi-select links four members, keeps overrides and updates participation',()=>{
  const characters=['furina','navia','noelle','xianyun','amber'].map(k=>newCharacter(k))
  const build=newBuild('team');build.members=[{character:'furina',kind:'hypothetical',stats:'er=2',profile:{level:80}}]
  characters[0].builds=[{id:'team',weight:3,reference:200},{id:'other',weight:2}]
  const workspace={characters,builds:[build]}
  const selected=updateBuildMembers(workspace,'team',['furina','navia','noelle','xianyun'],[])
  assert.deepEqual(selected,['navia','noelle','xianyun'])
  assert.equal(build.members[0].profile.level,80)
  assert.equal(characters[0].builds[0].weight,3)
  assert.deepEqual(selectedScenarioIds(characters,selected),['team'])
  assert.throws(()=>updateBuildMembers(workspace,'team',characters.map(c=>c.key),selected),/四/)
  updateBuildMembers(workspace,'team',['navia','noelle','xianyun'],selected)
  assert.deepEqual(characters[0].builds,[{id:'other',weight:2}])
})

test('existing unlinked party can explicitly join this calculation without dropping other selections',()=>{
  const workspace={characters:['furina','navia'].map(k=>newCharacter(k)),builds:[{id:'team',members:[{character:'furina'},{character:'navia'}]}]}
  assert.deepEqual(selectBuildMembers(workspace,'team',['amber']),['amber','furina','navia'])
  assert.deepEqual(selectedScenarioIds(workspace.characters,['furina','navia']),['team'])
})
