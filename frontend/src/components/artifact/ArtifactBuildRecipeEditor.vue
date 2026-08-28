<script setup>
import {Delete, Plus} from '@element-plus/icons-vue'
import {
  artifactAlternativeTone, artifactEquivalentSetKeys, artifactSetLabel, normalizeArtifactRecipe,
} from '@/features/artifact-analysis/buildModel.js'

const sets = defineModel('sets', {type: Array, default: () => []})
const alternatives = defineModel('alternatives', {type: Array, default: () => []})
const props = defineProps({setOptions: {type: Array, default: () => []}})

const normalizeInPlace = recipe => recipe.splice(0, recipe.length, ...normalizeArtifactRecipe(recipe))
const addRule = recipe => {
  if (recipe.length >= 2) return
  recipe.push({setKey: '', pieces: 2})
  normalizeInPlace(recipe)
}
const removeRule = (recipe, index) => {
  if (recipe.length <= 1) return
  recipe.splice(index, 1)
  normalizeInPlace(recipe)
}
const addAlternative = () => alternatives.value.push([{setKey: '', pieces: 4}])
const removeAlternative = index => alternatives.value.splice(index, 1)
const recipeTone = index => index === 0 ? 'tone-primary' : `tone-${artifactAlternativeTone(index)}`
const optionsForRule = (recipe, index) => props.setOptions.filter(option =>
  option === recipe[index].setKey || !recipe.some((rule, ruleIndex) => ruleIndex !== index && rule.setKey === option))
const equivalentLabel = rule => {
  if (rule.pieces !== 2) return ''
  const equivalents = artifactEquivalentSetKeys(rule.setKey).filter(key => key !== rule.setKey)
  if (!equivalents.length) return ''
  return equivalents.length <= 3
    ? `可替换：${equivalents.map(artifactSetLabel).join('、')}`
    : '可替换为任意同效果套装'
}
</script>

<template>
  <section class="editor-section">
    <header><div><h3>套装配方</h3><p>主方案与可接受的备选组合</p></div><el-button :icon="Plus" @click="addAlternative">添加备选</el-button></header>
    <div v-for="(recipe, recipeIndex) in [sets, ...alternatives]" :key="recipeIndex" class="recipe-block" :class="recipeTone(recipeIndex)">
      <div class="recipe-heading">
        <strong>{{ recipeIndex === 0 ? '主选方案' : `备选方案 ${recipeIndex}` }}</strong>
        <el-tooltip v-if="recipeIndex > 0" content="删除备选方案"><el-button circle text type="danger" :icon="Delete" :aria-label="`删除备选方案 ${recipeIndex}`" @click="removeAlternative(recipeIndex - 1)"/></el-tooltip>
      </div>
      <div v-for="(rule, index) in recipe" :key="index" class="rule-row">
        <el-select v-model="rule.setKey" filterable allow-create placeholder="选择套装">
          <el-option v-for="option in optionsForRule(recipe, index)" :key="option" :label="artifactSetLabel(option)" :value="option"/>
        </el-select>
        <el-tag effect="plain">{{ rule.pieces }} 件</el-tag>
        <el-tooltip content="删除套装"><el-button circle text type="danger" :icon="Delete" :aria-label="`删除套装 ${index + 1}`" :disabled="recipe.length === 1" @click="removeRule(recipe, index)"/></el-tooltip>
        <small v-if="equivalentLabel(rule)">{{ equivalentLabel(rule) }}</small>
      </div>
      <el-button text :icon="Plus" :disabled="recipe.length >= 2" @click="addRule(recipe)">添加套装</el-button>
    </div>
  </section>
</template>

<style scoped>
.editor-section>header,.recipe-heading,.rule-row{display:flex;align-items:center}.editor-section>header{justify-content:space-between;gap:16px;margin-bottom:12px}.editor-section h3{margin:0;font-size:16px}.editor-section p{margin:3px 0 0;color:var(--el-text-color-secondary);font-size:12px}.recipe-block{margin:8px 0;padding:10px 12px;border:1px solid;border-left-width:4px;border-radius:6px}.recipe-heading{justify-content:space-between;min-height:32px}.rule-row{display:grid;grid-template-columns:minmax(220px,1fr) 56px 34px;gap:10px;margin:8px 0}.rule-row .el-select{min-width:0}.rule-row small{grid-column:1/-1;color:var(--el-text-color-secondary);font-size:11px}.tone-primary{border-color:#93c5fd;background:#eff6ff}.tone-primary .recipe-heading{color:#1d4ed8}.tone-amber{border-color:#fcd34d;background:#fffbeb}.tone-amber .recipe-heading{color:#b45309}.tone-teal{border-color:#5eead4;background:#f0fdfa}.tone-teal .recipe-heading{color:#0f766e}.tone-violet{border-color:#c4b5fd;background:#f5f3ff}.tone-violet .recipe-heading{color:#7c3aed}.tone-rose{border-color:#fda4af;background:#fff1f2}.tone-rose .recipe-heading{color:#be123c}
@media(max-width:600px){.rule-row{grid-template-columns:minmax(0,1fr) 56px 34px}.editor-section>header{align-items:flex-start}}
</style>
