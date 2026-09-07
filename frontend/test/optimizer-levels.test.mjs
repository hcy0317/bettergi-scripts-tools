import test from 'node:test'
import assert from 'node:assert/strict'
import {levelCapOptions,ascensionDescription} from '../src/features/artifact-optimizer/model.js'
test('level cap choices are tens while promotion follows the actual engine table',()=>{
  assert.deepEqual(levelCapOptions,[10,20,30,40,50,60,70,80,90])
  const catalog={characters:[{key:'amber',stats:{promo_data:[{max_level:20},{max_level:40},{max_level:50}]}}]}
  assert.equal(ascensionDescription(catalog,'amber',30),'未突破')
  assert.equal(ascensionDescription(catalog,'amber',40),'已突破 1 次')
})
