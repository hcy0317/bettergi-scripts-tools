<script setup>
import {computed,ref,watch} from 'vue'
import {metric} from '@/features/artifact-optimizer/model.js'
const props=defineProps({report:{type:Object,required:true}})
const index=ref(0)
watch(()=>props.report,()=>index.value=0)
const trace=computed(()=>props.report.roundTraces?.[index.value])
const stateNames={complete:'实际轮次已完整记录',partial:'末轮被截断，不能冒充完整轮次',ambiguous:'存在多个主循环，尚未指定统计范围',unavailable:'无法可靠识别完整轮次'}
</script>
<template>
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
    <p v-if="report.samplesCompacted">大样本保存了逐样本摘要、逐轮边界及验证证据，没有保留无关的逐帧绘图缓存。</p>
  </section>
</template>
<style scoped>
.round-report{margin:16px 0;padding:16px;background:var(--el-fill-color-light);border-radius:8px}h4{font-size:15px;margin:0 0 10px}p{font-size:12px;line-height:1.6;color:var(--el-text-color-secondary)}.el-select{max-width:360px;margin-bottom:12px}.el-alert{margin-bottom:10px}
</style>
