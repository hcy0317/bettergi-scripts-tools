<script setup>
import {VideoPlay} from '@element-plus/icons-vue'

const open = defineModel('open', {type: Boolean, default: false})
defineProps({
  launch: {type: Object, default: null},
  taskLabel: {type: String, default: '圣遗物任务'},
})
const emit = defineEmits(['launched'])
const launchNow = () => { open.value = false; emit('launched') }
</script>

<template>
  <el-dialog v-model="open" title="连接 BetterGI" width="min(420px, 92vw)" append-to-body>
    <div class="launch-task"><span>任务</span><strong>{{ taskLabel }}</strong></div>
    <template #footer>
      <el-button @click="open = false">取消</el-button>
      <el-button tag="a" :href="launch?.launchUri" type="primary" :icon="VideoPlay" @click="launchNow">连接 BetterGI</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.launch-task{display:flex;align-items:center;justify-content:space-between;padding:12px 0}.launch-task span{color:var(--el-text-color-secondary);font-size:13px}.launch-task strong{font-size:15px}
</style>
