<script setup>
import {computed, ref, watch} from 'vue'
import {
  artifactCharacterAvatarUrl, artifactCharacterLabel,
} from '@/features/artifact-analysis/buildModel.js'

const props = defineProps({characterKey: {type: String, default: ''}})
const failed = ref(false)
const label = computed(() => artifactCharacterLabel(props.characterKey))
const url = computed(() => artifactCharacterAvatarUrl(props.characterKey))
const fallback = computed(() => label.value.slice(0, 2))
watch(() => props.characterKey, () => { failed.value = false })
</script>

<template>
  <span class="character-avatar" :aria-label="`${label}头像`">
    <img v-if="url && !failed" :src="url" :alt="`${label}头像`" loading="lazy" @error="failed = true"/>
    <span v-else aria-hidden="true">{{ fallback }}</span>
  </span>
</template>

<style scoped>
.character-avatar{display:grid;place-items:center;flex:0 0 42px;width:42px;height:42px;overflow:hidden;border:1px solid var(--el-border-color-lighter);border-radius:6px;background:#343a40;color:#fff;font-size:12px;font-weight:700}.character-avatar img{width:100%;height:100%;object-fit:cover;object-position:center top}
</style>
