<script setup>
import {computed, onBeforeUnmount, ref, watch} from 'vue'
import {Delete, Refresh, VideoPlay} from '@element-plus/icons-vue'
import {ElMessage, ElMessageBox} from 'element-plus'
import {deleteArtifactJob, getArtifactJob, getArtifactJobs, startArtifactJob} from '@api/artifact/artifactAnalysis.js'
import {
  artifactCountReconciliation,
  artifactHostHasAcceptedJob,
  artifactJobStatusMeta,
  artifactJobWasStopped,
  hasActiveArtifactJobs,
  artifactNonFiveStarCountStorageKey,
  artifactOperationMeta,
  canDeleteArtifactJob,
  normalizeArtifactNonFiveStarCount,
  shouldRefreshArtifactJobs,
  validateArtifactLaunch,
  waitForArtifactHostClaim,
  waitForArtifactJobCompletion,
} from '@/features/artifact-analysis/model.js'
import {artifactHostErrorLabel, formatArtifactDate} from '@/features/artifact-analysis/buildModel.js'
import ArtifactLaunchDialog from './ArtifactLaunchDialog.vue'

const props = defineProps({uid: {type: String, default: ''}})
const jobs = ref([])
const loading = ref(false)
const starting = ref(false)
const selectedId = ref('')
const launchDialogOpen = ref(false)
const pendingLaunch = ref(null)
const deletingIds = ref(new Set())
const manualNonFiveStarCount = ref(null)
const selected = computed(() => jobs.value.find(job => job.id === selectedId.value) || jobs.value[0])
let restoringManualCount = false
let refreshing = false
let watchGeneration = 0
const watchedJobIds = new Set()

const restoreManualCount = () => {
  restoringManualCount = true
  try {
    if (!props.uid.trim()) { manualNonFiveStarCount.value = null; return }
    manualNonFiveStarCount.value = normalizeArtifactNonFiveStarCount(
      window.localStorage.getItem(artifactNonFiveStarCountStorageKey(props.uid))
    )
  } finally { restoringManualCount = false }
}

const warnIfCountsNeedReview = nextJobs => {
  const latest = nextJobs.find(job => job.operation === 'ANALYZE' && job.snapshot)
  const reconciliation = artifactCountReconciliation(latest, manualNonFiveStarCount.value)
  if (!latest || !reconciliation || reconciliation.matches) return
  const warningKey = [
    'artifact-analysis:reconciliation-warning', props.uid.trim(), latest.id,
    latest.snapshot?.snapshotDigest || '', reconciliation.nonFiveStarCount,
  ].join(':')
  if (window.sessionStorage.getItem(warningKey)) return
  window.sessionStorage.setItem(warningKey, 'shown')
  void ElMessageBox.alert(
    `本次识别到 ${reconciliation.analyzableCount} 件可分析五星圣遗物，` +
      `人工填写 ${reconciliation.nonFiveStarCount} 件非五星，合计 ${reconciliation.combinedCount} 件；` +
      `背包总数为 ${reconciliation.totalCount} 件。可能存在漏检，或非五星圣遗物数量填写不正确，请复核。`,
    '圣遗物数量需要复核',
    {confirmButtonText: '知道了', type: 'warning'}
  ).catch(() => {})
}

const load = async (silent = false) => {
  if (!shouldRefreshArtifactJobs({
    silent, refreshing, documentHidden: typeof document !== 'undefined' && document.hidden,
  })) return
  if (!props.uid.trim()) { jobs.value = []; return }
  refreshing = true
  if (!silent) loading.value = true
  try {
    jobs.value = await getArtifactJobs(props.uid.trim())
    if (!selectedId.value && jobs.value.length) selectedId.value = jobs.value[0].id
    warnIfCountsNeedReview(jobs.value)
    resumeActiveWatches()
  } catch {
    if (!silent) ElMessage.error('分析记录加载失败，请稍后重试')
  } finally {
    refreshing = false
    if (!silent) loading.value = false
  }
}

const start = async () => {
  if (!props.uid.trim()) { ElMessage.warning('请选择 UID'); return }
  try {
    await ElMessageBox.confirm(
      '将由当前运行的 BetterGI 接收任务；如果尚未运行，随后可手动连接。',
      '开始扫描分析',
      {confirmButtonText: '开始', cancelButtonText: '取消', type: 'warning'}
    )
  } catch { return }
  starting.value = true
  try {
    const response = await startArtifactJob(props.uid.trim(), 'ANALYZE')
    if (!validateArtifactLaunch(response.launch, 'ANALYZE')) throw new Error('服务端未返回有效启动请求')
    jobs.value.unshift(response.job)
    selectedId.value = response.job.id
    pendingLaunch.value = response.launch
    const claimed = await waitForArtifactHostClaim(response.job.id, getArtifactJob)
    const index = jobs.value.findIndex(job => job.id === claimed.id)
    if (index >= 0) jobs.value[index] = claimed
    if (artifactHostHasAcceptedJob(claimed)) {
      if (artifactJobWasStopped(claimed)) ElMessage.info('扫描已停止')
      else if (claimed.status === 'FAILED') ElMessage.error('BetterGI 已接收扫描任务，但执行失败')
      else ElMessage.success('BetterGI 已接收扫描任务')
      if (hasActiveArtifactJobs([claimed])) watchActiveJob(claimed.id)
    } else launchDialogOpen.value = true
  } finally { starting.value = false }
}

const watchActiveJob = jobId => {
  if (!jobId || watchedJobIds.has(jobId)) return
  watchedJobIds.add(jobId)
  const generation = watchGeneration
  void waitForArtifactJobCompletion(jobId, getArtifactJob, {
    attempts: 1800,
    terminalStatuses: [
      'READY_FOR_REVIEW', 'RESCAN_REQUIRED', 'STALE_ABORT',
      'COMPLETED', 'FAILED',
    ],
    shouldContinue: () => generation === watchGeneration,
    onUpdate: next => {
      if (generation !== watchGeneration) return
      const index = jobs.value.findIndex(job => job.id === next?.id)
      if (index >= 0) jobs.value[index] = next
    },
  }).then(() => {
    if (generation === watchGeneration) void load(true)
  }).finally(() => watchedJobIds.delete(jobId))
}

const resumeActiveWatches = () => {
  jobs.value.filter(job => hasActiveArtifactJobs([job]))
    .forEach(job => watchActiveJob(job.id))
}

const remove = async job => {
  try { await ElMessageBox.confirm(`删除这条${artifactOperationMeta(job.operation).label}记录？`, '删除任务', {type: 'warning'}) }
  catch { return }
  const next = new Set(deletingIds.value); next.add(job.id); deletingIds.value = next
  try {
    await deleteArtifactJob(job.id)
    if (selectedId.value === job.id) selectedId.value = ''
    ElMessage.success('任务已删除')
    await load(true)
  } catch { ElMessage.error('任务可能已被 BetterGI 接收，当前无法删除') }
  finally { const done = new Set(deletingIds.value); done.delete(job.id); deletingIds.value = done }
}

watch(() => props.uid, () => {
  watchGeneration++
  watchedJobIds.clear()
  restoreManualCount()
  void load()
}, {immediate: true})
watch(manualNonFiveStarCount, value => {
  if (restoringManualCount || !props.uid.trim()) return
  const key = artifactNonFiveStarCountStorageKey(props.uid)
  const normalized = normalizeArtifactNonFiveStarCount(value)
  if (normalized === null) window.localStorage.removeItem(key)
  else window.localStorage.setItem(key, String(normalized))
  warnIfCountsNeedReview(jobs.value)
})
onBeforeUnmount(() => { watchGeneration++ })
</script>

<template>
  <section class="panel" v-loading="loading" aria-live="polite">
    <header class="toolbar">
      <div>
        <h2>分析记录</h2>
        <p>网页创建任务，BetterGI 只负责游戏内扫描。</p>
      </div>
      <div class="commands">
        <el-tooltip content="刷新"><el-button circle :icon="Refresh" :disabled="!uid" @click="load()"/></el-tooltip>
        <label class="non-five-count-control" title="仅用于扫描结果数量复核，不影响任务执行">
          <span>非五星数量</span>
          <el-input-number
            v-model="manualNonFiveStarCount"
            :min="0"
            :max="2700"
            :step="1"
            :precision="0"
            :controls="false"
            aria-label="非五星圣遗物数量"
            placeholder="未填写"
          />
          <span>件</span>
        </label>
        <el-button type="primary" :icon="VideoPlay" :loading="starting" :disabled="!uid" @click="start">
          开始扫描分析
        </el-button>
      </div>
    </header>

    <el-empty v-if="!uid" description="请选择 UID"/>
    <el-empty v-else-if="!jobs.length" description="暂无分析记录"/>
    <template v-else>
      <el-table :data="jobs" height="320" highlight-current-row @current-change="job => selectedId = job?.id || ''">
        <el-table-column label="任务" min-width="180">
          <template #default="{row}">{{ artifactOperationMeta(row.operation).label }}</template>
        </el-table-column>
        <el-table-column label="状态" width="130">
          <template #default="{row}"><el-tag :type="artifactJobStatusMeta(row.status, row.errorMessage).type" effect="plain">{{ artifactJobStatusMeta(row.status, row.errorMessage).label }}</el-tag></template>
        </el-table-column>
        <el-table-column label="创建时间" min-width="180"><template #default="{row}">{{ formatArtifactDate(row.createdAtUtc) }}</template></el-table-column>
        <el-table-column label="圣遗物" width="90" align="right">
          <template #default="{row}">{{ row.snapshot?.artifactCount ?? '-' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="70" align="center">
          <template #default="{row}"><el-tooltip :content="canDeleteArtifactJob(row) ? '删除任务' : '任务正在执行'"><el-button circle text type="danger" :icon="Delete" :loading="deletingIds.has(row.id)" :disabled="!canDeleteArtifactJob(row)" :aria-label="`删除${artifactOperationMeta(row.operation).label}任务`" @click.stop="remove(row)"/></el-tooltip></template>
        </el-table-column>
      </el-table>

      <section v-if="selected" class="summary-band">
        <div><span>保留</span><strong>{{ selected.analysisResult?.summary?.keep ?? '-' }}</strong></div>
        <div><span>排除</span><strong>{{ selected.analysisResult?.summary?.reject ?? '-' }}</strong></div>
        <div><span>待锁定</span><strong>{{ selected.analysisResult?.summary?.lock ?? '-' }}</strong></div>
        <div><span>待解锁</span><strong>{{ selected.analysisResult?.summary?.unlock ?? '-' }}</strong></div>
        <el-alert v-if="selected.errorMessage" :title="artifactHostErrorLabel(selected.errorMessage)" :type="artifactJobWasStopped(selected) ? 'info' : 'error'" :closable="false" show-icon/>
      </section>
    </template>
    <ArtifactLaunchDialog v-model:open="launchDialogOpen" :launch="pendingLaunch" task-label="扫描并分析" @launched="ElMessage.success('正在连接 BetterGI')"/>
  </section>
</template>

<style scoped>
.panel { min-height: 420px; }
.toolbar, .commands, .summary-band { display: flex; align-items: center; }
.toolbar { justify-content: space-between; gap: 16px; margin-bottom: 14px; }
.toolbar h2 { margin: 0; font-size: 18px; }
.toolbar p { margin: 4px 0 0; color: var(--el-text-color-secondary); font-size: 13px; }
.commands { gap: 8px; }
.non-five-count-control { display: flex; align-items: center; gap: 6px; color: var(--el-text-color-regular); font-size: 13px; white-space: nowrap; }
.non-five-count-control .el-input-number { width: 104px; }
.summary-band { flex-wrap: wrap; gap: 10px; padding: 16px 0; border-bottom: 1px solid var(--el-border-color-lighter); }
.summary-band > div { min-width: 118px; display: grid; gap: 3px; }
.summary-band span { color: var(--el-text-color-secondary); font-size: 12px; }
.summary-band strong { font-size: 22px; font-variant-numeric: tabular-nums; }
.summary-band .el-alert { width: 100%; }
@media (max-width: 680px) { .toolbar { align-items: stretch; flex-direction: column; } .commands { justify-content: flex-end; flex-wrap: wrap; } .non-five-count-control { order: -1; width: 100%; justify-content: flex-end; } }
</style>
