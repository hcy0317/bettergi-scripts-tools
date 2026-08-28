<script setup>
import {computed, ref, watch} from 'vue'
import {Check} from '@element-plus/icons-vue'
import {artifactBuildPayload, artifactCharacterLabel} from '@/features/artifact-analysis/buildModel.js'
import ArtifactBuildRecipeEditor from './ArtifactBuildRecipeEditor.vue'
import ArtifactBuildStatEditor from './ArtifactBuildStatEditor.vue'

const props = defineProps({
  open: {type: Boolean, default: false}, build: {type: Object, default: null},
  setOptions: {type: Array, default: () => []}, characterOptions: {type: Array, default: () => []},
})
const emit = defineEmits(['update:open', 'save'])
const form = ref({})
const createBuild = () => artifactBuildPayload(props.build || {
  id: `custom-${Date.now()}`, name: '', characterKey: '', sets: [{setKey: '', pieces: 4}],
  alternativeSetRecipes: [],
  mainStatsBySlot: {flower: ['hp'], plume: ['atk'], sands: [], goblet: [], circlet: []},
  substatWeights: {critRate_: 1, critDMG_: 1},
  analysisEnabled: true, nativeSyncEnabled: true, sourceVersion: 'custom',
})
watch(() => [props.open, props.build], () => { form.value = createBuild() }, {immediate: true, deep: true})

const isCustom = computed(() => !form.value.sourceVersion?.startsWith('genshin-artifact-analyzer@'))
const title = computed(() => form.value.name ? `编辑“${form.value.name}”` : '新建配装')
const recipesValid = computed(() => [form.value.sets, ...(form.value.alternativeSetRecipes || [])]
  .every(recipe => recipe?.length && recipe.length <= 2
    && new Set(recipe.map(rule => rule.setKey)).size === recipe.length
    && recipe.every(rule => rule.setKey && rule.pieces === (recipe.length === 1 ? 4 : 2))))
const canSave = computed(() => Boolean(
  form.value.name?.trim() && form.value.characterKey?.trim() && recipesValid.value
  && ['sands', 'goblet', 'circlet'].every(slot => form.value.mainStatsBySlot?.[slot]?.length)
))
const close = () => emit('update:open', false)
const save = () => { if (canSave.value) emit('save', artifactBuildPayload(form.value)) }
</script>

<template>
  <el-dialog :model-value="open" class="build-editor-dialog" width="min(1040px, 96vw)" :title="title" append-to-body destroy-on-close @close="close">
    <el-form v-if="form.mainStatsBySlot" label-position="top" class="build-form" @submit.prevent="save">
      <section class="editor-section basic-section">
        <header><div><h3>基本信息</h3><p>角色与配装名称</p></div><el-tag :type="isCustom ? 'info' : 'success'" effect="plain">{{ isCustom ? '自定义' : '上游预设' }}</el-tag></header>
        <div class="basic-grid">
          <el-form-item label="名称"><el-input v-model="form.name" maxlength="40" show-word-limit/></el-form-item>
          <el-form-item label="角色"><el-select v-model="form.characterKey" filterable allow-create placeholder="选择角色"><el-option v-for="item in characterOptions" :key="item" :label="artifactCharacterLabel(item)" :value="item"/></el-select></el-form-item>
        </div>
        <div class="status-row"><el-checkbox v-model="form.analysisEnabled">参与圣遗物分析</el-checkbox><el-checkbox v-model="form.nativeSyncEnabled">参与原神方案生成</el-checkbox></div>
      </section>

      <ArtifactBuildRecipeEditor v-model:sets="form.sets" v-model:alternatives="form.alternativeSetRecipes" :set-options="setOptions"/>
      <ArtifactBuildStatEditor v-model:main-stats="form.mainStatsBySlot" v-model:substats="form.substatWeights"/>
    </el-form>
    <template #footer><div class="dialog-footer"><span v-if="!canSave">请补全名称、角色、套装和沙杯冠主属性</span><div><el-button @click="close">取消</el-button><el-button type="primary" :icon="Check" :disabled="!canSave" @click="save">保存</el-button></div></div></template>
  </el-dialog>
</template>

<style scoped>
.build-form{display:grid;gap:20px}.editor-section{padding-bottom:18px;border-bottom:1px solid var(--el-border-color-lighter)}.editor-section:last-child{border-bottom:0}.editor-section>header,.status-row,.dialog-footer{display:flex;align-items:center}.editor-section>header{justify-content:space-between;gap:16px;margin-bottom:12px}.editor-section h3{margin:0;font-size:16px}.editor-section p{margin:3px 0 0;color:var(--el-text-color-secondary);font-size:12px}.basic-grid{display:grid;grid-template-columns:1fr 1fr;gap:14px}.basic-grid .el-select{width:100%}.status-row{gap:20px}.dialog-footer{justify-content:space-between;gap:16px}.dialog-footer>span{color:var(--el-color-danger);font-size:12px}.dialog-footer>div{display:flex;gap:8px;margin-left:auto}
:global(.build-editor-dialog){display:flex;flex-direction:column;height:min(900px,90dvh);margin:5dvh auto 0;overflow:hidden}:global(.build-editor-dialog .el-dialog__header),:global(.build-editor-dialog .el-dialog__footer){flex:0 0 auto}:global(.build-editor-dialog .el-dialog__body){flex:1;min-height:0;max-height:none;overflow-y:auto;padding-block:14px}
@media(max-width:640px){.basic-grid{grid-template-columns:1fr}.status-row{align-items:flex-start;flex-direction:column;gap:4px}.dialog-footer{align-items:flex-end;flex-direction:column}:global(.build-editor-dialog){width:100%!important;height:100dvh;margin:0;border-radius:0}}
</style>
