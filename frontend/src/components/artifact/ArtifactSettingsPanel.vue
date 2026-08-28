<script setup>
import {onMounted, reactive, ref} from 'vue'
import {Check, RefreshLeft} from '@element-plus/icons-vue'
import {ElMessage} from 'element-plus'
import {getArtifactSettings, saveArtifactSettings} from '@api/artifact/artifactAnalysis.js'

const loading = ref(false)
const saving = ref(false)
const settings = reactive({unfinishedPotentialThreshold:75, finishedScoreThreshold:80, fourLineStartProbability:0.2})
const load = async () => { loading.value = true; try { Object.assign(settings, await getArtifactSettings()) } catch { ElMessage.error('算法设置加载失败，请稍后重试') } finally { loading.value = false } }
const reset = () => Object.assign(settings, {unfinishedPotentialThreshold:75, finishedScoreThreshold:80, fourLineStartProbability:0.2})
const save = async () => { saving.value = true; try { Object.assign(settings, await saveArtifactSettings(settings)); ElMessage.success('算法设置已保存') } finally { saving.value = false } }
onMounted(load)
</script>

<template>
  <section v-loading="loading" class="settings-panel">
    <header><div><h2>算法设置</h2><p>分别控制未满级潜力和 +20 成品保留阈值。</p></div><div><el-button :icon="RefreshLeft" @click="reset">恢复默认</el-button><el-button type="primary" :icon="Check" :loading="saving" @click="save">保存</el-button></div></header>
    <div class="setting-row"><div><strong>未满级潜力阈值</strong><p>默认 75，约对应值得继续强化的候选。</p></div><el-slider v-model="settings.unfinishedPotentialThreshold" :min="0" :max="100" show-input/></div>
    <div class="setting-row"><div><strong>+20 成品分数阈值</strong><p>默认 80，主词条不匹配时不会仅凭分数保留。</p></div><el-slider v-model="settings.finishedScoreThreshold" :min="0" :max="100" show-input/></div>
    <div class="setting-row"><div><strong>初始四词条概率</strong><p>保留给总体分布与稀有度分析；已扫描圣遗物的条件潜力不使用该先验。</p></div><el-slider v-model="settings.fourLineStartProbability" :min="0" :max="1" :step="0.01" show-input/></div>
  </section>
</template>

<style scoped>
.settings-panel{max-width:980px}.settings-panel header,.settings-panel header>div:last-child,.setting-row{display:flex;align-items:center}.settings-panel header{justify-content:space-between;gap:16px;padding-bottom:16px;border-bottom:1px solid var(--el-border-color-lighter)}.settings-panel header h2{margin:0;font-size:18px}.settings-panel header p,.setting-row p{margin:4px 0 0;color:var(--el-text-color-secondary);font-size:13px}.settings-panel header>div:last-child{gap:8px}.setting-row{justify-content:space-between;gap:30px;min-height:105px;border-bottom:1px solid var(--el-border-color-lighter)}.setting-row>div{min-width:270px}.setting-row .el-slider{max-width:520px}@media(max-width:720px){.settings-panel header,.setting-row{align-items:stretch;flex-direction:column}.setting-row{gap:10px;padding:18px 0}.setting-row>div{min-width:0}.setting-row .el-slider{width:100%}}
</style>
