import {createApp,reactive,ref} from 'vue'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import Overview from '../src/components/artifact-optimizer/OptimizerPriorityOverview.vue'
// Synthetic fixture only. No production API or saved user profile is used.
const original={version:7,characters:[{key:'furina',name:'芙宁娜',weight:1,builds:[{id:'water',weight:1},{id:'mixed',weight:1}],protected:true},{key:'neuvillette',name:'那维莱特',weight:1,builds:[{id:'water',weight:1}]},{key:'jean',name:'琴',weight:1,builds:[{id:'water',weight:1},{id:'mixed',weight:1}]},{key:'zhongli',name:'钟离',weight:0,builds:[{id:'mixed',weight:1}]}],builds:[{id:'water',name:'水伤循环',weight:1,roundCount:3,members:[{character:'furina'},{character:'neuvillette'},{character:'jean'},{character:'zhongli'}]},{id:'mixed',name:'综合支援',weight:1,roundCount:3,members:[{character:'furina'},{character:'jean'},{character:'zhongli'}]}]}
const catalog={characters:[{key:'furina',icon_name:'UI_AvatarIcon_Furina'},{key:'neuvillette',icon_name:'UI_AvatarIcon_Neuvillette'},{key:'jean',icon_name:'UI_AvatarIcon_Qin'},{key:'zhongli',icon_name:'UI_AvatarIcon_Zhongli'}],localization:{character_names:{furina:'芙宁娜',neuvillette:'那维莱特',jean:'琴',zhongli:'钟离'}}}
createApp({components:{Overview},setup(){
  const workspace=reactive(structuredClone(original)),saved=ref(''),disabled=ref(false)
  async function save(){const response=await fetch('/__priority-test/save',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(workspace)});saved.value=JSON.stringify(await response.json(),null,2)}
  return {workspace,catalog,saved,disabled,save}
},template:`<main style="max-width:1240px;margin:auto;padding:24px"><header style="display:flex;gap:12px;align-items:center;margin-bottom:20px"><span>隔离夹具，不修改真实档案</span><el-button @click="save">保存夹具</el-button><el-checkbox v-model="disabled">模拟保存中</el-checkbox></header><Overview :workspace="workspace" :catalog="catalog" :selected="['furina','neuvillette','jean']" :disabled="disabled"/><details><summary>实际保存请求</summary><pre id="saved-payload" style="white-space:pre-wrap;overflow-wrap:anywhere">{{saved}}</pre></details></main>`}).use(ElementPlus).mount('#app')
