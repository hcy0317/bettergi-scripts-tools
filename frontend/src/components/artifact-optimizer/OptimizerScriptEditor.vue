<script setup>
import {computed,ref} from 'vue'
const props=defineProps({modelValue:{type:String,default:''},field:{type:String,required:true},label:{type:String,required:true},issues:{type:Array,default:()=>[]}})
const emit=defineEmits(['update:modelValue'])
const input=ref(null)
const problems=computed(()=>props.issues.filter(issue=>issue.field===props.field))
function snippet(issue){
  if(!Number.isInteger(issue.startOffset)||!Number.isInteger(issue.endOffset))return null
  const start=Math.max(0,Math.min(props.modelValue.length,issue.startOffset)),end=Math.max(start,Math.min(props.modelValue.length,issue.endOffset))
  const lineStart=props.modelValue.lastIndexOf('\n',start-1)+1,next=props.modelValue.indexOf('\n',end),lineEnd=next<0?props.modelValue.length:next
  const left=Math.max(lineStart,start-120),right=Math.min(lineEnd,end+120)
  return {before:(left>lineStart?'…':'')+props.modelValue.slice(left,start),marked:props.modelValue.slice(start,end),after:props.modelValue.slice(end,right)+(right<lineEnd?'…':'')}
}
function focusIssue(issue){
  const textarea=input.value?.textarea||input.value?.$el?.querySelector('textarea')
  if(!textarea)return
  textarea.focus()
  if(Number.isInteger(issue.startOffset)&&Number.isInteger(issue.endOffset)){
    textarea.setSelectionRange(issue.startOffset,issue.endOffset)
    const lineHeight=parseFloat(getComputedStyle(textarea).lineHeight)||22
    textarea.scrollTop=Math.max(0,((issue.line||1)-1)*lineHeight-textarea.clientHeight/3)
  }
}
</script>
<template>
  <div class="script-editor" :class="{'has-errors':problems.length}" :data-script-field="field">
    <el-input ref="input" :model-value="modelValue" @update:model-value="value=>emit('update:modelValue',value)" type="textarea" :autosize="{minRows:8,maxRows:24}" wrap="off" spellcheck="false" :aria-label="label" :aria-invalid="problems.length>0" :aria-describedby="problems.length?'script-problems-'+field:undefined"/>
    <div v-if="problems.length" :id="'script-problems-'+field" class="script-problems" role="status" aria-live="polite">
      <p>以下问题会阻止计算；原文未修改。</p>
      <div v-for="(issue,i) in problems" :key="i" class="script-problem">
        <button type="button" @click="focusIssue(issue)"><span v-if="issue.line">第 {{ issue.line }} 行，第 {{ issue.column }} 列：</span>{{ issue.message }}</button>
        <pre v-if="snippet(issue)"><code>{{ snippet(issue).before }}<mark>{{ snippet(issue).marked }}</mark>{{ snippet(issue).after }}</code></pre>
      </div>
    </div>
  </div>
</template>
<style scoped>
.script-editor :deep(textarea){font:13px/22px ui-monospace,Consolas,monospace;white-space:pre;padding:14px;tab-size:2}
.has-errors :deep(textarea){box-shadow:0 0 0 1px var(--el-color-danger) inset}
.script-problems{margin-top:10px;padding:12px;border:1px solid var(--el-color-danger-light-5);border-radius:8px;background:var(--el-color-danger-light-9)}
.script-problems p{margin:0 0 8px;font-size:13px}
.script-problem button{border:0;background:none;color:var(--el-color-danger);text-align:left;cursor:pointer;padding:4px 0;font:inherit}
.script-problem button:focus-visible{outline:2px solid var(--el-color-primary);outline-offset:2px}
.script-problem pre{margin:4px 0 10px;overflow:auto;white-space:pre;color:var(--el-text-color-primary)}
.script-problem mark{background:var(--el-color-danger-light-5);color:var(--el-text-color-primary);text-decoration:underline wavy var(--el-color-danger);text-underline-offset:3px}
</style>
