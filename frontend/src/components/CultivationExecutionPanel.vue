<script setup>
import {computed, onBeforeUnmount, onMounted, ref, watch} from 'vue'
import {ElMessage, ElMessageBox} from 'element-plus'
import {Connection, MagicStick, Refresh, Setting, VideoPlay} from '@element-plus/icons-vue'
import router from '@router/router.js'
import CultivationMaterialProgress from '@/components/cultivation/CultivationMaterialProgress.vue'
import CultivationOptionManager from '@/components/cultivation/CultivationOptionManager.vue'
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
const AUTO_SYNC_INTERVAL_MS = 5000
let autoSyncTimer = null
let autoSyncing = false
let loadGeneration = 0

const partyOptions = computed(() => {
  const result = new Set(projection.value?.partyOptions || [])
  const hidden = new Set()
  modules.value.forEach(module => {
    ;(module.settings?.hiddenPartyOptions || []).forEach(value => hidden.add(value))
    Object.entries(module.settings || {})
      .filter(([key]) => key.toLowerCase().includes('party') && !key.toLowerCase().includes('hidden'))
      .forEach(([, value]) => {
        if (Array.isArray(value)) value.filter(Boolean).forEach(item => result.add(item))
        else if (value) result.add(value)
      })
  })
  return Array.from(result).filter(value => !hidden.has(value))
})
const combatStrategyOptions = computed(() => {
  const result = new Set(projection.value?.combatStrategyOptions || [])
  const hidden = new Set()
  modules.value.forEach(module => {
    ;(module.settings?.hiddenCombatStrategyOptions || []).forEach(value => hidden.add(value))
    Object.entries(module.settings || {})
      .filter(([key]) => key.toLowerCase().includes('strategy') && !key.toLowerCase().includes('hidden'))
      .forEach(([, value]) => {
        if (Array.isArray(value)) value.filter(Boolean).forEach(item => result.add(item))
        else if (value) result.add(value)
      })
  })
  hidden.delete('根据队伍自动选择')
  return Array.from(result).filter(value => !hidden.has(value))
})
const progressByName = computed(() => new Map(
  (projection.value?.materialProgress || []).map(item => [item.materialName, item])))
const progressFor = materialName => {
  const progress = progressByName.value.get(materialName)
  if (!progress) return []
  return (projection.value?.materialProgress || [])
    .filter(item => item.familyName === progress.familyName)
    .sort((left, right) => (left.tierIndex ?? 0) - (right.tierIndex ?? 0))
}
const groupByMaterialFamily = (items, discriminator = () => '') => {
  const seen = new Set()
  return (items || []).filter(item => {
    const progress = progressByName.value.get(item.materialName)
    const key = `${progress?.familyName || item.materialName}|${discriminator(item)}`
    if (seen.has(key)) return false
    seen.add(key)
    return true
  })
}
const resinActions = computed(() => groupByMaterialFamily(
  projection.value?.resinActions, action => action.sourceName))
const gatherTargets = computed(() => groupByMaterialFamily(projection.value?.gatherAction?.csvTargets))
const monsterTargets = computed(() => groupByMaterialFamily(
  projection.value?.monsterAction?.targets, target => target.routeFamily))
const bossActions = computed(() => groupByMaterialFamily(
  projection.value?.bossActions, action => action.bossName))
const weeklyBossActions = computed(() => groupByMaterialFamily(
  projection.value?.weeklyBossActions, action => action.bossName))
const craftingActions = computed(() => groupByMaterialFamily(projection.value?.craftingActions))
const pendingMaterials = computed(() => groupByMaterialFamily(projection.value?.pendingMaterials))
const hasProjection = computed(() => Boolean(projection.value))
const displayModules = computed(() => [...modules.value].sort((left, right) => {
  if (left.module.moduleId === GROUP_SETTINGS_MODULE_ID) return -1
  if (right.module.moduleId === GROUP_SETTINGS_MODULE_ID) return 1
  return 0
}))
const isGroupSettings = module => module.module.moduleId === GROUP_SETTINGS_MODULE_ID
const isAutoPlan = module => module.module.moduleId === 'auto-plan-resin'

const load = async (silent = false) => {
  const generation = ++loadGeneration
  const uid = props.uid.trim()
  if (!silent) {
    projection.value = null
    modules.value = []
    loadError.value = ''
  }
  if (!uid) return
  if (!silent) loading.value = true
  try {
    const [nextProjection, moduleConfigurations] = await Promise.all([
      getCultivationExecutionProjection(uid),
      getCultivationExecutionModules(uid)
    ])
    if (generation !== loadGeneration) return
    projection.value = nextProjection
    modules.value = Array.isArray(moduleConfigurations) ? moduleConfigurations : []
  } catch (error) {
    if (!silent && generation === loadGeneration) {
      loadError.value = error?.message || '无法读取养成执行计划'
    }
  } finally {
    if (!silent && generation === loadGeneration) loading.value = false
  }
}

const autoSync = async () => {
  if (document.hidden || loading.value || settingsDialogOpen.value || savingModuleId.value
      || syncingModuleId.value || preparing.value || starting.value || autoSyncing) return
  autoSyncing = true
  try {
    await load(true)
  } finally {
    autoSyncing = false
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
const visibleModuleFields = module => moduleFields(module).filter(field => field.control !== 'hidden')
const settingsPayload = module => Object.fromEntries(
  moduleFields(module)
    .filter(field => field.editable)
    .map(field => [field.key, module.settings[field.key]])
)
const fieldOptions = field => {
  if (field.optionsSource === 'uid-parties') return partyOptions.value
  if (field.optionsSource === 'combat-strategies') return combatStrategyOptions.value
  if (field.optionsSource === 'monster-route-families') {
    return projection.value?.monsterAction?.availableRouteFamilies || []
  }
  return field.options || []
}
const optionManagerHiddenKey = field => field.key === 'managedPartyOptions'
  ? 'hiddenPartyOptions'
  : 'hiddenCombatStrategyOptions'
const optionManagerValues = (module, key) => Array.isArray(module.settings[key]) ? module.settings[key] : []
const updateOptionManagerValues = (module, key, value) => {
  module.settings[key] = value
}
const orderedValues = (module, field) => {
  const value = module.settings[field.key]
  if (!Array.isArray(value)) module.settings[field.key] = []
  return module.settings[field.key]
}
const moveOrderedValue = (module, field, index, offset) => {
  const values = [...orderedValues(module, field)]
  const target = index + offset
  if (target < 0 || target >= values.length) return
  ;[values[index], values[target]] = [values[target], values[index]]
  module.settings[field.key] = values
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

onMounted(() => {
  autoSyncTimer = window.setInterval(() => void autoSync(), AUTO_SYNC_INTERVAL_MS)
})

onBeforeUnmount(() => {
  if (autoSyncTimer !== null) window.clearInterval(autoSyncTimer)
})

watch(() => props.uid, () => load(), {immediate: true})
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
          width="920px"
          class="module-settings-dialog"
          append-to-body
          destroy-on-close
      >
          <div v-if="editingModule" class="settings-dialog-grid">
          <label
              v-for="field in visibleModuleFields(editingModule)"
              :key="field.key"
              class="module-field"
              :class="{'module-field-wide': field.control === 'option-manager' || field.control === 'ordered-multi-select'}"
          >
            <span>{{ field.label }}<small v-if="!field.editable">由账本自动生成</small></span>
            <CultivationOptionManager
                v-if="field.control === 'option-manager'"
                :model-value="optionManagerValues(editingModule, field.key)"
                :hidden-values="optionManagerValues(editingModule, optionManagerHiddenKey(field))"
                :detected-options="fieldOptions(field)"
                :placeholder="field.optionsSource === 'uid-parties' ? '输入讨伐队伍名称' : '输入战斗策略名称'"
                :protected-values="field.optionsSource === 'combat-strategies' ? ['根据队伍自动选择'] : []"
                @update:model-value="updateOptionManagerValues(editingModule, field.key, $event)"
                @update:hidden-values="updateOptionManagerValues(editingModule, optionManagerHiddenKey(field), $event)"
            />
            <div v-else-if="field.control === 'ordered-multi-select'" class="ordered-multi-select">
              <el-checkbox-group v-model="editingModule.settings[field.key]">
                <el-checkbox v-for="option in fieldOptions(field)" :key="option" :value="option">
                  {{ option }}
                </el-checkbox>
              </el-checkbox-group>
              <ol class="ordered-selection-list">
                <li v-for="(option, index) in orderedValues(editingModule, field)" :key="option">
                  <span>{{ index + 1 }}. {{ option }}</span>
                  <div>
                    <el-button size="small" :disabled="index === 0"
                               @click.prevent="moveOrderedValue(editingModule, field, index, -1)">上移</el-button>
                    <el-button size="small" :disabled="index === orderedValues(editingModule, field).length - 1"
                               @click.prevent="moveOrderedValue(editingModule, field, index, 1)">下移</el-button>
                  </div>
                </li>
              </ol>
              <small>勾选即启用；上方顺序就是实际消耗优先级，未勾选的树脂不会使用。</small>
            </div>
            <el-select
                v-else-if="field.control === 'party-select' || field.control === 'strategy-select' || field.control === 'select'"
                v-model="editingModule.settings[field.key]"
                :filterable="field.control === 'party-select' || field.control === 'strategy-select'"
                :allow-create="field.control === 'party-select' || field.control === 'strategy-select'"
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
            <el-tag effect="plain">{{ resinActions.length }} 项</el-tag>
          </div>
          <div v-if="resinActions.length" class="action-list">
            <div v-for="action in resinActions" :key="`${action.materialName}-${action.sourceName}`" class="plan-entry">
              <header class="entry-heading">
                <div>
                  <strong>{{ action.sourceName }}</strong>
                  <span>{{ action.actionType }} · {{ action.sourceType }}</span>
                </div>
              </header>
              <CultivationMaterialProgress :items="progressFor(action.materialName)"/>
            </div>
          </div>
          <el-empty v-else :image-size="64" description="当前没有可投影的体力行动"/>
        </article>

        <article class="action-card crafting-card">
          <div class="card-heading">
            <h3>材料合成</h3>
            <el-tag :type="craftingActions.length ? 'warning' : 'info'" effect="plain">
              {{ craftingActions.length }} 项
            </el-tag>
          </div>
          <p class="adapter-name">按 3:1 通路合成；每一级需求均单独保留</p>
          <div v-if="craftingActions.length" class="action-list">
            <div v-for="action in craftingActions" :key="action.materialName" class="plan-entry">
              <header class="entry-heading">
                <div>
                  <strong>合成 {{ action.materialName }} × {{ action.quantity.toLocaleString() }}</strong>
                  <span>{{ action.materialType }}</span>
                </div>
              </header>
              <CultivationMaterialProgress :items="progressFor(action.materialName)"/>
            </div>
          </div>
          <el-empty v-else :image-size="64" description="当前没有需要执行的材料合成"/>
        </article>

        <article class="action-card boss-card">
          <div class="card-heading">
            <h3>世界首领行动</h3>
            <el-tag :type="bossActions.length ? 'warning' : 'info'" effect="plain">{{ bossActions.length }} 项</el-tag>
          </div>
          <p class="adapter-name">执行模块：AutoPlan 首领任务</p>
          <div v-if="bossActions.length" class="action-list">
            <div v-for="action in bossActions" :key="action.materialName" class="plan-entry">
              <header class="entry-heading">
                <div>
                  <strong>{{ action.bossName }}</strong>
                  <span>{{ action.country }} · 队伍 {{ action.partyName || '沿用默认' }}</span>
                </div>
              </header>
              <CultivationMaterialProgress :items="progressFor(action.materialName)"/>
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
            <div v-for="target in gatherTargets" :key="target.materialName" class="plan-entry">
              <header class="entry-heading">
                <div><strong>{{ target.country }}</strong><span>地方特产路线</span></div>
              </header>
              <CultivationMaterialProgress :items="progressFor(target.materialName)"/>
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
            <div v-for="target in monsterTargets" :key="target.materialName" class="plan-entry">
              <header class="entry-heading">
                <div>
                  <strong>{{ target.routeFamily }}</strong>
                  <span>{{ target.monsters.slice(0, 3).join('、') }}{{ target.monsters.length > 3 ? ` 等 ${target.monsters.length} 种怪物` : '' }}</span>
                </div>
              </header>
              <CultivationMaterialProgress :items="progressFor(target.materialName)"/>
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
            <div v-for="action in weeklyBossActions" :key="action.materialName" class="plan-entry">
              <header class="entry-heading">
                <div>
                  <strong>{{ action.bossName }}</strong>
                  <span>{{ action.actionState }}</span>
                </div>
              </header>
              <CultivationMaterialProgress :items="progressFor(action.materialName)"/>
            </div>
          </div>
          <el-empty v-else :image-size="64" description="当前没有周本材料缺口"/>
        </article>

        <article v-if="pendingMaterials.length" class="action-card pending-card">
          <div class="card-heading">
            <h3>人工与未自动化材料</h3>
            <el-tag type="info" effect="plain">{{ pendingMaterials.length }} 项</el-tag>
          </div>
          <div class="pending-list">
            <div v-for="item in pendingMaterials" :key="item.materialName" class="plan-entry">
              <p class="pending-reason">{{ item.reason }}</p>
              <CultivationMaterialProgress :items="progressFor(item.materialName)"/>
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
.entry-heading {
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
.entry-heading {
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
  column-count: 3;
  column-gap: 12px;
}

.band-heading p,
.module-card p {
  margin: 3px 0 0;
  color: #68717c;
  font-size: 12px;
}

.module-card {
  display: grid;
  grid-auto-rows: max-content;
  align-content: start;
  gap: 10px;
  min-width: 0;
  margin-bottom: 12px;
  padding: 14px;
  background: var(--el-fill-color-blank);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: var(--el-border-radius-base);
  break-inside: avoid;
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

.module-field-wide {
  grid-column: 1 / -1;
}

.ordered-multi-select {
  display: grid;
  gap: 10px;
  padding: 10px;
  border: 1px solid rgba(70, 78, 88, 0.14);
  border-radius: 8px;
}

.ordered-selection-list {
  display: grid;
  gap: 6px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.ordered-selection-list li {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.settings-dialog-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(280px, 1fr));
  align-items: start;
  gap: 14px 20px;
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
  column-count: 2;
  column-gap: 14px;
}

.action-card {
  min-width: 0;
  margin-bottom: 14px;
  padding: 16px;
  background: var(--el-fill-color-blank);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: var(--el-border-radius-base);
  break-inside: avoid;
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

.plan-entry {
  display: grid;
  gap: 10px;
  padding: 10px 0;
  border-top: 1px solid rgba(70, 78, 88, 0.1);
}

.entry-heading,
.entry-heading > div {
  display: grid;
  gap: 3px;
}

.entry-heading span,
.pending-reason {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.adapter-name {
  margin-top: 8px;
  font-size: 13px;
}

.pending-card {
  column-span: none;
}

.pending-reason {
  margin: 0;
  line-height: 1.5;
}

.empty-projection {
  display: grid;
  justify-items: center;
  padding-bottom: 18px;
}

@media (max-width: 1050px) {
  .module-grid {
    column-count: 2;
  }
}

@media (max-width: 720px) {
  .execution-panel {
    padding: 14px;
  }

  .action-grid,
  .module-grid {
    column-count: 1;
  }

  .panel-header,
  .title-line,
  .entry-heading {
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
  .entry-heading {
    flex-direction: column;
  }
}
</style>
