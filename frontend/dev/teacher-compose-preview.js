// Development-only fixture: every API request is handled locally, including sends.
import { createApp, h, ref } from 'vue'
import { createPinia } from 'pinia'
import { createMemoryHistory, createRouter, RouterView } from 'vue-router'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import '../src/styles/global.css'
import appRouter from '../src/router'
import { useAuthStore } from '../src/stores/auth'
import http from '../src/api/http'

const params = new URLSearchParams(location.search)
let failNext = params.has('failOnce')
const requests = ref([])
const projects = [
  { id: 101, title: '简易数字示波器 DIY套件', icon: '📟', category: '普通设备', difficulty: '挑战', duration: '4周', enrolledCount: 1, status: 'PUBLISHED', updatedAt: '2026-09-09' },
  { id: 102, title: 'AI颜色识别分拣系统竞赛套件', icon: '🤖', category: 'AI应用', difficulty: '挑战', duration: '4周', enrolledCount: 0, status: 'PUBLISHED', updatedAt: '2026-09-03', hubItemId: 7 },
  { id: 103, title: 'FPGA开发板 · 踏浪100', icon: '💻', category: 'FPGA / EDA', difficulty: '挑战', duration: '6周', enrolledCount: 1, status: 'PUBLISHED', updatedAt: '2026-08-10' },
  { id: 104, title: '智能温控与报警系统', icon: '🌡️', category: '普通设备', difficulty: '进阶', duration: '3周', enrolledCount: 0, status: 'DRAFT', updatedAt: '2026-09-20' }
]
const classes = [
  { id: 201, name: '2026级 电子信息专业 2班', teacherName: '陈老师', memberCount: 1, assignmentCount: 1, teacherCount: 1, joinCode: 'F2NMYN', status: 'ACTIVE', description: '' },
  { id: 202, name: '2026级 嵌入式技术与智能系统实践班', teacherName: '李老师', memberCount: 128, assignmentCount: 12, teacherCount: 4, joinCode: 'EM2026', status: 'ACTIVE', description: '周三下午开展分组实践，按阶段提交项目成果。' }
]
const student = { userId: 301, studentName: '张同学', studentNo: '20230101', projectId: 103, projectTitle: projects[2].title, progress: 0, currentTask: '阅读项目简介与前置知识', reasons: ['时间过半进度仅 0%'], status: 'IN_PROGRESS' }
const stats = { projectCount: 4, studentTotal: 2, completedTotal: 0, pendingSubmissions: 0, resourceCount: 4 }
const brief = { summary: '张同学的 FPGA 项目需要及时跟进，建议先明确本周的学习任务。', highlights: ['示波器项目进展正常'], actions: [{ kind: 'remind', title: '提醒张同学推进项目', projectId: 103 }, { kind: 'announce', title: '发布本周项目安排', projectId: 101 }] }
http.defaults.adapter = async (config) => {
  let data
  const url = config.url
  if (config.method === 'post' && (/\/remind\//.test(url) || /\/announce$/.test(url) || /^\/store\/publish\//.test(url))) {
    requests.value.push({ url, body: JSON.parse(config.data || '{}') })
    await new Promise(resolve => setTimeout(resolve, 1200))
    if (failNext) {
      failNext = false
      return { data: { code: 500, message: '预览：模拟发送失败' }, status: 200, statusText: 'OK', headers: {}, config }
    }
    data = url.includes('/announce') ? { notified: 1 } : url.includes('/publish/') ? { id: 7, newItem: true, latestVersionNo: 1 } : {}
  } else if (url === '/teacher/stats') data = stats
  else if (url === '/teacher/projects') data = projects
  else if (url === '/teacher/classes') data = classes
  else if (/^\/teacher\/classes\/\d+$/.test(url)) {
    const group = classes.find(row => url.endsWith('/' + row.id))
    data = { ...group, canManageTeachers: true, joinEnabled: true, members: [], teachers: [], assignments: [], announcements: [] }
  }
  else if (url === '/teacher/at-risk') data = [student]
  else if (url === '/teacher/skill-dimensions') data = []
  else if (url === '/equipment') data = []
  else if (url === '/public/site-config') data = { title: 'AI未来项目实践中心' }
  else if (/^\/teacher\/projects\/\d+\/students$/.test(url)) {
    data = url.includes('/103/') ? [student] : url.includes('/101/') ? [{ ...student, userId: 302, studentName: '李同学', progress: 25 }] : []
  } else if (url === '/ai/chat') data = { content: JSON.stringify(brief) }
  else throw new Error('预览未配置接口：' + url)
  return { data: { code: 0, data }, status: 200, statusText: 'OK', headers: {}, config }
}

const router = createRouter({ history: createMemoryHistory(), routes: [
  appRouter.options.routes.find(route => route.path === '/teacher'),
  { path: '/:pathMatch(.*)*', component: { render: () => h('p', '本预览仅展示教师界面。') } }
] })
const pinia = createPinia()
// Set the in-memory store only; never replace a real user's saved authentication.
useAuthStore(pinia).$patch({ user: { id: 'teacher-navigation-preview', name: '陈老师', role: 'TEACHER' }, token: '' })
await router.push('/teacher/' + (['projects', 'classes'].includes(params.get('page')) ? params.get('page') : 'workbench'))
await router.isReady()
createApp({ setup: () => () => [
  h(RouterView),
  h('aside', { style: 'position:fixed;bottom:6px;left:50%;transform:translateX(-50%);z-index:1;padding:4px 12px;border-radius:8px;background:#eef2ff;color:#657399;font-size:12px;white-space:nowrap' }, '界面预览 · 示例数据 · 不发送真实通知'),
  h('output', { 'data-testid': 'fixture-requests', hidden: true }, JSON.stringify(requests.value)),
  h('output', { 'data-testid': 'fixture-route', hidden: true }, router.currentRoute.value.fullPath)
] }).use(pinia).use(router).use(ElementPlus).mount('#app')
