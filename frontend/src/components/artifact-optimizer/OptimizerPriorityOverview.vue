<script setup>
import {computed,ref,shallowRef,watch} from 'vue'
import {RefreshLeft} from '@element-plus/icons-vue'
import PriorityList from './OptimizerPriorityList.vue'
import {profileLabel} from '@/features/artifact-optimizer/localization.js'
import {priorityStamp,undoPriorities} from '@/features/artifact-optimizer/priorities.js'
const props=defineProps({workspace:{type:Object,required:true},catalog:{type:Object,required:true},selected:{type:Array,default:()=>[]},mode:{type:String,default:'balanced'},disabled:Boolean})
// Keep the original workspace identity; do not deep-wrap the transaction.
const last=shallowRef(null),message=ref(''),error=ref('')
const canUndo=computed(()=>{const action=last.value;if(!action||action.workspace!==props.workspace)return false;try{return priorityStamp(props.workspace,action.scope)===action.after}catch{return false}})
watch(()=>props.workspace,()=>{last.value=null;message.value='';error.value=''})
function changed(value){last.value=value.action;message.value=value.message;error.value=''}
function undo(){try{undoPriorities(props.workspace,last.value);last.value=null;message.value='已撤销最近的权重调整，保存档案后生效';error.value=''}catch(e){error.value=e.message}}
const linked=computed(()=>props.workspace.characters.filter(c=>c.builds?.length))
</script>
<template>
  <section class="priority-overview" aria-label="优先级总表">
    <div class="overview-heading"><div><h2>优先级总表</h2><p>角色、队伍和个人方案偏好，在这里一次排好。</p></div><el-button :icon="RefreshLeft" :disabled="disabled||!canUndo" @click="undo">撤销最近调整</el-button></div>
    <div class="priority-guidance"><strong>{{ mode==='balanced'?'当前均衡模式：队伍权重参与计算。':'角色权重与个人方案偏好用于保尖 / 兜底。' }}</strong><p>拖动后本组正权重按 N → 1 递减，0 权重保留；完成后“保存档案”。</p><details><summary>作用范围与保存规则</summary><p>队伍权重对应去重场景；角色及个人方案偏好用于保尖 / 兜底，不是强制先配谁。不改装备保护、队员或参算勾选；未操作时保留原数值，也可在总表直接填写精确权重。</p></details></div>
    <p class="operation-status" role="status" aria-live="polite">{{ message||'拖动手柄调整顺序，也可使用上下箭头；触屏长按手柄。' }}</p>
    <el-alert v-if="error" :title="error" type="error" :closable="false"/>
    <div class="priority-columns"><PriorityList :workspace="workspace" :scope="{kind:'characters'}" :catalog="catalog" :selected="selected" :disabled="disabled" title="角色优先级" @changed="changed" @failure="error=$event"/><PriorityList :workspace="workspace" :scope="{kind:'builds'}" :catalog="catalog" :disabled="disabled" title="队伍优先级" @changed="changed" @failure="error=$event"/></div>
    <section v-if="linked.length" class="binding-overview"><h2>角色在各队伍中的偏好</h2><p>只影响这个角色的个人目标，不改变上方的全局队伍权重。同组上方优先。</p><div class="binding-columns"><PriorityList v-for="character in linked" :key="character.key" :workspace="workspace" :scope="{kind:'bindings',character:character.key}" :catalog="catalog" :disabled="disabled" :title="profileLabel(catalog,character)" compact @changed="changed" @failure="error=$event"/></div></section>
  </section>
</template>
<style scoped>
.overview-heading{display:flex;justify-content:space-between;align-items:center;gap:16px;margin:0 0 18px}h2{font-size:20px;line-height:1.4;margin:0;color:var(--el-text-color-primary)}p{font-size:13px;line-height:1.7;color:var(--el-text-color-secondary);margin:6px 0}.priority-guidance{padding:14px 18px;background:var(--el-color-primary-light-9);border-left:3px solid var(--el-color-primary);border-radius:0 8px 8px 0;font-size:13px;color:var(--el-text-color-primary)}.priority-guidance p{margin:4px 0;color:var(--el-text-color-regular)}.priority-guidance summary{cursor:pointer;font-size:12px;margin-top:6px;color:var(--el-color-primary)}.priority-columns{display:grid;grid-template-columns:minmax(0,1fr) minmax(0,1.1fr);gap:18px;align-items:start}.operation-status{min-height:24px;margin:12px 0;color:var(--el-color-primary)}.binding-overview{margin-top:28px}.binding-overview h2{font-size:17px}.binding-columns{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:14px;margin-top:14px}.el-alert{margin-bottom:14px}@media(max-width:1000px){.priority-columns{grid-template-columns:1fr}}@media(max-width:650px){.binding-columns{grid-template-columns:1fr}.overview-heading{align-items:flex-start;flex-direction:column;gap:8px}h2{font-size:19px}.priority-guidance{padding:12px}.operation-status{font-size:12px}}
</style>
