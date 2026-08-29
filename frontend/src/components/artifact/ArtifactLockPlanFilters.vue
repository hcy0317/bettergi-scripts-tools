<script setup>
import {artifactSetLabel, artifactSlotLabel} from '@/features/artifact-analysis/buildModel.js'

defineProps({options: {type: Object, required: true}})
const view = defineModel('view', {default: 'all'})
const setKey = defineModel('setKey', {default: 'all'})
const slotKey = defineModel('slotKey', {default: 'all'})
const levelRange = defineModel('levelRange', {default: () => [0, 20]})
const sort = defineModel('sort', {default: 'potential-desc'})

const reset = () => {
  view.value = 'all'
  setKey.value = 'all'
  slotKey.value = 'all'
  levelRange.value = [0, 20]
  sort.value = 'potential-desc'
}
</script>

<template>
  <section class="filters" aria-label="锁定方案筛选">
    <el-radio-group v-model="view" size="small" aria-label="推荐分类">
      <el-radio-button value="all">全部</el-radio-button>
      <el-radio-button value="recommended">推荐</el-radio-button>
      <el-radio-button value="other">其他</el-radio-button>
    </el-radio-group>
    <el-select v-model="setKey" filterable aria-label="筛选套装">
      <el-option label="全部套装" value="all"/>
      <el-option v-for="key in options.setKeys" :key="key" :label="artifactSetLabel(key)" :value="key"/>
    </el-select>
    <el-select v-model="slotKey" aria-label="筛选位置">
      <el-option label="全部位置" value="all"/>
      <el-option v-for="key in options.slotKeys" :key="key" :label="artifactSlotLabel(key)" :value="key"/>
    </el-select>
    <div class="level-range" aria-label="筛选圣遗物等级范围">
      <span>+{{ levelRange[0] }}</span>
      <el-slider v-model="levelRange" range :min="0" :max="20" :step="1" aria-label="圣遗物等级范围"/>
      <span>+{{ levelRange[1] }}</span>
    </div>
    <el-select v-model="sort" aria-label="排序方式">
      <el-option label="潜力从高到低" value="potential-desc"/>
      <el-option label="潜力从低到高" value="potential-asc"/>
      <el-option label="当前分数从高到低" value="score-desc"/>
      <el-option label="当前分数从低到高" value="score-asc"/>
      <el-option label="等级从高到低" value="level-desc"/>
      <el-option label="等级从低到高" value="level-asc"/>
      <el-option label="扫描序号从前到后" value="index-asc"/>
      <el-option label="扫描序号从后到前" value="index-desc"/>
    </el-select>
    <el-button text @click="reset">重置筛选</el-button>
  </section>
</template>

<style scoped>
.filters {
  display: grid;
  grid-template-columns: auto minmax(170px, 1.3fr) minmax(130px, .8fr) minmax(120px, .7fr) minmax(180px, 1fr) auto;
  align-items: center;
  gap: 10px;
  margin: 4px 0 14px;
  padding: 12px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: var(--el-border-radius-base);
  background: var(--el-fill-color-extra-light);
}
.level-range { display: grid; grid-template-columns: 30px minmax(110px, 1fr) 30px; align-items: center; gap: 8px; font-size: 12px; font-variant-numeric: tabular-nums; text-align: center; }
@media (max-width: 980px) { .filters { grid-template-columns: repeat(2, minmax(0, 1fr)); } }
@media (max-width: 560px) { .filters { grid-template-columns: 1fr; } }
</style>
