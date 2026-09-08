<script setup>
import {computed} from 'vue'
import {profileLabel,weaponLabel,setLabel,buildLabel} from '@/features/artifact-optimizer/localization.js'
import {slotOptions,statOptions,levelCapOptions,ascensionDescription} from '@/features/artifact-optimizer/model.js'
const props=defineProps({character:{type:Object,required:true},catalog:{type:Object,default:()=>({})},builds:{type:Array,default:()=>[]},items:{type:Array,default:()=>[]}})
const emit=defineEmits(['edit-build','remove'])
const metadata=computed(()=>props.catalog.characters?.find(c=>c.key===props.character.key))
const weapons=computed(()=>props.catalog.weapons?.filter(w=>!metadata.value||w.weapon_class===metadata.value.weapon_class)||[])
function setBuilds(ids){props.character.builds=ids.map(id=>props.character.builds.find(b=>b.id===id)||{id,weight:1,metric:'damage_per_round',reference:0})}
const minKeys=computed(()=>Object.keys(props.character.minimumStats||{}))
function addMinimum(key){if(key)props.character.minimumStats[key]=0}
</script>

<template>
  <section class="character-editor">
    <header class="editor-heading"><div><h2>{{ profileLabel(catalog,character) }}</h2><p>个人条件对所有配队方案生效，可在方案中单独覆盖</p></div><el-button type="danger" plain @click="emit('remove')">移除档案</el-button></header>
    <el-form label-position="top" class="personal-grid">
      <el-form-item label="显示名称"><el-input v-model="character.name"/></el-form-item>
      <el-form-item label="游戏中装备显示名（改名角色选填）"><el-input v-model="character.inventoryName" placeholder="与扫描中的穿戴者名字一致"/></el-form-item>
      <el-form-item data-field="level" label="角色等级"><el-input-number v-model="character.level" :min="1" :max="100"/></el-form-item>
      <el-form-item data-field="maxLevel" label="突破后等级上限"><el-select v-model="character.maxLevel"><el-option v-for="n in levelCapOptions" :key="n" :value="n" :label="String(n)"/></el-select><small>{{ ascensionDescription(catalog,character.key,character.maxLevel) }}</small></el-form-item>
      <el-form-item data-field="constellation" label="命座"><el-input-number v-model="character.constellation" :min="0" :max="6"/></el-form-item>
      <el-form-item data-field="weapon" label="武器" class="wide"><el-select v-model="character.weapon" filterable placeholder="选择 gcsim 武器"><el-option v-for="w in weapons" :key="w.key" :value="w.key" :label="weaponLabel(catalog,w.key)"/></el-select></el-form-item>
      <el-form-item data-field="weaponLevel" label="武器等级"><el-input-number v-model="character.weaponLevel" :min="1" :max="90"/></el-form-item>
      <el-form-item data-field="weaponMaxLevel" label="武器等级上限"><el-select v-model="character.weaponMaxLevel"><el-option v-for="n in levelCapOptions" :key="n" :value="n" :label="String(n)"/></el-select><small>{{ ascensionDescription(catalog,character.weapon,character.weaponMaxLevel,true) }}</small></el-form-item>
      <el-form-item data-field="refinement" label="精炼"><el-input-number v-model="character.refinement" :min="1" :max="5"/></el-form-item>
      <el-form-item v-for="(name,index) in ['普通攻击基础等级','元素战技基础等级','元素爆发基础等级']" :key="name" :label="name"><el-input-number v-model="character.talents[index]" :min="1" :max="10"/></el-form-item>
      <el-form-item label="标签" class="wide"><el-select v-model="character.tags" multiple filterable :reserve-keyword="false" allow-create default-first-option placeholder="输入标签后按回车"/></el-form-item>
      <el-form-item label="角色目标权重（保尖 / 兜底）"><el-input-number v-model="character.weight" :min="0" :max="1000" :step="0.5"/></el-form-item>
      <el-form-item label="保护当前五件装备"><el-switch v-model="character.protected" active-text="不出借、不替换"/></el-form-item>
    </el-form>
    <section class="build-bindings" id="optimizer-builds"><h3>兼顾的配队方案</h3><p class="hint">选中多个方案后仍只求出一套通用装备。均衡模式的场景权重在方案中设置，不随角色引用次数叠加。</p>
      <el-select :model-value="character.builds.map(b=>b.id)" multiple filterable :reserve-keyword="false" placeholder="选择一个或多个配队方案" @update:model-value="setBuilds"><el-option v-for="b in builds" :key="b.id" :value="b.id" :label="buildLabel(b)"/></el-select>
      <div v-for="binding in character.builds" :key="binding.id" class="target-row">
        <el-button link type="primary" @click="emit('edit-build',binding.id)">{{ builds.find(b=>b.id===binding.id)?.name || binding.id }}</el-button>
        <label>目标 <el-select v-model="binding.metric"><el-option label="每轮个人伤害" value="damage_per_round"/><el-option label="每轮有效治疗" value="effective_healing_per_round"/></el-select></label>
        <label>方案权重 <el-input-number v-model="binding.weight" :min="0" :max="1000"/></label>
        <label>目标参照 <el-input-number v-model="binding.reference" :min="0" :max="100000000"/></label>
      </div><p class="hint">参照为 0 时从本次预算内合格样本生成并冻结，不是理论最优上界。</p>
    </section>
    <el-collapse><el-collapse-item title="高级硬约束" name="constraints">
      <p class="hint">这些限制在所有档位都生效。初始属性是帧零面板，不代表战斗内增益覆盖率；持续循环请在方案中约束。</p>
      <div class="constraint-grid"><label v-for="[slot,label] in slotOptions" :key="slot">{{ label }}主词条
        <el-select v-model="character.mainStats[slot]" multiple clearable placeholder="不限"><el-option v-for="[key,text] in statOptions" :key="key" :label="text" :value="key"/></el-select>
      </label></div>
      <div v-for="[slot,label] in slotOptions" :key="slot" class="fixed-row"><span>固定{{ label }}</span><el-select v-model="character.fixedSlots[slot]" clearable @clear="delete character.fixedSlots[slot]" placeholder="不固定"><el-option v-for="item in items.filter(i=>i.slotKey===slot)" :key="item.scanIndex" :value="item.scanIndex" :label="`#${item.scanIndex} ${setLabel(catalog,item.setKey)} +${item.level} ${statOptions.find(s=>s[0]===item.mainStatKey)?.[1]||'未知属性'}`"/></el-select></div>
      <div class="fixed-row"><span>必需套装</span><el-select multiple :reserve-keyword="false" :model-value="Object.keys(character.requiredSets)" @update:model-value="keys=>character.requiredSets=Object.fromEntries(keys.map(k=>[k,character.requiredSets[k]||2]))" filterable><el-option v-for="s in catalog.sets||[]" :key="s.key" :value="s.key" :label="setLabel(catalog,s.key)"/></el-select></div>
      <label v-for="key in Object.keys(character.requiredSets)" :key="key" class="fixed-row">{{ setLabel(catalog,key) }}件数 <el-input-number v-model="character.requiredSets[key]" :min="1" :max="5"/></label>
      <el-select model-value="" placeholder="添加属性下限" @change="addMinimum"><el-option v-for="[key,label] in statOptions" :key="key" :value="key" :label="label"/></el-select>
      <div v-for="key in minKeys" :key="key" class="fixed-row"><span>{{ statOptions.find(s=>s[0]===key)?.[1]||key }}</span><el-input-number v-model="character.minimumStats[key]" :min="0"/><el-button text @click="delete character.minimumStats[key]">删除</el-button></div>
    </el-collapse-item></el-collapse>
  </section>
</template>

<style scoped>
.editor-heading{display:flex;justify-content:space-between;gap:16px;align-items:start}.editor-heading h2{margin:0;font-size:21px}.editor-heading p,.hint{font-size:13px;color:var(--el-text-color-secondary);line-height:1.6}.personal-grid{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:0 16px}.wide{grid-column:span 2}.el-select{width:100%}.el-input-number{max-width:100%}.target-row{display:grid;grid-template-columns:1.2fr 1.4fr 1fr 1fr;gap:12px;margin-top:12px;align-items:center}.target-row label{font-size:12px;display:grid;gap:6px}.constraint-grid{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:12px}.fixed-row{display:flex;align-items:center;gap:12px;margin:12px 0}.fixed-row>.el-select{max-width:460px}.build-bindings{margin:14px 0 22px}.build-bindings h3{font-size:16px}.character-editor{min-width:0}@media(max-width:1000px){.personal-grid{grid-template-columns:repeat(2,minmax(0,1fr))}.target-row{grid-template-columns:repeat(2,minmax(0,1fr))}}@media(max-width:640px){.personal-grid,.constraint-grid{grid-template-columns:1fr}.wide{grid-column:auto}.target-row{grid-template-columns:1fr}.editor-heading{flex-direction:column}.fixed-row{flex-wrap:wrap}}
</style>
