import test from 'node:test'
import assert from 'node:assert/strict'
import {applyCommunityPreview,describeCommunitySettings} from '../src/features/artifact-optimizer/community.js'
test('confirmed import changes only selected script and scene fields, not personal conditions or membership',()=>{
  const member={character:'amber',kind:'real_fixed',profile:{level:80,weapon:'huntersbow'}}
  const build={name:'我的方案',weight:3,members:[member],rounds:[{id:'old',startFrame:0,endFrame:100}],buffs:[{id:'own'}]}
  const preview={importable:true,rotation:'active amber; amber attack;',scriptPrelude:'let note=1;',settings:{stopMode:'target_or_script',targets:[{level:90,hp:99999}],members:[{character:'kaeya'}]},sourceUrl:'https://gcsim.app/db/known'}
  applyCommunityPreview(build,preview)
  assert.equal(build.members[0],member);assert.equal(member.profile.level,80);assert.equal(build.name,'我的方案');assert.equal(build.weight,3);assert.equal(build.buffs[0].id,'own')
  assert.equal(build.roundPolicy.mode,'auto');assert.equal(build.legacyRounds[0].endFrame,100);assert.deepEqual(build.rounds,[])
  preview.settings.targets[0].hp=1;assert.equal(build.targets[0].hp,99999)
  assert.match(describeCommunitySettings({stopMode:'target_or_script',energy:{enabled:true,mode:'every',start:480,end:720,amount:1}})[1].value,/8 至 12 秒/)
  assert.throws(()=>applyCommunityPreview(build,{importable:false}),/不能等价/)
})
