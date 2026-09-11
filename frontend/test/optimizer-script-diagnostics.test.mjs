import test from 'node:test'
import assert from 'node:assert/strict'
import fs from 'node:fs'
import {diagnoseScript,validateBuildScripts} from '../src/features/artifact-optimizer/script-diagnostics.js'
import {validateOptimization,validateRotation} from '../src/features/artifact-optimizer/validation.js'
const fixtures=JSON.parse(fs.readFileSync(new URL('../../bgi-tools/src/test/resources/artifact-optimizer/script-diagnostics.json',import.meta.url),'utf8'))
for(const fixture of fixtures)test(fixture.name,()=>{
  const issues=diagnoseScript(fixture.source,'team','rotation',fixture.weapons,fixture.aliases)
  assert.deepEqual(issues.map(({startOffset,endOffset,line,column,character})=>({startOffset,endOffset,line,column,character})),fixture.expected)
})
test('rotation admission checks its own build and preserves unrelated drafts',()=>{
  const profile={key:'amber',level:90,maxLevel:90,constellation:0,talents:[6,6,6],weapon:'huntersbow',weaponLevel:90,weaponMaxLevel:90,refinement:1,builds:[{id:'chosen'},{id:'other'}]}
  const workspace={characters:[profile],builds:[
    {id:'chosen',duration:30,members:[{character:'amber'}],rotation:'amber skill;'},
    {id:'other',duration:30,members:[{character:'amber'}],rotation:'raiden burst;'},
  ]}
  const before=JSON.stringify(workspace),snapshot={artifacts:[{scanIndex:0}]}
  assert.ok(validateOptimization(workspace,['amber'],snapshot).some(issue=>issue.buildId==='other'))
  assert.deepEqual(validateRotation(workspace,'chosen',['amber'],snapshot),[])
  workspace.builds[0].rotation='while !.amber.mods.favonius-cd {amber attack;}'
  assert.ok(validateRotation(workspace,'chosen',['amber'],snapshot).some(issue=>issue.field==='rotation'))
  workspace.builds[0].rotation='amber skill;'
  assert.equal(JSON.stringify(workspace),before)
})
test('editing the real weapon or disabling a prelude clears only the resolved diagnostic',()=>{
  const characters=[{key:'furina',weapon:'fleuvecendreferryman'}]
  const build={id:'team',members:[{character:'furina'}],rotation:'furina skill;',scriptPrelude:'while !.furina.mods.favonius-cd {furina attack;}'}
  assert.equal(validateBuildScripts(build,characters).length,1)
  build.scriptPreludeEnabled=false
  assert.equal(validateBuildScripts(build,characters).length,0)
  build.scriptPreludeEnabled=true;build.members[0].profile={weapon:'favoniussword'}
  assert.equal(validateBuildScripts(build,characters).length,0)
  build.rotation='active kaeya;'
  assert.equal(validateBuildScripts(build,characters)[0].character,'kaeya')
  build.nativeRotation={enabled:true,source:'native source'}
  assert.equal(validateBuildScripts(build,characters).length,0)
})
