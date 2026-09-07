import test from 'node:test'
import assert from 'node:assert/strict'
import {reactive} from 'vue'
import {newCharacter, newBuild, mergeEnkaPreview, selectedScenarioIds} from '../src/features/artifact-optimizer/model.js'

test('character entry keeps manual edits and imports Enka only after explicit selection',()=>{
  const character=newCharacter('amber','安柏')
  character.level=85; character.tags=['主力']; character.builds=[{id:'team',weight:1}]
  const merged=mergeEnkaPreview([character],[{key:'amber',level:90,weapon:'huntersbow'}],[])
  assert.equal(merged[0].level,85)
  const accepted=mergeEnkaPreview([character],[{key:'amber',level:90,weapon:'huntersbow'}],['amber'])
  assert.equal(accepted[0].level,90)
  assert.deepEqual(accepted[0].tags,['主力'])
  assert.deepEqual(accepted[0].builds,[{id:'team',weight:1}])
})

test('Enka preview accepts Vue reactive state without mutating the editor',()=>{
  const current=reactive([newCharacter('amber')])
  const imported=mergeEnkaPreview(current,[{key:'amber',level:80}],['amber'])
  assert.equal(imported[0].level,80)
  assert.equal(current[0].level,90)
})
