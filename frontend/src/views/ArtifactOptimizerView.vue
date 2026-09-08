<script setup>
import {computed,nextTick,onBeforeUnmount,onMounted,ref,watch} from 'vue'
import {ElMessage,ElMessageBox} from 'element-plus'
import {ArrowLeft,HomeFilled,Plus,Refresh,Download} from '@element-plus/icons-vue'
import router from '@router/router.js'
import {onBeforeRouteLeave} from 'vue-router'
import UidSelector from '@/components/UidSelector.vue'
import CharacterEditor from '@/components/artifact-optimizer/OptimizerCharacterEditor.vue'
import BuildEditor from '@/components/artifact-optimizer/OptimizerBuildEditor.vue'
import Results from '@/components/artifact-optimizer/OptimizerResults.vue'
import RotationPanel from '@/components/artifact-optimizer/OptimizerRotationPanel.vue'
import EnginePanel from '@/components/artifact-optimizer/OptimizerEnginePanel.vue'
import * as api from '@/api/artifact/artifactOptimizer.js'
import {newCharacter,newBuild,normalizeBuild,mergeEnkaPreview,preferenceOptions,selectedScenarioIds,updateBuildMembers,selectBuildMembers} from '@/features/artifact-optimizer/model.js'
import {validateOptimization} from '@/features/artifact-optimizer/validation.js'
import {protectedInventoryKeys,setInventoryProtections,toggleCharacterProtection,isCharacterProtected} from '@/features/artifact-optimizer/inventory.js'
import {characterLabel,weaponLabel,profileLabel,jobLabels,buildLabel} from '@/features/artifact-optimizer/localization.js'

const uid=ref(''),workspace=ref({version:0,characters:[],builds:[]}),catalog=ref({characters:[],weapons:[],sets:[]})
const loading=ref(false),saving=ref(false),dirty=ref(false),error=ref(''),engineError=ref(''),search=ref(''),activeCharacter=ref(''),activeBuild=ref(''),selected=ref([]),tab=ref('characters')
const snapshots=ref([]),snapshotId=ref(''),snapshot=ref(null),mode=ref('balanced'),budget=ref(256),wallTimeSeconds=ref(120),job=ref(null),history=ref([])
const resultSnapshot=ref(null)
const serverIssues=ref([])
const inputIssues=computed(()=>validateOptimization(workspace.value,selected.value,snapshot.value,catalog.value,workspace.value.computeSettings||{}))
const allIssues=computed(()=>[...inputIssues.value,...serverIssues.value])
const addDialog=ref(false),addKey=ref(''),enkaDialog=ref(false),enka=ref(null),enkaKeys=ref([]),importing=ref(false)
const changingUid=ref(false),uidSelectorKey=ref(0)
let scope=0,pollTimer=null,applying=false,editVersion=0
const character=computed(()=>workspace.value.characters.find(c=>c.key===activeCharacter.value))
const build=computed(()=>workspace.value.builds.find(b=>b.id===activeBuild.value))
const running=computed(()=>['QUEUED','RUNNING'].includes(job.value?.state))
const filteredCharacters=computed(()=>workspace.value.characters.filter(c=>[profileLabel(catalog.value,c),c.key,...(c.tags||[])].join(' ').toLowerCase().includes(search.value.toLowerCase())))
const scenarios=computed(()=>selectedScenarioIds(workspace.value.characters,selected.value).map(id=>workspace.value.builds.find(b=>b.id===id)).filter(Boolean))
const itemList=computed(()=>snapshot.value?.artifacts||[])
const inventoryPeople=computed(()=>catalog.value.inventoryCharacters||[])
const inventoryProtections=computed(()=>protectedInventoryKeys(workspace.value,catalog.value))
function changeInventoryProtections(keys){try{setInventoryProtections(workspace.value,catalog.value,keys)}catch(e){error.value=e.message}}
function changeCharacterProtection(value){try{toggleCharacterProtection(workspace.value,catalog.value,activeCharacter.value,value)}catch(e){error.value=e.message}}
const clone=value=>JSON.parse(JSON.stringify(value))
watch(workspace,()=>{if(!applying){dirty.value=true;editVersion++;serverIssues.value=[]}},{deep:true,flush:'sync'})
function applyWorkspace(value){applying=true;value.buffTemplates ||= [];value.builds.forEach(normalizeBuild);value.computeSettings ||= {searchSamples:3,validationSamples:8};workspace.value=value;applying=false;dirty.value=false;serverIssues.value=[];activeCharacter.value=value.characters[0]?.key||'';activeBuild.value=value.builds[0]?.id||''}
function stopPoll(){if(pollTimer!==null)clearTimeout(pollTimer);pollTimer=null}
async function load(){
  const current=++scope,id=uid.value;stopPoll();job.value=null;snapshot.value=null;selected.value=[];snapshots.value=[];snapshotId.value='';history.value=[];error.value=''
  applyWorkspace({version:0,characters:[],builds:[]});saving.value=false;importing.value=false;enkaDialog.value=false
  if(!id){applyWorkspace({version:0,characters:[],builds:[]});return}
  loading.value=true
  try{const [w,s,h]=await Promise.all([api.loadWorkspace(id),api.loadSnapshots(id),api.listOptimizations(id)]);if(current!==scope)return;applyWorkspace(w);snapshots.value=s;history.value=h.filter(j=>j.kind!=='rotation');snapshotId.value=s.find(s=>s.complete)?.id||'';const active=history.value.find(j=>['QUEUED','RUNNING'].includes(j.state));if(active){job.value=active;poll(active.id,id,current)}}
  catch(e){if(current===scope)error.value=e.message||String(e)}finally{if(current===scope)loading.value=false}
}
watch(uid,load)
async function confirmDiscard(message){
  if(!dirty.value)return true
  try{await ElMessageBox.confirm(message,'有未保存编辑',{type:'warning',confirmButtonText:'放弃编辑并继续',cancelButtonText:'继续编辑'});return true}catch{return false}
}
async function changeUid(next){
  if(next===uid.value||changingUid.value||loading.value||saving.value)return
  changingUid.value=true
  try{
    if(await confirmDiscard('切换账号会放弃当前尚未保存的编辑，是否继续？'))uid.value=next
    else uidSelectorKey.value++
  }finally{changingUid.value=false}
}
onBeforeRouteLeave(()=>confirmDiscard('离开配装页面会放弃尚未保存的编辑，是否继续？'))
function beforeUnload(event){if(dirty.value){event.preventDefault();event.returnValue=''}}
function openAddDialog(){addKey.value='';addDialog.value=true}
watch(selected,()=>serverIssues.value=[])
watch(snapshotId,async id=>{const current=scope,account=uid.value;snapshot.value=null;if(!id)return;try{const value=await api.loadSnapshot(account,id);if(current===scope&&id===snapshotId.value)snapshot.value=value}catch(e){if(current===scope)error.value=e.message||String(e)}})
watch(()=>job.value?.snapshotId,async id=>{const current=scope,account=uid.value;resultSnapshot.value=null;if(!id)return;try{const value=await api.loadSnapshot(account,id);if(current===scope&&job.value?.snapshotId===id)resultSnapshot.value=value}catch(e){if(current===scope)error.value=e.message||String(e)}})
async function reload(){if(dirty.value){try{await ElMessageBox.confirm('重新载入会放弃尚未保存的编辑，是否继续？','重新载入',{type:'warning'})}catch{return}}await load()}
async function loadEngine(){engineError.value='';try{catalog.value=await api.loadCatalog()}catch(e){engineError.value=e.message||String(e)}}
async function save(){
  if(!dirty.value&&workspace.value.version>0)return true
  const current=scope,id=uid.value,payload=clone(workspace.value),revision=editVersion;saving.value=true;error.value=''
  try{const saved=await api.saveWorkspace(id,payload);if(current!==scope)return false;applying=true;workspace.value.version=saved.version;workspace.value.updatedAt=saved.updatedAt;applying=false;dirty.value=editVersion!==revision;if(dirty.value){error.value='保存期间又有编辑，请再次保存后计算';return false}return true}
  catch(e){if(current===scope)error.value=e.message||String(e);return false}finally{if(current===scope)saving.value=false}
}
function addCharacter(){if(!addKey.value||workspace.value.characters.some(c=>c.key===addKey.value))return;const c=newCharacter(addKey.value,characterLabel(catalog.value,addKey.value));workspace.value.characters.push(c);activeCharacter.value=c.key;addDialog.value=false;addKey.value=''}
function addBuild(){const b=newBuild();if(character.value){b.members.push({character:character.value.key,kind:'real_fixed'});b.name=`${character.value.name||character.value.key} 配队`;b.rotation=`active ${character.value.key};\nwhile 1 {\n  ${character.value.key} attack:3;\n}`;character.value.builds.push({id:b.id,weight:1,metric:'damage_per_round',reference:0})}workspace.value.builds.push(b);activeBuild.value=b.id;tab.value='builds'}
function editBuild(id){activeBuild.value=id;tab.value='builds'}
function changeMembers(keys){try{selected.value=updateBuildMembers(workspace.value,activeBuild.value,keys,selected.value)}catch(e){error.value=e.message}}
function useTeam(){selected.value=selectBuildMembers(workspace.value,activeBuild.value,selected.value)}
function selectMember(key,value){if(value){selectBuildMembers(workspace.value,activeBuild.value,[]);selected.value=[...new Set([...selected.value,key])]}else selected.value=selected.value.filter(k=>k!==key)}
async function locateIssue(issue){
  if(issue.buildId){activeBuild.value=issue.buildId;tab.value='builds'}else if(issue.character){activeCharacter.value=issue.character;tab.value='characters'}else if(issue.field==='selection'){tab.value='builds'}else if(['protections','inventoryOwners'].includes(issue.field)){tab.value='characters'}
  await nextTick();
  const scopeElement=issue.buildId&&issue.character?document.querySelector(`[data-optimizer-member="${CSS.escape(issue.character)}"]`):document.querySelector(issue.scope==='character'?'.character-editor':'.build-editor');
  const target=scopeElement?.querySelector(`[data-field="${CSS.escape(issue.field)}"]`)||document.getElementById(`optimizer-${issue.field}`)||scopeElement||document.querySelector('.compute-bar');
  for(let container=target?.closest('details');container;container=container.parentElement?.closest('details'))container.open=true;
  target?.scrollIntoView({behavior:'auto',block:'center'});target?.querySelector('input,textarea,button')?.focus();
}
async function removeCharacter(){try{await ElMessageBox.confirm('移除此角色档案及配队内引用？保存后生效，不影响游戏。','移除角色',{type:'warning'})}catch{return}const key=activeCharacter.value;workspace.value.characters=workspace.value.characters.filter(c=>c.key!==key);workspace.value.builds.forEach(b=>b.members=b.members.filter(m=>m.character!==key));selected.value=selected.value.filter(k=>k!==key);activeCharacter.value=workspace.value.characters[0]?.key||''}
async function removeBuild(){try{await ElMessageBox.confirm('删除此方案及所有角色对它的引用？保存后生效。','删除方案',{type:'warning'})}catch{return}const id=activeBuild.value;workspace.value.builds=workspace.value.builds.filter(b=>b.id!==id);workspace.value.characters.forEach(c=>c.builds=c.builds.filter(b=>b.id!==id));activeBuild.value=workspace.value.builds[0]?.id||''}
async function previewEnka(){const current=scope,account=uid.value;importing.value=true;error.value='';try{const value=await api.enkaPreview(account);if(current!==scope)return;enka.value=value;enkaKeys.value=[];enkaDialog.value=true}catch(e){if(current===scope)error.value=e.message||String(e)}finally{if(current===scope)importing.value=false}}
function acceptEnka(){workspace.value.characters=mergeEnkaPreview(workspace.value.characters,clone(enka.value.characters),enkaKeys.value);activeCharacter.value ||= workspace.value.characters[0]?.key||'';enkaDialog.value=false;ElMessage.success('已合并到本地编辑，请检查并保存')}
async function poll(id,account,current){try{const value=await api.loadOptimization(account,id);if(current!==scope||job.value?.id!==id)return;job.value=value;if(['QUEUED','RUNNING'].includes(value.state))pollTimer=setTimeout(()=>poll(id,account,current),1500);else{const values=await api.listOptimizations(account);if(current===scope)history.value=values.filter(j=>j.kind!=='rotation')}}catch(e){if(current===scope)error.value=e.message||String(e)}}
async function start(){
  error.value='';serverIssues.value=[];
  if(inputIssues.value.length){error.value='还有未填写或不匹配的设置，请点击下方问题定位修正';await locateIssue(inputIssues.value[0]);return}
  if(!(await save()))return;
  const current=scope,account=uid.value;saving.value=true;
  try{const value=await api.startOptimization(account,{workspaceVersion:workspace.value.version,snapshotId:snapshotId.value,characters:[...selected.value],mode:mode.value,budget:budget.value,wallTimeSeconds:wallTimeSeconds.value,...workspace.value.computeSettings});if(current!==scope)return;job.value=value;tab.value='results';stopPoll();await poll(value.id,account,current)}
  catch(e){if(current===scope){serverIssues.value=Array.isArray(e.response?.data?.data)?e.response.data.data:[];error.value=serverIssues.value.length?'请按下面的问题列表修正输入':e.message||String(e)}}
  finally{if(current===scope)saving.value=false}
}
async function cancel(){const current=scope,account=uid.value,id=job.value?.id;if(!id)return;try{const value=await api.cancelOptimization(account,id);if(current===scope&&job.value?.id===id){job.value=value;stopPoll()}}catch(e){if(current===scope)error.value=e.message||String(e)}}
async function showHistory(value){if(!value)return;stopPoll();const current=scope,account=uid.value;try{const detail=await api.loadOptimization(account,value.id);if(current!==scope)return;job.value=detail;tab.value='results';if(['QUEUED','RUNNING'].includes(detail.state))await poll(detail.id,account,current)}catch(e){if(current===scope)error.value=e.message||String(e)}}
onMounted(()=>{loadEngine();window.addEventListener('beforeunload',beforeUnload)})
onBeforeUnmount(()=>{scope++;stopPoll();window.removeEventListener('beforeunload',beforeUnload)})
</script>

<template>
  <main class="optimizer-page">
    <section class="optimizer-shell">
      <header class="page-header"><div class="nav"><el-button circle :icon="ArrowLeft" aria-label="返回" @click="router.back()"/><el-button circle :icon="HomeFilled" aria-label="首页" @click="router.push('/')"/></div><div><h1>圣遗物自动配装</h1><p>同一背包，多人分配。一套装备兼顾多个配队方案。</p></div><el-tag v-if="dirty" type="warning">有未保存编辑</el-tag></header>
      <section class="toolbar"><UidSelector :key="uidSelectorKey" :model-value="uid" :disabled="changingUid||loading||saving" @update:model-value="changeUid"/><el-button :icon="Refresh" :loading="loading" @click="reload">重新载入</el-button><el-button :icon="Download" :disabled="!uid||loading" :loading="importing" @click="previewEnka">Enka 导入</el-button><el-button type="primary" :loading="saving" :disabled="!uid||loading" @click="save">保存档案</el-button></section>
      <el-alert v-if="error" :title="error" type="error" show-icon :closable="false" class="notice"/>
      <el-alert v-if="engineError" :title="engineError" type="warning" :closable="false" class="notice"><el-button link type="primary" @click="loadEngine">重试读取计算引擎</el-button></el-alert>
      <section v-if="uid&&!loading&&allIssues.length" class="input-problems" aria-label="待修正的配装设置"><h2>还需完善 {{ allIssues.length }} 项设置</h2><p>这里统计的是本次参算角色和其关联方案，不是已创建的档案数量。</p><ul><li v-for="(issue,i) in allIssues" :key="i"><el-button link type="danger" @click="locateIssue(issue)">{{ issue.buildId?buildLabel(workspace.builds.find(b=>b.id===issue.buildId))+'：':'' }}{{ issue.message }}</el-button></li></ul></section>
      <el-skeleton v-if="loading" :rows="10" animated/>
      <el-empty v-else-if="!uid" description="先选择账号，复用圣遗物分析中已扫描的真实背包。"/>
      <template v-else>
        <el-tabs v-model="tab"><el-tab-pane label="角色档案" name="characters"/><el-tab-pane label="配队方案" name="builds"/><el-tab-pane label="分配结果" name="results"/><el-tab-pane label="战斗循环" name="rotations"/><el-tab-pane label="引擎与数据" name="engine"/></el-tabs>
        <details v-if="tab==='characters'" class="inventory-protection">
          <summary>库存装备保护 <span>已保护 {{ inventoryProtections.length }} 位角色</span></summary>
          <div id="optimizer-protections">
            <span id="optimizer-inventoryOwners"></span>
            <p class="hint">没有模拟实现或未建立档案的角色，也可保护其现有装备。这里与个人档案的保护开关同步，不会让这些角色自动参加计算。</p>
            <el-alert v-if="catalog.nativeAliasWarning" :title="catalog.nativeAliasWarning" type="warning" :closable="false"/>
            <el-select :model-value="inventoryProtections" @update:model-value="changeInventoryProtections" multiple filterable :reserve-keyword="false" :disabled="!inventoryPeople.length" placeholder="选择不出借、不替换装备的角色" aria-label="库存角色装备保护">
              <el-option v-for="person in inventoryPeople" :key="person.key" :value="person.key" :label="person.nativeName"/>
              <el-option v-for="key in inventoryProtections.filter(k=>!inventoryPeople.some(p=>p.key===k))" :key="key" :value="key" label="待核实角色（原保护保留）"/>
            </el-select>
          </div>
        </details>
        <section v-if="tab==='characters'" class="workbench">
          <aside class="roster"><el-input v-model="search" clearable placeholder="搜索角色或标签" aria-label="搜索角色"/><el-button :icon="Plus" @click="openAddDialog">添加角色</el-button><div v-if="!workspace.characters.length" class="hint">添加个人档案，或从公开 Enka 展柜导入。</div><p v-else-if="!filteredCharacters.length" class="hint">没有匹配的角色或标签，请调整或清空搜索。</p>
            <div v-for="c in filteredCharacters" :key="c.key" :class="['role-row',{active:c.key===activeCharacter}]">
              <el-checkbox :model-value="selected.includes(c.key)" :aria-label="`选择${profileLabel(catalog,c)}参与计算`" @change="checked=>selected=checked?[...selected,c.key]:selected.filter(k=>k!==c.key)"/>
              <button type="button" class="role-select" @click="activeCharacter=c.key"><img v-if="catalog.characters?.find(m=>m.key===c.key)?.icon_name" :src="`https://enka.network/ui/${catalog.characters.find(m=>m.key===c.key).icon_name}.png`" alt="" loading="lazy"/><span><strong>{{ profileLabel(catalog,c) }}</strong><small>{{ c.builds?.length||0 }} 个方案{{ c.protected?' · 装备保护':'' }}</small><small v-if="c.tags?.length">{{ c.tags.join(' / ') }}</small></span></button>
            </div>
          </aside>
          <CharacterEditor v-if="character" :character="character" :inventory-protected="isCharacterProtected(workspace,catalog,character.key)" @protection-change="changeCharacterProtection" :catalog="catalog" :builds="workspace.builds" :items="itemList" @edit-build="editBuild" @remove="removeCharacter"/><el-empty v-else description="从左侧添加或选择角色后，编辑个人条件和通用配装目标。"/>
        </section>
        <section v-else-if="tab==='builds'"><div class="build-toolbar"><el-select v-model="activeBuild" filterable placeholder="选择配队方案"><el-option v-for="b in workspace.builds" :key="b.id" :value="b.id" :label="buildLabel(b)"/></el-select><el-button :icon="Plus" @click="addBuild">新建方案</el-button></div><BuildEditor v-if="build" :uid="uid" @sampling="value=>workspace.computeSettings.validationSamples=value" :selected="selected" @members-change="changeMembers" @select-member="selectMember" @use-team="useTeam" :build="build" :characters="workspace.characters" :catalog="catalog" :templates="workspace.buffTemplates" @save-buff-template="template=>workspace.buffTemplates.push(template)" @remove="removeBuild"/><el-empty v-else description="新建方案，选择队员并编写一轮循环。"/></section>
        <section v-else-if="tab==='results'"><el-select v-if="history.length" :model-value="job?.id" placeholder="查看历史计算" class="history" @change="id=>showHistory(history.find(j=>j.id===id))"><el-option v-for="j in history" :key="j.id" :value="j.id" :label="`${j.createdAt} · ${jobLabels[j.state]||'未知状态'}`"/></el-select><Results :catalog="catalog" :job="job" :items="resultSnapshot?.artifacts||[]" :characters="workspace.characters"/></section>
        <RotationPanel :wall-time-seconds="wallTimeSeconds" :catalog="catalog" v-else-if="tab==='rotations'" :uid="uid" :workspace="workspace" :selected="selected" :snapshot-id="snapshotId" :equipment-job="job" :before-run="save"/>
        <EnginePanel v-else :catalog="catalog" @changed="loadEngine"/>
        <section class="compute-bar" aria-label="计算设置">
          <details class="compute-options"><summary>计算设置 <span>搜索 {{ workspace.computeSettings.searchSamples }} 次 / 独立验证 {{ workspace.computeSettings.validationSamples }} 次</span></summary><div class="compute-inputs"><label>真实背包快照 <el-select id="optimizer-snapshot" v-model="snapshotId" placeholder="选择完整扫描记录"><el-option v-for="s in snapshots" :key="s.id" :value="s.id" :disabled="!s.complete" :label="`${s.count} 件 · ${s.createdAt}${s.complete?'':' · 扫描不完整'}`"/></el-select></label><label v-if="tab!=='rotations'">优化档位 <el-select v-model="mode"><el-option v-for="m in preferenceOptions" :key="m.value" :value="m.value" :label="m.label"/></el-select></label><label v-if="tab!=='rotations'">候选场景评估上限 <el-input-number v-model="budget" :min="16" :max="4096" :step="64"/></label><label>计算耗时上限（真实秒） <el-input-number v-model="wallTimeSeconds" :min="5" :max="120" :step="10"/></label></div>
          <div class="sampling-controls" id="optimizer-sampling"><label>每个候选的随机采样次数 <el-input-number v-model="workspace.computeSettings.searchSamples" :min="1" :max="32"/></label><label>最终独立验证次数 <el-input-number v-model="workspace.computeSettings.validationSamples" :min="2" :max="1000"/></label><p>每次采样重新执行完整脚本。与脚本里循环几轮、单次战斗多久不同，也不是自动收敛阈值。</p></div>
          </details><div class="compute-bottom" id="optimizer-selection"><p><strong>本次参算 {{ selected.length }} 个角色 / {{ scenarios.length }} 个唯一方案</strong><span>{{ preferenceOptions.find(m=>m.value===mode)?.description }}</span><small v-if="!snapshots.length">尚无扫描记录，请先到 <router-link to="/Artifacts/Analysis">圣遗物分析</router-link> 扫描。</small></p><el-button v-if="running" type="danger" plain @click="cancel">取消本次计算</el-button><el-button v-if="tab!=='rotations'" type="primary" size="large" :disabled="saving||running||Boolean(engineError)" :loading="saving" @click="start">计算通用配装</el-button></div>
          <p v-if="scenarios.length" class="hint">均衡去重场景：{{ scenarios.map(s=>`${s.name} × ${s.weight}`).join('；') }}</p>
        </section>
      </template>
    </section>
    <el-dialog v-model="addDialog" title="添加角色档案" width="min(520px,94vw)" destroy-on-close><el-select v-model="addKey" filterable placeholder="选择 gcsim 已知角色" style="width:100%"><el-option v-for="c in catalog.characters.filter(c=>!workspace.characters.some(p=>p.key===c.key))" :key="c.key" :value="c.key" :label="characterLabel(catalog,c.key)"/></el-select><p class="hint">已有条目不保证所有机制完整。新角色可通过明确标记试算使用，缺失基础资料不能伪造。</p><template #footer><el-button @click="addDialog=false">取消</el-button><el-button type="primary" :disabled="!addKey" @click="addCharacter">添加</el-button></template></el-dialog>
    <el-dialog v-model="enkaDialog" title="Enka 导入预览" width="min(760px,94vw)"><p>{{ enka?.note }}</p><el-alert v-for="warning in enka?.warnings||[]" :key="warning" :title="warning" type="warning" :closable="false"/><el-checkbox-group v-model="enkaKeys"><label v-for="c in enka?.characters||[]" :key="c.key" class="enka-row"><el-checkbox :value="c.key">{{ characterLabel(catalog,c.key) }}</el-checkbox><span>等级 {{ c.level??'未知' }} / {{ c.constellation }} 命 / {{ c.weapon?weaponLabel(catalog,c.weapon):'武器未知' }} / 天赋 {{ c.talents?.map(t=>t??'?').join('/') }}</span><el-tag v-if="workspace.characters.some(old=>old.key===c.key)" type="warning">将替换个人条件</el-tag></label></el-checkbox-group><template #footer><el-button @click="enkaDialog=false">不导入</el-button><el-button type="primary" :disabled="!enkaKeys.length" @click="acceptEnka">合并选中角色</el-button></template></el-dialog>
  </main>
</template>

<style scoped>
.inventory-protection{background:var(--el-bg-color);border:1px solid var(--el-border-color-lighter);border-radius:12px;margin-bottom:18px;padding:14px 20px}.inventory-protection summary{cursor:pointer;font-weight:600}.inventory-protection summary span{font-weight:400;color:var(--el-text-color-secondary);margin-left:12px;font-size:12px}.inventory-protection .el-select{width:100%}
.input-problems{margin:12px 0;padding:16px;border:1px solid var(--el-color-warning-light-5);border-radius:8px;background:var(--el-color-warning-light-9)}.input-problems h2{font-size:16px;margin:0 0 8px}.input-problems p{font-size:13px}.input-problems ul{max-height:230px;overflow:auto;padding-left:20px}.input-problems li{margin:6px 0}.input-problems .el-button{white-space:normal;text-align:left;height:auto}.sampling-controls{display:flex;flex-wrap:wrap;gap:16px;margin-top:16px;align-items:end}.sampling-controls label{display:grid;gap:7px;font-size:12px}.sampling-controls p{flex:1;min-width:200px;font-size:12px;line-height:1.6;color:var(--el-text-color-secondary)}

.optimizer-page{min-height:100dvh;padding:24px;color:var(--el-text-color-primary)}.optimizer-shell{width:min(1500px,100%);box-sizing:border-box;margin:auto;padding:24px;background:var(--el-bg-color);border:1px solid var(--el-border-color-lighter);border-radius:12px}.page-header{display:flex;gap:22px;align-items:center;border-bottom:1px solid var(--el-border-color-lighter);padding-bottom:18px}.nav{display:flex;gap:8px}.page-header h1{font-size:25px;margin:0 0 7px}.page-header p{font-size:14px;color:var(--el-text-color-secondary);margin:0}.toolbar{display:flex;gap:12px;align-items:center;margin:18px 0;flex-wrap:wrap}.toolbar>:first-child{width:260px}.notice{margin-bottom:12px}.workbench{display:grid;grid-template-columns:260px minmax(0,1fr);gap:28px}.roster{min-width:0}.roster>.el-button{width:100%;margin:12px 0}.role-row{display:flex;gap:8px;align-items:center;padding:8px;border-radius:8px;margin:4px 0}.role-row.active{background:var(--el-color-primary-light-9)}.role-select{border:0;background:none;padding:0;display:flex;align-items:center;gap:10px;text-align:left;cursor:pointer;width:100%;min-width:0;color:inherit}.role-select:focus-visible{outline:2px solid var(--el-color-primary);outline-offset:4px}.role-select img{width:40px;height:40px;object-fit:cover;border-radius:6px;background:var(--el-fill-color)}.role-select strong{display:block;font-size:14px}.role-select small{display:block;font-size:11px;color:var(--el-text-color-secondary);margin-top:4px;overflow-wrap:anywhere}.hint{font-size:12px;line-height:1.7;color:var(--el-text-color-secondary)}.build-toolbar{display:flex;gap:12px;max-width:650px;margin-bottom:18px}.build-toolbar>.el-select{flex:1}.compute-bar{margin-top:28px;padding:20px;background:var(--el-fill-color-light);border:1px solid var(--el-border-color-lighter);border-radius:10px}.compute-inputs{display:grid;grid-template-columns:minmax(220px,2fr) minmax(130px,1fr) 170px 170px;gap:16px}.compute-inputs label{display:grid;gap:7px;font-size:12px}.compute-inputs .el-select{width:100%}.compute-bottom{display:flex;gap:16px;align-items:center;margin-top:10px}.compute-bottom p{flex:1;display:grid;gap:5px;font-size:14px}.compute-bottom span,.compute-bottom small{font-size:12px;color:var(--el-text-color-secondary)}.engine-details h2{font-size:19px}.engine-details p{line-height:1.7;max-width:850px}.history{max-width:550px;width:100%;margin:12px 0}.enka-row{display:flex;align-items:center;gap:14px;padding:12px 0;font-size:13px;flex-wrap:wrap}@media(max-width:1100px){.workbench{grid-template-columns:210px minmax(0,1fr);gap:18px}.compute-inputs{grid-template-columns:repeat(2,minmax(0,1fr))}}@media(max-width:768px){.optimizer-page{padding:0}.optimizer-shell{padding:16px;border-radius:0}.page-header{flex-wrap:wrap;gap:12px}.page-header h1{font-size:22px}.workbench{grid-template-columns:1fr}.roster{max-height:300px;overflow:auto}.compute-bottom{flex-wrap:wrap}.compute-bottom p{flex-basis:100%}.compute-inputs{grid-template-columns:1fr}.toolbar>:first-child{width:100%}}

/* A quiet canvas and distinct working surfaces keep the team and code primary. */
.optimizer-page{--el-color-primary:#2864d8;--el-color-primary-light-9:#eef4ff;--el-color-primary-light-7:#c5d8ff;background:#f2f5fa;padding:24px;min-height:100dvh;color:#24324b}.optimizer-shell{width:min(1440px,100%);padding:0;background:transparent;border:0}.page-header{padding:24px 26px 18px;background:var(--el-bg-color);border:1px solid var(--el-border-color-lighter);border-bottom:0;border-radius:16px 16px 0 0}.page-header h1{font-size:26px;letter-spacing:-.5px;color:var(--el-text-color-primary)}.page-header p{color:#76829a}.toolbar{padding:0 26px 22px;margin:0;background:var(--el-bg-color);border-left:1px solid var(--el-border-color-lighter);border-right:1px solid var(--el-border-color-lighter);gap:10px}.toolbar>:first-child{width:280px;margin-right:auto}.optimizer-shell>:deep(.el-tabs){background:var(--el-bg-color);padding:0 26px;border:1px solid var(--el-border-color-lighter);border-top:0;border-radius:0 0 16px 16px;margin-bottom:24px}.optimizer-shell>:deep(.el-tabs .el-tabs__header){margin:0}.optimizer-shell>:deep(.el-tabs .el-tabs__nav-wrap::after){background:transparent}.optimizer-shell>:deep(.el-tabs .el-tabs__item){height:48px;font-size:14px;font-weight:600}.workbench{background:var(--el-bg-color);padding:24px;border:1px solid var(--el-border-color-lighter);border-radius:14px}.roster{border-right:1px solid var(--el-border-color-lighter);padding-right:18px}.role-row{border:1px solid transparent;padding:10px}.role-row.active{border-color:var(--el-color-primary-light-7)}.build-toolbar{max-width:none;padding:0 2px;margin:0 0 16px}.build-toolbar>.el-select{max-width:440px}.compute-bar{position:sticky;bottom:12px;z-index:4;background:var(--el-bg-color);border:1px solid #ccd9ef;border-radius:14px;padding:0 22px 10px;margin-top:24px;box-shadow:0 8px 24px rgba(31,52,90,.09)}.compute-options>summary{list-style:none;cursor:pointer;display:flex;gap:14px;justify-content:space-between;padding:14px 0;color:var(--el-text-color-secondary);font-size:12px}.compute-options>summary span{color:var(--el-color-primary)}.compute-options[open]>summary{border-bottom:1px solid var(--el-border-color-light);margin-bottom:16px}.compute-bottom{margin-top:0;min-height:72px;border-top:1px solid var(--el-border-color-lighter)}.compute-bottom strong{font-size:17px;color:var(--el-text-color-primary)}.compute-bottom>.el-button{min-width:164px;height:44px;font-weight:600}.input-problems{margin:18px 0;padding:14px 20px;border-left:4px solid var(--el-color-warning);background:var(--el-color-warning-light-9)}.input-problems p{margin:4px 0}.input-problems ul{margin:8px 0 0}.input-problems li{margin:4px 0}.sampling-controls{padding-bottom:16px}.history{background:var(--el-bg-color)}

@media(max-width:768px){.optimizer-page{padding:12px}.page-header{padding:20px 18px 14px}.toolbar{padding:0 18px 18px}.toolbar>:first-child{width:100%;margin-right:0}.optimizer-shell>:deep(.el-tabs){padding:0 16px}.workbench{padding:16px}.roster{border:0;padding-right:0}.compute-bar{position:static;padding:0 16px 12px}.compute-bottom>.el-button{width:100%}.compute-options>summary{flex-wrap:wrap;gap:6px}}

.optimizer-page{background:var(--el-fill-color);color:var(--el-text-color-primary);padding-bottom:160px}.compute-bar{position:fixed;left:50%;transform:translateX(-50%);width:min(1440px,calc(100% - 48px));box-sizing:border-box;bottom:12px;margin:0;z-index:10}.compute-bottom p{margin:8px 0}.compute-bar>.hint{margin:0 0 2px;font-size:11px}.input-problems{box-shadow:none}@media(max-width:768px){.optimizer-page{padding-bottom:20px}.compute-bar{position:static;transform:none;width:100%;margin-top:20px}}
</style>
