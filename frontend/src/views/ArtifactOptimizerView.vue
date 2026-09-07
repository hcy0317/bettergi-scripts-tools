<script setup>
import {computed,onBeforeUnmount,onMounted,ref,watch} from 'vue'
import {ElMessage,ElMessageBox} from 'element-plus'
import {ArrowLeft,HomeFilled,Plus,Refresh,Download} from '@element-plus/icons-vue'
import router from '@router/router.js'
import UidSelector from '@/components/UidSelector.vue'
import CharacterEditor from '@/components/artifact-optimizer/OptimizerCharacterEditor.vue'
import BuildEditor from '@/components/artifact-optimizer/OptimizerBuildEditor.vue'
import Results from '@/components/artifact-optimizer/OptimizerResults.vue'
import RotationPanel from '@/components/artifact-optimizer/OptimizerRotationPanel.vue'
import EnginePanel from '@/components/artifact-optimizer/OptimizerEnginePanel.vue'
import * as api from '@/api/artifact/artifactOptimizer.js'
import {newCharacter,newBuild,mergeEnkaPreview,preferenceOptions,selectedScenarioIds} from '@/features/artifact-optimizer/model.js'
import {characterLabel,weaponLabel,profileLabel,jobLabels} from '@/features/artifact-optimizer/localization.js'

const uid=ref(''),workspace=ref({version:0,characters:[],builds:[]}),catalog=ref({characters:[],weapons:[],sets:[]})
const loading=ref(false),saving=ref(false),dirty=ref(false),error=ref(''),engineError=ref(''),search=ref(''),activeCharacter=ref(''),activeBuild=ref(''),selected=ref([]),tab=ref('characters')
const snapshots=ref([]),snapshotId=ref(''),snapshot=ref(null),mode=ref('balanced'),budget=ref(256),wallTimeSeconds=ref(120),job=ref(null),history=ref([])
const resultSnapshot=ref(null)
const addDialog=ref(false),addKey=ref(''),enkaDialog=ref(false),enka=ref(null),enkaKeys=ref([]),importing=ref(false)
let scope=0,pollTimer=null,applying=false,editVersion=0
const character=computed(()=>workspace.value.characters.find(c=>c.key===activeCharacter.value))
const build=computed(()=>workspace.value.builds.find(b=>b.id===activeBuild.value))
const running=computed(()=>['QUEUED','RUNNING'].includes(job.value?.state))
const filteredCharacters=computed(()=>workspace.value.characters.filter(c=>[profileLabel(catalog.value,c),c.key,...(c.tags||[])].join(' ').toLowerCase().includes(search.value.toLowerCase())))
const scenarios=computed(()=>selectedScenarioIds(workspace.value.characters,selected.value).map(id=>workspace.value.builds.find(b=>b.id===id)).filter(Boolean))
const itemList=computed(()=>snapshot.value?.artifacts||[])
const clone=value=>JSON.parse(JSON.stringify(value))
watch(workspace,()=>{if(!applying){dirty.value=true;editVersion++}},{deep:true,flush:'sync'})
function applyWorkspace(value){applying=true;value.buffTemplates ||= [];workspace.value=value;applying=false;dirty.value=false;activeCharacter.value=value.characters[0]?.key||'';activeBuild.value=value.builds[0]?.id||''}
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
async function removeCharacter(){try{await ElMessageBox.confirm('移除此角色档案及配队内引用？保存后生效，不影响游戏。','移除角色',{type:'warning'})}catch{return}const key=activeCharacter.value;workspace.value.characters=workspace.value.characters.filter(c=>c.key!==key);workspace.value.builds.forEach(b=>b.members=b.members.filter(m=>m.character!==key));selected.value=selected.value.filter(k=>k!==key);activeCharacter.value=workspace.value.characters[0]?.key||''}
async function removeBuild(){try{await ElMessageBox.confirm('删除此 方案 及所有角色对它的引用？保存后生效。','删除 方案',{type:'warning'})}catch{return}const id=activeBuild.value;workspace.value.builds=workspace.value.builds.filter(b=>b.id!==id);workspace.value.characters.forEach(c=>c.builds=c.builds.filter(b=>b.id!==id));activeBuild.value=workspace.value.builds[0]?.id||''}
async function previewEnka(){const current=scope,account=uid.value;importing.value=true;error.value='';try{const value=await api.enkaPreview(account);if(current!==scope)return;enka.value=value;enkaKeys.value=[];enkaDialog.value=true}catch(e){if(current===scope)error.value=e.message||String(e)}finally{if(current===scope)importing.value=false}}
function acceptEnka(){workspace.value.characters=mergeEnkaPreview(workspace.value.characters,clone(enka.value.characters),enkaKeys.value);activeCharacter.value ||= workspace.value.characters[0]?.key||'';enkaDialog.value=false;ElMessage.success('已合并到本地编辑，请检查并保存')}
async function poll(id,account,current){try{const value=await api.loadOptimization(account,id);if(current!==scope||job.value?.id!==id)return;job.value=value;if(['QUEUED','RUNNING'].includes(value.state))pollTimer=setTimeout(()=>poll(id,account,current),1500);else{const values=await api.listOptimizations(account);if(current===scope)history.value=values.filter(j=>j.kind!=='rotation')}}catch(e){if(current===scope)error.value=e.message||String(e)}}
async function start(){error.value='';if(!selected.value.length||!snapshotId.value)return;if(!(await save()))return;const current=scope,account=uid.value;saving.value=true;try{const value=await api.startOptimization(account,{workspaceVersion:workspace.value.version,snapshotId:snapshotId.value,characters:[...selected.value],mode:mode.value,budget:budget.value,wallTimeSeconds:wallTimeSeconds.value});if(current!==scope)return;job.value=value;tab.value='results';stopPoll();await poll(value.id,account,current)}catch(e){if(current===scope)error.value=e.message||String(e)}finally{if(current===scope)saving.value=false}}
async function cancel(){const current=scope,account=uid.value,id=job.value?.id;if(!id)return;try{const value=await api.cancelOptimization(account,id);if(current===scope&&job.value?.id===id){job.value=value;stopPoll()}}catch(e){if(current===scope)error.value=e.message||String(e)}}
async function showHistory(value){if(!value)return;stopPoll();const current=scope,account=uid.value;try{const detail=await api.loadOptimization(account,value.id);if(current!==scope)return;job.value=detail;tab.value='results';if(['QUEUED','RUNNING'].includes(detail.state))await poll(detail.id,account,current)}catch(e){if(current===scope)error.value=e.message||String(e)}}
onMounted(loadEngine)
onBeforeUnmount(()=>{scope++;stopPoll()})
</script>

<template>
  <main class="optimizer-page feature-page-background">
    <section class="optimizer-shell">
      <header class="page-header"><div class="nav"><el-button circle :icon="ArrowLeft" aria-label="返回" @click="router.back()"/><el-button circle :icon="HomeFilled" aria-label="首页" @click="router.push('/')"/></div><div><h1>圣遗物自动配装</h1><p>同一背包，多人分配。一套装备兼顾多个配队 方案。</p></div><el-tag v-if="dirty" type="warning">有未保存编辑</el-tag></header>
      <section class="toolbar"><UidSelector v-model="uid"/><el-button :icon="Refresh" :loading="loading" @click="reload">重新载入</el-button><el-button :icon="Download" :disabled="!uid||loading" :loading="importing" @click="previewEnka">Enka 导入</el-button><el-button type="primary" :loading="saving" :disabled="!uid||loading" @click="save">保存档案</el-button></section>
      <el-alert v-if="error" :title="error" type="error" show-icon :closable="false" class="notice"/>
      <el-alert v-if="engineError" :title="engineError" type="warning" :closable="false" class="notice"><el-button link type="primary" @click="loadEngine">重试读取计算引擎</el-button></el-alert>
      <el-skeleton v-if="loading" :rows="10" animated/>
      <el-empty v-else-if="!uid" description="先选择账号，复用圣遗物分析中已扫描的真实背包。"/>
      <template v-else>
        <el-tabs v-model="tab"><el-tab-pane label="角色档案" name="characters"/><el-tab-pane label="配队 方案" name="builds"/><el-tab-pane label="分配结果" name="results"/><el-tab-pane label="战斗循环" name="rotations"/><el-tab-pane label="引擎与数据" name="engine"/></el-tabs>
        <section v-if="tab==='characters'" class="workbench">
          <aside class="roster"><el-input v-model="search" clearable placeholder="搜索角色或 标签" aria-label="搜索角色"/><el-button :icon="Plus" @click="addDialog=true">添加角色</el-button><div v-if="!workspace.characters.length" class="hint">添加个人档案，或从公开 Enka 展柜导入。</div>
            <div v-for="c in filteredCharacters" :key="c.key" :class="['role-row',{active:c.key===activeCharacter}]">
              <el-checkbox :model-value="selected.includes(c.key)" :aria-label="`选择${profileLabel(catalog,c)}参与计算`" @change="checked=>selected=checked?[...selected,c.key]:selected.filter(k=>k!==c.key)"/>
              <button type="button" class="role-select" @click="activeCharacter=c.key"><img v-if="catalog.characters?.find(m=>m.key===c.key)?.icon_name" :src="`https://enka.network/ui/${catalog.characters.find(m=>m.key===c.key).icon_name}.png`" alt="" loading="lazy"/><span><strong>{{ profileLabel(catalog,c) }}</strong><small>{{ c.builds?.length||0 }} 个 方案{{ c.protected?' · 装备保护':'' }}</small><small v-if="c.tags?.length">{{ c.tags.join(' / ') }}</small></span></button>
            </div>
          </aside>
          <CharacterEditor v-if="character" :character="character" :catalog="catalog" :builds="workspace.builds" :items="itemList" @edit-build="editBuild" @remove="removeCharacter"/><el-empty v-else description="从左侧添加或选择角色后，编辑个人条件和通用配装目标。"/>
        </section>
        <section v-else-if="tab==='builds'"><div class="build-toolbar"><el-select v-model="activeBuild" filterable placeholder="选择配队 方案"><el-option v-for="b in workspace.builds" :key="b.id" :value="b.id" :label="b.name"/></el-select><el-button :icon="Plus" @click="addBuild">新建 方案</el-button></div><BuildEditor v-if="build" :build="build" :characters="workspace.characters" :catalog="catalog" :templates="workspace.buffTemplates" @save-buff-template="template=>workspace.buffTemplates.push(template)" @remove="removeBuild"/><el-empty v-else description="新建 方案，选择队员并编写一轮循环。"/></section>
        <section v-else-if="tab==='results'"><el-select v-if="history.length" :model-value="job?.id" placeholder="查看历史计算" class="history" @change="id=>showHistory(history.find(j=>j.id===id))"><el-option v-for="j in history" :key="j.id" :value="j.id" :label="`${j.createdAt} · ${jobLabels[j.state]||'未知状态'}`"/></el-select><Results :catalog="catalog" :job="job" :items="resultSnapshot?.artifacts||[]" :characters="workspace.characters"/></section>
        <RotationPanel :catalog="catalog" v-else-if="tab==='rotations'" :uid="uid" :workspace="workspace" :selected="selected" :snapshot-id="snapshotId" :equipment-job="job" :before-run="save"/>
        <EnginePanel v-else :catalog="catalog" @changed="loadEngine"/>
        <section class="compute-bar" aria-label="计算设置">
          <div class="compute-inputs"><label>真实背包快照 <el-select v-model="snapshotId" placeholder="选择完整扫描记录"><el-option v-for="s in snapshots" :key="s.id" :value="s.id" :disabled="!s.complete" :label="`${s.count} 件 · ${s.createdAt}${s.complete?'':' · 扫描不完整'}`"/></el-select></label><label>优化档位 <el-select v-model="mode"><el-option v-for="m in preferenceOptions" :key="m.value" :value="m.value" :label="m.label"/></el-select></label><label>评估预算 <el-input-number v-model="budget" :min="16" :max="4096" :step="64"/></label><label>最长秒数 <el-input-number v-model="wallTimeSeconds" :min="5" :max="120" :step="10"/></label></div>
          <div class="compute-bottom"><p><strong>{{ selected.length }} 个角色 / {{ scenarios.length }} 个唯一 方案</strong><span>{{ preferenceOptions.find(m=>m.value===mode)?.description }}</span><small v-if="!snapshots.length">尚无扫描记录，请先到 <router-link to="/Artifacts/Analysis">圣遗物分析</router-link> 扫描。</small></p><el-button v-if="running" type="danger" plain @click="cancel">取消本次计算</el-button><el-button type="primary" size="large" :disabled="!selected.length||!snapshotId||saving||running||Boolean(engineError)" :loading="saving" @click="start">计算通用配装</el-button></div>
          <p v-if="scenarios.length" class="hint">均衡去重场景：{{ scenarios.map(s=>`${s.name} × ${s.weight}`).join('；') }}</p>
        </section>
      </template>
    </section>
    <el-dialog v-model="addDialog" title="添加角色档案" width="min(520px,94vw)"><el-select v-model="addKey" filterable placeholder="选择 gcsim 已知角色" style="width:100%"><el-option v-for="c in catalog.characters.filter(c=>!workspace.characters.some(p=>p.key===c.key))" :key="c.key" :value="c.key" :label="characterLabel(catalog,c.key)"/></el-select><p class="hint">已有条目不保证所有机制完整。新角色可通过明确标记试算使用，缺失基础资料不能伪造。</p><template #footer><el-button @click="addDialog=false">取消</el-button><el-button type="primary" :disabled="!addKey" @click="addCharacter">添加</el-button></template></el-dialog>
    <el-dialog v-model="enkaDialog" title="Enka 导入预览" width="min(760px,94vw)"><p>{{ enka?.note }}</p><el-alert v-for="warning in enka?.warnings||[]" :key="warning" :title="warning" type="warning" :closable="false"/><el-checkbox-group v-model="enkaKeys"><label v-for="c in enka?.characters||[]" :key="c.key" class="enka-row"><el-checkbox :value="c.key">{{ characterLabel(catalog,c.key) }}</el-checkbox><span>等级 {{ c.level??'未知' }} / {{ c.constellation }} 命 / {{ c.weapon?weaponLabel(catalog,c.weapon):'武器未知' }} / 天赋 {{ c.talents?.map(t=>t??'?').join('/') }}</span><el-tag v-if="workspace.characters.some(old=>old.key===c.key)" type="warning">将替换个人条件</el-tag></label></el-checkbox-group><template #footer><el-button @click="enkaDialog=false">不导入</el-button><el-button type="primary" :disabled="!enkaKeys.length" @click="acceptEnka">合并选中角色</el-button></template></el-dialog>
  </main>
</template>

<style scoped>
.optimizer-page{min-height:100dvh;padding:24px;color:var(--el-text-color-primary)}.optimizer-shell{width:min(1500px,100%);box-sizing:border-box;margin:auto;padding:24px;background:var(--el-bg-color);border:1px solid var(--el-border-color-lighter);border-radius:12px}.page-header{display:flex;gap:22px;align-items:center;border-bottom:1px solid var(--el-border-color-lighter);padding-bottom:18px}.nav{display:flex;gap:8px}.page-header h1{font-size:25px;margin:0 0 7px}.page-header p{font-size:14px;color:var(--el-text-color-secondary);margin:0}.toolbar{display:flex;gap:12px;align-items:center;margin:18px 0;flex-wrap:wrap}.toolbar>:first-child{width:260px}.notice{margin-bottom:12px}.workbench{display:grid;grid-template-columns:260px minmax(0,1fr);gap:28px}.roster{min-width:0}.roster>.el-button{width:100%;margin:12px 0}.role-row{display:flex;gap:8px;align-items:center;padding:8px;border-radius:8px;margin:4px 0}.role-row.active{background:var(--el-color-primary-light-9)}.role-select{border:0;background:none;padding:0;display:flex;align-items:center;gap:10px;text-align:left;cursor:pointer;width:100%;min-width:0;color:inherit}.role-select:focus-visible{outline:2px solid var(--el-color-primary);outline-offset:4px}.role-select img{width:40px;height:40px;object-fit:cover;border-radius:6px;background:var(--el-fill-color)}.role-select strong{display:block;font-size:14px}.role-select small{display:block;font-size:11px;color:var(--el-text-color-secondary);margin-top:4px;overflow-wrap:anywhere}.hint{font-size:12px;line-height:1.7;color:var(--el-text-color-secondary)}.build-toolbar{display:flex;gap:12px;max-width:650px;margin-bottom:18px}.build-toolbar>.el-select{flex:1}.compute-bar{margin-top:28px;padding:20px;background:var(--el-fill-color-light);border:1px solid var(--el-border-color-lighter);border-radius:10px}.compute-inputs{display:grid;grid-template-columns:minmax(220px,2fr) minmax(130px,1fr) 170px 170px;gap:16px}.compute-inputs label{display:grid;gap:7px;font-size:12px}.compute-inputs .el-select{width:100%}.compute-bottom{display:flex;gap:16px;align-items:center;margin-top:10px}.compute-bottom p{flex:1;display:grid;gap:5px;font-size:14px}.compute-bottom span,.compute-bottom small{font-size:12px;color:var(--el-text-color-secondary)}.engine-details h2{font-size:19px}.engine-details p{line-height:1.7;max-width:850px}.history{max-width:550px;width:100%;margin:12px 0}.enka-row{display:flex;align-items:center;gap:14px;padding:12px 0;font-size:13px;flex-wrap:wrap}@media(max-width:1100px){.workbench{grid-template-columns:210px minmax(0,1fr);gap:18px}.compute-inputs{grid-template-columns:repeat(2,minmax(0,1fr))}}@media(max-width:768px){.optimizer-page{padding:0}.optimizer-shell{padding:16px;border-radius:0}.page-header{flex-wrap:wrap;gap:12px}.page-header h1{font-size:22px}.workbench{grid-template-columns:1fr}.roster{max-height:300px;overflow:auto}.compute-bottom{flex-wrap:wrap}.compute-bottom p{flex-basis:100%}.compute-inputs{grid-template-columns:1fr}.toolbar>:first-child{width:100%}}
</style>
