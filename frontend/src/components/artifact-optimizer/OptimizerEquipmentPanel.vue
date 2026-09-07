<script setup>
import {computed,onBeforeUnmount,ref,watch} from 'vue'
import * as api from '@/api/artifact/artifactOptimizer.js'
const props=defineProps({uid:{type:String,required:true},job:{type:Object,required:true}})
const plan=ref(null),busy=ref(false),error=ref(''),accepted=ref(false),dialog=ref(false),snapshots=ref([]),recoverySnapshot=ref('')
let generation=0,timer=null
const states={PREVIEW:'待确认',CONFIRMED:'等待已运行宿主接手',RUNNING:'正在穿戴并逐步核验',COMPLETED:'目标穿戴已核验',NEEDS_OBSERVATION:'已停止，需要重新观察',CANCELLED:'已取消',LAUNCH_UNCERTAIN:'交接状态不确定，请查看宿主'}
const rows=computed(()=>Object.entries(plan.value?.originalEquipment||{}).map(([owner,ids])=>({owner,before:ids.join(', ')})))
function clear(){generation++;if(timer)clearTimeout(timer);timer=null;plan.value=null;dialog.value=false;busy.value=false}
watch(()=>[props.uid,props.job.id],clear)
async function poll(id,g,uid){try{const value=await api.getEquipment(uid,id);if(g!==generation)return;plan.value=value;if(['CONFIRMED','RUNNING'].includes(value.state))timer=setTimeout(()=>poll(id,g,uid),1500)}catch(e){if(g===generation)error.value=e.message||String(e)}}
async function preview(){const g=generation;busy.value=true;error.value='';try{const value=await api.previewEquipment(props.uid,props.job.id);if(g!==generation)return;plan.value=value;accepted.value=false;dialog.value=true}catch(e){if(g===generation)error.value=e.message||String(e)}finally{if(g===generation)busy.value=false}}
async function confirm(){if(!accepted.value)return;const g=generation;busy.value=true;error.value='';try{const value=await api.confirmEquipment(props.uid,plan.value.id,plan.value.digest);if(g!==generation)return;plan.value=value;dialog.value=false;await poll(value.id,g,props.uid)}catch(e){if(g===generation)error.value=e.message||String(e)}finally{if(g===generation)busy.value=false}}
async function cancel(){const g=generation;try{const value=await api.cancelEquipment(props.uid,plan.value.id);if(g===generation)plan.value=value}catch(e){error.value=e.message||String(e)}}
async function refreshSnapshots(){try{snapshots.value=await api.loadSnapshots(props.uid)}catch(e){error.value=e.message||String(e)}}
async function recover(){const g=generation;busy.value=true;error.value='';try{const value=await api.recoverEquipment(props.uid,plan.value.id,recoverySnapshot.value);if(g!==generation)return;plan.value=value;accepted.value=false;dialog.value=true}catch(e){if(g===generation)error.value=e.message||String(e)}finally{if(g===generation)busy.value=false}}
onBeforeUnmount(clear)
</script>

<template>
  <section class="equipment-panel">
    <h3>确认后交给 BetterGI 穿戴</h3><p>此操作会改变游戏内装备。计算本身没有执行穿戴；开始前需要确认这份具体方案及所有出借角色。</p>
    <el-alert v-if="error" :title="error" type="error" :closable="false"/>
    <el-button type="primary" plain :loading="busy" @click="preview">预览穿戴计划</el-button>
    <template v-if="plan"><h4>{{ states[plan.state]||plan.state }}</h4><p v-if="plan.state==='CONFIRMED'">已生成一次性宿主请求。请在已配置 BetterGI 中查看任务；网页不会自动启动游戏。</p><el-button v-if="['CONFIRMED','RUNNING'].includes(plan.state)" type="danger" plain @click="cancel">请求安全停止</el-button><el-button @click="poll(plan.id,generation,uid)">读取执行状态</el-button>
      <el-alert v-if="plan.execution?.message" :title="plan.execution.message" type="warning" :closable="false"/><el-table v-if="plan.execution?.steps" :data="plan.execution.steps"><el-table-column prop="character" label="目标角色"/><el-table-column prop="artifactId" label="计划实物 ID"/><el-table-column label="执行状态"><template #default="{row}">{{ {completed:'已确认完成',not_executed:'未执行',unknown:'结果未知，禁止盲重试'}[row.state]||row.state }}</template></el-table-column><el-table-column prop="message" label="说明" min-width="180"/></el-table>
      <section v-if="['NEEDS_OBSERVATION','LAUNCH_UNCERTAIN','CANCELLED'].includes(plan.state)" class="recovery"><h4>重新观察后恢复</h4><p>先在圣遗物分析页重新扫描，再选择新记录生成恢复预览。旧步骤不会自动重放，恢复仍需再次确认。</p><el-button @click="refreshSnapshots">读取新的扫描记录</el-button><el-select v-model="recoverySnapshot" placeholder="选择执行后的新观察"><el-option v-for="s in snapshots" :key="s.id" :value="s.id" :label="`${s.count} 件 ${s.createdAt}`"/></el-select><el-button :disabled="!recoverySnapshot" :loading="busy" @click="recover">生成恢复预览</el-button></section>
    </template>
    <el-dialog v-model="dialog" title="确认具体穿戴方案及完整影响范围" width="min(820px,94vw)">
      <template v-if="plan"><el-alert :title="plan.notice" type="warning" :closable="false"/><p>方案 {{ plan.id }} / {{ plan.digest?.slice(0,12) }}，档案版本 {{ plan.workspaceVersion }}</p><el-table :data="rows"><el-table-column prop="owner" label="受影响角色（扫描名称）"/><el-table-column prop="before" label="原装备实物 ID" min-width="200"/></el-table><el-table :data="plan.targets"><el-table-column prop="inventoryName" label="目标穿戴者"/><el-table-column label="目标实物"><template #default="{row}">{{ row.artifacts.join(', ') }}</template></el-table-column></el-table><el-alert v-for="a in plan.assumptions||[]" :key="a" :title="a" type="warning" :closable="false"/><p>只执行穿戴和核验，不加解锁、不强化、不分解。结果未知或观察改变时立即停下，不能保证自动完成恢复。</p><el-checkbox v-model="accepted">我确认这些目标、出借角色、原装备影响和试算假设，并允许本次穿戴</el-checkbox></template>
      <template #footer><el-button @click="dialog=false">暂不执行</el-button><el-button type="primary" :disabled="!accepted" :loading="busy" @click="confirm">确认并交给宿主</el-button></template>
    </el-dialog>
  </section>
</template>

<style scoped>
.equipment-panel{margin-top:24px;padding-top:16px;border-top:1px solid var(--el-border-color)}h3{font-size:16px}p{font-size:13px;line-height:1.7;color:var(--el-text-color-secondary)}.recovery{margin-top:20px}.recovery .el-select{width:min(400px,100%);margin:10px}.el-alert{margin:12px 0}
</style>
