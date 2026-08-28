<script setup>
import {computed, ref, watch} from 'vue'
import {Refresh, VideoPlay} from '@element-plus/icons-vue'
import {ElMessage, ElMessageBox} from 'element-plus'
import {getArtifactJob, previewArtifactNativeSync, startArtifactJob} from '@api/artifact/artifactAnalysis.js'
import {artifactHostHasAcceptedJob, validateArtifactLaunch, waitForArtifactHostClaim} from '@/features/artifact-analysis/model.js'
import {
  artifactNativeSyncStatusMeta, artifactSetLabel, artifactSlotLabel, artifactStatLabel,
  artifactTranslationModeLabel,
} from '@/features/artifact-analysis/buildModel.js'
import ArtifactLaunchDialog from './ArtifactLaunchDialog.vue'

const props = defineProps({uid: {type: String, default: ''}})
const capacity = ref(100)
const preview = ref(null)
const loading = ref(false)
const starting = ref(false)
const launchDialogOpen = ref(false)
const pendingLaunch = ref(null)
const previewMeta = computed(() => artifactNativeSyncStatusMeta(preview.value?.status))
const statListLabel = values => [...(values || [])].map(artifactStatLabel).join(' / ') || '无'
const check = async () => {
  loading.value = true
  try { preview.value = await previewArtifactNativeSync(props.uid.trim(), capacity.value) }
  catch { preview.value = null; ElMessage.error('预检失败，请确认服务已启动后重试') }
  finally { loading.value = false }
}
const rebuild = async () => {
  if (!props.uid.trim()) { ElMessage.warning('请选择 UID'); return }
  try { await ElMessageBox.confirm('将保存目标方案和阶段日志后删除全部原神默认、推荐和旧自定义方案。删除后执行不再响应普通取消，并可用目标方案继续完成前向恢复；写入规则是该 UID 已启用配装的保守套装并集。', '完整重建原神方案', {confirmButtonText:'接受并重建',cancelButtonText:'取消',type:'warning'}) } catch { return }
  starting.value = true
  try {
    const response = await startArtifactJob(
      props.uid.trim(), 'REBUILD_NATIVE_PLANS', capacity.value, true,
      preview.value.planDigest)
    if (!validateArtifactLaunch(response.launch, 'REBUILD_NATIVE_PLANS')) throw new Error('服务端未返回有效启动请求')
    pendingLaunch.value = response.launch
    const claimed = await waitForArtifactHostClaim(response.job.id, getArtifactJob)
    if (artifactHostHasAcceptedJob(claimed)) {
      if (claimed.status === 'FAILED') ElMessage.error('BetterGI 已接收重建任务，但执行失败')
      else ElMessage.success('BetterGI 已接收重建任务')
    } else launchDialogOpen.value = true
  } finally { starting.value = false }
}
watch([capacity, () => props.uid], () => { preview.value = null })
</script>

<template>
  <section>
    <header class="toolbar"><div><h2>原神方案同步</h2><p>完整替换，不与原神默认配装共存。</p></div><div class="commands"><label class="capacity"><span>方案容量</span><el-input-number v-model="capacity" :min="1" :max="999"/></label><el-button :icon="Refresh" :loading="loading" @click="check">预检</el-button><el-button type="primary" :icon="VideoPlay" :loading="starting" :disabled="!uid || preview?.status !== 'READY'" @click="rebuild">开始重建</el-button></div></header>
    <el-empty v-if="!preview" description="先执行容量与表达能力预检"/>
    <template v-else>
      <el-alert :title="previewMeta.title" :description="previewMeta.description" :type="previewMeta.type" :closable="false" show-icon/>
      <div class="metrics"><div><span>来源配装</span><strong>{{ preview.sourceBuildCount }}</strong></div><div><span>套装与部位规则</span><strong>{{ preview.plans.length }}</strong></div><div><span>转换方式</span><strong>{{ artifactTranslationModeLabel(preview.translationMode) }}</strong></div><div><span>替换范围</span><strong>{{ preview.replaceAll ? '全部方案' : '不允许替换' }}</strong></div><div><span>操作前证据</span><strong>{{ preview.requiresPreMutationEvidence ? '必须保存' : '不要求' }}</strong></div></div>
      <el-table v-if="preview.plans.length" :data="preview.plans" height="360"><el-table-column label="套装" min-width="230"><template #default="{row}">{{ artifactSetLabel(row.setKey) }}</template></el-table-column><el-table-column label="部位" width="110"><template #default="{row}">{{ artifactSlotLabel(row.slotKey) }}</template></el-table-column><el-table-column label="主词条" min-width="220"><template #default="{row}">{{ statListLabel(row.mainStats) }}</template></el-table-column><el-table-column label="副词条" min-width="260"><template #default="{row}">{{ statListLabel(row.substats) }}</template></el-table-column></el-table>
    </template>
    <ArtifactLaunchDialog v-model:open="launchDialogOpen" :launch="pendingLaunch" task-label="重建原神方案" @launched="ElMessage.success('正在连接 BetterGI')"/>
  </section>
</template>

<style scoped>
.toolbar,.commands,.metrics,.capacity{display:flex;align-items:center}.toolbar{justify-content:space-between;gap:14px;margin-bottom:14px}.toolbar h2{margin:0;font-size:18px}.toolbar p{margin:4px 0 0;color:var(--el-text-color-secondary);font-size:13px}.commands{gap:8px}.capacity{gap:7px;color:var(--el-text-color-secondary);font-size:12px;white-space:nowrap}.metrics{gap:24px;flex-wrap:wrap;padding:18px 0}.metrics>div{display:grid;gap:3px;min-width:130px}.metrics span{color:var(--el-text-color-secondary);font-size:12px}.metrics strong{font-size:18px}@media(max-width:720px){.toolbar{align-items:stretch;flex-direction:column}.commands{justify-content:flex-end;flex-wrap:wrap}}
</style>
