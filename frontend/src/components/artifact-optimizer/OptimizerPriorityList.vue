<script setup>
import {computed,onBeforeUnmount,onMounted,ref} from 'vue'
import Draggable from 'vuedraggable'
import {Rank,ArrowUp,ArrowDown} from '@element-plus/icons-vue'
import Avatar from './OptimizerPriorityAvatar.vue'
import {profileLabel,buildLabel} from '@/features/artifact-optimizer/localization.js'
import {priorityRows,priorityStamp,reorderPriorities,setPriorityWeight} from '@/features/artifact-optimizer/priorities.js'
const props=defineProps({workspace:{type:Object,required:true},scope:{type:Object,required:true},catalog:{type:Object,required:true},selected:{type:Array,default:()=>[]},disabled:Boolean,compact:Boolean,title:{type:String,required:true}})
const emit=defineEmits(['changed','failure'])
let ticket=null,alive=true,media
const reduced=ref(false),dragging=ref(false)
const projection=computed(()=>{try{return {rows:priorityRows(props.workspace,props.scope),error:''}}catch(e){return {rows:[],error:e.message}}})
const all=computed(()=>projection.value.rows)
const positive=computed(()=>all.value.filter(row=>row.weight>0)),zero=computed(()=>all.value.filter(row=>row.weight===0))
const scopeId=computed(()=>props.scope.kind+(props.scope.character?'-'+props.scope.character:''))
function label(row){return props.scope.kind==='characters'?profileLabel(props.catalog,row.item):buildLabel(props.workspace.builds.find(b=>b.id===row.id)||{id:row.id,name:'已失联的方案'})}
function members(row){return props.scope.kind==='characters'?[row.id]:(props.workspace.builds.find(b=>b.id===row.id)?.members||[]).map(m=>m.character).slice(0,4)}
function begin(){try{ticket={workspace:props.workspace,stamp:priorityStamp(props.workspace,props.scope),scope:scopeId.value};dragging.value=true}catch(e){ticket=null;emit('failure',e.message)}}
function perform(operation){
  if(!alive||props.disabled)return
  try{const action=operation();if(action)emit('changed',{action,message:`已更新${props.title}，保存档案后生效`})}catch(e){emit('failure',e.message)}
}
function drop(rows){
  perform(()=>{
    if(!ticket||ticket.workspace!==props.workspace||ticket.scope!==scopeId.value)throw new Error('档案已切换，请重新拖动')
    return reorderPriorities(props.workspace,props.scope,rows.map(row=>row.id),ticket.stamp)
  })
}
function shift(row,delta){
  perform(()=>{
    const ids=positive.value.map(r=>r.id),index=ids.indexOf(row.id),next=index+delta
    if(index<0||next<0||next>=ids.length)return null
    const stamp=priorityStamp(props.workspace,props.scope)
    ;[ids[index],ids[next]]=[ids[next],ids[index]]
    return reorderPriorities(props.workspace,props.scope,ids,stamp)
  })
}
function setWeight(row,value){perform(()=>setPriorityWeight(props.workspace,props.scope,row.id,value,priorityStamp(props.workspace,props.scope)))}
function syncMotion(){reduced.value=media.matches}
onMounted(()=>{media=window.matchMedia('(prefers-reduced-motion: reduce)');syncMotion();media.addEventListener('change',syncMotion)})
onBeforeUnmount(()=>{alive=false;ticket=null;media?.removeEventListener('change',syncMotion)})
</script>
<template>
  <section :class="['priority-list',{compact,dragging}]" :data-priority-scope="scopeId" :aria-label="title">
    <header><h3><Avatar v-if="scope.character" :character-key="scope.character" :catalog="catalog"/>{{ title }}</h3><span>{{ all.length }} 项</span></header>
    <p v-if="!compact" class="group-note">拖动左侧手柄，上方优先。正权重按 {{ positive.length }} → 1 重新赋值，0 权重保留。</p>
    <el-alert v-if="projection.error" :title="projection.error+'；请先修正原档案中的权重'" type="error" :closable="false"/>
    <div v-else-if="!all.length" class="empty">{{ scope.kind==='characters'?'添加角色档案后，即可在这里统一排序。':'建立配队方案后，即可在这里统一排序。' }}</div>
    <Draggable :model-value="positive" @update:model-value="drop" item-key="id" handle=".priority-drag-handle" :disabled="disabled" :animation="reduced?0:150" :force-fallback="true" :delay="180" :delay-on-touch-only="true" :touch-start-threshold="6" ghost-class="priority-ghost" chosen-class="priority-chosen" @start="begin" @end="dragging=false;ticket=null" class="sortable-rows">
      <template #item="{element:row,index}">
        <div class="priority-row" :data-priority-id="row.id">
          <button type="button" class="priority-drag-handle" :disabled="disabled" :aria-label="`拖动${label(row)}优先级`" title="拖动排序；键盘上下方向键也可移动" @keydown.up.prevent="shift(row,-1)" @keydown.down.prevent="shift(row,1)"><el-icon><Rank/></el-icon></button>
          <span class="position" :title="`显示位置${index+1}；同权重时保持原档案顺序`">{{ index+1 }}</span>
          <div class="row-identity"><div v-if="!compact" class="avatar-group"><Avatar v-for="key in members(row)" :key="key" :character-key="key" :catalog="catalog"/><span v-if="!members(row).length" class="no-team">暂无队员</span></div><div class="row-label"><strong>{{ label(row) }}</strong><small v-if="scope.kind==='characters'">{{ row.item.builds?.length||0 }} 个方案{{ selected.includes(row.id)?' · 本次参算':' · 未参算' }}</small></div></div>
          <el-input-number :model-value="row.weight" @change="value=>setWeight(row,value)" :min="0" :max="1000" :step="1" :value-on-clear="0" :controls="false" :disabled="disabled" :aria-label="`${label(row)}权重`" class="row-weight"/>
          <div class="move-buttons"><el-button :icon="ArrowUp" :disabled="disabled||index===0" :aria-label="`上移${label(row)}`" @click="shift(row,-1)"/><el-button :icon="ArrowDown" :disabled="disabled||index===positive.length-1" :aria-label="`下移${label(row)}`" @click="shift(row,1)"/></div>
        </div>
      </template>
    </Draggable>
    <div v-if="zero.length" class="zero-section"><p>0 权重保留，不参与拖排；填写正数即可加入。</p><div v-for="row in zero" :key="row.id" class="zero-row"><Avatar v-if="scope.kind==='characters'" :character-key="row.id" :catalog="catalog"/><span class="zero-label">{{ label(row) }}</span><el-input-number :model-value="0" @change="value=>setWeight(row,value)" :min="0" :max="1000" :controls="false" :disabled="disabled" :aria-label="`${label(row)}权重`" class="row-weight"/></div></div>
  </section>
</template>
<style scoped>
.priority-list{min-width:0;background:var(--el-bg-color);border:1px solid var(--el-border-color-lighter);border-radius:12px;overflow:hidden}header{display:flex;align-items:center;justify-content:space-between;gap:12px;padding:18px 18px 8px}h3{display:flex;align-items:center;gap:10px;font-size:16px;margin:0}header>span,.group-note,.empty,.zero-section p{font-size:12px;color:var(--el-text-color-secondary)}.group-note{margin:0;padding:0 18px 14px;line-height:1.6}.sortable-rows{padding:0 8px 8px}.priority-row{display:flex;align-items:center;gap:10px;min-height:68px;padding:7px 8px;border-radius:8px;background:var(--el-bg-color);border-bottom:1px solid var(--el-border-color-extra-light)}.priority-row:hover{background:var(--el-fill-color-light)}.priority-drag-handle{font-size:18px;flex:none;width:32px;height:40px;border:0;background:transparent;color:var(--el-text-color-secondary);display:grid;place-items:center;cursor:grab;touch-action:none;border-radius:6px}.priority-drag-handle:active{cursor:grabbing}.priority-drag-handle:focus-visible{outline:2px solid var(--el-color-primary);outline-offset:1px}.position{font-size:12px;font-variant-numeric:tabular-nums;color:var(--el-text-color-secondary);min-width:16px}.row-identity{display:flex;align-items:center;gap:10px;flex:1;min-width:0}.row-label{min-width:0}.row-label strong{display:block;font-size:14px;overflow-wrap:anywhere;color:var(--el-text-color-primary)}.row-label small{display:block;margin-top:5px;color:var(--el-text-color-regular);font-size:11px}.avatar-group{display:flex;flex-shrink:0}.avatar-group>:not(:first-child){margin-left:-10px}.row-weight{width:72px;flex:none}.move-buttons{display:flex;gap:2px;flex:none}.move-buttons .el-button{width:28px;height:32px;padding:0;margin:0}.empty{padding:26px 18px}.zero-section{padding:4px 18px 12px;background:var(--el-fill-color-light)}.zero-row{display:flex;gap:10px;align-items:center;padding:6px 0}.zero-row>.zero-label{flex:1}.priority-ghost{opacity:.3;background:var(--el-color-primary-light-9);outline:2px dashed var(--el-color-primary)}.priority-chosen{box-shadow:0 4px 16px rgba(31,52,90,.14)}.no-team{font-size:11px;color:var(--el-text-color-secondary)}.compact header{padding:12px 16px 8px}.compact h3{font-size:14px}.compact .priority-row{min-height:50px}.compact .position{display:none}.compact .row-label strong{font-weight:500}.compact .sortable-rows{padding-bottom:4px}
@media(max-width:600px){header{padding:14px 12px 8px}.group-note{padding:0 12px 10px}.priority-row{gap:5px;padding:6px 2px}.position{display:none}.row-identity{gap:6px}.avatar-group{max-width:76px;flex-wrap:wrap;row-gap:2px}.avatar-group :deep(.priority-avatar){width:28px;height:28px;flex-basis:28px}.avatar-group>:not(:first-child){margin-left:-5px}.row-weight{width:60px}.move-buttons{flex-direction:column}.move-buttons .el-button{height:26px;width:28px}.priority-drag-handle{width:44px;height:44px}.row-label strong{font-size:13px}}
@media(prefers-reduced-motion:reduce){*{transition:none!important}}
</style>
