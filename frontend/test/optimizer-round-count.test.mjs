import test from 'node:test'
import assert from 'node:assert/strict'
import {newBuild,normalizeBuild} from '../src/features/artifact-optimizer/model.js'
import {applyCommunityPreview} from '../src/features/artifact-optimizer/community.js'
import {validateOptimization} from '../src/features/artifact-optimizer/validation.js'

test('new and legacy builds use cycle counts without deleting old reference settings',()=>{
  assert.equal(newBuild('new').stopMode,'loop_count')
  assert.equal(newBuild('new').roundCount,3)
  const legacy={id:'old',stopMode:'target_or_script',duration:60,targets:[{hp:999999999}],rotation:'while 1 {}',rounds:[{startFrame:1,endFrame:100}],roundPolicy:{mode:'legacy',warmup:0,loopIndex:0}}
  normalizeBuild(legacy)
  assert.equal(legacy.stopMode,'loop_count')
  assert.equal(legacy.legacyStopMode,'target_or_script')
  assert.equal(legacy.targets[0].hp,999999999)
  assert.equal(legacy.duration,60)
  assert.equal(legacy.rotation,'while 1 {}')
  assert.equal(legacy.legacyRounds.length,1)
  assert.equal(legacy.roundPolicy.mode,'auto')
  legacy.roundCount=5;normalizeBuild(legacy)
  assert.equal(legacy.roundCount,5)
  assert.equal(legacy.legacyStopMode,'target_or_script')
})

test('community settings cannot revert count termination or erase a chosen count',()=>{
  const build=newBuild('team');build.roundCount=5
  applyCommunityPreview(build,{importable:true,rotation:'active amber;while 1 {amber attack;}',settings:{stopMode:'target_or_script',targets:[{hp:999999999}]} })
  assert.equal(build.stopMode,'loop_count');assert.equal(build.roundCount,5);assert.equal(build.targets[0].hp,999999999)
})

test('invalid counts and warmup have a field-level blocker for both calculations',()=>{
  const build=newBuild('team');build.members=[{character:'amber'}];build.rotation='active amber; while 1 {amber attack;}'
  const workspace={builds:[build],characters:[{key:'amber',builds:[{id:'team'}]}]}
  for(const value of [0,65,1.5,null,'3']){
    build.roundCount=value
    assert.ok(validateOptimization(workspace,['amber'],{artifacts:[{}]}).some(i=>i.field==='rounds'))
  }
  build.roundCount=3;build.roundPolicy.warmup=3
  assert.ok(validateOptimization(workspace,['amber'],{artifacts:[{}]}).some(i=>i.field==='rounds'))
})
