<script setup>
import {computed, ref} from 'vue'
import {Plus} from '@element-plus/icons-vue'

const props = defineProps({
  modelValue: {type: Array, default: () => []},
  hiddenValues: {type: Array, default: () => []},
  detectedOptions: {type: Array, default: () => []},
  placeholder: {type: String, default: '输入名称后添加'},
  protectedValues: {type: Array, default: () => []}
})
const emit = defineEmits(['update:modelValue', 'update:hiddenValues'])
const draft = ref('')

const visibleOptions = computed(() => {
  const hidden = new Set(props.hiddenValues)
  return Array.from(new Set([...props.detectedOptions, ...props.modelValue]))
    .filter(value => value && !hidden.has(value))
})

const add = () => {
  const value = draft.value.trim()
  if (!value) return
  emit('update:modelValue', Array.from(new Set([...props.modelValue, value])))
  emit('update:hiddenValues', props.hiddenValues.filter(item => item !== value))
  draft.value = ''
}

const remove = value => {
  if (props.protectedValues.includes(value)) return
  emit('update:modelValue', props.modelValue.filter(item => item !== value))
  emit('update:hiddenValues', Array.from(new Set([...props.hiddenValues, value])))
}
</script>

<template>
  <div class="option-manager">
    <div class="option-input-row">
      <el-input v-model="draft" :placeholder="placeholder" clearable @keyup.enter="add"/>
      <el-button :icon="Plus" type="primary" plain :disabled="!draft.trim()" @click="add">添加</el-button>
    </div>
    <div v-if="visibleOptions.length" class="option-tags" aria-live="polite">
      <el-tag
          v-for="option in visibleOptions"
          :key="option"
          :closable="!protectedValues.includes(option)"
          effect="plain"
          @close="remove(option)"
      >{{ option }}</el-tag>
    </div>
    <el-empty v-else :image-size="42" description="尚未检测或添加任何项目"/>
    <small>自动读取 BetterGI 已有配置；新增和隐藏记录会随 UID 设置持久化。</small>
  </div>
</template>

<style scoped>
.option-manager {
  display: grid;
  gap: 10px;
  padding: 12px;
  background: var(--el-fill-color-lighter);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: var(--el-border-radius-base);
}

.option-input-row,
.option-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.option-input-row .el-input {
  flex: 1 1 220px;
}

.option-manager small {
  color: var(--el-text-color-secondary);
  line-height: 1.5;
}
</style>
