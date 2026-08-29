<script setup>
import {computed, ref} from 'vue'
import {Delete, Plus} from '@element-plus/icons-vue'
import {
  artifactMainStatsBySlot, artifactSlotOptions, artifactStatOptions, artifactSubstatOptions,
} from '@/features/artifact-analysis/buildCatalog.js'
import {artifactStatLabel} from '@/features/artifact-analysis/buildModel.js'

const mainStats = defineModel('mainStats', {type: Object, default: () => ({})})
const substats = defineModel('substats', {type: Object, default: () => ({})})
const newSubstat = ref('')
const configurableSlots = artifactSlotOptions.filter(([key]) => ['sands', 'goblet', 'circlet'].includes(key))
const configuredSubstats = computed(() => Object.entries(substats.value || {})
  .sort(([left], [right]) => artifactStatLabel(left).localeCompare(artifactStatLabel(right))))
const availableSubstats = computed(() => artifactSubstatOptions.filter(([key]) => !(key in (substats.value || {}))))
const optionsForSlot = slot => artifactStatOptions.filter(([key]) => artifactMainStatsBySlot[slot].includes(key))
const addSubstat = () => {
  if (!newSubstat.value) return
  substats.value = {...substats.value, [newSubstat.value]: 1}
  newSubstat.value = ''
}
const removeSubstat = key => {
  const next = {...substats.value}
  delete next[key]
  substats.value = next
}
</script>

<template>
  <section class="editor-section">
    <header><div><h3>主属性</h3><p>所有套装方案共用；同一部位的多个属性均为并列候选</p></div></header>
    <div class="fixed-slots">
      <div><span>生之花</span><strong>生命值</strong></div><div><span>死之羽</span><strong>攻击力</strong></div>
    </div>
    <div class="main-stat-grid">
      <el-form-item v-for="[slot, label] in configurableSlots" :key="slot" :label="label">
        <el-select v-model="mainStats[slot]" multiple filterable collapse-tags :max-collapse-tags="3" placeholder="选择主属性">
          <el-option v-for="[key, text] in optionsForSlot(slot)" :key="key" :label="text" :value="key"/>
        </el-select>
        <div v-if="mainStats[slot]?.length" class="stat-preview"><span v-for="(stat, index) in mainStats[slot]" :key="`${index}-${stat}`">{{ artifactStatLabel(stat) }}</span></div>
      </el-form-item>
    </div>
  </section>

  <section class="editor-section substat-section">
    <header><div><h3>副属性</h3><p>相对重要性 0 到 1，0 表示不参与评分</p></div><div class="add-substat"><el-select v-model="newSubstat" placeholder="选择副属性"><el-option v-for="[key, label] in availableSubstats" :key="key" :label="label" :value="key"/></el-select><el-button :icon="Plus" :disabled="!newSubstat" @click="addSubstat">添加</el-button></div></header>
    <el-empty v-if="!configuredSubstats.length" :image-size="64" description="未设置副属性"/>
    <div v-else class="weight-list">
      <div v-for="[key] in configuredSubstats" :key="key" class="weight-row">
        <strong>{{ artifactStatLabel(key) }}</strong>
        <el-slider v-model="substats[key]" :min="0" :max="1" :step="0.1" show-input :show-input-controls="false"/>
        <el-tooltip content="移除副属性"><el-button circle text type="danger" :icon="Delete" :aria-label="`移除${artifactStatLabel(key)}`" @click="removeSubstat(key)"/></el-tooltip>
      </div>
    </div>
  </section>
</template>

<style scoped>
.editor-section>header,.add-substat,.weight-row{display:flex;align-items:center}.editor-section>header{justify-content:space-between;gap:16px;margin-bottom:12px}.editor-section h3{margin:0;font-size:16px}.editor-section p{margin:3px 0 0;color:var(--el-text-color-secondary);font-size:12px}.fixed-slots,.main-stat-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:12px}.fixed-slots{margin-bottom:12px}.fixed-slots>div{display:flex;justify-content:space-between;padding:10px 12px;border:1px solid var(--el-border-color-lighter);border-radius:6px;background:var(--el-fill-color-lighter)}.fixed-slots span{color:var(--el-text-color-secondary);font-size:12px}.main-stat-grid{grid-template-columns:repeat(3,minmax(0,1fr))}.main-stat-grid .el-select{width:100%}.stat-preview{display:flex;flex-wrap:wrap;gap:4px;margin-top:7px}.stat-preview span{display:inline-flex;align-items:center;padding:2px 6px;border:1px solid #cbd5e1;border-radius:4px;background:#f8fafc;color:#334155;font-size:11px}.substat-section{margin-top:6px}.add-substat{gap:8px}.add-substat .el-select{width:180px}.weight-list{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:2px 24px}.weight-row{display:grid;grid-template-columns:92px minmax(180px,1fr) 34px;gap:10px;min-height:58px;border-bottom:1px solid var(--el-border-color-lighter)}.weight-row strong{font-size:13px}
@media(max-width:760px){.main-stat-grid,.weight-list{grid-template-columns:1fr}.editor-section>header{align-items:flex-start;flex-direction:column}.add-substat{width:100%}.add-substat .el-select{flex:1;width:auto}}
@media(max-width:480px){.fixed-slots{grid-template-columns:1fr}.weight-row{grid-template-columns:78px minmax(0,1fr) 34px}.weight-row :deep(.el-slider__input){width:64px}}
</style>
