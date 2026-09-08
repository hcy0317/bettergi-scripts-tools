<script setup>
defineProps({build:{type:Object,required:true}})
</script>
<template>
  <section v-if="build.nativeRotation" class="native-source" aria-label="已导入的原生控制流程">
    <header><div><h3>原生战斗流程</h3><span>{{ build.nativeRotation.sourceName||'粘贴的策略' }}</span></div><el-tag type="warning">带假设试算</el-tag></header>
    <el-switch v-model="build.nativeRotation.enabled" aria-label="使用此原生流程" active-text="使用此原生流程" inactive-text="保留原文，使用gcsim / 有限动作"/>
    <p>启用时，配装和循环优化使用同一份控制流程；原gcsim脚本保留为参考。不会修改BetterGI策略文件。</p>
    <div class="blocks"><el-tag v-for="block in build.nativeRotation.summary?.blocks||[]" :key="block.name" :type="block.macro?'warning':'info'">{{ block.name }}{{ block.macro?' · 标准重击映射':block.atomic?' · 不拆分':'' }}</el-tag></div>
    <p v-if="build.nativeRotation.macroMapping">原喷射宏完整保留；模拟时用gcsim标准重击，不能证明鼠标横扫的命中、吸水滴或实机效果。</p>
    <details><summary>查看已导入的完整原文</summary><el-input :model-value="build.nativeRotation.source" type="textarea" :rows="8" readonly aria-label="已导入原生流程原文"/></details>
  </section>
</template>
<style scoped>
.native-source{border:1px solid var(--el-color-warning-light-5);background:var(--el-color-warning-light-9);border-radius:10px;padding:16px;margin:16px 0}header{display:flex;justify-content:space-between;align-items:start;gap:12px}h3{font-size:16px;margin:0 0 5px}header span,p{font-size:12px;color:var(--el-text-color-secondary);line-height:1.7}p{margin:10px 0}.blocks{display:flex;flex-wrap:wrap;gap:6px;margin-top:12px}.el-switch{margin-top:12px}summary{cursor:pointer;font-size:12px;padding:8px 0}:deep(textarea){font-family:Consolas,monospace;line-height:1.6}@media(max-width:600px){header{flex-wrap:wrap}}
</style>
