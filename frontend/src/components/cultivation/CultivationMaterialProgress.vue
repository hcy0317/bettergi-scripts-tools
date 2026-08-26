<script setup>
import {computed} from 'vue'

const props = defineProps({
  items: {type: Array, default: () => []}
})

const orderedItems = computed(() => [...props.items].sort((left, right) =>
  (left.tierIndex ?? 0) - (right.tierIndex ?? 0)))
const isTiered = computed(() => orderedItems.value.length > 1)
const formatCount = value => Number(value || 0).toLocaleString()
</script>

<template>
  <div class="material-progress" :class="{'is-tiered': isTiered}">
    <div v-if="isTiered" class="family-caption">
      <span>多级材料</span>
      <el-tag size="small" effect="plain">{{ orderedItems.length }} 级</el-tag>
    </div>
    <div v-for="item in orderedItems" :key="item.materialName" class="material-tier">
      <div class="material-identity">
        <span v-if="isTiered" class="tier-index">第 {{ (item.tierIndex ?? 0) + 1 }} 级</span>
        <strong>{{ item.materialName }}</strong>
      </div>
      <div class="material-counts">
        <span>当前 / 目标</span>
        <strong>{{ formatCount(item.currentOwned) }} / {{ formatCount(item.required) }}</strong>
        <small :class="{'is-complete': item.remaining <= 0}">
          {{ item.remaining > 0 ? `还需 ${formatCount(item.remaining)}` : '已满足' }}
        </small>
      </div>
    </div>
  </div>
</template>

<style scoped>
.material-progress {
  display: grid;
  gap: var(--el-spacing-small, 8px);
}

.family-caption,
.material-tier {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.family-caption {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.material-tier {
  padding: 10px 12px;
  background: var(--el-fill-color-lighter);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: var(--el-border-radius-base);
}

.material-identity,
.material-counts {
  display: grid;
  gap: 3px;
}

.material-identity {
  min-width: 0;
}

.material-identity strong {
  overflow-wrap: anywhere;
}

.tier-index,
.material-counts span,
.material-counts small {
  color: var(--el-text-color-secondary);
  font-size: 11px;
}

.material-counts {
  flex: 0 0 auto;
  justify-items: end;
  font-variant-numeric: tabular-nums;
}

.material-counts strong {
  color: var(--el-text-color-primary);
  font-size: 14px;
}

.material-counts small {
  color: var(--el-color-warning-dark-2);
  font-weight: 600;
}

.material-counts small.is-complete {
  color: var(--el-color-success-dark-2);
}

@media (max-width: 640px) {
  .material-tier {
    align-items: flex-start;
  }
}
</style>
