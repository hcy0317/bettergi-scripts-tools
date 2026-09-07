<script setup>
import {computed,ref,watch} from 'vue'
import {levelCapOptions,ascensionDescription} from '@/features/artifact-optimizer/model.js'
import {profileLabel,weaponLabel} from '@/features/artifact-optimizer/localization.js'
const props=defineProps({member:{type:Object,required:true},profile:{type:Object,required:true},catalog:{type:Object,required:true},selected:{type:Boolean,default:false}})
const emit=defineEmits(['select','remove'])
const metadata=computed(()=>props.catalog.characters?.find(c=>c.key===props.member.character))
const portrait=computed(()=>/^[A-Za-z0-9_]+$/.test(metadata.value?.icon_name||'')?`https://enka.network/ui/${metadata.value.icon_name}.png`:'')
const brokenImage=ref(false)
watch(portrait,()=>brokenImage.value=false)
const effective=computed(()=>props.member.profile||props.profile)
const weapons=computed(()=>props.catalog.weapons?.filter(w=>w.weapon_class===metadata.value?.weapon_class)||[])
function override(enabled){
  if(!enabled){delete props.member.profile;return}
  props.member.profile=Object.fromEntries(['level','maxLevel','constellation','weapon','weaponLevel','weaponMaxLevel','refinement','talents'].map(key=>[key,JSON.parse(JSON.stringify(props.profile[key]??null))]))
  if(!Array.isArray(props.member.profile.talents))props.member.profile.talents=[null,null,null]
}
</script>
<template>
  <article :data-optimizer-member="member.character" :class="['member-card',{participating:selected}]" :aria-label="`${profileLabel(catalog,profile)}队员卡片`">
    <div class="portrait"><img v-if="portrait&&!brokenImage" :src="portrait" :alt="profileLabel(catalog,profile)" width="144" height="144" loading="lazy" @error="brokenImage=true"/><span v-else>{{ profileLabel(catalog,profile).slice(0,1) }}</span></div>
    <h4>{{ profileLabel(catalog,profile) }}</h4>
    <p class="personal-summary">等级 {{ effective.level??'未填' }} · {{ effective.constellation??'未填' }} 命</p>
    <p class="weapon-summary">{{ effective.weapon?weaponLabel(catalog,effective.weapon):'尚未填写武器' }}</p>
    <el-checkbox :model-value="selected" @change="value=>emit('select',value)">参与本次配装</el-checkbox>
    <details class="member-details"><summary>个人条件与装备来源</summary><el-form v-if="!selected" label-position="top">
      <el-form-item label="未参算时的装备来源"><el-select v-model="member.kind" :disabled="selected"><el-option label="固定当前实物装备" value="real_fixed"/><el-option label="明确的假设属性" value="hypothetical"/></el-select></el-form-item>
      <el-form-item v-if="!selected&&member.kind==='hypothetical'" label="假设圣遗物属性"><el-input v-model="member.stats" placeholder="例如 atk%=0.466 cr=0.311"/></el-form-item>
    </el-form>
    <el-checkbox :model-value="Boolean(member.profile)" @change="override">本方案覆盖个人条件</el-checkbox>
    <p v-if="!member.profile" class="inheritance">继承角色档案中的等级、武器与天赋</p>
    <el-form v-else label-position="top" class="override-fields">
      <el-form-item data-field="level" label="角色等级"><el-input-number v-model="member.profile.level" :min="1" :max="100"/></el-form-item>
      <el-form-item data-field="maxLevel" label="突破等级上限"><el-select v-model="member.profile.maxLevel"><el-option v-for="n in levelCapOptions" :key="n" :value="n" :label="String(n)"/></el-select></el-form-item>
      <el-form-item data-field="constellation" label="命座"><el-input-number v-model="member.profile.constellation" :min="0" :max="6"/></el-form-item>
      <el-form-item data-field="weapon" label="武器"><el-select v-model="member.profile.weapon" filterable><el-option v-for="w in weapons" :key="w.key" :value="w.key" :label="weaponLabel(catalog,w.key)"/></el-select></el-form-item>
      <el-form-item data-field="weaponLevel" label="武器等级"><el-input-number v-model="member.profile.weaponLevel" :min="1" :max="90"/></el-form-item>
      <el-form-item data-field="weaponMaxLevel" label="武器突破上限"><el-select v-model="member.profile.weaponMaxLevel"><el-option v-for="n in levelCapOptions" :key="n" :value="n" :label="String(n)"/></el-select></el-form-item>
      <el-form-item data-field="refinement" label="精炼"><el-input-number v-model="member.profile.refinement" :min="1" :max="5"/></el-form-item>
      <el-form-item v-for="(label,i) in ['普攻基础等级','战技基础等级','爆发基础等级']" :key="label" :label="label"><el-input-number v-model="member.profile.talents[i]" :min="1" :max="15"/></el-form-item>
    </el-form>
    </details><el-button text type="danger" @click="emit('remove')">移除队员</el-button>
  </article>
</template>
<style scoped>
.member-card{min-width:0;padding:16px;border:1px solid var(--el-border-color);border-radius:10px;background:var(--el-fill-color-blank)}.member-card.participating{border-color:var(--el-color-primary);background:var(--el-color-primary-light-9)}.portrait{height:152px;display:grid;place-items:center;background:var(--el-fill-color-light);border-radius:8px}.portrait img{width:144px;height:144px;object-fit:contain}.portrait span{font-size:56px;color:var(--el-text-color-secondary)}h4{font-size:18px;margin:12px 0 5px}.personal-summary,.weapon-summary{font-size:13px;margin:6px 0;overflow-wrap:anywhere}.inheritance{font-size:12px;color:var(--el-text-color-secondary);line-height:1.6}.el-form{margin-top:12px}.el-select,.el-input-number{width:100%}.el-form-item{margin-bottom:12px}.override-fields{border-top:1px solid var(--el-border-color-light);padding-top:12px}

.member-card.participating{background:var(--el-fill-color-blank);border-color:var(--el-color-primary-light-7);box-shadow:inset 0 3px 0 var(--el-color-primary)}.portrait{height:144px;background:var(--el-fill-color-light)}.member-details{margin:10px 0 2px;border-top:1px solid var(--el-border-color-lighter);padding-top:8px}.member-details>summary{font-size:12px;cursor:pointer;color:var(--el-text-color-secondary)}.member-card h4{font-size:16px}.member-card>.el-button{font-size:12px}.member-card .personal-summary,.member-card .weapon-summary{font-size:12px;line-height:1.5}
</style>
