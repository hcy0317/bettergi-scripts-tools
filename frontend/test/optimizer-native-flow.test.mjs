import test from 'node:test'
import assert from 'node:assert/strict'
import {applyCommunityPreview} from '../src/features/artifact-optimizer/community.js'
import {applyNativeImport,nativeImportTicket} from '../src/features/artifact-optimizer/native-flow.js'

test('choosing a community loop disables but preserves the previous native source',()=>{
  const build={rotation:'old',nativeRotation:{enabled:true,source:'strategy(loop=battle)'}}
  applyCommunityPreview(build,{importable:true,rotation:'active kaeya; kaeya skill;',settings:{}},{script:true,settings:false})
  assert.equal(build.nativeRotation.enabled,false)
  assert.equal(build.nativeRotation.source,'strategy(loop=battle)')
})

test('native import keeps the reference script and refuses stale source ownership',()=>{
  const build={id:'water',rotation:'keep this gcsim reference',members:[{character:'neuvillette'}]}
  const ticket=nativeImportTicket('100000001',build,'source')
  assert.equal(ticket.matches('100000001',build,'source'),true)
  assert.equal(ticket.matches('100000002',build,'source'),false)
  assert.equal(ticket.matches('100000001',build,'edited'),false)
  const parsed={supported:true,mode:'native_flow',program:{schemaVersion:'native-flow-v1',source:'source',root:[],blocks:{宏:{macro:'neuvillette_charge_v1',declaration:{options:{atomic:'true'}},nodes:[]}}}}
  applyNativeImport(build,parsed,'00-水.txt')
  assert.equal(build.rotation,'keep this gcsim reference')
  assert.equal(build.nativeRotation.macroMapping,'neuvillette_charge_v1')
  assert.equal(build.nativeRotation.source,'source')
  assert.equal(ticket.matches('100000001',build,'source'),false)
})
