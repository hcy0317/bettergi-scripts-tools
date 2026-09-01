<script setup>
import {computed, onBeforeUnmount, ref, watch} from 'vue'
import {Refresh, VideoPlay} from '@element-plus/icons-vue'
import {ElMessage, ElMessageBox} from 'element-plus'
import {getArtifactJob, previewArtifactNativeSync, startArtifactJob} from '@api/artifact/artifactAnalysis.js'
import {
  artifactHostHasAcceptedJob, validateArtifactLaunch,
  waitForArtifactHostClaim, waitForArtifactJobCompletion,
} from '@/features/artifact-analysis/model.js'
import {
  artifactCharacterLabel, artifactNativeSyncStatusMeta, artifactRecipeLabel,
  artifactSetLabel, artifactSlotLabel, artifactStatLabel,
  artifactTranslationModeLabel,
} from '@/features/artifact-analysis/buildModel.js'
import ArtifactLaunchDialog from './ArtifactLaunchDialog.vue'

const props = defineProps({uid: {type: String, default: ''}})
const capacity = ref(100)
const preview = ref(null)
const loading = ref(false)
const starting = ref(false)
const observing = ref(false)
const launchDialogOpen = ref(false)
const pendingLaunch = ref(null)
const pendingLaunchJobId = ref('')
let watchGeneration = 0
let watchedJobId = ''
let previewRequestGeneration = 0
const previewMeta = computed(() => artifactNativeSyncStatusMeta(preview.value?.status))
const lockSchemeCount = computed(() => new Set((preview.value?.lockPlans || [])
  .map(plan => `${plan.setKey}:${plan.buildId}`)).size)
const statListLabel = values => [...(values || [])].map(artifactStatLabel).join(' / ') || '无'
const check = async () => {
  const requestedUid = props.uid.trim()
  const requestedCapacity = capacity.value
  const requestGeneration = ++previewRequestGeneration
  loading.value = true
  try {
    const result = await previewArtifactNativeSync(requestedUid, requestedCapacity)
    if (requestGeneration !== previewRequestGeneration
      || requestedUid !== props.uid.trim()
      || requestedCapacity !== capacity.value) return
    preview.value = result
  } catch {
    if (requestGeneration !== previewRequestGeneration
      || requestedUid !== props.uid.trim()
      || requestedCapacity !== capacity.value) return
    preview.value = null
    ElMessage.error('预检失败，请确认服务已启动后重试')
  } finally {
    if (requestGeneration === previewRequestGeneration) loading.value = false
  }
}
const watchNativeSync = jobId => {
  if (!jobId || watchedJobId === jobId) return
  watchedJobId = jobId
  observing.value = true
  const generation = watchGeneration
  void waitForArtifactJobCompletion(jobId, getArtifactJob, {
    attempts: null,
    shouldContinue: () => generation === watchGeneration,
  }).then(async completed => {
    if (generation !== watchGeneration) return
    if (completed?.status === 'FAILED') ElMessage.error('原神方案重建失败')
    else if (completed?.status === 'COMPLETED') ElMessage.success('原神方案重建完成')
    await check()
  }).catch(() => {
    if (generation === watchGeneration) {
      ElMessage.error('原神方案任务状态连续读取失败，请重新预检')
    }
  }).finally(() => {
    if (watchedJobId === jobId) watchedJobId = ''
    if (generation === watchGeneration) observing.value = false
  })
}
const continueNativeSync = () => {
  ElMessage.success('正在连接 BetterGI')
  watchNativeSync(pendingLaunchJobId.value)
}
const rebuild = async () => {
  if (!props.uid.trim()) { ElMessage.warning('请选择 UID'); return }
  try { await ElMessageBox.confirm('将保存目标方案和阶段日志，完整替换已选择的套装锁定方案，并更新明确选择的角色快速装备方案 1/2；未选择的另一个快速装备方案不会清理。套装删除后执行不再响应普通取消。', '同步原神方案', {confirmButtonText:'接受并同步',cancelButtonText:'取消',type:'warning'}) } catch { return }
  starting.value = true
  pendingLaunchJobId.value = ''
  pendingLaunch.value = null
  try {
    let response
    try {
      response = await startArtifactJob(
        props.uid.trim(), 'REBUILD_NATIVE_PLANS', capacity.value, true,
        preview.value.planDigest)
      if (!validateArtifactLaunch(response.launch, 'REBUILD_NATIVE_PLANS')) throw new Error('服务端未返回有效启动请求')
    } catch {
      ElMessage.error('无法创建原神方案重建任务，请稍后重试')
      return
    }
    pendingLaunch.value = response.launch
    pendingLaunchJobId.value = response.job.id
    let claimed
    try {
      claimed = await waitForArtifactHostClaim(response.job.id, getArtifactJob)
    } catch {
      ElMessage.warning('原神方案任务状态读取暂时失败，已继续在后台观察')
      watchNativeSync(response.job.id)
      return
    }
    if (artifactHostHasAcceptedJob(claimed)) {
      if (claimed.status === 'FAILED') ElMessage.error('BetterGI 已接收重建任务，但执行失败')
      else ElMessage.success('BetterGI 已接收重建任务')
      watchNativeSync(response.job.id)
    } else launchDialogOpen.value = true
  } finally { starting.value = false }
}
watch(capacity, () => {
  previewRequestGeneration++
  loading.value = false
  preview.value = null
})
watch(() => props.uid, () => {
  watchGeneration++
  previewRequestGeneration++
  watchedJobId = ''
  observing.value = false
  loading.value = false
  pendingLaunchJobId.value = ''
  preview.value = null
})
onBeforeUnmount(() => {
  watchGeneration++
  previewRequestGeneration++
})
</script>

<template>
  <section>
    <header class="toolbar"><div><h2>原神方案同步</h2><p>按 Build 同步套装锁定与角色快速装备。</p></div><div class="commands"><label class="capacity"><span>套装容量</span><el-input-number v-model="capacity" :min="1" :max="999"/></label><el-button :icon="Refresh" :loading="loading" @click="check">预检</el-button><el-button type="primary" :icon="VideoPlay" :loading="starting || observing" :disabled="!uid || observing || preview?.status !== 'READY'" @click="rebuild">开始同步</el-button></div></header>
    <el-empty v-if="!preview" description="先执行容量与表达能力预检"/>
    <template v-else>
      <el-alert :title="previewMeta.title" :description="previewMeta.description" :type="previewMeta.type" :closable="false" show-icon/>
      <div class="metrics"><div><span>来源配装</span><strong>{{ preview.sourceBuildCount }}</strong></div><div><span>套装锁定方案</span><strong>{{ lockSchemeCount }}</strong></div><div><span>快速装备方案</span><strong>{{ preview.quickEquipPlans.length }}</strong></div><div><span>转换方式</span><strong>{{ artifactTranslationModeLabel(preview.translationMode) }}</strong></div><div><span>套装替换</span><strong>{{ preview.replaceLockPlans ? '完整替换' : '不改套装' }}</strong></div><div><span>操作前证据</span><strong>{{ preview.requiresPreMutationEvidence ? '必须保存' : '不要求' }}</strong></div></div>
      <el-table v-if="preview.lockPlans.length" :data="preview.lockPlans" height="300"><el-table-column label="Build" min-width="170" prop="buildName"/><el-table-column label="套装" min-width="220"><template #default="{row}">{{ artifactSetLabel(row.setKey) }}</template></el-table-column><el-table-column label="部位" width="110"><template #default="{row}">{{ artifactSlotLabel(row.slotKey) }}</template></el-table-column><el-table-column label="主词条" min-width="220"><template #default="{row}">{{ statListLabel(row.mainStats) }}</template></el-table-column><el-table-column label="强副词条" min-width="240"><template #default="{row}">{{ statListLabel(row.substats) }}</template></el-table-column></el-table>
      <el-table v-if="preview.quickEquipPlans.length" :data="preview.quickEquipPlans" height="240"><el-table-column label="角色" min-width="130"><template #default="{row}">{{ artifactCharacterLabel(row.characterKey) }}</template></el-table-column><el-table-column label="Build" min-width="170" prop="buildName"/><el-table-column label="方案" width="90"><template #default="{row}">方案 {{ row.presetIndex }}</template></el-table-column><el-table-column label="主配方" min-width="240"><template #default="{row}">{{ artifactRecipeLabel(row.sets) }}</template></el-table-column><el-table-column label="优先副词条" min-width="220"><template #default="{row}">{{ statListLabel(row.prioritySubstats) }}</template></el-table-column><el-table-column label="次级副词条" min-width="220"><template #default="{row}">{{ statListLabel(row.secondarySubstats) }}</template></el-table-column></el-table>
      <el-alert v-for="issue in preview.issues" :key="`${issue.code}:${issue.subjectKey}`" class="issue" type="error" :closable="false" :title="issue.message" :description="issue.buildIds.join('、')" show-icon/>
    </template>
    <ArtifactLaunchDialog v-model:open="launchDialogOpen" :launch="pendingLaunch" task-label="重建原神方案" @launched="continueNativeSync"/>
  </section>
</template>

<style scoped>
.toolbar,.commands,.metrics,.capacity{display:flex;align-items:center}.toolbar{justify-content:space-between;gap:14px;margin-bottom:14px}.toolbar h2{margin:0;font-size:18px}.toolbar p{margin:4px 0 0;color:var(--el-text-color-secondary);font-size:13px}.commands{gap:8px}.capacity{gap:7px;color:var(--el-text-color-secondary);font-size:12px;white-space:nowrap}.metrics{gap:24px;flex-wrap:wrap;padding:18px 0}.metrics>div{display:grid;gap:3px;min-width:130px}.metrics span{color:var(--el-text-color-secondary);font-size:12px}.metrics strong{font-size:18px}.issue{margin-top:10px}@media(max-width:720px){.toolbar{align-items:stretch;flex-direction:column}.commands{justify-content:flex-end;flex-wrap:wrap}}
</style>
