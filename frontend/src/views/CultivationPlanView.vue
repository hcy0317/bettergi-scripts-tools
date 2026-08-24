<script setup>
import {computed, ref} from 'vue'
import {ElMessage, ElMessageBox} from 'element-plus'
import {
  ArrowLeft,
  Check,
  Delete,
  HomeFilled,
  Plus,
  Refresh,
  Right,
  Upload,
  WarningFilled
} from '@element-plus/icons-vue'
import router from '@router/router.js'
import UidSelector from '@/components/UidSelector.vue'
import CultivationExecutionPanel from '@/components/CultivationExecutionPanel.vue'
import {
  confirmCultivationPlan,
  getLatestCultivationPlan,
  previewCultivationPlan
} from '@api/auto_plan/cultivationPlan.js'

const uid = ref(String(router.currentRoute.value.query.uid || ''))
const activeTab = ref('import')
const selectedFile = ref(null)
const fileList = ref([])
const preview = ref(null)
const rows = ref([])
const currentPlan = ref(null)
const recognizing = ref(false)
const confirming = ref(false)
const loadingLatest = ref(false)
const recognitionError = ref('')
const recognitionMessage = ref('')
const confirmationError = ref('')

const canRecognize = computed(() => selectedFile.value && !recognizing.value)
const canConfirm = computed(() => preview.value && rows.value.length > 0 && !confirming.value)
const reviewCount = computed(() => rows.value.filter(row => row.needsReview).length)
const selectedFileSummary = computed(() => {
  if (!selectedFile.value) return ''
  return `${selectedFile.value.name} · ${formatFileSize(selectedFile.value.size)}`
})

const owned = row => Math.max(Number(row.required || 0) - Number(row.remaining || 0), 0)

const formatFileSize = size => {
  if (!Number.isFinite(size) || size <= 0) return '未知大小'
  if (size < 1024 * 1024) return `${Math.ceil(size / 1024)} KB`
  return `${(size / 1024 / 1024).toFixed(1)} MB`
}

const handleFileChange = async uploadFile => {
  selectedFile.value = uploadFile.raw
  fileList.value = [uploadFile]
  preview.value = null
  rows.value = []
  recognitionError.value = ''
  recognitionMessage.value = ''
  confirmationError.value = ''
  ElMessage.success(`已选择 ${uploadFile.name}`)
  if (uid.value.trim()) {
    await recognize()
  }
}

const handleFileRemove = () => {
  selectedFile.value = null
  fileList.value = []
  preview.value = null
  rows.value = []
  recognitionError.value = ''
  recognitionMessage.value = ''
  confirmationError.value = ''
}

const recognize = async () => {
  if (!uid.value.trim()) {
    ElMessage.warning('请输入 UID')
    return
  }
  if (!selectedFile.value) {
    ElMessage.warning('请选择图片')
    return
  }

  recognizing.value = true
  recognitionError.value = ''
  recognitionMessage.value = '正在识别图片并解析材料'
  confirmationError.value = ''
  try {
    const result = await previewCultivationPlan(uid.value.trim(), selectedFile.value)
    preview.value = result
    rows.value = (result.requirements || []).map(row => ({...row, manuallyCorrected: false}))
    if (rows.value.length === 0) {
      recognitionMessage.value = '识别完成，但未解析出材料行'
      ElMessage.warning('未解析出材料行')
    } else {
      recognitionMessage.value = `识别完成，共 ${rows.value.length} 种材料`
      ElMessage.success(`已识别 ${rows.value.length} 种材料`)
    }
  } catch (error) {
    recognitionMessage.value = ''
    recognitionError.value = error?.message || '图片识别失败，请重试'
  } finally {
    recognizing.value = false
  }
}

const markCorrected = row => {
  row.manuallyCorrected = true
  row.needsReview = false
  row.remainingEvidence = 'MANUAL'
}

const addRow = () => {
  rows.value.push({
    sourceIndex: null,
    materialName: '',
    required: 0,
    remaining: 0,
    remainingEvidence: 'MANUAL',
    confidence: null,
    needsReview: false,
    manuallyCorrected: true,
    sourceBlocks: []
  })
}

const removeRow = index => {
  rows.value.splice(index, 1)
}

const validateRows = () => {
  const names = new Set()
  for (const row of rows.value) {
    const name = row.materialName?.trim()
    if (!name) return '材料名称不能为空'
    if (names.has(name)) return `存在重复材料：${name}`
    names.add(name)
    if (row.required < 0 || row.remaining < 0) return `材料数量不能为负数：${name}`
    if (row.remaining > row.required) return `还需数量不能大于总需求：${name}`
  }
  return null
}

const confirm = async () => {
  const validationError = validateRows()
  if (validationError) {
    ElMessage.error(validationError)
    return
  }
  try {
    await ElMessageBox.confirm(
        `确认 UID ${uid.value.trim()} 的 ${rows.value.length} 种材料需求？`,
        '确认养成账本',
        {confirmButtonText: '确认', cancelButtonText: '取消', type: 'warning'}
    )
  } catch {
    return
  }

  confirming.value = true
  confirmationError.value = ''
  try {
    currentPlan.value = await confirmCultivationPlan({
      previewId: preview.value.previewId,
      uid: uid.value.trim(),
      requirements: rows.value.map(row => ({
        sourceIndex: row.sourceIndex,
        materialName: row.materialName.trim(),
        required: Number(row.required),
        remaining: Number(row.remaining)
      }))
    })
    activeTab.value = 'execution'
    ElMessage.success(`已建立账本版本 ${currentPlan.value.revision}`)
  } catch (error) {
    confirmationError.value = error?.message || '无法建立养成账本版本'
  } finally {
    confirming.value = false
  }
}

const loadLatest = async () => {
  if (!uid.value.trim()) {
    ElMessage.warning('请输入 UID')
    return
  }
  loadingLatest.value = true
  try {
    currentPlan.value = await getLatestCultivationPlan(uid.value.trim())
    if (!currentPlan.value) ElMessage.info('该 UID 暂无养成账本')
  } finally {
    loadingLatest.value = false
  }
}

const evidenceLabel = row => {
  if (row.remainingEvidence === 'MANUAL' || row.manuallyCorrected) return '人工'
  if (row.remainingEvidence === 'INFERRED_ZERO') return '推断为 0'
  return '图片识别'
}

const engineLabel = version => version?.includes('PP-OCRv6')
  ? 'PP-OCR 第六版'
  : (version || '未知识别引擎')

const planStateLabel = state => ({
  IMPORTED: '已导入',
  ACTIVE: '执行中',
  COMPLETED: '已完成'
})[state] || '状态未知'

const catalogLabel = version => version === 'name-only-v1'
  ? '材料目录：基础名称版'
  : '材料目录：兼容版本'

const rowClassName = ({row}) => row.needsReview ? 'needs-review' : ''

const handleUidChange = async nextUid => {
  preview.value = null
  rows.value = []
  currentPlan.value = null
  confirmationError.value = ''
  if (!nextUid) return
  await loadLatest()
  if (selectedFile.value) await recognize()
}

const goToExecution = () => { activeTab.value = 'execution' }
</script>

<template>
  <main class="cultivation-page">
    <section class="cultivation-shell">
    <header class="page-header">
      <div class="header-actions">
        <el-tooltip content="返回" placement="bottom">
          <el-button circle :icon="ArrowLeft" @click="router.back()"/>
        </el-tooltip>
        <el-tooltip content="首页" placement="bottom">
          <el-button circle :icon="HomeFilled" @click="router.push('/')"/>
        </el-tooltip>
      </div>
      <div>
        <h1>养成计划</h1>
        <p v-if="currentPlan">UID {{ currentPlan.uid }} · 账本版本 {{ currentPlan.revision }}</p>
        <p v-else>材料需求账本</p>
      </div>
    </header>

    <section class="identity-bar">
      <UidSelector v-model="uid" class="uid-input" @change="handleUidChange"/>
      <el-button :icon="Refresh" :loading="loadingLatest" @click="loadLatest">读取账本</el-button>
    </section>

    <el-tabs v-model="activeTab" class="workspace-tabs">
      <el-tab-pane label="导入校正" name="import">
        <section class="import-band">
          <el-upload
              drag
              :auto-upload="false"
              :disabled="recognizing"
              :limit="1"
              accept="image/png,image/jpeg,image/webp"
              :file-list="fileList"
              :on-change="handleFileChange"
              :on-remove="handleFileRemove"
          >
            <el-icon class="upload-icon"><Upload/></el-icon>
            <div class="upload-label">选择养成计算器图片</div>
          </el-upload>
          <el-button
              type="primary"
              :icon="Refresh"
              :disabled="!canRecognize"
              :loading="recognizing"
              @click="recognize"
          >识别图片</el-button>
        </section>

        <div class="recognition-status" aria-live="polite">
          <el-alert
              v-if="recognitionError"
              :title="recognitionError"
              type="error"
              :description="selectedFileSummary"
              :closable="false"
              show-icon
          />
          <el-alert
              v-else-if="recognizing"
              title="正在识别图片并解析材料"
              type="info"
              :description="selectedFileSummary"
              :closable="false"
              show-icon
          />
          <el-alert
              v-else-if="selectedFile"
              :title="preview ? recognitionMessage : (uid.trim() ? '图片已选择，等待识别' : '图片已选择，等待 UID')"
              :type="preview ? 'success' : 'info'"
              :description="selectedFileSummary"
              :closable="false"
              show-icon
          />
        </div>

        <section v-if="preview" class="preview-band">
          <div class="metadata-row">
              <span>识别引擎：{{ engineLabel(preview.engineVersion) }}</span>
              <span>模型来源：BetterGI 本地资源</span>
            <span>{{ preview.imageWidth }} × {{ preview.imageHeight }}</span>
            <el-tag v-if="reviewCount" type="warning" effect="plain">
              {{ reviewCount }} 项待确认
            </el-tag>
          </div>

          <el-alert
              v-for="warning in preview.warnings"
              :key="warning"
              :title="warning"
              type="warning"
              :closable="false"
              show-icon
              class="parse-warning"
          />

          <el-alert
              v-if="confirmationError"
              :title="confirmationError"
              type="error"
              :closable="false"
              show-icon
              class="confirmation-error"
          />

          <div class="table-toolbar">
            <div class="table-title">
              <strong>材料需求</strong>
              <span>{{ rows.length }} 种材料</span>
            </div>
            <div class="table-actions">
              <el-tooltip content="补录材料" placement="top">
                <el-button circle :icon="Plus" @click="addRow"/>
              </el-tooltip>
              <el-button
                  type="primary"
                  :icon="Check"
                  :disabled="!canConfirm"
                  :loading="confirming"
                  @click="confirm"
              >确认并建立账本版本</el-button>
            </div>
          </div>

          <el-table
              :data="rows"
              :row-class-name="rowClassName"
              table-layout="fixed"
              class="requirements-table"
          >
            <el-table-column width="44" align="center">
              <template #default="{row}">
                <el-tooltip v-if="row.needsReview" content="需要人工确认" placement="top">
                  <el-icon class="warning-icon"><WarningFilled/></el-icon>
                </el-tooltip>
              </template>
            </el-table-column>
            <el-table-column label="材料" min-width="190">
              <template #default="{row}">
                <el-input v-model="row.materialName" @change="markCorrected(row)"/>
              </template>
            </el-table-column>
            <el-table-column label="总需求" width="170">
              <template #default="{row}">
                <el-input-number
                    v-model="row.required"
                    :min="0"
                    :max="999999999"
                    controls-position="right"
                    @change="markCorrected(row)"
                />
              </template>
            </el-table-column>
            <el-table-column label="当前拥有" width="130" align="right">
              <template #default="{row}">{{ owned(row).toLocaleString() }}</template>
            </el-table-column>
            <el-table-column label="还需" width="170">
              <template #default="{row}">
                <el-input-number
                    v-model="row.remaining"
                    :min="0"
                    :max="999999999"
                    controls-position="right"
                    @change="markCorrected(row)"
                />
              </template>
            </el-table-column>
            <el-table-column label="证据" width="115" align="center">
              <template #default="{row}">
                <el-tag :type="evidenceLabel(row) === '人工' ? 'success' : 'info'" effect="plain">
                  {{ evidenceLabel(row) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column width="64" align="center" fixed="right">
              <template #default="{$index}">
                <el-tooltip content="删除" placement="top">
                  <el-button text type="danger" :icon="Delete" @click="removeRow($index)"/>
                </el-tooltip>
              </template>
            </el-table-column>
          </el-table>

        </section>
      </el-tab-pane>

      <el-tab-pane label="当前账本" name="ledger">
        <section v-if="currentPlan" class="ledger-band">
          <div class="ledger-toolbar">
            <div class="metadata-row">
              <strong>账本版本 {{ currentPlan.revision }}</strong>
              <span>{{ planStateLabel(currentPlan.state) }}</span>
              <span>{{ catalogLabel(currentPlan.catalogVersion) }}</span>
              <span>识别引擎：{{ engineLabel(currentPlan.engineVersion) }}</span>
            </div>
            <el-button type="primary" :icon="Right" @click="goToExecution">进入一条龙执行</el-button>
          </div>
          <el-alert
              title="账本已建立"
              description="材料导入、账本、执行计划、脚本设置和 BetterGI 启动均在本页统一管理。"
              type="success"
              :closable="false"
              show-icon
              class="ledger-handoff"
          />
          <el-table :data="currentPlan.requirements" table-layout="fixed" class="requirements-table">
            <el-table-column prop="materialName" label="材料" min-width="200"/>
            <el-table-column label="总需求" width="150" align="right">
              <template #default="{row}">{{ row.required.toLocaleString() }}</template>
            </el-table-column>
            <el-table-column label="基线拥有" width="150" align="right">
              <template #default="{row}">{{ row.baselineOwned.toLocaleString() }}</template>
            </el-table-column>
            <el-table-column label="还需" width="150" align="right">
              <template #default="{row}">{{ row.remaining.toLocaleString() }}</template>
            </el-table-column>
            <el-table-column label="证据" width="110" align="center">
              <template #default="{row}">
                <el-tag :type="row.manuallyCorrected ? 'success' : 'info'" effect="plain">
                  {{ row.manuallyCorrected ? '人工' : '图片识别' }}
                </el-tag>
              </template>
            </el-table-column>
          </el-table>
        </section>
        <el-empty v-else description="暂无养成账本"/>
      </el-tab-pane>

      <el-tab-pane label="一条龙执行" name="execution">
        <CultivationExecutionPanel :key="`${uid}-${currentPlan?.revision || 0}`" :uid="uid"/>
      </el-tab-pane>

    </el-tabs>
    </section>
  </main>
</template>

<style scoped>
.cultivation-page {
  min-height: 100dvh;
  color: #20252b;
  box-sizing: border-box;
  padding: clamp(16px, 3vw, 32px);
  background: url("@assets/MHY_XTLL.png") center / cover fixed no-repeat;
}

.cultivation-shell {
  width: min(1440px, 100%);
  margin: 0 auto;
  padding: clamp(18px, 3vw, 34px);
  box-sizing: border-box;
  background: rgba(255, 255, 255, 0.86);
  border: 1px solid rgba(255, 255, 255, 0.72);
  border-radius: 16px;
  box-shadow: 0 18px 45px rgba(50, 59, 72, 0.22);
  backdrop-filter: blur(18px) saturate(1.1);
}

.page-header,
.identity-bar,
.metadata-row,
.table-toolbar {
  display: flex;
  align-items: center;
}

.page-header {
  justify-content: space-between;
  gap: 24px;
  margin-bottom: 18px;
}

.page-header > div:last-child {
  text-align: right;
}

.page-header h1 {
  margin: 0;
  font-size: 26px;
  line-height: 1.2;
  letter-spacing: 0;
}

.page-header p {
  margin: 5px 0 0;
  color: #6b7280;
  font-size: 13px;
}

.header-actions {
  display: flex;
  gap: 8px;
}

.identity-bar,
.workspace-tabs {
  margin-left: auto;
  margin-right: auto;
}

.identity-bar {
  gap: 10px;
  padding: 14px 0 18px;
  border-bottom: 1px solid rgba(112, 122, 132, 0.24);
}

.uid-input {
  width: min(320px, 65vw);
}

.workspace-tabs {
  margin-top: 16px;
}

.import-band {
  display: grid;
  grid-template-columns: minmax(280px, 520px) max-content;
  align-items: stretch;
  gap: 16px;
  padding: 8px 0 20px;
}

.import-band :deep(.el-upload) {
  width: min(520px, 100%);
}

.import-band :deep(.el-upload-dragger) {
  min-height: 112px;
  border-radius: 8px;
  padding: 22px;
  background: rgba(255, 255, 255, 0.78);
  border-color: rgba(96, 109, 123, 0.38);
  transition: border-color 0.2s ease, background-color 0.2s ease;
}

.import-band :deep(.el-upload-dragger:hover) {
  border-color: #409eff;
  background: rgba(255, 255, 255, 0.94);
}

.import-band > .el-button {
  align-self: flex-end;
  min-width: 136px;
}

.upload-icon {
  font-size: 28px;
  color: #52606d;
}

.upload-label {
  margin-top: 8px;
  font-size: 14px;
}

.recognition-status {
  margin-bottom: 14px;
}

.recognition-status:empty {
  margin-bottom: 0;
}

.recognition-status :deep(.el-alert) {
  border-radius: 8px;
  align-items: center;
}

.preview-band,
.ledger-band {
  border-top: 1px solid rgba(112, 122, 132, 0.24);
  padding-top: 16px;
}

.ledger-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 10px;
}

.ledger-handoff {
  margin-bottom: 14px;
}

.metadata-row {
  flex-wrap: wrap;
  gap: 8px 18px;
  min-height: 36px;
  color: #5c6670;
  font-size: 13px;
}

.parse-warning {
  margin: 8px 0;
}

.confirmation-error {
  margin: 8px 0;
}

.table-toolbar {
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 12px;
  min-height: 52px;
}

.table-title,
.table-actions {
  display: flex;
  align-items: center;
}

.table-title {
  gap: 10px;
}

.table-title span {
  color: #6b7280;
  font-size: 13px;
}

.table-actions {
  gap: 8px;
  margin-left: auto;
}

.requirements-table {
  width: 100%;
  border: 1px solid rgba(112, 122, 132, 0.24);
  border-radius: 8px;
  overflow: hidden;
  background: rgba(255, 255, 255, 0.92);
}

.requirements-table :deep(.needs-review td) {
  background: #fff8e8;
}

.requirements-table :deep(.el-input-number) {
  width: 100%;
}

.warning-icon {
  color: #c47a00;
  font-size: 18px;
}

@media (max-width: 760px) {
  .cultivation-page {
    padding: 12px;
    background-attachment: scroll;
  }

  .cultivation-shell {
    padding: 16px 14px 24px;
    border-radius: 10px;
  }

  .page-header {
    align-items: flex-start;
  }

  .page-header h1 {
    font-size: 22px;
  }

  .identity-bar,
  .import-band {
    flex-wrap: wrap;
  }

  .identity-bar {
    align-items: stretch;
  }

  .import-band {
    grid-template-columns: minmax(0, 1fr);
  }

  .uid-input,
  .import-band :deep(.el-upload),
  .import-band > .el-button {
    width: 100%;
  }

  .identity-bar > .el-button {
    flex: 1;
  }

  .table-title,
  .table-actions,
  .ledger-toolbar {
    width: 100%;
  }

  .ledger-toolbar {
    align-items: stretch;
    flex-direction: column;
  }

  .table-actions {
    justify-content: flex-end;
  }
}
</style>
