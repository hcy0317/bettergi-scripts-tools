<script setup>
import {computed,onBeforeUnmount,onMounted,ref,watch} from 'vue'
import {characterLabel,profileLabel,jobLabels,buildLabel} from '@/features/artifact-optimizer/localization.js'
import {applyNativeImport,nativeImportTicket,nativeAssumptionLabel} from '@/features/artifact-optimizer/native-flow.js'
import * as api from '@/api/artifact/artifactOptimizer.js'
import RoundReport from './OptimizerRoundReport.vue'
import NativeSource from './OptimizerNativeSource.vue'
import {metric,resultLabels} from '@/features/artifact-optimizer/model.js'
import {validateRotation} from '@/features/artifact-optimizer/validation.js'
const props=defineProps({wallTimeSeconds:{type:Number,default:120},catalog:{type:Object,default:()=>({})},uid:{type:String,required:true},workspace:{type:Object,required:true},selected:{type:Array,default:()=>[]},snapshot:{type:Object,default:null},snapshotId:{type:String,default:''},equipmentJob:{type:Object,default:null},beforeRun:{type:Function,required:true}})
const emit=defineEmits(['locate-issue','validation-issues'])
const buildId=ref(props.workspace.builds[0]?.id||''),sourceName=ref(''),sourceText=ref(''),strategies=ref([]),actions=ref([]),error=ref(''),issues=ref([]),loading=ref(false),job=ref(null),preset=ref('moderate'),preview=ref(null),budget=ref(48),usePlan=ref(false)
const sourceInfo=ref(null),sectionId=ref('whole'),referenceAccepted=ref(false)
let generation=0,timer=null,restoring=false
let validationRevision=0
watch([()=>props.workspace,()=>props.selected,()=>props.snapshotId,()=>props.catalog],()=>validationRevision++,{deep:true,flush:'sync'})
const build=computed(()=>props.workspace.builds.find(b=>b.id===buildId.value))
const members=computed(()=>build.value?.members||[])
const rotationSelection=computed(()=>props.selected.filter(key=>members.value.some(member=>member.character===key)))
const inputProblems=computed(()=>validateRotation(props.workspace,buildId.value,rotationSelection.value,props.snapshot,props.catalog,props.workspace.computeSettings||{}))
const nativeEnabled=computed(()=>Boolean(build.value?.nativeRotation?.enabled))
const running=computed(()=>['QUEUED','RUNNING'].includes(job.value?.state))
const result=computed(()=>job.value?.result)
const qualityRows=computed(()=>{
  const before=result.value?.baseline?.nativeQuality,after=result.value?.report?.nativeQuality
  if(!after)return []
  return [['minCriticalShieldCoverage','关键输出护盾覆盖',true],['minShieldCoverage','全程护盾覆盖',true],['maxFailedRounds','最差失败轮数',false],['maxDamageGapSeconds','最长无伤害间隔（秒）',false],['minPartyHp','最低队伍生命比例',true]].map(([key,label,percent])=>({label,before:before?.[key]==null?'—':metric(before[key]*(percent?100:1))+(percent?'%':''),after:after[key]==null?'—':metric(after[key]*(percent?100:1))+(percent?'%':'')}))
})
const history=ref([])
watch(actions,value=>{if(!restoring&&build.value)build.value.rotationActions=JSON.parse(JSON.stringify(value))},{deep:true,flush:'sync'})
function loadActions(){restoring=true;actions.value=JSON.parse(JSON.stringify(build.value?.rotationActions||[]));restoring=false}
function stopPoll(){if(timer!==null)clearTimeout(timer);timer=null}
function reset(){
  generation++;stopPoll();loadActions();job.value=null;preview.value=null;issues.value=[];error.value='';loading.value=false
  sourceName.value='';sourceInfo.value=null;sectionId.value='whole';sourceText.value=build.value?.nativeRotation?.source||'';referenceAccepted.value=false
}
watch(()=>props.uid,()=>{reset();loadHistory()});watch(buildId,reset)
watch(preview,()=>referenceAccepted.value=false)
async function discover(){
  const version=generation;loading.value=true;error.value=''
  try{const value=await api.listCombatStrategies();if(version===generation)strategies.value=value}
  catch(e){if(version===generation)error.value=e.message||String(e)}
  finally{if(version===generation)loading.value=false}
}
async function readSource(){
  const version=generation,name=sourceName.value;sourceInfo.value=null;if(!name)return
  loading.value=true;error.value=''
  try{const value=await api.readCombatStrategy(name);if(version!==generation||sourceName.value!==name)return;sourceInfo.value=value;sectionId.value='whole';sourceText.value=value.originalScript}
  catch(e){if(version===generation)error.value=e.message||String(e)}finally{if(version===generation)loading.value=false}
}
watch(sourceName,readSource)
watch(sectionId,id=>{const section=sourceInfo.value?.sections?.find(s=>s.id===id);if(section)sourceText.value=section.source})
function add(){actions.value.push({character:members.value[0]?.character||'',kind:'skill'})}
function kindChanged(a){if(['attack_seconds','wait'].includes(a.kind))a.seconds=2;else delete a.seconds}
async function importSource(){
  const version=generation,account=props.uid,source=sourceText.value,id=buildId.value
  if(!(await props.beforeRun())||version!==generation||account!==props.uid||id!==buildId.value||source!==sourceText.value)return
  const ticket=nativeImportTicket(account,build.value,source)
  loading.value=true;error.value=''
  try{
    const parsed=await api.importCombatStrategy(account,{buildId:id,source})
    if(version!==generation||!ticket.matches(props.uid,build.value,sourceText.value))return
    issues.value=[...(parsed.issues||[]),...(parsed.note?[parsed.note]:[])]
    if(parsed.supported&&parsed.mode==='native_flow')applyNativeImport(build.value,parsed,sourceName.value||'粘贴的策略')
    else if(parsed.supported){if(build.value.nativeRotation)build.value.nativeRotation.enabled=false;actions.value=parsed.actions}
    else error.value='策略尚不能完整转换，未覆盖当前流程。请按下方行号检查；原文会保留。'
  }catch(e){if(version===generation)error.value=e.message||String(e)}finally{if(version===generation)loading.value=false}
}
async function poll(id,version,account){
  try{const value=await api.loadOptimization(account,id);if(version!==generation)return;job.value=value;if(['QUEUED','RUNNING'].includes(value.state))timer=setTimeout(()=>poll(id,version,account),1500)}
  catch(e){if(version===generation)error.value=e.message||String(e)}
}
async function start(){
  const version=generation,account=props.uid,id=buildId.value
  if(inputProblems.value.length){error.value='请先修正当前方案的问题';emit('locate-issue',inputProblems.value[0]);return}
  if(!(await props.beforeRun())||version!==generation||account!==props.uid||id!==buildId.value)return
  if(inputProblems.value.length){error.value='输入已变化，请先修正当前方案的问题';return}
  const submittedValidation=validationRevision
  loading.value=true;error.value='';preview.value=null
  try{
    const value=await api.startRotation(account,{workspaceVersion:props.workspace.version,buildId:id,snapshotId:props.snapshotId,characters:rotationSelection.value,actions:nativeEnabled.value?[]:JSON.parse(JSON.stringify(actions.value)),rotationBudget:budget.value,wallTimeSeconds:props.wallTimeSeconds,...props.workspace.computeSettings,equipmentJobId:usePlan.value?props.equipmentJob?.id:''})
    if(version!==generation)return;job.value=value;await poll(value.id,version,account)
  }catch(e){if(version===generation){error.value=submittedValidation===validationRevision?e.message||String(e):'输入已改变，已忽略旧的校验结果';const problems=e.response?.data?.data;if(submittedValidation===validationRevision&&Array.isArray(problems)&&problems.length){emit('validation-issues',problems);emit('locate-issue',problems[0])}}}finally{if(version===generation)loading.value=false}
}
async function cancel(){const id=job.value?.id,version=generation;try{const value=await api.cancelOptimization(props.uid,id);if(version===generation){job.value=value;stopPoll()}}catch(e){if(version===generation)error.value=e.message||String(e)}}
async function createPreview(){
  const version=generation,account=props.uid,id=job.value?.id,requestedPreset=preset.value
  error.value='';preview.value=null
  try{
    if(!id||!(await props.beforeRun())||version!==generation||job.value?.id!==id)return null
    const ticket=nativeImportTicket(account,build.value,sourceText.value)
    const value=await api.previewRotation(account,id,requestedPreset)
    if(version!==generation||job.value?.id!==id||!ticket.matches(props.uid,build.value,sourceText.value)||preset.value!==requestedPreset)return null
    preview.value=value;return value
  }catch(e){if(version===generation)error.value=e.message||String(e);return null}
}
function download(){
  if(!preview.value||(preview.value.simulationOnly&&!referenceAccepted.value))return
  const blob=new Blob([preview.value.script],{type:'text/plain;charset=utf-8'}),url=URL.createObjectURL(blob),a=document.createElement('a')
  a.href=url;a.download=`${preview.value.simulationOnly?'参考-原生流程':'gcsim'}-${job.value.id}.txt`;a.click();setTimeout(()=>URL.revokeObjectURL(url),1000)
}
async function applyToBuild(){
  const id=job.value?.id,target=props.workspace.builds.find(b=>b.id===job.value?.buildId)
  if(!target)return
  if(result.value?.native){
    const value=await createPreview()
    if(!value||job.value?.id!==id)return
    if(target.nativeRotation?.source!==value.baseSource){error.value='原生流程正文已编辑，请重新计算，避免覆盖新内容';return}
    target.nativeRotation.source=value.script;target.nativeRotation.enabled=true
  }else if(result.value?.script){
    if(!(await props.beforeRun())||job.value?.id!==id)return
    if(props.workspace.version!==job.value.workspaceVersion){error.value='方案已修改，请重新计算';return}
    target.rotation=result.value.script;if(target.nativeRotation)target.nativeRotation.enabled=false
    target.rotationActions=JSON.parse(JSON.stringify(result.value.actions));loadActions()
  }
}
async function loadHistory(){const version=generation,account=props.uid;try{const values=await api.listOptimizations(account);if(version===generation)history.value=values.filter(j=>j.kind==='rotation')}catch(e){if(version===generation)error.value=e.message||String(e)}}
async function showHistory(id){const version=generation,account=props.uid;try{const value=await api.loadOptimization(account,id);if(version!==generation)return;job.value=value;preview.value=null;stopPoll();if(['QUEUED','RUNNING'].includes(value.state))await poll(id,version,account)}catch(e){if(version===generation)error.value=e.message||String(e)}}
onMounted(()=>{loadActions();sourceText.value=build.value?.nativeRotation?.source||'';loadHistory();discover()})
onBeforeUnmount(()=>{generation++;stopPoll()})
</script>

<template>
  <section class="rotation-panel">
    <header><h2>战斗循环自动优化</h2><p>复用同一队伍的个人条件、场景与装备，以实际整队每秒伤害比较，并用独立样本验证。</p></header>
    <el-select v-if="history.length" placeholder="查看或恢复循环计算" :model-value="job?.id" @change="showHistory"><el-option v-for="h in history" :key="h.id" :value="h.id" :label="`${h.createdAt} ${jobLabels[h.state]||'未知状态'}`"/></el-select>
    <el-alert v-if="error" :title="error" type="error" :closable="false"/>
    <div v-if="inputProblems.length" class="input-problems" aria-label="当前循环的阻断问题"><p>请先修正以下问题，当前不会进入计算：</p><div v-for="(issue,i) in inputProblems" :key="i"><el-button link type="danger" @click="emit('locate-issue',issue)">{{ issue.message }}</el-button></div></div>
    <el-form label-position="top" class="setup-grid">
      <el-form-item label="配队方案"><el-select v-model="buildId"><el-option v-for="b in workspace.builds" :key="b.id" :value="b.id" :label="buildLabel(b)"/></el-select></el-form-item>
      <el-form-item label="装备输入"><el-switch v-model="usePlan" :disabled="!equipmentJob?.result?.plan?.qualified" active-text="最近合格配装" inactive-text="扫描时实装"/></el-form-item>
      <el-form-item label="候选预算"><el-input-number v-model="budget" :min="4" :max="512" :step="16"/></el-form-item>
      <el-form-item v-if="build" label="每场模拟的循环次数"><el-input-number v-model="build.roundCount" :min="1" :max="64" :precision="0" :disabled="running"/></el-form-item>
    </el-form>
    <p class="hint">背包：{{ snapshotId||'未选择' }}。本方案参选角色：{{ rotationSelection.map(k=>characterLabel(catalog,k)).join('、')||'请先勾选本方案的角色' }}。只优化当前方案，不编译其他关联方案，也不改变圣遗物归属。</p>
    <el-collapse>
      <el-collapse-item title="接入已有 BetterGI 战斗策略" name="import">
        <div class="source-toolbar">
          <el-button :loading="loading" @click="discover">读取策略目录</el-button>
          <el-select v-model="sourceName" filterable placeholder="选择现有 .txt / .json 策略"><el-option v-for="name in strategies" :key="name" :value="name" :label="name"/></el-select>
          <el-button :disabled="!sourceName" :loading="loading" @click="readSource">读取原文</el-button>
          <el-button :disabled="!sourceText||!buildId" :loading="loading" @click="importSource">检查并导入流程</el-button>
        </div>
        <p class="hint">支持子文件夹。读取原文不需要建队；导入时检查本队角色及整个控制流程。00-水请选择完整原文，保留其全部定义段。</p>
        <template v-if="sourceInfo">
          <el-alert :title="sourceInfo.note" type="info" :closable="false"/>
          <label>检查范围 <el-select v-model="sectionId" aria-label="策略片段"><el-option v-for="section in sourceInfo.sections" :key="section.id" :value="section.id" :label="section.name"/></el-select></label>
        </template>
        <el-input v-model="sourceText" type="textarea" :rows="7" aria-label="原生战斗策略正文" placeholder="也可直接粘贴完整策略"/>
        <el-button class="pasted-import" :disabled="!sourceText||!buildId" :loading="loading" @click="importSource">解析粘贴内容</el-button>
        <p class="hint">原文件保留；候选允许重排、删除可选输出和化简等价分支，再核验保护依赖及护盾/鲁棒/进展指标。那维莱特的完整喷射宏按gcsim标准重击试算，不模拟镜头扫射命中。</p>
      </el-collapse-item>
    </el-collapse>
    <el-alert v-for="issue in issues" :key="issue" :title="issue" type="warning" :closable="false" class="issue"/>
    <NativeSource v-if="build" :build="build"/>
    <template v-if="nativeEnabled">
      <h3>在保护条件下优化循环</h3>
      <p class="hint">每个候选执行本方案指定的循环次数，按实际耗时比较DPS。允许受保护依赖约束的重排、可选动作删除、等价分支化简及等待 / 普攻时段调整；保留必需开场、补盾和完整宏，再复评护盾覆盖与流程进展。</p>
    </template>
    <template v-else>
      <h3>可优化的一轮有限动作</h3>
      <p class="hint">支持战技、爆发、有明确秒数的普攻与等待。这个模式搜索动作顺序；需要守卫时请导入完整原生流程。</p>
      <el-empty v-if="!actions.length" description="导入现有策略，或添加有限动作后搜索。" :image-size="70"/>
      <div v-for="(a,i) in actions" :key="i" class="action-row">
        <span class="order">{{ i+1 }}</span>
        <el-select v-model="a.character" aria-label="动作角色"><el-option v-for="m in members" :key="m.character" :value="m.character" :label="profileLabel(catalog,workspace.characters.find(c=>c.key===m.character)||{key:m.character})"/></el-select>
        <el-select v-model="a.kind" aria-label="动作类型" @change="kindChanged(a)"><el-option value="skill" label="元素战技 E"/><el-option value="burst" label="元素爆发 Q"/><el-option value="attack_seconds" label="普攻时段"/><el-option value="wait" label="等待"/></el-select>
        <el-input-number v-if="['attack_seconds','wait'].includes(a.kind)" v-model="a.seconds" :min="0.25" :max="30" :step="0.25" aria-label="动作秒数"/>
        <el-button text :disabled="i===0" @click="[actions[i-1],actions[i]]=[actions[i],actions[i-1]]">上移</el-button><el-button text type="danger" @click="actions.splice(i,1)">移除</el-button>
      </div>
    </template>
    <div class="actions">
      <el-button v-if="!nativeEnabled" :disabled="!buildId||actions.length>=80" @click="add">添加动作</el-button>
      <el-button v-if="running" type="danger" plain @click="cancel">取消循环优化</el-button>
      <el-button type="primary" :loading="loading" :disabled="(!nativeEnabled&&!actions.length)||!rotationSelection.length||!snapshotId||running||inputProblems.length>0" @click="start">自动搜索循环</el-button>
    </div>
    <section v-if="job" class="rotation-result" aria-live="polite">
      <h3>{{ result?resultLabels[result.status]||result.status:({QUEUED:'等待计算',RUNNING:'正在搜索循环',CANCELLED:'循环优化已取消',FAILED:'循环优化失败'}[job.state]||job.state) }}</h3>
      <el-alert v-if="job.error" :title="job.error" type="error" :closable="false"/><el-skeleton v-if="running" :rows="4" animated/>
      <template v-if="result?.report">
        <RoundReport :report="result.report"/>
        <el-table v-if="result.nativeTradeoffs?.length" :data="result.nativeTradeoffs"><el-table-column label="未推荐的取舍示例：DPS"><template #default="{row}">{{ metric(row.dps) }}</template></el-table-column><el-table-column label="当时基线DPS"><template #default="{row}">{{ metric(row.baselineDps) }}</template></el-table-column><el-table-column prop="reason" label="未推荐原因"/></el-table>
        <el-table v-if="result.nativeProbes?.length" :data="result.nativeProbes"><el-table-column label="受控扰动复评"><template #default="{row}">{{ row.kind==='input_delay_200ms'?'输入延迟200ms':'每人首次Q调用丢失' }}</template></el-table-column><el-table-column prop="samples" label="独立测试样本数"/><el-table-column label="候选结果"><template #default="{row}">{{ row.passed?'通过':'未通过，候选未推荐' }}</template></el-table-column><el-table-column prop="baselineError" label="基线检查问题"/><el-table-column prop="candidateError" label="候选检查问题"/></el-table>
        <div class="dps"><span>基线每秒伤害 <strong>{{ metric(result.baseline?.meanDps) }}</strong></span><span>候选每秒伤害 <strong>{{ metric(result.report.meanDps) }}</strong></span><span>实际评估 <strong>{{ result.evaluations }}</strong></span></div>
        <el-alert title="独立验证仅针对本批模拟样本；带假设试算不等于实机已验证" type="info" :closable="false"/>
        <el-alert v-if="result.status==='feasible_uncertain'" title="改善尚不确定，保留原基线供比较。" type="warning" :closable="false"/>
        <template v-if="result.native"><el-table :data="qualityRows"><el-table-column prop="label" label="模拟安全与通畅指标"/><el-table-column prop="before" label="基线"/><el-table-column prop="after" label="候选"/></el-table><p class="hint">来自当前配置下的SDK护盾、动作和生命值，不代表实机观测或未配置敌人攻击下的生存保证。</p><el-table :data="result.nativeEdits||[]" empty-text="没有推荐结构变更"><el-table-column label="结构操作"><template #default="{row}">{{ {swap:'重排相邻节点',drop:'删除可选/冗余节点',collapse_branch:'化简同目标分支'}[row.kind]||row.kind }}</template></el-table-column><el-table-column prop="block" label="流程块"/><el-table-column prop="node" label="原节点"/><el-table-column prop="other" label="相邻节点"/></el-table><el-table :data="result.nativeChanges||[]" empty-text="没有推荐数值变更"><el-table-column prop="line" label="原文行号"/><el-table-column label="参数"><template #default="{row}">{{ row.kind==='wait'?'等待':'普攻时段' }}</template></el-table-column><el-table-column label="原秒数" prop="original"/><el-table-column label="建议秒数" prop="value"/></el-table></template>
        <el-input v-else :model-value="result.script" type="textarea" :rows="8" readonly aria-label="优化后的 gcsim 循环"/>
        <el-button @click="applyToBuild">应用到此方案的模拟循环</el-button>
        <div class="preview-controls">
          <el-radio-group v-if="!result.native" v-model="preset"><el-radio-button value="relaxed">宽松</el-radio-button><el-radio-button value="moderate">适中</el-radio-button><el-radio-button value="strict">严格</el-radio-button></el-radio-group>
          <el-button @click="createPreview">{{ result.native?'预览保留守卫的参考策略':'预览执行策略' }}</el-button>
        </div>
        <template v-if="preview">
          <el-alert :title="preview.note" type="warning" :closable="false"/>
          <el-alert v-for="a in preview.assumptions||[]" :key="a" :title="nativeAssumptionLabel(a)" type="warning" :closable="false"/>
          <el-input :model-value="preview.script" type="textarea" :rows="10" readonly aria-label="BetterGI 策略参考预览"/>
          <el-checkbox v-if="preview.simulationOnly" v-model="referenceAccepted">我理解这是带假设的参考策略，使用前需要实机复核</el-checkbox>
          <p class="hint">不会自动启动战斗或覆盖原策略文件。</p>
          <el-button type="primary" :disabled="preview.simulationOnly&&!referenceAccepted" @click="download">{{ preview.simulationOnly?'下载参考策略':'确认并下载新策略' }}</el-button>
        </template>
      </template>
      <el-alert v-if="result" v-for="issue in result.issues||[]" :key="issue" :title="issue" type="warning" :closable="false"/>
    </section>
  </section>
</template>

<style scoped>
h2{font-size:21px}h3{font-size:16px}header p,.hint{font-size:13px;color:var(--el-text-color-secondary);line-height:1.7}.setup-grid{display:grid;grid-template-columns:2fr 2fr 1fr;gap:18px}.el-select{width:100%}.source-toolbar,.actions,.preview-controls{display:flex;gap:12px;align-items:center;flex-wrap:wrap;margin:16px 0}.source-toolbar .el-select{flex:1;min-width:220px}.pasted-import{margin-top:12px}.issue{margin-top:8px}.action-row{display:flex;gap:10px;align-items:center;margin:12px 0}.action-row .el-select{max-width:230px}.order{font-variant-numeric:tabular-nums;width:26px}.rotation-result{border-top:1px solid var(--el-border-color);margin-top:24px;padding-top:16px}.dps{display:flex;gap:32px;margin:20px 0}.dps span{display:grid;gap:8px;font-size:12px;color:var(--el-text-color-secondary)}.dps strong{font-size:25px;color:var(--el-text-color-primary);font-variant-numeric:tabular-nums}.rotation-result :deep(textarea){font-family:Consolas,monospace;line-height:1.7;margin:14px 0}@media(max-width:760px){.setup-grid{grid-template-columns:1fr}.action-row{flex-wrap:wrap}.action-row .el-select{max-width:100%;flex:1 1 130px}.dps{gap:20px}}
</style>
