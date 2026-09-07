<script setup>
import {computed} from 'vue'
import {metric,resultLabels,slotOptions,statOptions} from '@/features/artifact-optimizer/model.js'
import {characterLabel,profileLabel,setLabel,jobLabels,metricLabels} from '@/features/artifact-optimizer/localization.js'
import EquipmentPanel from './OptimizerEquipmentPanel.vue'
const props=defineProps({catalog:{type:Object,default:()=>({})},job:{type:Object,default:null},items:{type:Array,default:()=>[]},characters:{type:Array,default:()=>[]}})
const result=computed(()=>props.job?.result)
const plan=computed(()=>result.value?.plan)
const characterName=key=>profileLabel(props.catalog,props.characters.find(c=>c.key===key)||{key})
const item=id=>props.items.find(i=>i.scanIndex===id)
const slotName=key=>slotOptions.find(s=>s[0]===key)?.[1]||key
const statName=key=>statOptions.find(s=>s[0]===key)?.[1]||key
const scenes=computed(()=>Object.entries(plan.value?.reports||{}).map(([id,report])=>({id,...report,baseline:result.value?.baseline?.reports?.[id]?.meanDps})))
function download(){const blob=new Blob([JSON.stringify(props.job,null,2)],{type:'application/json'});const url=URL.createObjectURL(blob);const link=document.createElement('a');link.href=url;link.download=`配装-${props.job.id}.json`;link.click();setTimeout(()=>URL.revokeObjectURL(url),1000)}
</script>

<template>
  <section aria-live="polite">
    <el-empty v-if="!job" description="选择角色、方案 和背包快照后开始计算。每名参选角色都会得到一套通用装备。"/>
    <template v-else>
      <div class="result-heading"><h2>{{ result?resultLabels[result.status]||result.status:({QUEUED:'等待计算',RUNNING:'正在搜索与复评',FAILED:'计算失败',CANCELLED:'计算已取消',INTERRUPTED:'任务已中断'}[job.state]||job.state) }}</h2><el-button v-if="result" @click="download">导出结果 JSON</el-button></div>
      <el-alert v-if="job.error" :title="job.error" type="error" :closable="false"/>
      <el-skeleton v-if="['QUEUED','RUNNING'].includes(job.state)" :rows="6" animated/>
      <p v-if="['QUEUED','RUNNING'].includes(job.state)" class="hint">计算在独立受限进程中进行。先搜索再使用独立种子复评；结束前不显示虚假收益，也不会自动操作游戏。</p>
      <template v-if="result">
        <div class="summary"><span>模拟评估 <strong>{{ result.evaluations }}</strong></span><span>搜索范围 <strong>{{ result.exhaustive?'本次完整枚举':'有预算搜索' }}</strong></span><span v-if="plan">加权实际总 每秒伤害 <strong>{{ metric(plan.rank.weightedDps) }}</strong></span></div>
        <el-alert v-if="result.status==='feasible_uncertain'" title="当前方案满足声明验证集，但改善尚未获得充分统计证据，请结合基线和逐场景结果判断。" type="warning" :closable="false"/>
        <el-alert v-if="!plan" :title="result.message||'没有合格推荐；不会回退到不合格旧装。'" type="warning" :closable="false"/>
        <template v-if="plan">
          <p class="hint">所有已选 方案 共用以下装备，实物不会被两名角色重复占用。验证仅覆盖本次声明样本，不代表实机已验证。</p>
          <section v-for="(ids,key) in plan.equipment" :key="key" class="outfit"><h3>{{ characterName(key) }}</h3><div class="gear-grid">
            <article v-for="id in ids" :key="id" class="gear"><template v-if="item(id)"><div class="gear-top"><strong>{{ slotName(item(id).slotKey) }}</strong><span>+{{ item(id).level }}</span></div><p>{{ setLabel(catalog,item(id).setKey) }}</p><b>{{ statName(item(id).mainStatKey) }}</b><div v-for="sub in item(id).substats" :key="sub.key" class="substat"><span>{{ statName(sub.key) }}</span><span>{{ sub.value }}</span></div><small>#{{ id }} · 来源：{{ item(id).location?characterName(item(id).location.toLowerCase()):'未装备' }}{{ item(id).locked?' · 已锁定':'' }}</small></template><template v-else><strong>实物 #{{ id }}</strong><p>快照详情尚未加载</p></template></article>
          </div></section>
          <h3>逐场景比较</h3><el-table :data="scenes" stripe><el-table-column prop="id" label="场景" min-width="120"/><el-table-column label="原装 每秒伤害" min-width="110"><template #default="{row}">{{ metric(row.baseline) }}</template></el-table-column><el-table-column label="方案 每秒伤害" min-width="110"><template #default="{row}">{{ metric(row.meanDps) }}</template></el-table-column><el-table-column label="差值" min-width="100"><template #default="{row}">{{ row.baseline===undefined?'未知':metric(row.meanDps-row.baseline) }}</template></el-table-column><el-table-column label="验证" min-width="110"><template #default="{row}"><el-tag :type="row.validation.state==='passed'?'success':'warning'">{{ row.validation.state==='passed'?'本样本集通过':(jobLabels[row.validation.state]||'待检查') }}</el-tag></template></el-table-column><el-table-column label="支持状态" min-width="100"><template #default="{row}">{{ row.support==='trial'?'标记试算':'原生模拟' }}</template></el-table-column></el-table>
          <el-collapse class="details"><el-collapse-item title="角色目标、假设与验证明细" name="evidence"><p>保尖目标保留率：{{ plan.rank.targetRetentionAvailable?metric(plan.rank.targetRetention*100)+'%':'未使用或不可用' }}</p><p>参照来源：{{ result.referenceSource?'本次预算内合格样本生成并冻结':'用户明示目标' }}</p><div v-for="scene in scenes" :key="scene.id"><h4>{{ scene.id }}</h4><el-alert v-for="warning in scene.assumptions||[]" :key="warning" :title="warning" type="warning" :closable="false"/><el-table :data="scene.metrics||[]" size="small"><el-table-column label="角色"><template #default="{row}">{{ characterName(row.character) }}</template></el-table-column><el-table-column label="指标" min-width="180"><template #default="{row}">{{ metricLabels[row.kind]||'其他指标' }}</template></el-table-column><el-table-column label="原始值"><template #default="{row}">{{ metric(row.value) }}</template></el-table-column><el-table-column label="单位"><template #default="{row}">{{ metricLabels[row.unit]||'数值' }}</template></el-table-column><el-table-column label="跨样本口径" min-width="210"><template #default="{row}">{{ metricLabels[row.aggregation]||'原始样本聚合' }}</template></el-table-column></el-table><el-table v-if="scene.validation?.checks?.length" :data="scene.validation.checks" max-height="300" size="small"><el-table-column prop="seed" label="种子"/><el-table-column prop="round" label="轮次"/><el-table-column label="状态"><template #default="{row}">{{ jobLabels[row.state]||'待检查' }}</template></el-table-column><el-table-column prop="observed" label="观测"/><el-table-column prop="reason" label="原因" min-width="160"/></el-table></div></el-collapse-item></el-collapse>
          <h3>换装影响范围</h3><p class="hint">以下包括全部目标角色、出借角色和卸下的旧装。计算不会自动执行穿戴。</p><el-table :data="result.impacts||[]"><el-table-column label="受影响角色"><template #default="{row}">{{ characterName(row.character) }}</template></el-table-column><el-table-column label="之前实物"><template #default="{row}">{{ row.before?.join(', ')||'无' }}</template></el-table-column><el-table-column label="之后实物"><template #default="{row}">{{ row.after?.join(', ')||'空装备' }}</template></el-table-column></el-table>
          <EquipmentPanel :catalog="catalog" :uid="job.uid" :job="job"/>
        </template>
      </template>
    </template>
  </section>
</template>

<style scoped>
.result-heading{display:flex;align-items:center;justify-content:space-between;gap:12px}h2{font-size:20px}h3{font-size:16px}.summary{display:flex;gap:24px;flex-wrap:wrap;margin:18px 0}.summary span{display:grid;gap:6px;font-size:12px;color:var(--el-text-color-secondary)}.summary strong{font-size:23px;color:var(--el-text-color-primary);font-variant-numeric:tabular-nums}.hint{font-size:13px;color:var(--el-text-color-secondary);line-height:1.7}.outfit{margin:24px 0}.gear-grid{display:grid;grid-template-columns:repeat(5,minmax(0,1fr));gap:12px}.gear{padding:14px;background:var(--el-fill-color-light);border:1px solid var(--el-border-color-lighter);border-radius:8px;min-width:0}.gear-top,.substat{display:flex;justify-content:space-between;gap:6px}.gear p{font-size:13px;min-height:34px}.gear b{font-size:13px}.gear small{display:block;margin-top:12px;font-size:11px;overflow-wrap:anywhere;color:var(--el-text-color-secondary)}.substat{font-size:12px;margin-top:6px}.details{margin:20px 0}@media(max-width:1100px){.gear-grid{grid-template-columns:repeat(3,minmax(0,1fr))}}@media(max-width:650px){.gear-grid{grid-template-columns:repeat(2,minmax(0,1fr))}.result-heading{align-items:start;flex-direction:column}}
</style>
