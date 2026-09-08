<script setup>
import {computed,onBeforeUnmount,ref,watch} from 'vue'
import * as api from '@/api/artifact/artifactOptimizer.js'
import {characterLabel} from '@/features/artifact-optimizer/localization.js'
import {metric} from '@/features/artifact-optimizer/model.js'
import {applyCommunityPreview,describeCommunitySettings,createCommunityRequestScope,communityErrorMessage,communityFilterIssue,communityParts,hasCommunitySelection} from '@/features/artifact-optimizer/community.js'
const props=defineProps({uid:{type:String,required:true},build:{type:Object,required:true},catalog:{type:Object,required:true}})
const emit=defineEmits(['sampling'])
const open=ref(false),include=ref([]),exclude=ref([]),tags=ref([]),excludedTags=ref([9]),sort=ref('date'),page=ref(1),entries=ref([]),hasMore=ref(false),searching=ref(false),reading=ref(false),error=ref(''),reference=ref(''),pasted=ref(''),preview=ref(null),previewOpen=ref(false),applyScript=ref(true),applySettings=ref(true),applySampling=ref(true)
const tagOptions=[{id:5,name:'一斗参考'},{id:6,name:'动作执行延迟'},{id:7,name:'新手参考'},{id:8,name:'动作优先级列表'},{id:9,name:'攻略参考'}]
let binding=null
const requests=createCommunityRequestScope(),searched=ref(false)
const filterIssue=computed(()=>communityFilterIssue({include:include.value,exclude:exclude.value,tags:tags.value,excludedTags:excludedTags.value},key=>characterLabel(props.catalog,key)))
const parts=computed(()=>communityParts(preview.value))
const canApply=computed(()=>hasCommunitySelection(preview.value,{script:applyScript.value,settings:applySettings.value,sampling:applySampling.value}))
const mismatches=computed(()=>(preview.value?.characters||[]).filter(key=>!props.build.members.some(m=>m.character===key)))
function cancelPending(){requests.cancel();searching.value=false;reading.value=false}
function resetSearch(){
  cancelPending();entries.value=[];hasMore.value=false;page.value=1;searched.value=false;error.value=''
  preview.value=null;binding=null;previewOpen.value=false
}
function begin(){cancelPending();error.value='';return requests.begin()}
function show(){resetSearch();include.value=props.build.members.map(m=>m.character);open.value=true;search(1)}
async function search(next=1){
  const request=begin();entries.value=[];hasMore.value=false;page.value=next;searched.value=false
  if(filterIssue.value)return
  searching.value=true
  try{
    const value=await api.searchCommunity({include:[...include.value],exclude:[...exclude.value],tags:[...tags.value],excludedTags:[...excludedTags.value],sort:sort.value,direction:'desc',page:next},request.signal)
    if(!request.isCurrent())return
    if(!Array.isArray(value?.entries))throw new Error('社区返回的结果格式不完整，请稍后重试')
    entries.value=value.entries;hasMore.value=value.hasMore===true;searched.value=true
  }catch(e){if(request.isCurrent())error.value=communityErrorMessage(e,'社区搜索失败，请稍后重试')}
  finally{if(request.isCurrent())searching.value=false}
}
async function read(input){
  const request=begin();reading.value=true;preview.value=null;binding=null
  const current={uid:props.uid,id:props.build.id,snapshot:JSON.stringify(props.build)}
  try{
    const value=await api.previewCommunity(input,request.signal)
    if(!request.isCurrent())return
    preview.value=value;binding=current
    const available=communityParts(value)
    applyScript.value=available.script;applySettings.value=available.settings;applySampling.value=available.sampling
    previewOpen.value=true
  }catch(e){if(request.isCurrent())error.value=communityErrorMessage(e,'脚本预览失败')}
  finally{if(request.isCurrent())reading.value=false}
}
function apply(){
  error.value=''
  if(!binding||binding.uid!==props.uid||binding.id!==props.build.id||binding.snapshot!==JSON.stringify(props.build)){
    error.value='方案在预览后发生了修改，请重新预览，避免覆盖新编辑';return
  }
  if(!canApply.value){error.value='请至少选择一项可用的导入内容';return}
  try{
    if(applyScript.value&&parts.value.script||applySettings.value&&parts.value.settings){
      applyCommunityPreview(props.build,preview.value,{script:applyScript.value&&parts.value.script,settings:applySettings.value&&parts.value.settings})
    }
    if(applySampling.value&&parts.value.sampling)emit('sampling',preview.value.validationSamples)
    previewOpen.value=false;open.value=false
  }catch(e){error.value=communityErrorMessage(e,'导入失败')}
}
watch([include,exclude,tags,excludedTags,sort],resetSearch,{deep:true,flush:'sync'})
watch(open,value=>{if(!value)resetSearch()},{flush:'sync'})
watch(()=>[props.uid,props.build.id],()=>{resetSearch();open.value=false;reference.value='';pasted.value=''})
onBeforeUnmount(cancelPending)
</script>
<template>
  <section class="community-entry"><el-button type="primary" plain @click="show">搜索社区循环并导入</el-button><span>按队员筛选 sim pact / gcsim 公开脚本，或粘贴链接与原文。</span>
    <el-dialog v-model="open" title="社区循环搜索与快速导入" width="min(1180px,96vw)" destroy-on-close>
      <el-alert v-if="error" :title="error" type="error" :closable="false"/>
      <el-alert v-if="filterIssue" :title="filterIssue" type="warning" :closable="false"/>
      <el-form label-position="top" class="filters">
        <el-form-item label="包含角色（最多四人）"><el-select v-model="include" multiple filterable :reserve-keyword="false" :multiple-limit="4"><el-option v-for="c in catalog.characters||[]" :key="c.key" :value="c.key" :label="characterLabel(catalog,c.key)"/></el-select></el-form-item>
        <el-form-item label="排除角色"><el-select v-model="exclude" multiple filterable :reserve-keyword="false" :multiple-limit="16"><el-option v-for="c in catalog.characters||[]" :key="c.key" :value="c.key" :label="characterLabel(catalog,c.key)"/></el-select></el-form-item>
        <el-form-item label="排序"><el-select v-model="sort"><el-option value="date" label="最近发布"/><el-option value="dps" label="参考每目标伤害从高到低"/></el-select></el-form-item>
        <el-form-item label="包含标签"><el-select v-model="tags" multiple><el-option v-for="tag in tagOptions" :key="tag.id" :value="tag.id" :label="tag.name"/></el-select></el-form-item>
        <el-form-item label="排除标签"><el-select v-model="excludedTags" multiple><el-option v-for="tag in tagOptions" :key="tag.id" :value="tag.id" :label="tag.name"/></el-select></el-form-item>
        <el-form-item label="应用筛选"><el-button type="primary" :disabled="!!filterIssue||reading" :loading="searching" @click="search(1)">搜索</el-button></el-form-item>
      </el-form>
      <p class="hint">只发送角色与标签筛选，不发送账号、背包或个人培养数据。参考伤害来自作者条件，不能当作你的配装收益。</p>
      <el-skeleton v-if="searching" :rows="5" animated/>
      <el-empty v-else-if="!error&&!filterIssue&&!searched" description="筛选已调整，请点击搜索。"/>
      <el-empty v-else-if="!error&&!filterIssue&&!entries.length" description="没有匹配的公开方案，可减少包含角色或调整标签。"/>
      <div v-else-if="!error&&!filterIssue" class="community-results"><article v-for="entry in entries" :key="entry.id"><div class="entry-team"><strong>{{ (entry.characters||[]).map(key=>characterLabel(catalog,key)).join(' / ') }}</strong><span>每目标每秒伤害 {{ metric(entry.dps) }}</span></div><p>{{ entry.description||'作者未提供说明' }}</p><div class="entry-actions"><a :href="entry.url" target="_blank" rel="noopener noreferrer">查看原始条目</a><el-button :loading="reading" @click="read({reference:entry.id})">预览导入</el-button></div></article></div>
      <div class="pagination"><el-button :disabled="page===1||searching||reading||!searched||!!error||!!filterIssue" @click="search(page-1)">上一页</el-button><span>第 {{ page }} 页</span><el-button :disabled="!hasMore||searching||reading||!searched||!!error||!!filterIssue" @click="search(page+1)">下一页</el-button></div>
      <el-collapse><el-collapse-item title="从链接或粘贴的完整脚本导入" name="paste"><el-form label-position="top"><el-form-item label="数据库条目链接或编号"><el-input v-model="reference" placeholder="https://gcsim.app/db/条目编号"/></el-form-item><el-button :disabled="!reference" :loading="reading" @click="read({reference})">读取链接并预览</el-button><el-form-item label="完整 gcsim 脚本"><el-input v-model="pasted" type="textarea" :rows="8" aria-label="待导入的完整脚本"/></el-form-item><el-button :disabled="!pasted.trim()" :loading="reading" @click="read({source:pasted})">拆分脚本并预览</el-button></el-form></el-collapse-item></el-collapse>
    </el-dialog>
    <el-dialog v-model="previewOpen" title="确认循环与场景导入" width="min(1000px,96vw)" append-to-body>
      <template v-if="preview"><el-alert :title="preview.note" type="info" :closable="false"/><el-alert v-if="error" :title="error" type="error" :closable="false"/><el-alert v-for="(issue,i) in preview.unsupported||[]" :key="i" :title="`${issue.line?`第${issue.line}行：`:''}${issue.message}`" type="error" :closable="false"/><el-alert v-for="warning in preview.warnings||[]" :key="warning" :title="warning" type="warning" :closable="false"/><el-alert v-if="mismatches.length" :title="`参考脚本另包含：${mismatches.map(k=>characterLabel(catalog,k)).join('、')}。不会自动替换你的队员，请在计算前检查配队。`" type="warning" :closable="false"/>
        <el-checkbox v-model="applyScript">采用主体循环与必要辅助逻辑</el-checkbox><h4>主体循环</h4><el-input :model-value="preview.rotation" type="textarea" :rows="8" readonly aria-label="导入主体循环预览"/><h4 v-if="preview.scriptPrelude">单独保留的辅助逻辑</h4><el-input v-if="preview.scriptPrelude" :model-value="preview.scriptPrelude" type="textarea" :rows="6" readonly aria-label="导入辅助逻辑预览"/>
        <el-checkbox v-if="Object.keys(preview.settings||{}).length" v-model="applySettings">采用提取的场景、敌人和掉球设置</el-checkbox><el-table v-if="Object.keys(preview.settings||{}).length" :data="describeCommunitySettings(preview.settings)"><el-table-column prop="label" label="设置" width="160"/><el-table-column prop="value" label="将采用的值"/></el-table>
        <el-checkbox v-if="preview.validationSamples" v-model="applySampling">最终独立验证采样 {{ preview.validationSamples }} 次（不是战斗轮数）</el-checkbox>
        <el-collapse><el-collapse-item :title="`已移除 ${preview.omittedDeclarations?.length||0} 条社区角色/装备声明`" name="removed"><pre v-for="(item,i) in preview.omittedDeclarations||[]" :key="i">{{ item.source }}</pre></el-collapse-item><el-collapse-item title="完整原文（保留作对照）" name="original"><el-input :model-value="preview.originalScript" type="textarea" :rows="10" readonly/></el-collapse-item></el-collapse>
      </template><template #footer><el-button @click="previewOpen=false">暂不导入</el-button><el-button type="primary" :disabled="!canApply" @click="apply">确认应用到当前方案</el-button></template>
    </el-dialog>
  </section>
</template>
<style scoped>
.community-entry{display:flex;gap:14px;align-items:center;flex-wrap:wrap;margin:18px 0}.community-entry>span,.hint{font-size:13px;line-height:1.7;color:var(--el-text-color-secondary)}.filters{display:grid;grid-template-columns:2fr 2fr 1fr;gap:0 16px}.el-select{width:100%}.community-results{max-height:410px;overflow:auto}.community-results article{padding:16px 0;border-bottom:1px solid var(--el-border-color-light)}.entry-team,.entry-actions,.pagination{display:flex;gap:16px;align-items:center;justify-content:space-between}.entry-team{flex-wrap:wrap}.community-results p{font-size:13px;line-height:1.6;margin:10px 0;overflow-wrap:anywhere}.pagination{justify-content:center;margin:16px}.el-alert{margin-bottom:10px}pre{white-space:pre-wrap;overflow-wrap:anywhere;font-size:12px;background:var(--el-fill-color-light);padding:12px;border-radius:6px}.el-checkbox{margin:14px 0}.el-form-item{margin-top:12px}@media(max-width:700px){.filters{grid-template-columns:1fr}.entry-team{align-items:start;flex-direction:column}}
</style>
