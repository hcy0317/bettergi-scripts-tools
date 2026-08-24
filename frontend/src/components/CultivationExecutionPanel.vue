<script setup>
import {computed, ref, watch} from 'vue'
import {ElMessage, ElMessageBox} from 'element-plus'
import {Connection, MagicStick, Refresh, Setting, VideoPlay} from '@element-plus/icons-vue'
import router from '@router/router.js'
import {
  getCultivationExecutionModules,
  getCultivationExecutionProjection,
  prepareCultivationOneStop,
  saveCultivationExecutionModule,
  startCultivationOneStop,
  syncCultivationExecutionModule
} from '@api/auto_plan/cultivationPlan.js'

const props = defineProps({
  uid: {type: String, default: ''}
})

const loading = ref(false)
const savingModuleId = ref('')
const syncingModuleId = ref('')
const preparing = ref(false)
const starting = ref(false)
const preparation = ref(null)
const settingsDialogOpen = ref(false)
const editingModule = ref(null)
const projection = ref(null)
const modules = ref([])
const loadError = ref('')
const GROUP_SETTINGS_MODULE_ID = 'script-group-settings'

const partyOptions = computed(() => {
  const result = new Set(projection.value?.partyOptions || [])
  modules.value.forEach(module => {
    Object.entries(module.settings || {})
      .filter(([key]) => key.toLowerCase().includes('party'))
      .forEach(([, value]) => {
        if (value) result.add(value)
      })
  })
  return Array.from(result)
})
const gatherTargets = computed(() => projection.value?.gatherAction?.csvTargets || [])
const monsterTargets = computed(() => projection.value?.monsterAction?.targets || [])
const bossActions = computed(() => projection.value?.bossActions || [])
const weeklyBossActions = computed(() => projection.value?.weeklyBossActions || [])
const hasProjection = computed(() => Boolean(projection.value))
const displayModules = computed(() => [...modules.value].sort((left, right) => {
  if (left.module.moduleId === GROUP_SETTINGS_MODULE_ID) return -1
  if (right.module.moduleId === GROUP_SETTINGS_MODULE_ID) return 1
  return 0
}))
const isGroupSettings = module => module.module.moduleId === GROUP_SETTINGS_MODULE_ID
const isAutoPlan = module => module.module.moduleId === 'auto-plan-resin'

const load = async () => {
  const uid = props.uid.trim()
  projection.value = null
  modules.value = []
  loadError.value = ''
  if (!uid) return
  loading.value = true
  try {
    const [nextProjection, moduleConfigurations] = await Promise.all([
      getCultivationExecutionProjection(uid),
      getCultivationExecutionModules(uid)
    ])
    projection.value = nextProjection
    modules.value = Array.isArray(moduleConfigurations) ? moduleConfigurations : []
  } catch (error) {
    loadError.value = error?.message || '无法读取养成执行计划'
  } finally {
    loading.value = false
  }
}

const saveModule = async module => {
  if (!props.uid.trim()) {
    ElMessage.warning('请先选择 UID')
    return
  }
  savingModuleId.value = module.module.moduleId
  try {
    if (isGroupSettings(module)) module.enabled = true
    await saveCultivationExecutionModule(props.uid.trim(), module.module.moduleId, {
      enabled: module.enabled,
      settings: settingsPayload(module)
    })
    ElMessage.success(`${module.module.displayName}设置已保存`)
    await load()
  } finally {
    savingModuleId.value = ''
  }
}

const moduleFields = module => module.module.settingsSchema || []
const settingsPayload = module => Object.fromEntries(
  moduleFields(module)
    .filter(field => field.editable)
    .map(field => [field.key, module.settings[field.key]])
)
const fieldOptions = field => {
  if (field.optionsSource === 'uid-parties') return partyOptions.value
  if (field.optionsSource === 'monster-route-families') {
    return projection.value?.monsterAction?.availableRouteFamilies || []
  }
  return field.options || []
}
const canSync = module => ['cd-aware-auto-gather', 'fully-auto-and-semi-auto-tools']
  .includes(module.module.moduleId)

const openModuleSettings = module => {
  editingModule.value = module
  settingsDialogOpen.value = true
}

const saveEditingModule = async () => {
  if (!editingModule.value) return
  await saveModule(editingModule.value)
  settingsDialogOpen.value = false
  editingModule.value = null
}

const syncModule = async module => {
  const uid = props.uid.trim()
  if (!uid) {
    ElMessage.warning('请先选择 UID')
    return
  }
  try {
    await ElMessageBox.confirm(
      `将当前缺口与${module.module.displayName}设置写入 BetterGI 脚本组？系统会先建立回滚备份。`,
      '同步脚本设置',
      {confirmButtonText: '同步', cancelButtonText: '取消', type: 'warning'}
    )
  } catch {
    return
  }
  syncingModuleId.value = module.module.moduleId
  try {
    await saveCultivationExecutionModule(uid, module.module.moduleId, {
      enabled: module.enabled,
      settings: settingsPayload(module)
    })
    const result = await syncCultivationExecutionModule(uid, module.module.moduleId)
    ElMessage.success(`${result.message}，已更新 ${result.updatedTasks} 个任务`)
    await load()
  } finally {
    syncingModuleId.value = ''
  }
}

const prepareOneStop = async () => {
  const uid = props.uid.trim()
  if (!uid) return
  preparing.value = true
  try {
    preparation.value = await prepareCultivationOneStop(uid)
    const warningText = preparation.value.warnings?.length
      ? `；${preparation.value.warnings.join('；')}`
      : ''
    ElMessage.success(`${preparation.value.message}${warningText}`)
    await load()
  } finally {
    preparing.value = false
  }
}

const startOneStop = async () => {
  const uid = props.uid.trim()
  if (!uid) return
  try {
    await ElMessageBox.confirm(
      '将刷新当前养成配置并立即交给 BetterGI 执行。请确认游戏已进入可操作界面。',
      '启动养成一条龙',
      {confirmButtonText: '同步并启动', cancelButtonText: '取消', type: 'warning'}
    )
  } catch {
    return
  }
  starting.value = true
  try {
    const result = await startCultivationOneStop(uid)
    preparation.value = result.preparation
    if (!/^bettergicultivation:\/\/one-stop\?request=[0-9a-f-]{36}$/i.test(result.launchUri || '')) {
      throw new Error('服务端未返回有效的 BetterGI 宿主启动请求')
    }
    window.location.assign(result.launchUri)
    ElMessage.success(`${result.message}；请在浏览器弹窗中确认打开 BetterGI`)
  } finally {
    starting.value = false
  }
}

watch(() => props.uid, load, {immediate: true})
</script>

<template>
  <section class="execution-panel" v-loading="loading">
    <header class="panel-header">
      <div>
        <div class="title-line">
          <h2>养成账本执行计划</h2>
          <el-tag v-if="projection" type="success" effect="plain">账本版本 {{ projection.revision }}</el-tag>
        </div>
        <p>自动体力与采集任务均按最新缺口生成下一步行动。</p>
      </div>
      <div class="header-command-row">
        <el-tooltip content="刷新执行计划">
          <el-button circle :icon="Refresh" :disabled="!uid" @click="load"/>
        </el-tooltip>
        <el-button :icon="MagicStick" :loading="preparing" :disabled="!projection" @click="prepareOneStop">
          生成一条龙配置
        </el-button>
        <el-button type="primary" :icon="VideoPlay" :loading="starting" :disabled="!projection" @click="startOneStop">
          同步并启动
        </el-button>
      </div>
    </header>

    <el-alert v-if="loadError" :title="loadError" type="error" :closable="false" show-icon/>
    <el-empty v-else-if="!uid" description="请选择 UID"/>
    <template v-else>
      <el-alert
          v-if="projection"
          :title="projection.executionMode"
          description="不会把总缺口机械换算为固定次数；每轮执行完成后，需要依据最新库存重新规划。"
          type="info"
          :closable="false"
          show-icon
      />
      <el-alert
          v-if="preparation"
          :title="`${preparation.scriptGroupName} 已就绪`"
          :description="`${preparation.autoPlanActions} 个体力行动，${preparation.scriptTasks} 个脚本任务`"
          type="success"
          :closable="false"
          show-icon
      />

      <section class="preferences-band">
        <div class="band-heading">
          <el-icon><Setting/></el-icon>
          <div>
            <strong>脚本设置代管中心</strong>
            <p>模块可独立启停、替换和升级，账本只依赖统一适配接口。</p>
          </div>
        </div>
        <div class="module-grid">
          <article v-for="module in displayModules" :key="module.module.moduleId" class="module-card">
            <header class="module-header">
              <div>
                <strong>{{ module.module.displayName }}</strong>
                <span>适配器版本 {{ module.module.adapterVersion }}</span>
              </div>
              <el-tag v-if="isGroupSettings(module)" type="primary" effect="plain">生成时固定启用</el-tag>
              <el-switch v-else v-model="module.enabled" active-text="启用" inactive-text="暂停"/>
            </header>
            <p>{{ module.module.description }}</p>
            <el-tag :type="module.module.integrationState.includes('等待') ? 'warning' : 'success'" effect="plain">
              {{ module.module.integrationState }}
            </el-tag>
            <div class="capability-row">
              <el-tag v-for="capability in module.module.capabilities" :key="capability" size="small" effect="plain">
                {{ capability }}
              </el-tag>
            </div>
            <div v-if="isAutoPlan(module)" class="resin-switch-grid">
              <label><span>天赋书秘境</span><el-switch v-model="module.settings.talentDomainEnabled"/></label>
              <label><span>摩拉地脉</span><el-switch v-model="module.settings.moraLeyLineEnabled"/></label>
              <label><span>大英雄经验地脉</span><el-switch v-model="module.settings.experienceLeyLineEnabled"/></label>
            </div>
            <el-button :icon="Setting" plain @click="openModuleSettings(module)">完整脚本配置</el-button>
            <div class="module-actions">
              <el-button
                  type="primary"
                  :loading="savingModuleId === module.module.moduleId"
                  @click="saveModule(module)"
              >{{ isGroupSettings(module) ? '保存配置组设置' : (isAutoPlan(module) ? '保存体力开关' : '保存启停') }}</el-button>
              <el-button
                  v-if="canSync(module)"
                  :icon="Connection"
                  :loading="syncingModuleId === module.module.moduleId"
                  @click="syncModule(module)"
              >同步到 BetterGI 脚本组</el-button>
            </div>
          </article>
        </div>
      </section>

      <el-dialog
          v-model="settingsDialogOpen"
          :title="editingModule ? `${editingModule.module.displayName} 设置` : '脚本设置'"
          width="760px"
          class="module-settings-dialog"
          append-to-body
          destroy-on-close
      >
        <div v-if="editingModule" class="settings-dialog-grid">
          <label v-for="field in moduleFields(editingModule)" :key="field.key" class="module-field">
            <span>{{ field.label }}<small v-if="!field.editable">由账本自动生成</small></span>
            <el-select
                v-if="field.control === 'party-select' || field.control === 'select'"
                v-model="editingModule.settings[field.key]"
                :filterable="field.control === 'party-select'"
                :allow-create="field.control === 'party-select'"
                :clearable="field.editable"
                :disabled="!field.editable"
                placeholder="请选择"
            >
              <el-option v-for="option in fieldOptions(field)" :key="option" :label="option" :value="option"/>
            </el-select>
            <el-select
                v-else-if="field.control === 'multi-select'"
                v-model="editingModule.settings[field.key]"
                multiple
                filterable
                collapse-tags
                :disabled="!field.editable"
                placeholder="请选择"
            >
              <el-option v-for="option in fieldOptions(field)" :key="option" :label="option" :value="option"/>
            </el-select>
            <el-switch
                v-else-if="field.control === 'switch'"
                v-model="editingModule.settings[field.key]"
                :disabled="!field.editable"
            />
            <el-input-number
                v-else-if="field.control === 'number'"
                v-model="editingModule.settings[field.key]"
                :min="0"
                controls-position="right"
                :disabled="!field.editable"
            />
            <el-input v-else v-model="editingModule.settings[field.key]" :disabled="!field.editable"/>
          </label>
        </div>
        <template #footer>
          <el-button @click="settingsDialogOpen = false">取消</el-button>
          <el-button
              type="primary"
              :loading="savingModuleId === editingModule?.module.moduleId"
              @click="saveEditingModule"
          >保存设置</el-button>
        </template>
      </el-dialog>

      <div v-if="!hasProjection && !loading" class="empty-projection">
        <el-empty description="该 UID 尚未建立养成账本"/>
        <el-button type="primary" @click="router.push({name: 'CultivationPlan', query: {uid}})">导入养成计算器图片</el-button>
      </div>

      <div v-if="projection" class="action-grid">
        <article class="action-card">
          <div class="card-heading">
            <h3>自动体力行动</h3>
            <el-tag effect="plain">{{ projection.resinActions.length }} 项</el-tag>
          </div>
          <div v-if="projection.resinActions.length" class="action-list">
            <div v-for="action in projection.resinActions" :key="`${action.materialName}-${action.sourceName}`" class="action-row">
              <div>
                <strong>{{ action.sourceName }}</strong>
                <span>{{ action.actionType }} · {{ action.sourceType }}</span>
              </div>
              <div class="action-target">
                <span>{{ action.materialName }}</span>
                <strong>还需 {{ action.remaining.toLocaleString() }}</strong>
              </div>
            </div>
          </div>
          <el-empty v-else :image-size="64" description="当前没有可投影的体力行动"/>
        </article>

        <article class="action-card boss-card">
          <div class="card-heading">
            <h3>世界首领行动</h3>
            <el-tag :type="bossActions.length ? 'warning' : 'info'" effect="plain">{{ bossActions.length }} 项</el-tag>
          </div>
          <p class="adapter-name">执行模块：AutoPlan 首领任务</p>
          <div v-if="bossActions.length" class="action-list">
            <div v-for="action in bossActions" :key="action.materialName" class="action-row">
              <div>
                <strong>{{ action.bossName }}</strong>
                <span>{{ action.country }} · 队伍 {{ action.partyName || '沿用默认' }}</span>
              </div>
              <div class="action-target">
                <span>{{ action.materialName }}</span>
                <strong>还需 {{ action.remaining.toLocaleString() }}</strong>
              </div>
            </div>
          </div>
          <el-empty v-else :image-size="64" description="当前没有世界首领材料缺口"/>
        </article>

        <article class="action-card gather-card">
          <div class="card-heading">
            <h3>地方特产采集</h3>
            <el-tag :type="gatherTargets.length ? 'warning' : 'info'" effect="plain">
              {{ projection.gatherAction.actionState }}
            </el-tag>
          </div>
          <p class="adapter-name">执行脚本：{{ projection.gatherAction.scriptName }}</p>
          <div v-if="gatherTargets.length" class="target-list">
            <div v-for="target in gatherTargets" :key="target.materialName" class="target-row">
              <span>{{ target.country }} · {{ target.materialName }}</span>
              <strong>目标库存 {{ target.required.toLocaleString() }}</strong>
              <small>当前 {{ target.baselineOwned.toLocaleString() }}，还需 {{ target.remaining.toLocaleString() }}</small>
            </div>
          </div>
          <el-empty v-else :image-size="64" description="当前没有地方特产缺口"/>
        </article>

        <article class="action-card monster-card">
          <div class="card-heading">
            <h3>怪物材料路线</h3>
            <el-tag :type="monsterTargets.length ? 'warning' : 'info'" effect="plain">
              {{ projection.monsterAction.actionState }}
            </el-tag>
          </div>
          <p class="adapter-name">执行脚本：{{ projection.monsterAction.scriptName }}</p>
          <div v-if="monsterTargets.length" class="target-list">
            <div v-for="target in monsterTargets" :key="target.materialName" class="target-row">
              <span>{{ target.routeFamily }} · {{ target.materialName }}</span>
              <strong>还需 {{ target.remaining.toLocaleString() }}</strong>
              <small>{{ target.monsters.slice(0, 3).join('、') }}{{ target.monsters.length > 3 ? ` 等 ${target.monsters.length} 种怪物` : '' }}</small>
            </div>
          </div>
          <el-empty v-else :image-size="64" description="当前没有怪物材料缺口"/>
        </article>

        <article class="action-card weekly-card">
          <div class="card-heading">
            <h3>周本行动</h3>
            <el-tag :type="weeklyBossActions.length ? 'warning' : 'info'" effect="plain">
              {{ weeklyBossActions.length }} 项
            </el-tag>
          </div>
          <p class="adapter-name">执行脚本：WeeklyBoss</p>
          <div v-if="weeklyBossActions.length" class="action-list">
            <div v-for="action in weeklyBossActions" :key="action.materialName" class="action-row">
              <div>
                <strong>{{ action.bossName }}</strong>
                <span>{{ action.actionState }}</span>
              </div>
              <div class="action-target">
                <span>{{ action.materialName }}</span>
                <strong>还需 {{ action.remaining.toLocaleString() }}</strong>
              </div>
            </div>
          </div>
          <el-empty v-else :image-size="64" description="当前没有周本材料缺口"/>
        </article>

        <article v-if="projection.pendingMaterials.length" class="action-card pending-card">
          <div class="card-heading">
            <h3>人工与未自动化材料</h3>
            <el-tag type="info" effect="plain">{{ projection.pendingMaterials.length }} 项</el-tag>
          </div>
          <div class="pending-list">
            <div v-for="item in projection.pendingMaterials" :key="item.materialName">
              <span>{{ item.materialName }} · 还需 {{ item.remaining.toLocaleString() }}</span>
              <small>{{ item.reason }}</small>
            </div>
          </div>
        </article>
      </div>
    </template>
  </section>
</template>

<style scoped>
.execution-panel {
  display: grid;
  gap: 16px;
  min-height: 190px;
  padding: 20px;
  background: rgba(255, 255, 255, 0.82);
  border: 1px solid rgba(70, 78, 88, 0.16);
  border-radius: 8px;
}

.panel-header,
.title-line,
.band-heading,
.card-heading,
.action-row,
.target-row {
  display: flex;
  align-items: center;
}

.header-command-row {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 8px;
}

.panel-header,
.card-heading,
.action-row,
.target-row {
  justify-content: space-between;
}

.title-line,
.band-heading {
  gap: 10px;
}

.panel-header h2,
.card-heading h3,
.panel-header p,
.adapter-name {
  margin: 0;
}

.panel-header h2 {
  font-size: 18px;
}

.panel-header p,
.adapter-name,
.action-row span,
.target-row small,
.pending-list small {
  color: #68717c;
}

.panel-header p {
  margin-top: 5px;
  font-size: 13px;
}

.preferences-band {
  display: grid;
  gap: 12px;
  padding: 16px 0;
  border-block: 1px solid rgba(70, 78, 88, 0.12);
}

.module-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(220px, 1fr));
  gap: 12px;
}

.band-heading p,
.module-card p {
  margin: 3px 0 0;
  color: #68717c;
  font-size: 12px;
}

.module-card {
  display: grid;
  gap: 10px;
  min-width: 0;
  padding: 14px;
  background: rgba(249, 250, 251, 0.92);
  border: 1px solid rgba(70, 78, 88, 0.12);
  border-radius: 8px;
}

.module-header {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: flex-start;
}

.module-header > div {
  display: grid;
  gap: 3px;
}

.module-header span {
  color: #68717c;
  font-size: 11px;
}

.capability-row {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.resin-switch-grid {
  display: grid;
  gap: 8px;
  padding: 10px 0;
  border-top: 1px solid rgba(92, 105, 117, 0.16);
  border-bottom: 1px solid rgba(92, 105, 117, 0.16);
}

.resin-switch-grid label {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  min-width: 0;
  font-size: 12px;
  color: #3f4a55;
}

.module-field {
  display: grid;
  gap: 7px;
  min-width: 0;
  font-size: 13px;
  color: #4f5863;
}

.module-field span {
  display: flex;
  justify-content: space-between;
  gap: 8px;
}

.module-field small {
  color: #8a939e;
  font-size: 11px;
}

.settings-dialog-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px 18px;
  max-height: min(68vh, 720px);
  overflow-y: auto;
  padding-right: 6px;
}

:global(.module-settings-dialog) {
  max-width: calc(100vw - 32px);
}

.module-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.action-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}

.action-card {
  min-width: 0;
  padding: 16px;
  background: rgba(249, 250, 251, 0.9);
  border: 1px solid rgba(70, 78, 88, 0.12);
  border-radius: 8px;
}

.card-heading h3 {
  font-size: 15px;
}

.action-list,
.target-list,
.pending-list {
  display: grid;
  gap: 8px;
  margin-top: 12px;
}

.action-row,
.target-row,
.pending-list > div {
  gap: 12px;
  padding: 10px 0;
  border-top: 1px solid rgba(70, 78, 88, 0.1);
}

.action-row > div,
.action-target,
.target-row,
.pending-list > div {
  display: grid;
  gap: 3px;
}

.action-target {
  text-align: right;
}

.target-row {
  grid-template-columns: minmax(120px, 1fr) auto;
}

.target-row small {
  grid-column: 1 / -1;
}

.adapter-name {
  margin-top: 8px;
  font-size: 13px;
}

.pending-card {
  grid-column: 1 / -1;
}

.pending-list > div {
  grid-template-columns: minmax(180px, auto) 1fr;
}

.empty-projection {
  display: grid;
  justify-items: center;
  padding-bottom: 18px;
}

@media (max-width: 1050px) {
  .module-grid {
    grid-template-columns: repeat(2, minmax(180px, 1fr));
  }
}

@media (max-width: 720px) {
  .execution-panel {
    padding: 14px;
  }

  .action-grid,
  .module-grid {
    grid-template-columns: 1fr;
  }

  .panel-header,
  .title-line,
  .action-row,
  .target-row {
    align-items: flex-start;
  }

  .header-command-row {
    width: 100%;
    justify-content: flex-start;
  }

  .settings-dialog-grid {
    grid-template-columns: 1fr;
  }

  .title-line,
  .action-row,
  .target-row {
    flex-direction: column;
  }

  .action-target {
    text-align: left;
  }

  .target-row,
  .pending-list > div {
    grid-template-columns: 1fr;
  }

  .target-row small,
  .pending-card {
    grid-column: auto;
  }
}
</style>
