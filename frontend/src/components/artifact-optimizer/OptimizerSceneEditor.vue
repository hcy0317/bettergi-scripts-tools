<script setup>
const props=defineProps({build:{type:Object,required:true},loopChoices:{type:Array,default:()=>[]},outlineError:{type:String,default:''},maxTrajectorySeconds:{type:Number,default:0}})
function addTarget(){props.build.targets.push({level:100,resistance:0.1,radius:1,x:0,y:0,hp:null})}
function automaticRounds(){props.build.legacyRounds=JSON.parse(JSON.stringify(props.build.rounds||[]));props.build.rounds=[];props.build.roundPolicy={mode:'auto',warmup:0,loopIndex:0}}
</script>
<template>
  <section class="scene-editor" id="optimizer-scene">
    <h3>单次模拟与敌人</h3>
    <p v-if="maxTrajectorySeconds" class="hint">完成指定循环次数后结束。{{ maxTrajectorySeconds }} 游戏秒仅是异常保护上限；等待或分支卡住时报告已完成轮数，不会把截断伤害当作合格结果。</p>
    <el-form label-position="top" class="scene-grid">
      <el-form-item label="每场模拟的循环次数"><el-input-number v-model="build.roundCount" :min="1" :max="64" :precision="0"/></el-form-item>
      <el-form-item label="切换角色额外延迟（帧）"><el-input-number v-model="build.swapDelay" :min="0" :max="120"/></el-form-item>
    </el-form>
    <p class="hint">按主流程实际完成的总轮数结束，每轮充能、等待和分支耗时照实计算。敌人为不因血量提前结束的试算靶子；原血量和时长保留作参考。底部的采样次数是不同随机种子的重复试验，不是循环次数。</p>
    <div v-for="(target,i) in build.targets" :key="i" class="target-row" :id="`optimizer-targets.${i}`">
      <strong>敌人 {{ i+1 }}</strong><el-form label-position="top" class="target-grid">
        <el-form-item label="等级"><el-input-number v-model="target.level" :min="1" :max="200"/></el-form-item>
        <el-form-item label="基础抗性"><el-input-number v-model="target.resistance" :min="-1" :max="10" :step="0.1"/></el-form-item>
        <el-form-item label="半径"><el-input-number v-model="target.radius" :min="0.01" :max="100" :step="0.1"/></el-form-item>
        <el-form-item label="横向位置"><el-input-number v-model="target.x" :min="-1000" :max="1000" :step="0.1"/></el-form-item>
        <el-form-item label="纵向位置"><el-input-number v-model="target.y" :min="-1000" :max="1000" :step="0.1"/></el-form-item>
      </el-form><el-button text type="danger" :disabled="build.targets.length===1" @click="build.targets.splice(i,1)">移除敌人</el-button>
    </div>
    <el-button :disabled="build.targets.length>=10" @click="addTarget">增加敌人</el-button>
    <section id="optimizer-energy"><h3>外部掉球</h3><el-switch v-model="build.energy.enabled" active-text="启用无元素微粒掉落"/>
      <el-form v-if="build.energy.enabled" label-position="top" class="scene-grid">
        <el-form-item label="掉落方式"><el-select v-model="build.energy.mode"><el-option value="once" label="指定时间掉落一次"/><el-option value="every" label="按随机间隔持续掉落"/></el-select></el-form-item>
        <el-form-item :label="build.energy.mode==='once'?'掉落时间（帧）':'最小间隔（帧）'"><el-input-number v-model="build.energy.start" :min="1" :max="36000"/></el-form-item>
        <el-form-item v-if="build.energy.mode==='every'" label="最大间隔（帧，必须更大）"><el-input-number v-model="build.energy.end" :min="1" :max="36000"/></el-form-item>
        <el-form-item label="每次无元素微粒数量"><el-input-number v-model="build.energy.amount" :min="1" :max="100"/></el-form-item>
      </el-form><p v-if="build.energy.enabled" class="hint">{{ build.energy.start/60 }}{{ build.energy.mode==='every'?` 至 ${build.energy.end/60}`:'' }} 秒，{{ build.energy.amount }} 颗无元素微粒。沿用 gcsim 的掉球分配和充能效率计算；这是外部供能假设，不替代角色技能自身产球。</p>
    </section>
    <section id="optimizer-rounds"><h3>自动统计循环</h3><p class="hint">按所选主循环实际执行的起止帧统计，辅助嵌套循环不额外计数。达到上方设定轮数后结束；整体每秒伤害仍按完整战斗轨迹计算，不用固定秒数替代。</p>
      <el-alert v-if="build.nativeRotation?.enabled" title="原生流程按主轴每次真实执行的边界自动计时；旧手填窗口或gcsim循环位置不参与此模式。" type="info" :closable="false"/>
      <el-alert v-else-if="build.roundPolicy.mode==='legacy'" title="旧方案保存了手工时间窗。切换后改为自动测时，原窗口会留在兼容备份中。" type="info" :closable="false"/>
      <el-button v-if="!build.nativeRotation?.enabled&&build.roundPolicy.mode==='legacy'" @click="automaticRounds">改用脚本自动测时</el-button>
      <el-alert v-if="outlineError" :title="outlineError" type="warning" :closable="false"/>
      <el-form v-else-if="build.roundPolicy.mode==='auto'&&loopChoices.length>1" label-position="top" class="scene-grid"><el-form-item label="脚本有多个主循环，请选择统计范围"><el-select v-model="build.roundPolicy.loopIndex"><el-option :value="0" label="尚未指定，计算时将报告歧义"/><el-option v-for="choice in loopChoices" :key="choice.index" :value="choice.index" :label="`第 ${choice.index} 个主循环：脚本第 ${choice.line} 行`"/></el-select></el-form-item></el-form>
      <p v-else-if="!build.nativeRotation?.enabled&&build.roundPolicy.mode==='auto'" class="hint">{{ loopChoices.length===1?`已识别脚本第 ${loopChoices[0].line} 行的主循环，实际秒数由运行结果自动记录。`:'未发现顶层主循环时，将完整线性脚本作为一轮重复执行。' }}</p>
      <el-collapse><el-collapse-item title="逐轮指标高级设置" name="advanced-rounds"><el-form label-position="top"><el-form-item label="逐轮指标忽略开场轮数"><el-input-number v-model="build.roundPolicy.warmup" :min="0" :max="63"/></el-form-item></el-form><p class="hint">只影响逐轮指标，不改变整场每秒伤害。没有完整计分轮次时会明确标为未知。</p></el-collapse-item></el-collapse>
    </section>
  </section>
</template>
<style scoped>
h3{font-size:16px;margin-top:24px}.scene-grid{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:12px;margin-top:14px}.target-grid{display:grid;grid-template-columns:repeat(6,minmax(0,1fr));gap:12px;margin-top:12px}.target-row{margin:16px 0;padding-bottom:10px;border-bottom:1px solid var(--el-border-color-light)}.hint{font-size:13px;line-height:1.7;color:var(--el-text-color-secondary)}.el-input-number,.el-select{width:100%}@media(max-width:1050px){.target-grid{grid-template-columns:repeat(3,minmax(0,1fr))}}@media(max-width:650px){.scene-grid{grid-template-columns:1fr}.target-grid{grid-template-columns:repeat(2,minmax(0,1fr))}}
</style>
