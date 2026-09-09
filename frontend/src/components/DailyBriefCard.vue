<template>
  <!-- 个人中心「今日建议」:AI 按学生状态给出的待办清单,不是聊天框 -->
  <div class="brief">
    <div class="brief-head">
      <span class="brief-icon">☀️</span>
      <div class="grow">
        <div class="brief-title">今日建议</div>
        <div class="brief-sub">{{ meta }}</div>
      </div>
      <el-button size="small" text :loading="loading" @click="load(true)"><RefreshCw :size="13" style="margin-right:4px" />换一批</el-button>
    </div>

    <div v-if="loading && !brief" class="skeleton"><div class="sk w80"></div><div class="sk"></div><div class="sk w60"></div></div>

    <template v-else-if="brief">
      <div class="headline">{{ brief.headline }}</div>
      <div class="todos">
        <div v-for="(t, i) in brief.todos" :key="i" class="todo" :class="'k-' + t.kind">
          <span class="todo-kind">{{ kindText(t.kind) }}</span>
          <div class="grow">
            <div class="todo-title">{{ t.title }}</div>
            <div class="todo-reason">{{ t.reason }}<span v-if="t.minutes"> · 约 {{ t.minutes }} 分钟</span></div>
          </div>
          <el-button size="small" :type="i === 0 ? 'primary' : 'default'" plain @click="go(t)">{{ actionText(t.kind) }}</el-button>
        </div>
      </div>
      <div v-if="brief.encouragement" class="encourage">{{ brief.encouragement }}</div>
    </template>

    <div v-else class="fallback">
      <div class="headline">{{ fallback.headline }}</div>
      <div class="todos">
        <div v-for="(t, i) in fallback.todos" :key="i" class="todo" :class="'k-' + t.kind">
          <span class="todo-kind">{{ kindText(t.kind) }}</span>
          <div class="grow"><div class="todo-title">{{ t.title }}</div><div class="todo-reason">{{ t.reason }}</div></div>
          <el-button size="small" plain @click="go(t)">{{ actionText(t.kind) }}</el-button>
        </div>
      </div>
      <div class="muted">{{ error ? 'AI 暂不可用,先按规则给你排了个顺序:' + error : '' }}</div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { RefreshCw } from 'lucide-vue-next'
import { runJsonSkillCached } from '../api/aiJson'

const props = defineProps({
  userId: { type: [Number, String], required: true },
  /** 个人中心接口返回的进行中项目,用于 AI 不可用时的规则兜底 */
  ongoing: { type: Array, default: () => [] }
})
const router = useRouter()
const brief = ref(null)
const loading = ref(false)
const error = ref('')
const cachedAt = ref(null)

const today = () => new Date().toISOString().slice(0, 10)
const meta = computed(() => (cachedAt.value ? `根据你的进度、截止日期和技能画像生成 · ${new Date(cachedAt.value).toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })}` : '根据你的进度、截止日期和技能画像生成'))

const kindText = (k) => ({ progress: '推进', submit: '提交', revise: '修改', deadline: '临期', skill: '补短板', explore: '探索' }[k] || '待办')
const actionText = (k) => ({ progress: '继续做', submit: '去提交', revise: '去修改', deadline: '去处理', skill: '看技能', explore: '挑项目' }[k] || '去看看')
const go = (t) => {
  if (t.kind === 'skill') return router.push('/app/skills')
  if (t.kind === 'explore' || !t.projectId) return router.push('/app/projects')
  router.push(`/app/projects/${t.projectId}`)
}

const fallback = computed(() => {
  const todos = []
  const sorted = [...props.ongoing].sort((a, b) => String(a.deadline || '9999').localeCompare(String(b.deadline || '9999')))
  for (const e of sorted.slice(0, 3)) {
    const days = e.deadline ? Math.ceil((new Date(String(e.deadline).replace(/-/g, '/')) - new Date()) / 86400000) : null
    if (e.progress >= 100) todos.push({ kind: 'submit', title: `提交《${e.projectTitle}》成果`, reason: '进度已到 100%,等评审通过才算完成', projectId: e.projectId })
    else if (days !== null && days <= 3) todos.push({ kind: 'deadline', title: `推进《${e.projectTitle}》`, reason: days < 0 ? `已过截止 ${-days} 天` : `还剩 ${days} 天截止`, projectId: e.projectId })
    else todos.push({ kind: 'progress', title: `继续《${e.projectTitle}》`, reason: e.currentTask ? `当前任务:${e.currentTask}` : `当前进度 ${e.progress}%`, projectId: e.projectId })
  }
  if (!todos.length) todos.push({ kind: 'explore', title: '去项目中心挑一个项目', reason: '还没有进行中的项目', projectId: null })
  return { headline: todos[0].title, todos }
})

const load = async (force = false) => {
  loading.value = true
  error.value = ''
  try {
    const r = await runJsonSkillCached(`daily-brief:${props.userId}:${today()}`, 12 * 3600 * 1000, 'daily-brief', '请生成我今天的学习建议', { scope: 'student' }, force)
    brief.value = r.data
    cachedAt.value = r.at
  } catch (e) {
    brief.value = null
    error.value = e?.message || ''
  } finally {
    loading.value = false
  }
}

onMounted(() => load(false))
</script>

<style scoped>
.brief { background: linear-gradient(135deg, #fefce8, #fff7ed); border: 1px solid #fde68a; border-radius: 16px; padding: 18px 20px; margin-bottom: 20px; }
.grow { flex: 1; min-width: 0; }
.muted { font-size: 12px; color: #9ca3af; margin-top: 6px; }
.brief-head { display: flex; align-items: center; gap: 12px; margin-bottom: 10px; }
.brief-icon { width: 40px; height: 40px; border-radius: 12px; background: #fff; display: grid; place-items: center; font-size: 20px; box-shadow: var(--shadow-card); }
.brief-title { font-weight: 800; font-size: 15px; color: #78350f; }
.brief-sub { font-size: 12px; color: #a16207; }
.headline { font-size: 16px; font-weight: 700; color: #111827; margin: 4px 0 12px; }
.todos { display: flex; flex-direction: column; gap: 8px; }
.todo { display: flex; align-items: center; gap: 12px; background: #fff; border-radius: 12px; padding: 10px 14px; border-left: 4px solid #93c5fd; }
.todo.k-deadline, .todo.k-revise { border-left-color: #f87171; }
.todo.k-submit { border-left-color: #22c55e; }
.todo.k-skill { border-left-color: #a78bfa; }
.todo.k-explore { border-left-color: #fbbf24; }
.todo-kind { font-size: 11px; font-weight: 700; color: #6b7280; width: 40px; flex-shrink: 0; }
.todo-title { font-size: 14px; font-weight: 600; }
.todo-reason { font-size: 12px; color: var(--text-secondary); margin-top: 2px; }
.encourage { font-size: 13px; color: #92400e; margin-top: 12px; }
.skeleton { display: flex; flex-direction: column; gap: 10px; padding: 6px 0; }
.sk { height: 12px; border-radius: 6px; background: linear-gradient(90deg, #fef3c7, #fde68a, #fef3c7); background-size: 200% 100%; animation: sk 1.2s infinite; }
.sk.w80 { width: 80%; } .sk.w60 { width: 60%; }
@keyframes sk { 0% { background-position: 200% 0; } 100% { background-position: -200% 0; } }
</style>
