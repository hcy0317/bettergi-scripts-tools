<script setup>
import {computed,ref,watch} from 'vue'
import {metric} from '@/features/artifact-optimizer/model.js'
import {nativeAssumptionLabel} from '@/features/artifact-optimizer/native-flow.js'
const props=defineProps({report:{type:Object,required:true}})
const index=ref(0)
watch(()=>props.report,()=>index.value=0)
const trace=computed(()=>props.report.roundTraces?.[index.value])
const stateNames={complete:'实际轮次已完整记录',partial:'末轮被截断，不能冒充完整轮次',ambiguous:'存在多个主循环，尚未指定统计范围',unavailable:'无法可靠识别完整轮次'}
const eventNames={action:'动作已执行',call:'进入片段',return:'片段结束',branch:'选择分支',record:'生成记录',maintenance:'补充维护',maintenance_failed:'维护未进展',skipped:'跳过',failed:'未满足',satisfied:'已有状态满足',check:'战斗结束检查'}
function detail(row){if(row.kind==='action')return ({skill:'元素战技',burst:'元素爆发',charge:'标准重击映射',attack:'普攻'})[row.detail]||row.detail;return row.detail.replace(/^then:/,'条件成立 → ').replace(/^else:/,'条件否定 → ').replace(/^unknown:/,'观测未知 → ').replace(/:true$/,': 完成').replace(/:false$/,': 未完成').replace(/^maintain:/,'维护窗口仍有效：').replace(/^once:/,'本场开场已完成：')}
</script>
<template>
  <el-alert v-for="warning in report.warnings||[]" :key="warning" :title="warning" type="warning" :closable="false"/>
  <el-alert v-for="assumption in (report.assumptions||[]).filter(a=>a.startsWith('native_'))" :key="assumption" :title="nativeAssumptionLabel(assumption)" type="warning" :closable="false"/>
  <el-collapse v-if="report.nativeFlowTraces?.length"><el-collapse-item title="查看原生流程的分支与维护轨迹（诊断样本）" name="native-traces"><p>最多展示前4个样本各128条诊断；诊断截断不代表截断伤害或验证证据。</p><section v-for="sample in report.nativeFlowTraces" :key="sample.seed"><p>种子 {{ sample.seed }}{{ sample.truncated?' · 后续诊断已省略':'' }}</p><el-table :data="sample.events" size="small" max-height="320"><el-table-column label="秒" width="85"><template #default="{row}">{{ metric(row.frame/60) }}</template></el-table-column><el-table-column label="事件" width="120"><template #default="{row}">{{ eventNames[row.kind]||'流程事件' }}</template></el-table-column><el-table-column label="内容"><template #default="{row}">{{ detail(row) }}</template></el-table-column></el-table></section></el-collapse-item></el-collapse>
  <section v-if="report.roundTraces?.length" class="round-report">
    <h4>脚本实际轮次与耗时</h4><p>已执行 {{ report.samplingIterations||report.roundTraces.length }} 次独立随机模拟。下列时间来自本样本的脚本边界，不是手填或静态估算。</p>
    <el-select v-model="index" filterable aria-label="查看轮次的随机样本"><el-option v-for="(sample,i) in report.roundTraces" :key="sample.seed" :value="i" :label="`样本 ${i+1}（种子 ${sample.seed}）`"/></el-select>
    <template v-if="trace"><el-alert :title="stateNames[trace.state]||'轮次状态待检查'" :type="trace.state==='complete'?'success':'warning'" :closable="false"/>
      <el-alert v-for="issue in trace.issues||[]" :key="issue" :title="issue" type="warning" :closable="false"/>
      <el-table :data="trace.rounds||[]" size="small"><el-table-column label="轮次" width="90"><template #default="{ $index }">{{ $index+1 }}</template></el-table-column><el-table-column label="开始（秒）"><template #default="{row}">{{ metric(row.startFrame/60) }}</template></el-table-column><el-table-column label="结束（秒）"><template #default="{row}">{{ metric(row.endFrame/60) }}</template></el-table-column><el-table-column label="实际耗时（秒）"><template #default="{row}">{{ metric(row.durationSeconds) }}</template></el-table-column><el-table-column label="完整性"><template #default="{row}">{{ row.complete?'完整':'未完成' }}</template></el-table-column></el-table>
      <p v-if="trace.choices?.length>1">可选主循环位置：{{ trace.choices.map(c=>`第${c.index}个，脚本第${c.line}行`).join('；') }}。请回方案中选择后重新计算。</p>
    </template>
    <p v-if="!report.roundMetricsAvailable">本批次逐轮指标无法完整判定；没有逐轮要求时，整场每秒伤害仍可参考。</p>
    <el-alert v-for="issue in (report.metricIssues||[]).slice(0,5)" :key="issue" :title="issue" type="warning" :closable="false"/>
    <p v-if="report.samplesCompacted">本次保存了逐样本摘要、逐轮边界及验证证据，没有保留无关的逐帧绘图缓存。</p>
  </section>
</template>
<style scoped>
.round-report{margin:16px 0;padding:16px;background:var(--el-fill-color-light);border-radius:8px}h4{font-size:15px;margin:0 0 10px}p{font-size:12px;line-height:1.6;color:var(--el-text-color-secondary)}.el-select{max-width:360px;margin-bottom:12px}.el-alert{margin-bottom:10px}
</style>
