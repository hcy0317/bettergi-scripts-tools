import test from 'node:test'
import assert from 'node:assert/strict'
import {validateOptimization} from '../src/features/artifact-optimizer/validation.js'
import {newCharacter,newBuild} from '../src/features/artifact-optimizer/model.js'
const make=()=>{const c=newCharacter('amber','安柏');const b=newBuild('team');b.members=[{character:'amber',kind:'real_fixed'}];b.rotation='active 安柏; while 1 {安柏 attack;}';return {characters:[c],builds:[b]}}
test('zero participants explains the missing selection instead of an unexplained disabled button',()=>{
  const issues=validateOptimization(make(),[],{artifacts:[]},{})
  assert.ok(issues.some(i=>i.field==='selection'&&i.message.includes('参与')))
})
test('validation identifies the exact character and field and catches detached references',()=>{
  const workspace=make()
  let issues=validateOptimization(workspace,['amber'],{artifacts:[{}]},{});
  assert.ok(issues.some(i=>i.character==='amber'&&i.field==='weapon'))
  assert.ok(issues.some(i=>i.character==='amber'&&i.field==='builds'))
  workspace.characters[0].weapon='huntersbow';workspace.characters[0].builds=[{id:'team'}]
  workspace.builds[0].energy={enabled:true,mode:'every',start:480,end:480,amount:1}
  issues=validateOptimization(workspace,['amber'],{artifacts:[{}]},{});
  assert.ok(issues.some(i=>i.buildId==='team'&&i.field==='energy'))
})
