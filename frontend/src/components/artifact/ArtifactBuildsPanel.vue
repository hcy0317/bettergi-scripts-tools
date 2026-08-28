<script setup>
import {computed, onBeforeUnmount, onMounted, ref, watch} from 'vue'
import {
  Download, MoreFilled, Plus, Refresh, Search, Upload, UserFilled,
} from '@element-plus/icons-vue'
import {ElMessage, ElMessageBox} from 'element-plus'
import {
  deleteArtifactBuild, getArtifactBuildAutoActivationResult,
  getArtifactBuildAutoActivationSettings, getArtifactBuilds,
  getArtifactJob, importArtifactBuilds, saveArtifactBuild,
  saveArtifactBuildAutoActivationSettings, startArtifactCharacterRosterJob,
  updateArtifactBuildBulkState,
} from '@api/artifact/artifactAnalysis.js'
import {
  applyArtifactBuildBulkState, artifactBuildPayload, artifactCharacterLabel, artifactPageSize, cloneArtifactBuild,
  filterArtifactBuilds, normalizeArtifactAutoActivationSettings, prepareArtifactBuilds,
} from '@/features/artifact-analysis/buildModel.js'
import {
  artifactCharacterScanStatusMeta, artifactHostHasAcceptedJob,
  waitForArtifactHostClaim, waitForArtifactJobCompletion,
} from '@/features/artifact-analysis/model.js'
import {artifactSetLabel} from '@/features/artifact-analysis/buildModel.js'
import ArtifactBuildEditorDialog from './ArtifactBuildEditorDialog.vue'
import ArtifactBuildTable from './ArtifactBuildTable.vue'
import ArtifactLaunchDialog from './ArtifactLaunchDialog.vue'
import {getUid} from '@api/uid/uid.js'

const props = defineProps({uid: {type: String, default: ''}})

const builds = ref([])
const loading = ref(false)
const refreshing = ref(false)
const error = ref('')
const query = ref('')
const character = ref('all')
const source = ref('all')
const status = ref('all')
const page = ref(1)
const pageSize = ref(25)
const dialogOpen = ref(false)
const editing = ref(null)
const fileInput = ref(null)
const pendingIds = ref(new Set())
const autoActivation = ref(normalizeArtifactAutoActivationSettings())
const autoActivationResult = ref(null)
const autoActivationLoading = ref(false)
const characterScanJobId = ref('')
const characterLaunch = ref(null)
const characterLaunchDialogOpen = ref(false)
const characterScanJob = ref(null)
const characterScanWatchJobId = ref('')
const characterScanMeta = computed(() => artifactCharacterScanStatusMeta(characterScanJob.value))

const sourceOptions = [
  {label: '全部', value: 'all'}, {label: '上游预设', value: 'upstream'}, {label: '自定义', value: 'custom'},
]
const statusOptions = [
  {label: '全部状态', value: 'all'}, {label: '参与分析', value: 'analysis'},
  {label: '参与原神同步', value: 'native'}, {label: '全部停用', value: 'disabled'},
]
const pageSizeOptions = [
  {label: '25 条', value: 25}, {label: '50 条', value: 50},
  {label: '100 条', value: 100},
]
const preparedBuilds = computed(() => prepareArtifactBuilds(builds.value))
const characters = computed(() => [...new Set(builds.value.map(build => build.characterKey).filter(Boolean))]
  .sort((left, right) => artifactCharacterLabel(left).localeCompare(artifactCharacterLabel(right), 'zh-CN')))
const setOptions = computed(() => [...new Set(builds.value
  .flatMap(build => [build.sets, ...(build.alternativeSetRecipes || [])]).flat()
  .map(rule => rule.setKey).filter(Boolean))]
  .sort((left, right) => artifactSetLabel(left).localeCompare(artifactSetLabel(right))))
const filtered = computed(() => filterArtifactBuilds(preparedBuilds.value, {
  query: query.value, character: character.value, source: source.value, status: status.value,
}))
const effectivePageSize = computed(() => artifactPageSize(pageSize.value, filtered.value.length))
const visibleRows = computed(() => filtered.value
  .slice((page.value - 1) * effectivePageSize.value, page.value * effectivePageSize.value))
const metrics = computed(() => ({
  total: builds.value.length,
  analysis: builds.value.filter(build => build.analysisEnabled).length,
  native: builds.value.filter(build => build.nativeSyncEnabled).length,
  characters: characters.value.length,
}))

let loadGeneration = 0
let refreshScheduled = false

watch([query, character, source, status, pageSize], () => { page.value = 1 })

const load = async (silent = false) => {
  const generation = ++loadGeneration
  if (!silent) loading.value = true
  error.value = ''
  try {
    const nextBuilds = await getArtifactBuilds()
    if (generation === loadGeneration) builds.value = nextBuilds
  }
  catch { error.value = '配装数据加载失败，请稍后重试' }
  finally { if (!silent && generation === loadGeneration) loading.value = false }
}
const refresh = async () => {
  refreshing.value = true
  try { await Promise.all([load(true), loadAutoActivationResult()]) }
  finally { refreshing.value = false }
}
const scheduleVisibilityRefresh = () => {
  if (refreshScheduled || document.visibilityState !== 'visible') return
  refreshScheduled = true
  queueMicrotask(() => {
    refreshScheduled = false
    void Promise.all([load(true), loadAutoActivationResult()])
  })
}
const loadAutoActivationSettings = async () => {
  try {
    autoActivation.value = normalizeArtifactAutoActivationSettings(
      await getArtifactBuildAutoActivationSettings()
    )
  } catch {
    ElMessage.error('角色自动启停设置加载失败，请稍后重试')
  }
}
const loadAutoActivationResult = async () => {
  if (!props.uid.trim()) { autoActivationResult.value = null; return }
  try { autoActivationResult.value = await getArtifactBuildAutoActivationResult(props.uid.trim()) }
  catch { autoActivationResult.value = null }
}
const waitForCharacterScan = async jobId => {
  const completed = await waitForArtifactJobCompletion(jobId, getArtifactJob, {
    onUpdate: job => { characterScanJob.value = job },
  })
  if (completed?.status === 'COMPLETED') {
    await Promise.all([load(true), loadAutoActivationResult()])
    const result = autoActivationResult.value
    ElMessage.success(result
      ? `已识别 ${result.characterCount} 人，最终启用 ${result.eligibleCharacterCount} 人、${result.enabledBuildCount} 个配装`
      : '已按游戏角色等级与收藏状态更新配装')
    return
  }
  if (completed?.status === 'FAILED') {
    ElMessage.error(artifactCharacterScanStatusMeta(completed).title)
    return
  }
  ElMessage.warning('角色检测仍在运行，请稍后刷新配装')
}
const watchCharacterScan = jobId => {
  if (!jobId || characterScanWatchJobId.value === jobId) return
  characterScanWatchJobId.value = jobId
  void waitForCharacterScan(jobId).finally(() => {
    if (characterScanWatchJobId.value === jobId) characterScanWatchJobId.value = ''
  })
}
const connectCharacterScan = async () => {
  const claimed = await waitForArtifactHostClaim(characterScanJobId.value, getArtifactJob)
  characterScanJob.value = claimed
  if (!artifactHostHasAcceptedJob(claimed)) {
    characterLaunchDialogOpen.value = true
    watchCharacterScan(characterScanJobId.value)
    return
  }
  ElMessage.info('BetterGI 正在检测游戏角色，请保持角色列表可正常打开')
  watchCharacterScan(characterScanJobId.value)
}
const scanCharacters = async () => {
  if (!props.uid.trim()) { ElMessage.warning('请选择 UID'); return }
  autoActivationLoading.value = true
  try {
    const settings = normalizeArtifactAutoActivationSettings(autoActivation.value)
    autoActivation.value = await saveArtifactBuildAutoActivationSettings(settings)
    const uidInfo = await getUid(props.uid.trim())
    const response = await startArtifactCharacterRosterJob(
      props.uid.trim(),
      String(uidInfo?.gameNickname || '').trim(),
      String(uidInfo?.miliastraNickname || '').trim(),
      uidInfo?.miliastraCharacterKey === 'MannequinBoy'
        ? 'MannequinBoy' : 'MannequinGirl')
    characterScanJobId.value = response.job.id
    characterScanJob.value = response.job
    characterLaunch.value = response.launch
    void connectCharacterScan()
  } finally { autoActivationLoading.value = false }
}
const continueCharacterScan = async () => {
  ElMessage.info('已连接 BetterGI，正在等待角色检测完成')
  watchCharacterScan(characterScanJobId.value)
}
const markPending = (id, pending) => {
  const next = new Set(pendingIds.value)
  pending ? next.add(id) : next.delete(id)
  pendingIds.value = next
}
const openEditor = build => { editing.value = build ? artifactBuildPayload(build) : null; dialogOpen.value = true }
const save = async build => {
  markPending(build.id, true)
  try { await saveArtifactBuild(build, props.uid.trim()); dialogOpen.value = false; ElMessage.success('配装已保存，锁定方案已重新计算'); await load() }
  finally { markPending(build.id, false) }
}
const toggle = async (build, key, value) => {
  markPending(build.id, true)
  try { await saveArtifactBuild({...artifactBuildPayload(build), [key]: value}, props.uid.trim()); await load() }
  finally { markPending(build.id, false) }
}
const clone = async build => {
  const copy = cloneArtifactBuild(build)
  await saveArtifactBuild(copy, props.uid.trim())
  ElMessage.success('已创建自定义副本')
  await load()
  openEditor(copy)
}
const remove = async build => {
  try { await ElMessageBox.confirm(`删除“${build.name}”？`, '删除配装', {type: 'warning'}) }
  catch { return }
  await deleteArtifactBuild(build.id, props.uid.trim())
  ElMessage.success('配装已删除')
  await load()
}
const backup = () => {
  const url = URL.createObjectURL(new Blob([JSON.stringify(builds.value, null, 2)], {type: 'application/json'}))
  const link = document.createElement('a')
  link.href = url; link.download = 'artifact-builds.json'; link.click(); URL.revokeObjectURL(url)
}
const openImport = () => fileInput.value?.click()
const importFile = async event => {
  const file = event.target.files?.[0]
  if (!file) return
  try {
    const imported = JSON.parse(await file.text())
    if (!Array.isArray(imported)) throw new Error('配装备份格式不正确')
    await importArtifactBuilds(imported, props.uid.trim())
    ElMessage.success(`已导入 ${imported.length} 个配装`)
    await load()
  } finally { event.target.value = '' }
}
const bulk = async command => {
  const [scope, key, rawValue] = command.split(':')
  const value = rawValue === 'true'
  const updated = applyArtifactBuildBulkState(builds.value, {scope, key, value})
  const targetCount = updated.filter((build, index) => build !== builds.value[index]).length
  try {
    await ElMessageBox.confirm(`将修改 ${targetCount} 个配装，是否继续？`, '批量更新', {type: 'warning'})
  } catch { return }
  builds.value = await updateArtifactBuildBulkState({scope, field: key, enabled: value}, props.uid.trim())
  ElMessage.success(`已更新 ${targetCount} 个配装状态`)
  await load(true)
}
const resetFilters = () => { query.value = ''; character.value = 'all'; source.value = 'all'; status.value = 'all' }
onMounted(() => {
  void load()
  void loadAutoActivationSettings()
  void loadAutoActivationResult()
  window.addEventListener('focus', scheduleVisibilityRefresh)
  document.addEventListener('visibilitychange', scheduleVisibilityRefresh)
})
watch(() => props.uid, () => {
  void Promise.all([load(true), loadAutoActivationResult()])
})
onBeforeUnmount(() => {
  window.removeEventListener('focus', scheduleVisibilityRefresh)
  document.removeEventListener('visibilitychange', scheduleVisibilityRefresh)
})
</script>

<template>
  <section class="build-manager" v-loading="loading">
    <header class="manager-header">
      <div><h2>配装管理</h2><p>角色配装、套装配方、主词条与评分权重</p></div>
      <div class="header-actions">
        <el-tooltip content="刷新配装"><el-button circle :icon="Refresh" :loading="refreshing" aria-label="手动刷新配装" @click="refresh"/></el-tooltip>
        <el-button :icon="Download" @click="backup">备份</el-button>
        <el-button :icon="Upload" @click="openImport">导入</el-button>
        <el-dropdown trigger="click" @command="bulk">
          <el-button :icon="MoreFilled">批量</el-button>
          <template #dropdown><el-dropdown-menu>
            <el-dropdown-item command="upstream:analysisEnabled:true">启用全部预设分析</el-dropdown-item>
            <el-dropdown-item command="upstream:analysisEnabled:false">停用全部预设分析</el-dropdown-item>
            <el-dropdown-item command="custom:analysisEnabled:true">启用全部自定义分析</el-dropdown-item>
            <el-dropdown-item command="custom:analysisEnabled:false">停用全部自定义分析</el-dropdown-item>
            <el-dropdown-item divided command="all:analysisEnabled:true">启用全部分析</el-dropdown-item>
            <el-dropdown-item command="all:analysisEnabled:false">停用全部分析</el-dropdown-item>
            <el-dropdown-item divided command="all:nativeSyncEnabled:true">启用全部原神同步</el-dropdown-item>
            <el-dropdown-item command="all:nativeSyncEnabled:false">停用全部原神同步</el-dropdown-item>
          </el-dropdown-menu></template>
        </el-dropdown>
        <el-button type="primary" :icon="Plus" @click="openEditor(null)">新增</el-button>
        <input ref="fileInput" hidden type="file" accept="application/json" @change="importFile"/>
      </div>
    </header>

    <div class="summary-strip" aria-label="配装统计">
      <div><span>全部配装</span><strong>{{ metrics.total }}</strong></div>
      <div><span>参与分析</span><strong>{{ metrics.analysis }}</strong></div>
      <div><span>原神同步</span><strong>{{ metrics.native }}</strong></div>
      <div><span>角色人数</span><strong>{{ metrics.characters }}</strong></div>
    </div>

    <section class="auto-activation-card" aria-label="按游戏角色自动启停配装">
      <div class="auto-activation-copy">
        <h3>按游戏角色自动启停</h3>
        <p>检测完整角色列表；符合条件的角色同时启用分析与原神同步，其余角色的相关配装会停用。</p>
      </div>
      <label class="level-threshold">
        <span>启用等级</span>
        <strong>大于等于 {{ autoActivation.levelThreshold }} 级</strong>
        <el-slider v-model="autoActivation.levelThreshold" :min="0" :max="90" :step="1"/>
      </label>
      <el-switch v-model="autoActivation.favoriteOverride" active-text="收藏角色无视等级启用"/>
      <el-button type="primary" plain :icon="UserFilled" :loading="autoActivationLoading" :disabled="!uid" @click="scanCharacters">
        检测游戏角色并应用
      </el-button>
      <div v-if="autoActivationResult" class="auto-activation-result" aria-label="最近一次角色自动启停结果">
        <span>扫描 {{ autoActivationResult.characterCount }} 人</span>
        <span>收藏 {{ autoActivationResult.favoriteCharacterCount }} 人</span>
        <span>等级达标 {{ autoActivationResult.levelEligibleCharacterCount }} 人</span>
        <strong>满足条件 {{ autoActivationResult.eligibleCharacterCount }} 人</strong>
        <span>启用配装 {{ autoActivationResult.enabledBuildCount }} 个</span>
      </div>
    </section>

    <el-alert v-if="error" :title="error" type="error" show-icon :closable="false"/>
    <el-alert v-if="characterScanMeta" :title="characterScanMeta.title" :type="characterScanMeta.type" show-icon closable @close="characterScanJob = null"/>
    <div class="filter-bar">
      <el-input v-model="query" clearable :prefix-icon="Search" placeholder="搜索角色、配装、套装或词条"/>
      <el-select v-model="character" filterable aria-label="按角色筛选">
        <el-option label="全部角色" value="all"/>
        <el-option v-for="item in characters" :key="item" :label="artifactCharacterLabel(item)" :value="item"/>
      </el-select>
      <el-segmented v-model="source" :options="sourceOptions" aria-label="按来源筛选"/>
      <el-select v-model="status" aria-label="按状态筛选">
        <el-option v-for="item in statusOptions" :key="item.value" :label="item.label" :value="item.value"/>
      </el-select>
      <el-tooltip content="清除筛选"><el-button circle :icon="Refresh" aria-label="清除筛选" @click="resetFilters"/></el-tooltip>
    </div>

    <el-empty v-if="!loading && !filtered.length" description="没有符合条件的配装"/>
    <template v-else>
      <div class="table-scroll">
        <ArtifactBuildTable :rows="visibleRows" :pending-ids="pendingIds" @toggle="toggle" @edit="openEditor" @clone="clone" @remove="remove"/>
      </div>
      <div class="page-controls">
        <label>每页显示<el-select v-model="pageSize" aria-label="每页显示数量"><el-option v-for="item in pageSizeOptions" :key="item.value" :label="item.label" :value="item.value"/></el-select></label>
        <el-pagination v-model:current-page="page" size="small" background layout="prev, pager, next, total" :page-size="effectivePageSize" :total="filtered.length" class="pagination"/>
      </div>
    </template>
    <ArtifactBuildEditorDialog v-model:open="dialogOpen" :build="editing" :set-options="setOptions" :character-options="characters" @save="save"/>
    <ArtifactLaunchDialog v-model:open="characterLaunchDialogOpen" :launch="characterLaunch" task-label="检测角色并更新配装" @launched="continueCharacterScan"/>
  </section>
</template>

<style scoped>
.build-manager{min-width:0}.manager-header,.header-actions,.filter-bar,.page-controls,.page-controls label{display:flex;align-items:center}.manager-header{justify-content:space-between;gap:18px;margin-bottom:14px}.manager-header h2{margin:0;font-size:18px}.manager-header p{margin:4px 0 0;color:var(--el-text-color-secondary);font-size:13px}.header-actions{flex-wrap:wrap;justify-content:flex-end;gap:8px}.summary-strip{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));border-block:1px solid var(--el-border-color-lighter);margin-bottom:14px}.summary-strip>div{display:flex;align-items:baseline;justify-content:space-between;padding:12px 18px;border-right:1px solid var(--el-border-color-lighter)}.summary-strip>div:last-child{border-right:0}.summary-strip span{color:var(--el-text-color-secondary);font-size:12px}.summary-strip strong{font-size:21px}.filter-bar{display:grid;grid-template-columns:minmax(260px,1fr) 180px auto 170px 34px;gap:8px;margin:14px 0}.table-scroll{max-width:100%;overflow-x:auto}.page-controls{justify-content:space-between;gap:14px;margin-top:14px}.page-controls label{gap:8px;color:var(--el-text-color-secondary);font-size:12px;white-space:nowrap}.page-controls .el-select{width:96px}.pagination{justify-content:flex-end}
.auto-activation-card{display:grid;grid-template-columns:minmax(280px,1fr) minmax(230px,320px) auto auto;align-items:center;gap:18px;padding:16px 18px;margin-bottom:14px;border:1px solid var(--el-border-color-light);border-radius:8px;background:var(--el-fill-color-extra-light)}.auto-activation-copy h3{margin:0;font-size:15px}.auto-activation-copy p{margin:5px 0 0;color:var(--el-text-color-secondary);font-size:12px;line-height:1.55}.level-threshold{display:grid;grid-template-columns:auto auto;align-items:center;gap:2px 10px;color:var(--el-text-color-secondary);font-size:12px}.level-threshold strong{color:var(--el-text-color-primary);text-align:right}.level-threshold .el-slider{grid-column:1/-1;margin:0 8px;width:auto}
.auto-activation-result{grid-column:1/-1;display:flex;align-items:center;gap:10px 18px;flex-wrap:wrap;padding-top:12px;border-top:1px solid var(--el-border-color-lighter);color:var(--el-text-color-secondary);font-size:12px}.auto-activation-result strong{color:var(--el-color-success)}
@media(max-width:1120px){.auto-activation-card{grid-template-columns:1fr 1fr}.auto-activation-copy{grid-column:1/-1}}
@media(max-width:900px){.manager-header{align-items:stretch;flex-direction:column}.header-actions{justify-content:flex-start}.filter-bar{grid-template-columns:1fr 1fr}.filter-bar>.el-input{grid-column:1/-1}.filter-bar>.el-segmented{grid-column:1/-1}.summary-strip{grid-template-columns:repeat(2,minmax(0,1fr))}.summary-strip>div:nth-child(2){border-right:0}.summary-strip>div:nth-child(-n+2){border-bottom:1px solid var(--el-border-color-lighter)}}
@media(max-width:520px){.header-actions .el-button{margin:0}.filter-bar,.auto-activation-card{grid-template-columns:1fr}.filter-bar>*{grid-column:1!important}.auto-activation-copy{grid-column:1}.summary-strip>div{padding:10px 12px}.summary-strip strong{font-size:18px}.page-controls{align-items:flex-start;flex-direction:column}.pagination{max-width:100%;overflow-x:auto;justify-content:flex-start}}
</style>
