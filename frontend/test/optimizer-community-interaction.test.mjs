import test from 'node:test'
import assert from 'node:assert/strict'
import {readFileSync} from 'node:fs'
import {parse as parseSfc} from '@vue/compiler-sfc'
import {parse} from '@vue/compiler-dom'
import * as community from '../src/features/artifact-optimizer/community.js'

test('a replaced or closed community request cannot apply a late response',()=>{
  const requests=community.createCommunityRequestScope()
  const first=requests.begin(),second=requests.begin()
  assert.equal(first.isCurrent(),false)
  assert.equal(first.signal.aborted,true)
  assert.equal(second.isCurrent(),true)
  requests.cancel()
  assert.equal(second.isCurrent(),false)
  assert.equal(second.signal.aborted,true)
})

test('hidden unavailable import options cannot enable an empty confirmation',()=>{
  const preview={importable:true,rotation:'active amber;',settings:{}}
  assert.equal(community.hasCommunitySelection(preview,{script:false,settings:true,sampling:true}),false)
  assert.equal(community.hasCommunitySelection(preview,{script:true,settings:false,sampling:false}),true)
  assert.equal(community.hasCommunitySelection({...preview,validationSamples:100},{script:false,settings:false,sampling:true}),true)
})

test('community failures retain the controlled server explanation',()=>{
  assert.equal(community.communityErrorMessage({response:{data:{message:'社区服务限流，请稍后重试'}}},'失败'),'社区服务限流，请稍后重试')
  assert.equal(community.communityErrorMessage(new Error('Network Error'),'失败'),'社区连接失败，请稍后重试，或直接粘贴脚本')
})

test('contradictory include and exclude filters point to the conflicting character',()=>{
  assert.match(community.communityFilterIssue({include:['noelle'],exclude:['noelle']},()=> '诺艾尔'),/诺艾尔/)
  assert.equal(community.communityFilterIssue({include:['noelle'],exclude:['amber']}), '')
})

test('all optimizer searchable multiselects explicitly clear their query after selection',()=>{
  let checked=0
  for(const file of ['OptimizerCommunityPanel.vue','OptimizerBuildEditor.vue','OptimizerCharacterEditor.vue']){
    const {descriptor}=parseSfc(readFileSync(new URL(`../src/components/artifact-optimizer/${file}`,import.meta.url),'utf8'))
    const walk=node=>{
      if(node.type===1&&node.tag==='el-select'&&node.props.some(p=>p.type===6&&p.name==='multiple')&&node.props.some(p=>p.type===6&&p.name==='filterable')){
        checked++
        assert.ok(node.props.some(p=>p.type===7&&p.name==='bind'&&p.arg?.content==='reserve-keyword'&&p.exp?.content==='false'),`${file}: selected tags must not retain the search query`)
      }
      for(const child of node.children||[])walk(child)
    }
    walk(parse(descriptor.template.content))
  }
  assert.ok(checked>=6)
})
