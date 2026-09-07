<script setup>
import {ref} from 'vue'
import MemberCard from './OptimizerMemberCard.vue'
import {characterLabel,profileLabel,weaponLabel,statLabel,elementLabels,buildLabel} from '@/features/artifact-optimizer/localization.js'
const props=defineProps({build:{type:Object,required:true},characters:{type:Array,default:()=>[]},catalog:{type:Object,default:()=>({})},templates:{type:Array,default:()=>[]},selected:{type:Array,default:()=>[]}})
const cName=key=>profileLabel(props.catalog,props.characters.find(c=>c.key===key)||{key})
const emit=defineEmits(['remove','save-buff-template','members-change','select-member','use-team'])
const roundSeconds=ref(20),warmupRounds=ref(1),scoredRounds=ref(2)
function setRounds(){const start=warmupRounds.value*roundSeconds.value;props.build.duration=start+scoredRounds.value*roundSeconds.value;props.build.rounds=Array.from({length:scoredRounds.value},(_,i)=>({id:`round-${i+1}`,startFrame:(start+i*roundSeconds.value)*60,endFrame:(start+(i+1)*roundSeconds.value)*60}))}
function addConstraint(){props.build.constraints.push({id:crypto.randomUUID(),kind:'min_actions',character:props.build.members[0]?.character||'',action:'burst',threshold:1})}
function addBuff(){props.build.buffs.push({id:crypto.randomUUID(),kind:'stat',target:props.build.members[0]?.character||'',stat:'atk%',value:0.2,unit:'fraction',durationFrames:-1,anchor:'start',relationship:'pending_review',source:'手动补充'})}
function useTemplate(id){const template=props.templates.find(b=>b.id===id);if(template)props.build.buffs.push({...JSON.parse(JSON.stringify(template)),id:crypto.randomUUID(),enabled:true})}
function buffKindChanged(b){delete b.stat;delete b.element;delete b.attackTag;if(['resistance','defense_reduction'].includes(b.kind)){b.target='all_enemies';b.unit='fraction';if(b.kind==='resistance')b.element='pyro'}else{b.target=props.build.members[0]?.character||'';if(b.kind==='stat')b.stat='atk%';else b.attackTag='normal';b.unit='fraction'}}
function anchorChanged(b){delete b.sourceCharacter;delete b.action;if(b.anchor==='action'){b.sourceCharacter=props.build.members[0]?.character||'';b.action='skill'}}
</script>

<template>
  <section class="build-editor">
    <header><h2>配队方案</h2><el-button type="danger" plain @click="emit('remove')">删除方案</el-button></header>
    <el-form label-position="top" class="grid">
      <el-form-item label="名称" class="span2"><el-input v-model="build.name"/></el-form-item>
      <el-form-item label="唯一场景权重（均衡）"><el-input-number v-model="build.weight" :min="0" :max="1000" :step="0.5"/></el-form-item>
      <el-form-item label="模拟时长（秒）"><el-input-number v-model="build.duration" :min="1" :max="600"/></el-form-item>
      <el-form-item label="敌人等级"><el-input-number v-model="build.enemyLevel" :min="1" :max="200"/></el-form-item>
      <el-form-item label="基础抗性（0.1 = 10%）"><el-input-number v-model="build.resistance" :min="-1" :max="10" :step="0.1"/></el-form-item>
      <el-form-item label="敌人数"><el-input-number v-model="build.enemyCount" :min="1" :max="10"/></el-form-item>
    </el-form>
    <h3>队员与装备来源</h3><p class="hint">本次勾选计算的队员统一换成候选实物；其他队员须明确为固定实物或纯假设输入。固定队友的装备不会借出。</p>
    <div class="member-controls" id="optimizer-members"><el-select :model-value="build.members.map(m=>m.character)" multiple filterable :multiple-limit="4" placeholder="选择最多四名队员" aria-label="配队队员多选" @update:model-value="keys=>emit('members-change',keys)"><el-option v-for="c in characters" :key="c.key" :value="c.key" :label="profileLabel(catalog,c)"/></el-select><el-button :disabled="!build.members.length" @click="emit('use-team')">将本队加入配装</el-button></div>
    <p class="hint">本方案已有 {{ build.members.length }} 名队员。本次参算在各卡片中选择，取消参算的队友仍按指定装备计算。</p>
    <div class="team-grid"><MemberCard v-for="member in build.members" :key="member.character" :member="member" :profile="characters.find(c=>c.key===member.character)||{key:member.character}" :catalog="catalog" :selected="selected.includes(member.character)" @select="value=>emit('select-member',member.character,value)" @remove="emit('members-change',build.members.filter(m=>m.character!==member.character).map(m=>m.character))"/></div>
    <h3>一轮循环与脚本</h3><p class="hint">动作沿用 gcsim 语法，角色名支持中文（例如安柏、雷电将军）；编译时自动转为引擎标识，不改变原文。个人条件在上方编辑，此处不要重复声明 char、add stats 或敌人；模拟脚本不直接变成游戏输入。</p>
    <el-input v-model="build.rotation" type="textarea" :autosize="{minRows:8,maxRows:24}" spellcheck="false" aria-label="gcsim 循环脚本" placeholder="active 安柏;&#10;while 1 {&#10;  安柏 skill;&#10;  安柏 attack:3;&#10;}" class="script-input"/>
    <el-collapse class="details"><el-collapse-item title="持续循环验证与硬约束" name="rounds">
      <div class="round-controls"><label>每轮秒数 <el-input-number v-model="roundSeconds" :min="1" :max="120"/></label><label>暖机轮数 <el-input-number v-model="warmupRounds" :min="0" :max="3"/></label><label>计分轮数 <el-input-number v-model="scoredRounds" :min="1" :max="8"/></label><el-button @click="setRounds">生成计分窗口</el-button></div>
      <p class="hint">窗口由你声明，不自动推断动作循环。持续验证建议暖机后至少两轮；失败样本不会被删除或用平均次数掩盖。</p>
      <div v-for="(round,i) in build.rounds" :key="round.id" class="round-row"><span>{{ round.id }}</span><label>开始帧 <el-input-number v-model="round.startFrame" :min="0" :max="36000"/></label><label>结束帧 <el-input-number v-model="round.endFrame" :min="1" :max="36000"/></label><el-button text @click="build.rounds.splice(i,1)">删除</el-button></div>
      <el-button @click="addConstraint">添加每轮硬约束</el-button>
      <div v-for="(constraint,i) in build.constraints" :key="constraint.id" class="constraint-row"><el-select v-model="constraint.character" aria-label="约束角色"><el-option v-for="m in build.members" :key="m.character" :value="m.character" :label="cName(m.character)"/></el-select><el-select v-model="constraint.kind" aria-label="约束类型" @change="kind=>kind!=='min_actions'&&(delete constraint.action)"><el-option value="min_actions" label="最少成功动作次数"/><el-option value="max_failed_wait_frames" label="最多失败等待帧数"/><el-option value="min_effective_healing" label="最少有效治疗"/></el-select><el-select v-if="constraint.kind==='min_actions'" v-model="constraint.action" aria-label="约束动作"><el-option value="burst" label="元素爆发 Q"/><el-option value="skill" label="元素战技 E"/><el-option value="attack" label="普通攻击"/></el-select><el-input-number v-model="constraint.threshold" :min="0" aria-label="约束阈值"/><el-button text @click="build.constraints.splice(i,1)">删除</el-button></div>
    </el-collapse-item>
    <el-collapse-item title="通用增益补充与标记试算" name="buffs">
      <el-alert title="手动增益是明确假设，不是实机已触发的证据。无法安全替代原生效果时只允许额外或待复核，未知机制不会自动变成可用。" type="warning" :closable="false"/>
      <el-button class="add-buff" @click="addBuff">添加通用增益</el-button>
      <el-select model-value="" placeholder="复用已保存的增益模板" @change="useTemplate"><el-option v-for="b in templates" :key="b.id" :value="b.id" :label="`${b.source||b.kind} ${b.stat?statLabel(b.stat):b.element?elementLabels[b.element]:({normal:'普通攻击',skill:'元素战技',burst:'元素爆发'}[b.attackTag]||'')} ${b.value}`"/></el-select>
      <el-form v-for="(buff,i) in build.buffs" :key="buff.id" label-position="top" class="buff-form">
        <el-form-item label="此方案中启用"><el-switch :model-value="buff.enabled!==false" @update:model-value="value=>buff.enabled=value"/></el-form-item><el-form-item label="保存为可复用模板"><el-button @click="emit('save-buff-template',{...JSON.parse(JSON.stringify(buff)),id:crypto.randomUUID()})">保存模板</el-button></el-form-item>
        <el-form-item label="类型"><el-select v-model="buff.kind" @change="buffKindChanged(buff)"><el-option label="属性增益" value="stat"/><el-option label="敌人减抗" value="resistance"/><el-option label="敌人减防" value="defense_reduction"/><el-option label="限定攻击增伤" value="attack_bonus"/></el-select></el-form-item>
        <el-form-item label="作用对象"><el-select v-model="buff.target"><el-option v-if="['resistance','defense_reduction'].includes(buff.kind)" label="全部敌人" value="all_enemies"/><template v-else><el-option v-for="m in build.members" :key="m.character" :label="cName(m.character)" :value="m.character"/></template></el-select></el-form-item>
        <el-form-item v-if="buff.kind==='stat'" label="属性"><el-select v-model="buff.stat"><el-option v-for="s in ['hp','hp%','atk','atk%','def','def%','em','er','cr','cd','heal','pyro%','hydro%','cryo%','electro%','anemo%','geo%','dendro%','phys%']" :key="s" :value="s" :label="statLabel(s)"/></el-select></el-form-item>
        <el-form-item v-if="buff.kind==='resistance'" label="元素"><el-select v-model="buff.element"><el-option v-for="s in ['pyro','hydro','cryo','electro','anemo','geo','dendro','physical']" :key="s" :value="s" :label="elementLabels[s]"/></el-select></el-form-item>
        <el-form-item v-if="buff.kind==='attack_bonus'" label="攻击范围"><el-select v-model="buff.attackTag"><el-option value="normal" label="普攻"/><el-option value="skill" label="战技"/><el-option value="burst" label="爆发"/></el-select></el-form-item>
        <el-form-item label="数值（减抗用负数）"><el-input-number v-model="buff.value" :step="0.1"/></el-form-item><el-form-item label="单位"><el-select v-model="buff.unit"><el-option value="fraction" label="比例，0.2 = 20%"/><el-option value="flat" label="平值"/></el-select></el-form-item>
        <el-form-item label="触发时间锚"><el-select v-model="buff.anchor" @change="anchorChanged(buff)"><el-option value="start" label="开局"/><el-option value="round" label="声明的每轮边界"/><el-option value="action" label="动作成功时"/></el-select></el-form-item>
        <el-form-item v-if="buff.anchor==='action'" label="触发角色"><el-select v-model="buff.sourceCharacter"><el-option v-for="m in build.members" :key="m.character" :value="m.character" :label="cName(m.character)"/></el-select></el-form-item><el-form-item v-if="buff.anchor==='action'" label="触发动作"><el-select v-model="buff.action"><el-option value="skill" label="战技"/><el-option value="burst" label="爆发"/><el-option value="attack" label="普攻"/></el-select></el-form-item>
        <el-form-item label="持续帧数（60 帧 = 1 秒，-1 常驻）"><el-input-number v-model="buff.durationFrames" :min="-1" :max="36000"/></el-form-item><el-form-item label="与原生效果关系"><el-select v-model="buff.relationship" @change="()=>buff.reviewedEngineRevision=catalog.engineRevision"><el-option value="pending_review" label="可能重叠，待复核"/><el-option value="additional" label="确认是额外效果"/></el-select></el-form-item><el-form-item label="来源说明"><el-input v-model="buff.source"/></el-form-item><el-button text type="danger" @click="build.buffs.splice(i,1)">删除此增益</el-button>
      </el-form>
    </el-collapse-item></el-collapse>
    <el-checkbox v-model="build.allowPartial">明确允许上游标记不完整角色参与试算（结果会保留标记）</el-checkbox>
  </section>
</template>

<style scoped>
.member-controls{display:flex;gap:12px;align-items:center}.member-controls>.el-select{flex:1;min-width:0}.team-grid{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:16px;margin:18px 0 28px;align-items:start}@media(max-width:1050px){.team-grid{grid-template-columns:repeat(2,minmax(0,1fr))}}@media(max-width:600px){.team-grid{grid-template-columns:1fr}.member-controls{align-items:stretch;flex-direction:column}}

header{display:flex;justify-content:space-between;align-items:center}h2{font-size:21px;margin:0 0 16px}h3{font-size:16px;margin-top:24px}.hint{color:var(--el-text-color-secondary);font-size:13px;line-height:1.7}.grid{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:0 14px}.span2{grid-column:span 2}.el-select{width:100%}.el-input-number{max-width:100%}.member{padding:12px 0;border-bottom:1px solid var(--el-border-color-lighter)}.member-row{display:grid;grid-template-columns:1fr 2fr auto;gap:12px;align-items:center;margin-bottom:10px}.override-grid{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:12px;margin-top:12px}.details{margin:20px 0}.script-input :deep(textarea){font-family:Consolas,monospace;line-height:1.7}.round-controls,.round-row{display:flex;gap:12px;align-items:end;flex-wrap:wrap;margin-bottom:12px}.round-controls label,.round-row label{display:grid;gap:6px}.constraint-row{display:flex;gap:8px;margin-top:12px;align-items:center}.buff-form{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:0 14px;border-bottom:1px solid var(--el-border-color-lighter);padding-top:14px}.add-buff{margin-top:12px}@media(max-width:900px){.grid{grid-template-columns:repeat(2,minmax(0,1fr))}.buff-form,.override-grid{grid-template-columns:repeat(2,minmax(0,1fr))}.constraint-row{flex-wrap:wrap}}@media(max-width:640px){.grid,.member-row,.buff-form,.override-grid{grid-template-columns:1fr}.span2{grid-column:auto}}
</style>
