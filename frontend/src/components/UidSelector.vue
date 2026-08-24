<script setup>
import {computed, onMounted} from 'vue'
import {Star, StarFilled} from '@element-plus/icons-vue'
import {ElMessage} from 'element-plus'
import {useUidSelection} from '@/composables/useUidSelection.js'

const props = defineProps({
  modelValue: {type: String, default: ''},
  disabled: {type: Boolean, default: false},
  autoSelectDefault: {type: Boolean, default: true}
})
const emit = defineEmits(['update:modelValue', 'change', 'default-selected'])
const {uidOptions, defaultUid, loadingUidOptions, loadUidOptions, setDefaultUid} = useUidSelection()

const value = computed({
  get: () => props.modelValue,
  set: next => emit('update:modelValue', String(next || '').trim())
})
const selectedRecord = computed(() => uidOptions.value.find(item => item.uid === value.value))
const canSetDefault = computed(() => selectedRecord.value && !selectedRecord.value.defaultUid)
const optionLabel = item => item.as && item.as !== '未命名账号'
  ? `${item.as} · ${item.uid}`
  : item.uid

const selectDefault = async () => {
  if (!selectedRecord.value) {
    ElMessage.info('请先在 UID 管理中保存该 UID，再设为默认')
    return
  }
  await setDefaultUid(value.value)
  emit('default-selected', value.value)
}

onMounted(async () => {
  await loadUidOptions()
  if (props.autoSelectDefault && !value.value && defaultUid.value) {
    value.value = defaultUid.value
    emit('change', defaultUid.value)
  }
})
</script>

<template>
  <div class="uid-selector">
    <el-select
        v-model="value"
        filterable
        allow-create
        default-first-option
        clearable
        :disabled="disabled"
        :loading="loadingUidOptions"
        placeholder="输入或选择 UID"
        @focus="loadUidOptions(true)"
        @change="next => emit('change', next)"
    >
      <el-option
          v-for="item in uidOptions"
          :key="item.uid"
          :label="optionLabel(item)"
          :value="item.uid"
      >
        <span v-if="item.as && item.as !== '未命名账号'" class="uid-option-main">{{ item.as }}</span>
        <span class="uid-option-id">{{ item.uid }}</span>
        <el-tag v-if="item.defaultUid" size="small" type="success" effect="plain">默认</el-tag>
      </el-option>
    </el-select>
    <el-tooltip :content="canSetDefault ? '设为默认 UID' : (selectedRecord?.defaultUid ? '当前默认 UID' : '保存后可设为默认')">
      <el-button
          circle
          :disabled="disabled || !value || !canSetDefault"
          :icon="selectedRecord?.defaultUid ? StarFilled : Star"
          @click="selectDefault"
      />
    </el-tooltip>
  </div>
</template>

<style scoped>
.uid-selector {
  display: grid;
  grid-template-columns: minmax(190px, 1fr) 34px;
  gap: 8px;
  align-items: center;
  width: 100%;
}

.uid-option-main {
  margin-right: 10px;
  color: #252a30;
}

.uid-option-id {
  margin-right: 10px;
  color: #747b84;
  font-variant-numeric: tabular-nums;
}
</style>
