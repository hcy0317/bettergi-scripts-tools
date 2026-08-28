<script setup>
import {computed, onBeforeUnmount, onMounted, ref, watch} from 'vue'
import {Check, Refresh, VideoPlay} from '@element-plus/icons-vue'
import {ElMessage, ElMessageBox} from 'element-plus'
import {
  approveArtifactJob, getArtifactBuilds, getArtifactJob, getArtifactJobs,
  getArtifactSettings, launchArtifactPlan,
} from '@api/artifact/artifactAnalysis.js'
import {artifactHostHasAcceptedJob, artifactJobStatusMeta, canApproveArtifactJob, canExecuteArtifactJob, validateArtifactLaunch, waitForArtifactHostClaim} from '@/features/artifact-analysis/model.js'
import {artifactSetLabel, artifactSlotLabel, formatArtifactDate} from '@/features/artifact-analysis/buildModel.js'
import {
  artifactDecisionEvaluation, artifactDecisionRows, artifactLockPlanFilterOptions,
  artifactExecutionSummary, artifactExecutionTargets, artifactHasDormantSubstat,
  DEFAULT_ARTIFACT_ANALYSIS_THRESHOLDS,
  filterAndSortArtifactDecisionRows,
  preferredArtifactJobId,
} from '@/features/artifact-analysis/lockPlanModel.js'
import ArtifactBuildScoreRail from './ArtifactBuildScoreRail.vue'
import ArtifactLaunchDialog from './ArtifactLaunchDialog.vue'
import ArtifactLockPlanFilters from './ArtifactLockPlanFilters.vue'

const props = defineProps({uid: {type: String, default: ''}})
const jobs = ref([])
const builds = ref([])
const job = ref(null)
const settings = ref({...DEFAULT_ARTIFACT_ANALYSIS_THRESHOLDS})
const jobId = ref('')
const loading = ref(false)
const refreshing = ref(false)
const approving = ref(false)
const launching = ref(false)
const launchDialogOpen = ref(false)
const pendingLaunch = ref(null)
const panelRoot = ref(null)
const page = ref(1)
const view = ref('all')
const setKey = ref('all')
const slotKey = ref('all')
const levelRange = ref([0, 20])
const sort = ref('potential-desc')
const pageSize = 30
let visibilityObserver = null
let detailGeneration = 0

const analyzable = computed(() => jobs.value.filter(item => item.analysisResult))
const rows = computed(() => artifactDecisionRows(job.value))
const filterOptions = computed(() => artifactLockPlanFilterOptions(rows.value))
const filtered = computed(() => filterAndSortArtifactDecisionRows(rows.value, {
  view: view.value, setKey: setKey.value, slotKey: slotKey.value, levelRange: levelRange.value, sort: sort.value,
}))
const visible = computed(() => filtered.value.slice((page.value - 1) * pageSize, page.value * pageSize))
const executionTargets = computed(() => artifactExecutionTargets(filtered.value))
const executionSummary = computed(() => artifactExecutionSummary(filtered.value))
const hasBuildScoreMatrix = computed(() => Boolean(job.value?.analysisResult?.buildIds?.length))
const evaluation = row => artifactDecisionEvaluation(row)
const loadJobDetail = async id => {
  const generation = ++detailGeneration
  if (!id) { job.value = null; return }
  const detail = await getArtifactJob(id)
  if (generation === detailGeneration && id === jobId.value) job.value = detail
}

const load = async (silent = false) => {
  if (refreshing.value) return
  if (!props.uid.trim()) { jobs.value = []; jobId.value = ''; job.value = null; return }
  refreshing.value = true
  if (!silent) loading.value = true
  const previousNewestId = analyzable.value[0]?.id || ''
  try {
    const [nextJobs, nextBuilds, nextSettings] = await Promise.all([
      getArtifactJobs(props.uid.trim()), getArtifactBuilds(), getArtifactSettings(),
    ])
    const nextJobId = preferredArtifactJobId(nextJobs, jobId.value, previousNewestId)
    jobs.value = nextJobs
    builds.value = nextBuilds
    settings.value = {...DEFAULT_ARTIFACT_ANALYSIS_THRESHOLDS, ...(nextSettings || {})}
    jobId.value = nextJobId
    await loadJobDetail(nextJobId)
  }
  catch { if (!silent) ElMessage.error('锁定方案加载失败，请稍后重试') }
  finally { refreshing.value = false; if (!silent) loading.value = false }
}
const approve = async () => {
  try { await ElMessageBox.confirm('批准后方案将与当前扫描摘要绑定；数量变化会强制重新扫描。', '批准锁定方案', {type:'warning'}) } catch { return }
  approving.value = true
  try { await approveArtifactJob(job.value.id, job.value.snapshot.snapshotDigest); ElMessage.success('方案已批准'); await load() }
  finally { approving.value = false }
}
const execute = async () => {
  if (!executionTargets.value.length) { ElMessage.info('当前筛选没有需要加解锁的目标'); return }
  try { await ElMessageBox.confirm(`当前筛选将锁定 ${executionSummary.value.lock} 件、解锁 ${executionSummary.value.unlock} 件。BetterGI 会先核对圣遗物总数；数量不变就直接执行，数量变化则全量复扫并返回审核。`, '执行筛选后的锁定方案', {type:'warning'}) } catch { return }
  launching.value = true
  try {
    const response = await launchArtifactPlan(
      job.value.id, executionTargets.value.map(row => row.scanIndex)
    )
    if (!validateArtifactLaunch(response.launch, 'EXECUTE_LOCK_PLAN')) throw new Error('服务端未返回有效启动请求')
    pendingLaunch.value = response.launch
    const claimed = await waitForArtifactHostClaim(response.job.id, getArtifactJob)
    await load(true)
    if (artifactHostHasAcceptedJob(claimed)) {
      if (claimed.status === 'FAILED') ElMessage.error('BetterGI 已接收锁定任务，但执行失败')
      else ElMessage.success('BetterGI 已接收锁定任务')
    } else launchDialogOpen.value = true
  } finally { launching.value = false }
}
watch(() => props.uid, () => void load(), {immediate: true})
watch([jobId, view, setKey, slotKey, levelRange, sort], () => { page.value = 1 })
onMounted(() => {
  if (!window.IntersectionObserver || !panelRoot.value) return
  visibilityObserver = new window.IntersectionObserver(entries => {
    if (entries.some(entry => entry.isIntersecting)) void load(true)
  }, {threshold: 0.01})
  visibilityObserver.observe(panelRoot.value)
})
onBeforeUnmount(() => visibilityObserver?.disconnect())
</script>

<template>
  <section ref="panelRoot" v-loading="loading">
    <header class="toolbar">
      <div><h2>锁定方案</h2><p>审核评分结果后，独立启动原生锁定执行。</p></div>
      <div class="commands">
        <el-select v-model="jobId" placeholder="选择分析记录" @change="loadJobDetail"><el-option v-for="item in analyzable" :key="item.id" :label="`${formatArtifactDate(item.createdAtUtc)} · ${artifactJobStatusMeta(item.status).label}`" :value="item.id"/></el-select>
        <el-tooltip content="刷新最新方案"><el-button circle :icon="Refresh" :loading="refreshing" :disabled="!uid" aria-label="刷新最新锁定方案" @click="load()"/></el-tooltip>
        <el-button :icon="Check" :loading="approving" :disabled="!canApproveArtifactJob(job)" @click="approve">批准方案</el-button>
        <el-tooltip :content="executionTargets.length ? `执行当前筛选的 ${executionTargets.length} 个目标` : '当前筛选没有加解锁目标'"><span><el-button type="primary" :icon="VideoPlay" :loading="launching" :disabled="!canExecuteArtifactJob(job) || !executionTargets.length" @click="execute">执行方案</el-button></span></el-tooltip>
      </div>
    </header>
    <el-empty v-if="!uid" description="请选择 UID"/>
    <el-empty v-else-if="!job" description="暂无可审核分析结果"/>
    <template v-else>
      <div class="summary">
        <el-tag :type="artifactJobStatusMeta(job.status).type" effect="plain">{{ artifactJobStatusMeta(job.status).label }}</el-tag>
        <span>快照 {{ job.snapshot?.artifactCount }} 件</span><span>推荐 {{ job.analysisResult.summary.keep }}</span><span>其他 {{ (job.analysisResult.summary.reject ?? 0) + (job.analysisResult.summary.unscored ?? 0) }}</span>
        <span>筛选锁定 {{ executionSummary.lock }}</span><span>筛选解锁 {{ executionSummary.unlock }}</span><span>筛选结果 {{ filtered.length }} 件</span>
      </div>
      <el-alert v-if="!hasBuildScoreMatrix" title="此记录由旧版评分生成，只保存了最佳 Build；请重新扫描以查看全部达到阈值的 Build 分数。" type="warning" :closable="false" show-icon/>
      <ArtifactLockPlanFilters v-model:view="view" v-model:set-key="setKey" v-model:slot-key="slotKey" v-model:level-range="levelRange" v-model:sort="sort" :options="filterOptions"/>
      <el-table :data="visible" table-layout="fixed" empty-text="没有符合筛选条件的圣遗物">
        <el-table-column prop="scanIndex" label="序号" width="70"/>
        <el-table-column label="圣遗物" min-width="210"><template #default="{row}"><div class="artifact-meta"><strong>{{ artifactSetLabel(row.artifact?.setKey) }}</strong><span>{{ artifactSlotLabel(row.artifact?.slotKey) }} · +{{ row.artifact?.level ?? '?' }}</span><el-tag v-if="artifactHasDormantSubstat(row)" size="small" type="warning" effect="plain">第四词条待激活</el-tag></div></template></el-table-column>
        <el-table-column label="评分" width="116"><template #default="{row}"><div class="score-pair"><strong>{{ row.currentScore }}</strong><small>潜力 {{ row.potentialScore }}</small></div></template></el-table-column>
        <el-table-column label="评价" width="150"><template #default="{row}"><div class="evaluation"><el-tag :type="evaluation(row).type" effect="plain">{{ evaluation(row).label }}</el-tag><small>{{ row.setFit === 'SET_MATCH' ? '套装匹配' : '散件候选' }}</small></div></template></el-table-column>
        <el-table-column label="各 Build 分数（达到当前阈值）" min-width="430"><template #default="{row}"><ArtifactBuildScoreRail :row="row" :builds="builds" :settings="settings"/></template></el-table-column>
        <el-table-column label="锁状态" width="100"><template #default="{row}">{{ row.expectedLocked === row.desiredLocked ? '不变' : row.desiredLocked ? '锁定' : '解锁' }}</template></el-table-column>
      </el-table>
      <el-pagination v-model:current-page="page" layout="prev, pager, next, total" :page-size="pageSize" :total="filtered.length" class="pagination"/>
    </template>
    <ArtifactLaunchDialog v-model:open="launchDialogOpen" :launch="pendingLaunch" task-label="执行锁定方案" @launched="ElMessage.success('正在连接 BetterGI')"/>
  </section>
</template>

<style scoped>
.toolbar, .commands, .summary { display: flex; align-items: center; }
.toolbar { justify-content: space-between; gap: 14px; margin-bottom: 14px; }
.toolbar h2 { margin: 0; font-size: 18px; }
.toolbar p { margin: 4px 0 0; color: var(--el-text-color-secondary); font-size: 13px; }
.commands { gap: 8px; flex-wrap: wrap; }
.commands .el-select { width: 300px; }
.summary { gap: 16px; min-height: 46px; flex-wrap: wrap; color: var(--el-text-color-regular); font-size: 13px; }
.artifact-meta, .score-pair, .evaluation { display: grid; gap: 3px; min-width: 0; }
.artifact-meta strong { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.artifact-meta span, .score-pair small, .evaluation small { color: var(--el-text-color-secondary); font-size: 12px; }
.score-pair strong { font-size: 18px; font-variant-numeric: tabular-nums; }
.pagination { justify-content: flex-end; margin-top: 14px; }
@media (max-width: 860px) { .toolbar { align-items: stretch; flex-direction: column; } .commands .el-select { width: 100%; } }
</style>
