<script setup>
import {computed} from 'vue'
import {artifactCharacterAvatarUrl, artifactCharacterLabel} from '@/features/artifact-analysis/buildModel.js'
import {
  artifactDecisionScores,
  DEFAULT_ARTIFACT_ANALYSIS_THRESHOLDS,
} from '@/features/artifact-analysis/lockPlanModel.js'

const props = defineProps({
  row: {type: Object, required: true},
  builds: {type: Array, default: () => []},
  settings: {type: Object, default: () => ({...DEFAULT_ARTIFACT_ANALYSIS_THRESHOLDS})},
})
const buildMap = computed(() => new Map(props.builds.map(build => [build.id, build])))
const scores = computed(() => artifactDecisionScores(props.row, props.settings))
const visible = computed(() => scores.value.slice(0, 6))
const hidden = computed(() => Math.max(0, scores.value.length - visible.value.length))
const build = id => buildMap.value.get(id)
const character = id => artifactCharacterLabel(build(id)?.characterKey)
const avatar = id => artifactCharacterAvatarUrl(build(id)?.characterKey)
const name = id => build(id) ? `${character(id)} · ${build(id).name}` : '未知配装'
const primaryScore = score => props.row.artifact?.level >= 20 ? score.currentScore : score.potentialScore
const scoreDetail = score => `${name(score.buildId)}｜当前 ${score.currentScore}｜潜力 ${score.potentialScore}`
</script>

<template>
  <div v-if="scores.length" class="score-rail" :aria-label="`适配 ${scores.length} 个配装`">
    <el-tooltip v-for="score in visible" :key="score.buildId" :content="scoreDetail(score)" placement="top">
      <div class="score-avatar" :aria-label="scoreDetail(score)">
        <el-avatar :size="34" :src="avatar(score.buildId)">{{ character(score.buildId).slice(0, 1) }}</el-avatar>
        <strong>{{ primaryScore(score) }}</strong>
      </div>
    </el-tooltip>
    <el-popover v-if="hidden" trigger="click" placement="left" :width="360">
      <template #reference><el-button circle size="small" :aria-label="`查看另外 ${hidden} 个配装分数`">+{{ hidden }}</el-button></template>
      <div class="score-list">
        <div v-for="score in scores" :key="score.buildId" class="score-list-row">
          <el-avatar :size="30" :src="avatar(score.buildId)">{{ character(score.buildId).slice(0, 1) }}</el-avatar>
          <span><strong>{{ name(score.buildId) }}</strong><small>当前 {{ score.currentScore }} · 潜力 {{ score.potentialScore }}</small></span>
        </div>
      </div>
    </el-popover>
  </div>
  <span v-else class="empty-score">没有达到当前阈值的 Build</span>
</template>

<style scoped>
.score-rail { display: flex; align-items: center; gap: 6px; min-width: 0; overflow: hidden; }
.score-avatar { position: relative; flex: 0 0 auto; width: 42px; height: 42px; }
.score-avatar strong { position: absolute; right: -1px; bottom: -1px; min-width: 20px; padding: 1px 3px; border-radius: 8px; color: #fff; background: var(--el-color-primary); font-size: 11px; line-height: 16px; text-align: center; box-shadow: 0 1px 3px rgba(0,0,0,.2); }
.score-list { display: grid; gap: 8px; max-height: 360px; overflow-y: auto; }
.score-list-row { display: grid; grid-template-columns: auto minmax(0, 1fr); align-items: center; gap: 8px; }
.score-list-row span { display: grid; min-width: 0; }
.score-list-row strong { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.score-list-row small, .empty-score { color: var(--el-text-color-secondary); font-size: 12px; }
</style>
