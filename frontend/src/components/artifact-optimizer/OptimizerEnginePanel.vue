<script setup>
import {ref} from 'vue'
import {ElMessageBox} from 'element-plus'
import * as api from '@/api/artifact/artifactOptimizer.js'
const props=defineProps({catalog:{type:Object,required:true}})
const emit=defineEmits(['changed'])
const result=ref(null),busy=ref(false),error=ref(''),message=ref(''),assetId=ref(null)
async function check(){busy.value=true;error.value='';try{result.value=await api.checkEngineUpdates()}catch(e){error.value=e.message||String(e)}finally{busy.value=false}}
async function install(){try{await ElMessageBox.confirm('从本仓库发布包验证并切换引擎？已有计算结束前不会切换。旧有效程序和个人档案保留，手动 Buff 需要针对新版本复核。','确认更新',{type:'warning'})}catch{return}busy.value=true;error.value='';try{const value=await api.installEnginePackage(assetId.value);message.value=value.note;emit('changed')}catch(e){error.value=e.message||String(e)}finally{busy.value=false}}
async function rollback(){try{await ElMessageBox.confirm('回到上一有效引擎及其数据？不会覆盖个人 Build 或手工补充。','确认回退',{type:'warning'})}catch{return}busy.value=true;error.value='';try{const value=await api.rollbackEngine();message.value=value.note;emit('changed')}catch(e){error.value=e.message||String(e)}finally{busy.value=false}}
</script>

<template>
  <section class="engine-panel"><h2>gcsim 引擎与持续维护</h2><p>角色、武器、套装、技能数值和动态反应来自同一个固定版本的 gcsim。社区主词条等级表是独立补充；个人条件与 Buff 不随更新覆盖。</p>
    <el-descriptions :column="1" border><el-descriptions-item label="当前引擎">{{ catalog.engineRevision||'尚未配置' }}</el-descriptions-item><el-descriptions-item label="已知条目">{{ catalog.characters?.length||0 }} 角色 / {{ catalog.weapons?.length||0 }} 武器 / {{ catalog.sets?.length||0 }} 套装</el-descriptions-item><el-descriptions-item label="上游标记不完整">{{ catalog.capabilities?.upstreamMarkedIncomplete?.join(', ')||'暂无或未连接' }}</el-descriptions-item></el-descriptions>
    <el-alert v-if="catalog.nativeAliasWarning" :title="catalog.nativeAliasWarning" type="warning" :closable="false"/><el-alert v-if="error" :title="error" type="error" :closable="false"/><el-alert v-if="message" :title="message" type="success" :closable="false"/>
    <div class="actions"><el-button :loading="busy" @click="check">检查上游与验证包</el-button><el-button :disabled="busy" @click="rollback">回退上一有效版本</el-button></div>
    <template v-if="result"><p>gcsim 上游最新提交：<code>{{ result.upstreamRevision }}</code></p><p>{{ result.note }}</p><el-select v-model="assetId" placeholder="选择与当前平台一致的已验证发布包"><el-option v-for="p in result.packages||[]" :key="p.assetId" :value="p.assetId" :label="`${p.release} / ${p.name}`"/></el-select><el-button :disabled="!assetId||busy" :loading="busy" @click="install">验证并切换</el-button><el-empty v-if="!result.packages?.length" description="尚无匹配的验证发布包。可使用仓库 Build verified gcsim engine packages 工作流生成，回归失败时不会发布。" :image-size="70"/></template>
    <p class="hint">坏包、断网、字段漂移或回归失败不会替换当前程序。上游有新条目不等于所有组合完整支持；试算与实机证据始终分开。</p>
  </section>
</template>
<style scoped>
h2{font-size:20px}p{font-size:13px;line-height:1.7;color:var(--el-text-color-secondary)}.actions{display:flex;gap:12px;flex-wrap:wrap;margin:18px 0}.el-select{width:min(560px,100%);margin:12px 12px 12px 0}.el-alert{margin-top:12px}code{overflow-wrap:anywhere}.hint{margin-top:24px}
</style>
