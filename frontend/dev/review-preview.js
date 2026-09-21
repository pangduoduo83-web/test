// Development-only visual fixture. No data is sent to the backend.
import { createApp, ref, h } from 'vue'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import '../src/styles/global.css'
import SubmissionUploader from '../src/components/SubmissionUploader.vue'
import ReviewRubricEditor from '../src/components/ReviewRubricEditor.vue'
import GradeDialog from '../src/components/GradeDialog.vue'
import AdminAiSettings from '../src/views/admin/AdminAiSettings.vue'
import http from '../src/api/http'
const settings = { enabled:true, baseUrl:'https://api.example.com/v1', model:'review-model', apiKeySet:true, apiKeyMasked:'已配置', maxTokens:6000, temperature:0.4, connectTimeoutMs:15000, readTimeoutMs:120000, mineruMode:'cloud', visionEnabled:true, visionModel:'vision-model', speechEnabled:false, mineruCloudEnabled:true, mineruCloudBaseUrl:'https://mineru.net', mineruCloudModel:'vlm', mineruLocalModel:'standard' }
http.defaults.adapter = async config => ({ data:{code:0,data:settings},status:200,statusText:'OK',headers:{},config })
const evidence = {id:'file-0-1',name:'智能温控实验报告.pdf',location:'第3页',text:'示例材料：实验在温度达到阈值时触发报警，并记录了三组测试数据。'}
const result = {suggestedScore:86,summary:'报警和复位功能完成，实验记录较完整。建议补充临界温度附近的重复测试，并在演示中展示传感器接线。',criteria:[{name:'核心功能实现',score:36,maxScore:40,reason:'报告包含报警和复位的测试记录，与视频中的运行现象一致。',evidence:[evidence]},{name:'电路与接线规范',score:24,maxScore:30,reason:'接线较清晰，电源保护与传感器接口仍需结合原图核对。',needsConfirmation:true,evidence:[evidence]},{name:'实验记录与分析',score:14,maxScore:15,reason:'有多组测试数据和结果分析，建议增加重复次数。',evidence:[]},{name:'表达与材料完整性',score:12,maxScore:15,reason:'报告结构完整，演示说明较清楚。',evidence:[]}],strengths:['功能验证有记录','报告结构清楚'],pendingChecks:['核实传感器接口及电源保护接线'],feedbackDraft:'整体完成较好，请补充临界温度附近的重复测试。',note:'这里是示例数据，仅用于检查界面，不会写入成绩。'}
const job = {status:'DONE',progress:100,message:'评审完成，请教师核对',materials:[{id:'file-0',name:'智能温控实验报告.pdf',status:'DONE',warnings:[]},{id:'file-1',name:'运行演示.mp4',status:'PARTIAL',warnings:['视频画面为抽样分析，请结合原视频核实。']}],result}
createApp({setup(){
  const files=ref([{name:'智能温控实验报告.pdf',url:'/uploads/202609/preview.pdf',size:2460000}])
  const rubric=ref([{name:'核心功能实现',points:40,description:'展示触发、报警与复位过程，结合测试记录核实运行效果。'},{name:'电路与接线规范',points:30,description:'电源、传感器与输出接口连接正确，并提供清晰接线图。'},{name:'实验记录与分析',points:15,description:'提交测试数据、问题分析与改进方案。'},{name:'表达与材料完整性',points:15,description:'材料清晰完整，能说明实现思路。'}])
  const dialog=ref(false), mode=ref('materials'), narrow=ref(false)
  return ()=>h('main',{style:{maxWidth:narrow.value?'390px':'1100px',margin:'0 auto',padding:'24px 12px',transition:'max-width .2s'}},[
    h('h1',{class:'page-title'},'成果评审 · 界面预览'),h('p',{class:'page-subtitle'},'使用实际业务组件与示例数据，操作不会保存到系统。'),
    h('div',{style:'display:flex;gap:8px;flex-wrap:wrap;margin-bottom:20px'},[
      h('button',{class:'pill',onClick:()=>mode.value='materials'},'提交与评分细则'),h('button',{class:'pill',onClick:()=>mode.value='settings'},'AI 服务设置'),h('button',{class:'btn-gradient',onClick:()=>dialog.value=true},'打开教师评审'),h('button',{class:'pill',onClick:()=>narrow.value=!narrow.value},narrow.value?'恢复宽屏':'窄屏内容')]),
    mode.value==='materials'?h('div',{class:'card'},[h('h3',{style:'margin-top:0;font-size:16px'},'成果材料'),h(SubmissionUploader,{modelValue:files.value,'onUpdate:modelValue':v=>files.value=v,disabled:true}),h(ReviewRubricEditor,{modelValue:rubric.value,'onUpdate:modelValue':v=>rubric.value=v})]):h(AdminAiSettings),
    h(GradeDialog,{modelValue:dialog.value,'onUpdate:modelValue':v=>dialog.value=v,submission:{id:1,userName:'示例学生',projectTitle:'智能温控与报警系统',assessmentName:'功能演示',submittedAt:'2026-09-20T15:30:00',content:'已完成温度采集、阈值报警与手动复位，提交实验报告和演示视频。',attachments:[]},project:{skillRequirements:[{name:'电路设计',required:70}]},aiStatusFn:async()=>job,aiReviewFn:async()=>job,gradeFn:async()=>{}})
  ])
}}).use(ElementPlus).mount('#app')
