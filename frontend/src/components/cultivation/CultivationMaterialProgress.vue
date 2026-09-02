<script setup>
import {computed} from 'vue'

const props = defineProps({
  items: {type: Array, default: () => []}
})

const orderedItems = computed(() => [...props.items].sort((left, right) =>
  (left.tierIndex ?? 0) - (right.tierIndex ?? 0)))
const isTiered = computed(() => orderedItems.value.length > 1)
const isValueWeighted = computed(() => orderedItems.value.some(item => Number(item.valuePerItem || 0) > 0))
const formatCount = value => Number(value || 0).toLocaleString()
const weightedSummary = computed(() => {
  if (!isValueWeighted.value) return null
  const ownedValue = orderedItems.value.reduce((total, item) =>
    total + Number(item.currentOwned || 0) * Number(item.valuePerItem || 0), 0)
  const requiredValue = orderedItems.value.reduce((maximum, item) =>
    Math.max(maximum, Number(item.required || 0) * Number(item.valuePerItem || 0)), 0)
  const topTierValue = Math.max(...orderedItems.value.map(item => Number(item.valuePerItem || 0)))
  return {
    ownedValue,
    requiredValue,
    remainingTopTier: topTierValue > 0
      ? Math.ceil(Math.max(requiredValue - ownedValue, 0) / topTierValue)
      : 0,
  }
})
const ownedGap = item => Math.max(
  Number(item.required || 0) - Number(item.currentOwned || 0),
  0,
)
const isOwnedComplete = item => ownedGap(item) <= 0
const gapLabel = item => {
  const gap = ownedGap(item)
  if (gap <= 0) return '已满足'
  return item.remaining <= 0
    ? `还需 ${formatCount(gap)}（待合成）`
    : `还需 ${formatCount(gap)}`
}
</script>

<template>
  <div class="material-progress" :class="{'is-tiered': isTiered}">
    <div v-if="isTiered" class="family-caption">
      <div>
        <span>{{ isValueWeighted ? '经验书分级' : '多级材料' }}</span>
        <small v-if="weightedSummary">
          总经验 {{ formatCount(weightedSummary.ownedValue) }} / {{ formatCount(weightedSummary.requiredValue) }}
          · 折合还需 {{ formatCount(weightedSummary.remainingTopTier) }} 本大英雄的经验
        </small>
      </div>
      <el-tag size="small" effect="plain">{{ orderedItems.length }} 级</el-tag>
    </div>
    <div v-for="item in orderedItems" :key="item.materialName" class="material-tier">
      <div class="material-identity">
        <span v-if="isTiered" class="tier-index">第 {{ (item.tierIndex ?? 0) + 1 }} 级</span>
        <strong>{{ item.materialName }}</strong>
      </div>
      <div class="material-counts">
        <span>{{ isValueWeighted ? '当前数量' : '当前 / 目标' }}</span>
        <strong>
          {{ formatCount(item.currentOwned) }}<template v-if="!isValueWeighted"> / {{ formatCount(item.required) }}</template>
        </strong>
        <small v-if="isValueWeighted">{{ formatCount(item.valuePerItem) }} 经验/本</small>
        <small v-else :class="{'is-complete': isOwnedComplete(item), 'is-crafting': !isOwnedComplete(item) && item.remaining <= 0}">
          {{ gapLabel(item) }}
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

.family-caption > div {
  display: grid;
  gap: 2px;
}

.family-caption small {
  color: var(--el-text-color-secondary);
  font-size: 11px;
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

.material-counts small.is-crafting {
  color: var(--el-color-primary);
}

@media (max-width: 640px) {
  .material-tier {
    align-items: flex-start;
  }
}
</style>
