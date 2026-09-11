<script setup>
import {computed,ref,watch} from 'vue'
import {characterLabel} from '@/features/artifact-optimizer/localization.js'
const props=defineProps({characterKey:{type:String,required:true},catalog:{type:Object,required:true}})
const failed=ref(false)
const name=computed(()=>characterLabel(props.catalog,props.characterKey))
const icon=computed(()=>props.catalog.characters?.find(c=>c.key===props.characterKey)?.icon_name||'')
const url=computed(()=>/^[A-Za-z0-9_]+$/.test(icon.value)?`https://enka.network/ui/${icon.value}.png`:'')
watch(url,()=>failed.value=false)
</script>
<template><span class="priority-avatar" :title="name"><img v-if="url&&!failed" :src="url" :alt="`${name}头像`" width="40" height="40" loading="lazy" draggable="false" @error="failed=true"/><span v-else :aria-label="`${name}头像不可用`">{{ name.slice(0,1) }}</span></span></template>
<style scoped>
.priority-avatar{display:inline-grid;place-items:center;flex:0 0 40px;width:40px;height:40px;overflow:hidden;border-radius:9px;background:var(--el-fill-color-dark);color:var(--el-text-color-primary);font-weight:600;border:1px solid var(--el-border-color-lighter)}img{width:100%;height:100%;object-fit:cover;object-position:top}
</style>
