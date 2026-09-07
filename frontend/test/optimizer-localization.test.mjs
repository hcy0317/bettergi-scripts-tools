import test from 'node:test'
import assert from 'node:assert/strict'
import {readFile} from 'node:fs/promises'
import {characterLabel,weaponLabel,setLabel,statLabel,profileLabel,buildLabel} from '../src/features/artifact-optimizer/localization.js'
const catalog={localization:{character_names:{raidenshogun:'雷电将军'},weapon_names:{engulfinglightning:'薙草之稻光'},artifact_names:{emblemofseveredfate:'绝缘之旗印'}}}
test('display names use upstream Chinese only while engine keys stay stable',()=>{
  assert.equal(characterLabel(catalog,'raidenshogun'),'雷电将军')
  assert.equal(weaponLabel(catalog,'engulfinglightning'),'薙草之稻光')
  assert.equal(setLabel(catalog,'EmblemOfSeveredFate'),'绝缘之旗印')
  assert.equal(profileLabel(catalog,{key:'raidenshogun',name:'raidenshogun'}),'雷电将军')
  assert.equal(statLabel('atk%'),'攻击力百分比')
  const oldBuild={name:'新的配队 Build'};assert.equal(buildLabel(oldBuild),'新的配队方案');assert.equal(oldBuild.name,'新的配队 Build')
  assert.equal(catalog.localization.character_names.raidenshogun,'雷电将军')
})
test('optimizer entry belongs to a production-visible home group',async()=>{
  const routes=await readFile(new URL('../src/router/router.js',import.meta.url),'utf8')
  const optimizer=routes.slice(routes.indexOf("path: '/Artifacts/Optimizer'"),routes.indexOf("path: '/Artifacts/Analysis'"))
  assert.match(optimizer,/group: 'JS扩展功能'/)
})
