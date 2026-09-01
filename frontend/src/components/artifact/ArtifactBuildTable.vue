<script setup>
import {CopyDocument, Delete, Edit} from '@element-plus/icons-vue'
import {
  artifactAlternativeTone, artifactCharacterLabel,
} from '@/features/artifact-analysis/buildModel.js'
import ArtifactCharacterAvatar from './ArtifactCharacterAvatar.vue'

const props = defineProps({
  rows: {type: Array, default: () => []},
  pendingIds: {type: Set, default: () => new Set()},
  pendingFields: {type: Set, default: () => new Set()},
})
const emit = defineEmits(['toggle', 'edit', 'clone', 'remove'])
const recipeTone = index => `tone-${artifactAlternativeTone(index)}`
const isPending = (row, field) =>
  props.pendingIds.has(row.id) || props.pendingFields.has(`${row.id}:${field}`)
</script>

<template>
  <el-table :data="rows" row-key="id" table-layout="fixed" class="build-table">
    <el-table-column label="状态" width="170" fixed="left"><template #default="{row}"><div class="status-cell">
      <label><span>分析</span><el-switch size="small" :loading="isPending(row, 'analysisEnabled')" :model-value="row.analysisEnabled" @change="value => emit('toggle', row, 'analysisEnabled', value)"/></label>
      <label><span>锁定</span><el-switch size="small" :loading="isPending(row, 'nativeSyncEnabled')" :model-value="row.nativeSyncEnabled" @change="value => emit('toggle', row, 'nativeSyncEnabled', value)"/></label>
      <label><span>速装</span><el-select size="small" :loading="isPending(row, 'quickEquipPresetIndex')" :model-value="row.quickEquipPresetIndex" aria-label="快速装备方案" @change="value => emit('toggle', row, 'quickEquipPresetIndex', value)"><el-option label="关闭" :value="0"/><el-option label="方案 1" :value="1"/><el-option label="方案 2" :value="2"/></el-select></label>
    </div></template></el-table-column>
    <el-table-column label="角色 / 配装" width="220" fixed="left"><template #default="{row}"><div class="identity-cell">
      <ArtifactCharacterAvatar :character-key="row.characterKey"/>
      <div><strong>{{ artifactCharacterLabel(row.characterKey) }}</strong><span>{{ row.name }}</span><el-tag size="small" effect="plain" :type="row.summary.sourceKind === 'upstream' ? 'success' : 'info'">{{ row.summary.sourceKind === 'upstream' ? '预设' : '自定义' }}</el-tag></div>
    </div></template></el-table-column>
    <el-table-column label="套装配方" min-width="255"><template #default="{row}"><div class="recipe-cell">
      <p class="tone-primary"><b>主选</b><span>{{ row.summary.primaryRecipe }}</span></p>
      <p v-for="(recipe, index) in row.summary.alternativeRecipes" :key="`${index}-${recipe}`" :class="recipeTone(index + 1)"><b>备选 {{ index + 1 }}</b><span>{{ recipe }}</span></p>
    </div></template></el-table-column>
    <el-table-column v-for="slot in ['sands', 'goblet', 'circlet']" :key="slot" :label="{sands:'时之沙',goblet:'空之杯',circlet:'理之冠'}[slot]" min-width="166"><template #default="{row}"><div class="stat-stack"><span v-for="(stat, index) in row.summary.mainStats[slot]" :key="`${index}-${stat}`" class="stat-option">{{ stat }}</span><small v-if="!row.summary.mainStats[slot].length">未设置</small></div></template></el-table-column>
    <el-table-column label="副词条权重" min-width="240"><template #default="{row}"><div class="weight-list">
      <div v-for="stat in row.summary.topSubstats.slice(0, 5)" :key="stat.key"><span>{{ stat.label }}</span><i><b :style="{width: `${stat.weight * 100}%`}"/></i><em>{{ stat.weight.toFixed(1) }}</em></div>
    </div></template></el-table-column>
    <el-table-column label="操作" width="126" fixed="right" align="center"><template #default="{row}"><div class="row-actions">
      <el-tooltip content="编辑"><el-button circle text :icon="Edit" :aria-label="`编辑 ${row.name}`" @click="emit('edit', row)"/></el-tooltip>
      <el-tooltip content="克隆为自定义"><el-button circle text :icon="CopyDocument" :aria-label="`克隆 ${row.name}`" @click="emit('clone', row)"/></el-tooltip>
      <el-tooltip v-if="row.summary.sourceKind !== 'upstream'" content="删除"><el-button circle text type="danger" :icon="Delete" :aria-label="`删除 ${row.name}`" @click="emit('remove', row)"/></el-tooltip>
    </div></template></el-table-column>
  </el-table>
</template>

<style scoped>
.build-table{min-width:1460px}.status-cell{display:grid;gap:5px}.status-cell label{display:flex;align-items:center;justify-content:space-between;gap:8px;font-size:12px}.status-cell .el-select{width:96px}.identity-cell,.row-actions{display:flex;align-items:center}.identity-cell{gap:10px}.identity-cell>div{display:grid;grid-template-columns:auto auto;align-items:center;gap:2px 6px;min-width:0}.identity-cell strong{grid-column:1/-1;overflow:hidden;color:var(--el-text-color-primary);font-size:15px;text-overflow:ellipsis;white-space:nowrap}.identity-cell span{overflow:hidden;color:var(--el-text-color-secondary);font-size:12px;text-overflow:ellipsis;white-space:nowrap}.recipe-cell{display:grid;gap:5px}.recipe-cell p{display:flex;align-items:flex-start;gap:7px;margin:0;padding-left:7px;border-left:3px solid;line-height:1.45}.recipe-cell p b{flex:0 0 48px;font-size:11px}.recipe-cell p span{overflow:hidden;text-overflow:ellipsis}.recipe-cell small,.stat-stack small{color:var(--el-text-color-placeholder)}.stat-stack{display:flex;flex-wrap:wrap;gap:4px}.stat-stack>span{display:inline-flex;align-items:center;padding:2px 6px;border:1px solid;border-radius:4px;font-size:12px}.stat-option{border-color:#cbd5e1!important;background:#f8fafc;color:#334155}.tone-primary{border-color:#93c5fd!important;background:#eff6ff;color:#1d4ed8}.tone-amber{border-color:#fcd34d!important;background:#fffbeb;color:#b45309}.tone-teal{border-color:#5eead4!important;background:#f0fdfa;color:#0f766e}.tone-violet{border-color:#c4b5fd!important;background:#f5f3ff;color:#7c3aed}.tone-rose{border-color:#fda4af!important;background:#fff1f2;color:#be123c}.weight-list{display:grid;gap:5px}.weight-list>div{display:grid;grid-template-columns:88px 1fr 28px;align-items:center;gap:6px;font-size:11px}.weight-list span{overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.weight-list i{height:4px;overflow:hidden;background:var(--el-fill-color);border-radius:2px}.weight-list b{display:block;height:100%;background:var(--el-color-success)}.weight-list em{color:var(--el-text-color-secondary);font-style:normal;text-align:right}.row-actions{justify-content:center}
</style>
