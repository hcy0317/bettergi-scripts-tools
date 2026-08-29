<script setup>
import {ref} from 'vue'
import {ArrowLeft, HomeFilled} from '@element-plus/icons-vue'
import router from '@router/router.js'
import UidSelector from '@/components/UidSelector.vue'
import ArtifactJobsPanel from '@/components/artifact/ArtifactJobsPanel.vue'
import ArtifactBuildsPanel from '@/components/artifact/ArtifactBuildsPanel.vue'
import ArtifactLockPlanPanel from '@/components/artifact/ArtifactLockPlanPanel.vue'
import ArtifactNativeSyncPanel from '@/components/artifact/ArtifactNativeSyncPanel.vue'
import ArtifactSettingsPanel from '@/components/artifact/ArtifactSettingsPanel.vue'

const uid = ref('')
const activeTab = ref('analysis')
</script>

<template>
  <main class="artifact-page">
    <section class="artifact-shell">
      <header class="page-header">
        <div class="header-actions">
          <el-tooltip content="返回"><el-button circle :icon="ArrowLeft" @click="router.back()"/></el-tooltip>
          <el-tooltip content="首页"><el-button circle :icon="HomeFilled" @click="router.push('/')"/></el-tooltip>
        </div>
        <div class="page-title">
          <h1>圣遗物分析</h1>
          <p>扫描、评分、配装与锁定方案</p>
        </div>
      </header>

      <section class="identity-band" aria-label="分析账号">
        <UidSelector v-model="uid" class="uid-selector"/>
      </section>

      <el-tabs v-model="activeTab" class="artifact-tabs">
        <el-tab-pane label="分析记录" name="analysis" lazy><ArtifactJobsPanel v-if="activeTab === 'analysis'" :uid="uid"/></el-tab-pane>
        <el-tab-pane label="配装管理" name="builds" lazy><ArtifactBuildsPanel v-if="activeTab === 'builds'" :uid="uid"/></el-tab-pane>
        <el-tab-pane label="锁定方案" name="lock" lazy><ArtifactLockPlanPanel v-if="activeTab === 'lock'" :uid="uid"/></el-tab-pane>
        <el-tab-pane label="原神方案同步" name="native" lazy><ArtifactNativeSyncPanel v-if="activeTab === 'native'" :uid="uid"/></el-tab-pane>
        <el-tab-pane label="算法设置" name="settings" lazy><ArtifactSettingsPanel v-if="activeTab === 'settings'" :uid="uid"/></el-tab-pane>
      </el-tabs>
    </section>
  </main>
</template>

<style scoped>
.artifact-page {
  min-height: 100dvh;
  padding: clamp(14px, 2.5vw, 30px);
  box-sizing: border-box;
  color: var(--el-text-color-primary);
  background: #eef1f4;
}

.artifact-shell {
  width: min(1480px, 100%);
  min-height: calc(100dvh - 60px);
  margin: 0 auto;
  padding: clamp(16px, 2.5vw, 30px);
  box-sizing: border-box;
  background: #fff;
  border: 1px solid var(--el-border-color-light);
  box-shadow: 0 12px 32px rgba(31, 41, 55, 0.08);
}

.page-header,
.identity-band {
  display: flex;
  align-items: center;
}

.page-header {
  justify-content: space-between;
  gap: 20px;
  padding-bottom: 16px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.header-actions { display: flex; gap: 8px; }
.page-title { text-align: right; }
.page-title h1 { margin: 0; font-size: 26px; letter-spacing: 0; }
.page-title p { margin: 5px 0 0; color: var(--el-text-color-secondary); font-size: 13px; }
.identity-band { padding: 16px 0; }
.uid-selector { width: min(360px, 100%); }
.artifact-tabs { margin-top: 2px; }

@media (max-width: 720px) {
  .artifact-page { padding: 0; }
  .artifact-shell { min-height: 100dvh; padding: 14px; border: 0; }
  .page-title h1 { font-size: 21px; }
  .identity-band, .uid-selector { width: 100%; }
  .artifact-tabs :deep(.el-tabs__nav-wrap) { overflow-x: auto; }
}
</style>
