// Local UI fixture: all HTTP requests are handled in memory; no backend data is changed.
import { createApp, h, ref } from 'vue'
import { createPinia } from 'pinia'
import { createRouter, createMemoryHistory, RouterView, RouterLink } from 'vue-router'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import '../src/styles/global.css'
import StudentLayout from '../src/views/student/StudentLayout.vue'
import SkillsView from '../src/views/student/SkillsView.vue'
import { useAuthStore } from '../src/stores/auth'
import http from '../src/api/http'

// Reinstall the in-memory adapter after any dependency hot update.
if (import.meta.hot) import.meta.hot.on('vite:beforeUpdate', () => window.location.reload())

const mode = ref('new')
const lastRequest = ref('仅使用示例数据')
const names = ['嵌入式开发', '编程能力', '通信技术', 'PCB设计', '信号处理', '硬件调试']
const descriptions = ['单片机开发、外设驱动与实时系统设计', 'C / C++ 编程、算法设计与代码调试', '有线与无线通信协议、组网与协议分析', '电路原理图设计、PCB 布局布线与信号完整性', '信号采集、数字滤波与频谱分析', '仪器仪表使用、电路故障定位与焊接工艺']
const summaryFor = () => {
  const populated = mode.value === 'progress'
  const skills = mode.value === 'empty' ? [] : names.map((skillName, i) => ({ skillName, description: descriptions[i], score: populated ? [66, 75, 48, 59, 30, 42][i] : 30, evidenceCount: populated && i !== 4 ? i + 1 : 0 }))
  if (mode.value === 'many') skills.push(...Array.from({ length: 7 }, (_, i) => ({ skillName: '专业拓展与综合能力维度' + (i + 1), description: '检验较多维度与长技能名称的展示效果。', score: 30, evidenceCount: 0 })))
  const events = populated ? Array.from({ length: 14 }, (_, i) => ({ skillName: names[i % 6], source: i % 2 ? 'PROJECT' : 'QUIZ', beforeScore: 30 + i, afterScore: 52 + i, note: i % 2 ? '智能温控系统项目评审通过，电路设计与调试能力得到验证。' : '完成 AI 能力测评，当前维度的能力画像已更新。', createdAt: '2026-09-' + String(21 - i).padStart(2, '0') + 'T10:30:00' })) : []
  return { skills, overall: populated ? 53 : 30, evidenceTotal: populated ? 16 : 0, suggestions: ['先完成一次 AI 测评，让能力画像更准确。', '通过入门项目加强嵌入式开发能力。', '将编程知识应用到实战项目中。'], history: populated ? [30, 37, 42, 48, 53].map((overall, i) => ({ overall, time: '2026-09-' + (15 + i) + 'T10:30:00', source: 'QUIZ' })) : [], events }
}
let currentSummary = summaryFor()
let currentSkill = names[0]
let savedPlan = null
let failOnce = false
const questions = Array.from({ length: 6 }, (_, i) => ({ q: '示例题 ' + (i + 1) + '：调试传感器时，应当先检查哪一项？', options: ['供电与接线', '外壳颜色', '文件名称', '桌面壁纸'], difficulty: '基础' }))
const planFor = () => ({
  source: 'AI', summary: '从基础传感器实验出发，逐步完成可以远程监测的物联网作品。结合每周可用时间，先验证电路，再加入通信与数据处理。',
  generatedAt: '2026-09-21T15:30:00', cached: false,
  focusSkills: [{ name: '嵌入式开发', currentScore: 30, targetScore: 65, reason: '巩固外设驱动与传感器数据采集。' }, { name: '通信技术', currentScore: 30, targetScore: 60, reason: '在联网项目中练习数据传输。' }],
  recommendedProjects: ['温度传感器与数据采集', '无线环境监测终端', '物联网远程监测作品'].map((title, i) => ({ projectId: i + 1, title, stage: i + 1, difficulty: i ? '进阶' : '入门', matchScore: 95 - i * 3, reasons: ['覆盖当前需要提升的技能，难度循序渐进。'], skillGaps: i ? ['通信协议与数据处理'] : [], nextAction: '查看项目要求，准备开发板与传感器。' }))
})
http.defaults.adapter = async config => {
  let result
  if (config.url === '/skills') {
    if (mode.value === 'error' && !failOnce) { failOnce = true; throw new Error('示例加载失败') }
    result = currentSummary
  } else if (config.url === '/skills/quiz/start') {
    currentSkill = JSON.parse(config.data).skillName
    result = { quizId: 'local-quiz', questions }
  } else if (config.url === '/skills/quiz/submit') {
    const { answers } = JSON.parse(config.data)
    const skill = currentSummary.skills.find(s => s.skillName === currentSkill)
    const before = skill.score
    skill.score = 52; skill.evidenceCount++
    currentSummary.evidenceTotal++
    currentSummary.overall = Math.round(currentSummary.skills.reduce((sum, s) => sum + s.score, 0) / currentSummary.skills.length)
    currentSummary.events.unshift({ skillName: currentSkill, source: 'QUIZ', beforeScore: before, afterScore: 52, note: '示例测评已完成，能力画像已更新。', createdAt: '2026-09-21T15:40:00' })
    currentSummary.history = [{ overall: 30, time: '2026-09-20T15:30:00', source: 'INIT' }, { overall: currentSummary.overall, time: '2026-09-21T15:40:00', source: 'QUIZ' }]
    result = { score: 100, correct: 6, total: 6, skillName: currentSkill, before, after: 52, detail: questions.map((_, index) => ({ index, answer: 0, given: answers[index], correct: answers[index] === 0, explain: '先检查供电与接线，再进行程序调试。' })), summary: currentSummary }
    savedPlan = null
  } else if (config.url === '/ai/learning-plan') result = savedPlan
  else if (config.url === '/ai/learning-plan/generate') { savedPlan = planFor(); result = savedPlan }
  else if (config.url === '/auth/me') result = { name: '示例同学', role: 'STUDENT', major: '电子信息工程', level: 1 }
  else if (config.url === '/dashboard') result = { weeklyActiveDays: 3, weeklyActivities: 5, skillAvg: currentSummary.overall }
  else if (config.url === '/notifications') result = { items: [], unread: 0 }
  else if (config.url === '/public/site-config') result = { title: 'AI未来项目实践中心', footerText: '本地界面预览 · 所有数据均为示例' }
  else throw new Error('Unexpected fixture endpoint: ' + config.url)
  if (config.method === 'post') lastRequest.value = config.url + ' · 本地模拟完成'
  return { data: { code: 0, data: result }, status: 200, statusText: 'OK', headers: {}, config }
}
const router = createRouter({ history: createMemoryHistory(), routes: [{ path: '/app', component: StudentLayout, children: [
  { path: 'skills', component: { setup: () => () => h(SkillsView, { key: mode.value }) } },
  { path: 'projects/:id', component: { setup: () => () => h('div', { class: 'card' }, [h('h2', '项目跳转验证成功'), h(RouterLink, { to: '/app/skills' }, () => '返回技能评估')]) } },
  { path: ':pathMatch(.*)*', component: { setup: () => () => h('div', { class: 'card' }, [h('h2', '当前仅预览技能评估'), h('p', '其他页面请在实际系统中查看。'), h(RouterLink, { to: '/app/skills' }, () => '返回技能评估')]) } }
] }] })
const pinia = createPinia()
const auth = useAuthStore(pinia)
auth.$patch({ token: '', user: { name: '示例同学', role: 'STUDENT' } })
// Prevent the real layout's refresh action from persisting the fixture user.
auth.updateUser = user => { auth.user = user }
const App = { setup: () => () => h('div', [
  h('div', { class: 'preview-toolbar' }, [
    h('b', '界面预览 · 示例数据'),
    h('select', { 'aria-label': '示例场景', value: mode.value, onChange: e => { mode.value = e.target.value; currentSummary = summaryFor(); savedPlan = null; failOnce = false; router.push('/app/skills') } }, [
      h('option', { value: 'new' }, '首次使用'), h('option', { value: 'progress' }, '已有实证'), h('option', { value: 'empty' }, '未配置技能'), h('option', { value: 'many' }, '多维度长名称'), h('option', { value: 'error' }, '加载失败与重试')
    ]),
    h('small', lastRequest.value)
  ]), h(RouterView)
]) }
const style = document.createElement('style')
style.textContent = '.preview-toolbar{height:36px;display:flex;align-items:center;gap:14px;padding:0 18px;background:#edeaf9;color:#736591;font:11px system-ui}.preview-toolbar select{font:11px system-ui;border:1px solid #d8d2e8;border-radius:5px;padding:3px;color:#706186}.preview-toolbar small{margin-left:auto;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}.layout{height:calc(100vh - 36px)!important}'
document.head.appendChild(style)
await router.push('/app/skills')
createApp(App).use(pinia).use(router).use(ElementPlus).mount('#app')
