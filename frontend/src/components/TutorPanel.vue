<template>
  <!-- 项目导师面板:嵌在项目页右侧,以阶段步进条 + 行动清单 + 动作按钮为主,对话只是补充 -->
  <el-drawer :model-value="modelValue" size="520px" :with-header="false" class="tutor-drawer"
             @update:model-value="(v) => $emit('update:modelValue', v)" @open="onOpen">
    <div class="tp">
      <header class="tp-head">
        <span class="tp-icon">🧭</span>
        <div class="grow">
          <div class="tp-title">项目导师</div>
          <div class="tp-sub">《{{ project?.title }}》</div>
        </div>
        <el-button text @click="$emit('update:modelValue', false)">关闭</el-button>
      </header>

      <!-- 阶段步进 -->
      <div v-if="phases.length" class="stepper">
        <div v-for="(ph, i) in phases" :key="i" class="step" :class="{ done: donePhases.has(i + 1), current: currentPhase === i + 1 }">
          <span class="step-dot">{{ donePhases.has(i + 1) ? '✓' : i + 1 }}</span>
          <span class="step-label">{{ ph.title || ph.phase }}</span>
        </div>
        <div class="step" :class="{ current: currentPhase === null && enrollment && enrollment.status !== 'COMPLETED', done: enrollment?.status === 'COMPLETED' }">
          <span class="step-dot">{{ enrollment?.status === 'COMPLETED' ? '✓' : '★' }}</span>
          <span class="step-label">提交成果</span>
        </div>
      </div>

      <div class="tp-body">
        <!-- 未报名 -->
        <div v-if="!enrollment" class="card-soft">
          <b>先报名,再开始</b>
          <p>报名后我会按教学大纲一步步带你做,并记录你的进度。</p>
          <el-button type="primary" size="small" @click="$emit('enroll')">立即报名</el-button>
        </div>

        <!-- 已完成 -->
        <div v-else-if="enrollment.status === 'COMPLETED'" class="card-soft done-card">
          <b>🎉 这个项目已经通过评审完成</b>
          <p>想继续提升可以去项目中心挑一个更进阶的项目,或者在下面问我下一步学什么。</p>
        </div>

        <!-- 当前阶段行动清单 -->
        <template v-else>
          <div class="plan-head">
            <div>
              <div class="plan-phase">{{ currentPhase ? `第 ${currentPhase} 阶段 · ${phases[currentPhase - 1]?.title || ''}` : '所有阶段已完成 · 准备提交成果' }}</div>
              <div class="plan-goal" v-if="plan?.goal">{{ plan.goal }}</div>
            </div>
            <el-button size="small" text :loading="planLoading" @click="loadPlan(true)"><RefreshCw :size="13" style="margin-right:4px" />重新生成</el-button>
          </div>

          <div v-if="planLoading" class="skeleton">
            <div class="sk-line w70"></div><div class="sk-line"></div><div class="sk-line w90"></div><div class="sk-line w60"></div>
            <div class="sk-tip">正在根据你的进度和教学大纲生成行动清单…</div>
          </div>
          <div v-else-if="planError" class="card-soft warn">
            <b>暂时生成不了行动清单</b>
            <p>{{ planError }}</p>
            <el-button size="small" @click="loadPlan(true)">再试一次</el-button>
          </div>
          <template v-else-if="plan">
            <ol class="steps">
              <li v-for="(s, i) in plan.steps" :key="i" class="step-card">
                <div class="sc-top"><b>{{ s.title }}</b><span v-if="s.minutes" class="sc-min">约 {{ s.minutes }} 分钟</span></div>
                <div class="sc-detail">{{ s.detail }}</div>
                <a class="sc-ask" @click="ask(`「${s.title}」这一步具体怎么做?请给我可操作的细节。`)">问导师怎么做 →</a>
              </li>
            </ol>

            <div v-if="plan.equipment?.length" class="mini-sec">
              <div class="mini-title">需要的设备</div>
              <div class="eq-list">
                <span v-for="(e, i) in plan.equipment" :key="i" class="eq-chip" :title="e.reason" @click="$router.push({ path: '/app/equipment', query: { keyword: e.name } })">
                  <Wrench :size="12" /> {{ e.name }} <small>查库存</small>
                </span>
              </div>
            </div>

            <div v-if="plan.checks?.length" class="mini-sec">
              <div class="mini-title">做完后自查</div>
              <label v-for="(c, i) in plan.checks" :key="i" class="check-item">
                <el-checkbox v-model="checked[i]" />{{ c }}
              </label>
            </div>

            <div v-if="plan.pitfalls?.length" class="mini-sec pitfalls">
              <div class="mini-title">容易踩的坑</div>
              <ul><li v-for="(p, i) in plan.pitfalls" :key="i">{{ p }}</li></ul>
            </div>
            <div v-if="plan.askTeacher" class="ask-teacher">🙋 {{ plan.askTeacher }}</div>

            <div class="plan-actions">
              <el-button v-if="currentPhase" type="primary" :loading="completing" @click="completePhase">
                <Check :size="14" style="margin-right:4px" /> 本阶段做完了,标记完成
              </el-button>
              <el-button v-else type="success" @click="$emit('go-submit')">去提交成果</el-button>
              <el-button v-if="currentPhase" text @click="ask('我在这个阶段卡住了,帮我判断问题可能出在哪,一步步排查。')">我卡住了</el-button>
            </div>
          </template>
        </template>

        <!-- 对话区 -->
        <div class="chat">
          <div class="chat-title">问导师<span class="muted">· 它知道这个项目和你的进度</span></div>
          <div ref="scroller" class="msgs">
            <div v-if="messages.length === 0" class="chips">
              <span v-for="q in quickQuestions" :key="q" class="chip-q" @click="ask(q)">{{ q }}</span>
            </div>
            <div v-for="(m, i) in messages" :key="i" class="msg" :class="m.role">
              <div v-if="m.role === 'user'" class="bubble user">{{ m.content }}</div>
              <div v-else class="bubble assistant">
                <div v-for="(t, j) in m.tools" :key="j" class="tool-line" :class="{ fail: t.ok === false }">🔧 {{ toolLabel(t.name) }}<span v-if="t.ok !== undefined"> · {{ t.ok ? '完成' : '失败' }}</span><span v-else> · 执行中…</span></div>
                <div v-if="m.confirm" class="confirm">
                  <div class="confirm-title">需要你确认:{{ confirmText(m.confirm) }}</div>
                  <div v-if="!m.confirm.resolved" class="confirm-actions">
                    <el-button type="primary" size="small" :loading="busy" @click="confirmTool(m)">确认执行</el-button>
                    <el-button size="small" :disabled="busy" @click="m.confirm.resolved = 'cancelled'">取消</el-button>
                  </div>
                  <div v-else class="muted">{{ m.confirm.resolved === 'confirmed' ? '已确认执行' : '已取消' }}</div>
                </div>
                <div class="content" v-html="render(m.content)"></div>
                <span v-if="m.streaming" class="cursor">▍</span>
              </div>
            </div>
          </div>
          <div class="composer">
            <el-input v-model="draft" placeholder="问点什么,Enter 发送" :disabled="busy" @keydown.enter.exact.prevent="ask()" />
            <el-button v-if="!busy" type="primary" :disabled="!draft.trim()" @click="ask()">发送</el-button>
            <el-button v-else type="danger" plain @click="stop">停止</el-button>
          </div>
          <div class="foot-link"><a @click="$router.push({ path: '/app/ai', query: { task: 'ask' } })">更多问题去 AI 助教 →</a></div>
        </div>
      </div>
    </div>
  </el-drawer>
</template>

<script setup>
import { computed, nextTick, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Check, RefreshCw, Wrench } from 'lucide-vue-next'
import { updateProgress } from '../api'
import { streamChat } from '../api/aiStream'
import { runJsonSkillCached } from '../api/aiJson'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  projectId: { type: [Number, String], required: true },
  project: { type: Object, default: null },
  enrollment: { type: Object, default: null }
})
const emit = defineEmits(['update:modelValue', 'enroll', 'refresh', 'go-submit'])

const arr = (v) => (Array.isArray(v) ? v : [])
const phases = computed(() => arr(props.project?.syllabus))
const donePhases = computed(() => {
  try { return new Set(JSON.parse(props.enrollment?.completedPhases || '[]')) } catch (e) { return new Set() }
})
const currentPhase = computed(() => {
  if (!phases.value.length) return null
  for (let i = 1; i <= phases.value.length; i++) if (!donePhases.value.has(i)) return i
  return null
})

// ---------- 行动清单 ----------
const plan = ref(null)
const planLoading = ref(false)
const planError = ref('')
const checked = reactive({})
const completing = ref(false)
const planKey = computed(() => `tutor-plan:${props.projectId}:${[...donePhases.value].sort().join('.') || 'none'}:${props.enrollment?.status || 'x'}`)

const loadPlan = async (force = false) => {
  if (!props.enrollment || props.enrollment.status === 'COMPLETED') return
  planLoading.value = true
  planError.value = ''
  try {
    const r = await runJsonSkillCached(planKey.value, 3 * 24 * 3600 * 1000, 'project-next-step',
      '请根据我的进度生成当前阶段的行动清单', { projectId: Number(props.projectId) }, force)
    plan.value = r.data
    Object.keys(checked).forEach((k) => delete checked[k])
  } catch (e) {
    plan.value = null
    planError.value = e?.response?.data?.message || e?.message || 'AI 暂不可用'
  } finally {
    planLoading.value = false
  }
}

const completePhase = async () => {
  if (!currentPhase.value) return
  completing.value = true
  try {
    const set = new Set(donePhases.value)
    set.add(currentPhase.value)
    await updateProgress(props.projectId, { progress: 0, completedPhases: [...set] })
    ElMessage.success(set.size === phases.value.length ? '全部阶段完成!去提交成果吧' : `第 ${currentPhase.value} 阶段已完成`)
    emit('refresh')
  } catch (e) { /* 已提示 */ } finally {
    completing.value = false
  }
}

watch(planKey, () => { if (props.modelValue) loadPlan() })
const onOpen = () => { if (!plan.value) loadPlan() }

// ---------- 对话 ----------
const messages = ref([])
const draft = ref('')
const busy = ref(false)
const conversationId = ref(null)
const scroller = ref(null)
let controller = null
const quickQuestions = ['我现在该做什么?', '这个阶段需要借哪些设备?', '帮我讲讲这个阶段的原理', '我的成果要怎么写才容易通过?']
const toolLabel = (n) => ({
  'project.get': '查看项目资料', 'enrollment.my_list': '查看我的进度', 'enrollment.update_progress': '更新进度', 'submission.my_list': '查看我的成果',
  'skill.my_scores': '查看技能画像', 'equipment.search': '查设备库存', 'equipment.get': '查看设备详情', 'borrow.apply': '提交借阅申请', 'discussion.post': '发到讨论区'
}[n] || n)
const confirmText = (c) => {
  if (c.tool === 'enrollment.update_progress') {
    const a = c.args || {}
    return a.completedPhases ? `把已完成阶段更新为 ${JSON.stringify(a.completedPhases)}${a.currentTask ? ',下一步「' + a.currentTask + '」' : ''}` : `把进度更新为 ${a.progress}%`
  }
  if (c.tool === 'borrow.apply') return `提交借阅申请(设备 #${c.args?.equipmentId} × ${c.args?.quantity || 1})`
  if (c.tool === 'discussion.post') return `在项目讨论区发布:「${(c.args?.content || '').slice(0, 40)}」`
  return c.description || c.tool
}
const render = (text) => {
  if (!text) return ''
  const esc = text.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
  return esc.replace(/\*\*([^*]+)\*\*/g, '<b>$1</b>').replace(/`([^`]+)`/g, '<code>$1</code>')
    .replace(/^#{1,4} (.*)$/gm, '<b>$1</b>').replace(/^[-*] (.*)$/gm, '• $1').replace(/^(\d+)\. (.*)$/gm, '$1. $2').replace(/\n/g, '<br/>')
}
const scrollToBottom = () => nextTick(() => { if (scroller.value) scroller.value.scrollTop = scroller.value.scrollHeight })

const run = (body) => {
  busy.value = true
  const assistant = reactive({ role: 'assistant', content: '', tools: [], confirm: null, streaming: true })
  messages.value.push(assistant)
  scrollToBottom()
  controller = streamChat(body, {
    onDelta: (t) => { assistant.content += t; scrollToBottom() },
    onToolCall: (t) => { assistant.tools.push({ name: t.name, args: t.args }); scrollToBottom() },
    onToolResult: (r) => { const t = [...assistant.tools].reverse().find((x) => x.name === r.name && x.ok === undefined); if (t) t.ok = r.ok },
    onConfirmRequired: (c) => { assistant.confirm = { ...c, resolved: null } },
    onDone: (d) => {
      assistant.streaming = false
      busy.value = false
      if (d.conversationId) conversationId.value = d.conversationId
      if (!assistant.content && !assistant.confirm) assistant.content = d.content || ''
      if (assistant.tools.some((t) => t.name === 'enrollment.update_progress' && t.ok)) emit('refresh')
      scrollToBottom()
    },
    onError: (msg) => { assistant.streaming = false; assistant.content = assistant.content || `⚠️ ${msg}`; busy.value = false }
  })
}
const ask = (text) => {
  const content = (text ?? draft.value).trim()
  if (!content || busy.value) return
  draft.value = ''
  messages.value.push({ role: 'user', content, tools: [] })
  run({ skillKey: 'project-tutor', conversationId: conversationId.value, input: content, context: conversationId.value ? undefined : { projectId: Number(props.projectId) } })
}
const confirmTool = (m) => { m.confirm.resolved = 'confirmed'; run({ skillKey: 'project-tutor', conversationId: conversationId.value, confirm: true }) }
const stop = () => { controller?.abort(); busy.value = false; const last = messages.value[messages.value.length - 1]; if (last) last.streaming = false }
</script>

<style scoped>
.tp { display: flex; flex-direction: column; height: 100%; }
.grow { flex: 1; min-width: 0; }
.muted { font-size: 12px; color: var(--text-secondary); font-weight: 400; }
.tp-head { display: flex; align-items: center; gap: 12px; padding: 4px 4px 14px; border-bottom: 1px solid var(--border); }
.tp-icon { width: 42px; height: 42px; border-radius: 12px; background: linear-gradient(135deg, #ede9fe, #ddd6fe); display: grid; place-items: center; font-size: 22px; }
.tp-title { font-weight: 800; font-size: 16px; }
.tp-sub { font-size: 12px; color: var(--text-secondary); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }

.stepper { display: flex; gap: 4px; padding: 14px 4px 6px; overflow-x: auto; }
.step { display: flex; flex-direction: column; align-items: center; gap: 6px; min-width: 72px; flex: 1; position: relative; }
.step::after { content: ''; position: absolute; top: 12px; left: 50%; width: 100%; height: 2px; background: #e5e7eb; z-index: 0; }
.step:last-child::after { display: none; }
.step-dot { width: 24px; height: 24px; border-radius: 50%; background: #e5e7eb; color: #6b7280; font-size: 12px; font-weight: 700; display: grid; place-items: center; z-index: 1; }
.step.done .step-dot { background: #22c55e; color: #fff; }
.step.done::after { background: #86efac; }
.step.current .step-dot { background: var(--brand-blue); color: #fff; box-shadow: 0 0 0 4px #dbeafe; }
.step-label { font-size: 11px; color: #6b7280; text-align: center; max-width: 84px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.step.current .step-label { color: var(--brand-blue); font-weight: 600; }

.tp-body { flex: 1; overflow-y: auto; padding: 8px 4px 0; display: flex; flex-direction: column; gap: 14px; }
.card-soft { background: #f9fafb; border: 1px solid var(--border); border-radius: 12px; padding: 14px; font-size: 13px; }
.card-soft p { margin: 6px 0 10px; color: var(--text-secondary); line-height: 1.6; }
.card-soft.warn { background: #fffbeb; border-color: #fde68a; }
.done-card { background: #f0fdf4; border-color: #bbf7d0; }

.plan-head { display: flex; justify-content: space-between; align-items: flex-start; gap: 10px; }
.plan-phase { font-weight: 700; font-size: 14px; color: #1e3a8a; }
.plan-goal { font-size: 12px; color: var(--text-secondary); margin-top: 3px; line-height: 1.5; }
.skeleton { display: flex; flex-direction: column; gap: 10px; padding: 8px 0; }
.sk-line { height: 12px; border-radius: 6px; background: linear-gradient(90deg, #f3f4f6, #e5e7eb, #f3f4f6); background-size: 200% 100%; animation: sk 1.2s infinite; }
.sk-line.w70 { width: 70%; } .sk-line.w90 { width: 90%; } .sk-line.w60 { width: 60%; }
@keyframes sk { 0% { background-position: 200% 0; } 100% { background-position: -200% 0; } }
.sk-tip { font-size: 12px; color: #9ca3af; }

.steps { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; gap: 8px; counter-reset: st; }
.step-card { position: relative; padding: 10px 12px 10px 40px; border: 1px solid var(--border); border-radius: 12px; background: #fff; }
.step-card::before { counter-increment: st; content: counter(st); position: absolute; left: 12px; top: 11px; width: 20px; height: 20px; border-radius: 6px; background: #eef2ff; color: #4f46e5; font-size: 12px; font-weight: 700; display: grid; place-items: center; }
.sc-top { display: flex; justify-content: space-between; align-items: center; gap: 8px; font-size: 14px; }
.sc-min { font-size: 11px; color: #9ca3af; white-space: nowrap; }
.sc-detail { font-size: 12px; color: #4b5563; margin-top: 4px; line-height: 1.6; }
.sc-ask { font-size: 12px; color: var(--brand-blue); cursor: pointer; display: inline-block; margin-top: 6px; }

.mini-sec { background: #f9fafb; border-radius: 12px; padding: 10px 12px; }
.mini-title { font-size: 12px; font-weight: 700; color: #374151; margin-bottom: 6px; }
.eq-list { display: flex; flex-wrap: wrap; gap: 6px; }
.eq-chip { display: inline-flex; align-items: center; gap: 4px; font-size: 12px; padding: 4px 10px; border-radius: 999px; background: #fff; border: 1px solid var(--border); cursor: pointer; }
.eq-chip small { color: var(--brand-blue); }
.check-item { display: flex; align-items: flex-start; gap: 8px; font-size: 13px; padding: 3px 0; cursor: pointer; }
.pitfalls { background: #fffbeb; }
.pitfalls ul { margin: 0; padding-left: 18px; font-size: 12px; color: #92400e; line-height: 1.7; }
.ask-teacher { font-size: 12px; color: #4b5563; background: #eff6ff; border-radius: 10px; padding: 8px 12px; }
.plan-actions { display: flex; gap: 8px; align-items: center; }

.chat { border-top: 1px dashed var(--border); padding-top: 12px; display: flex; flex-direction: column; gap: 8px; margin-top: auto; }
.chat-title { font-size: 13px; font-weight: 700; display: flex; align-items: center; gap: 6px; }
.msgs { max-height: 260px; overflow-y: auto; display: flex; flex-direction: column; gap: 8px; }
.chips { display: flex; flex-wrap: wrap; gap: 6px; }
.chip-q { font-size: 12px; padding: 5px 10px; border-radius: 999px; background: #f3f4f6; color: #374151; cursor: pointer; }
.chip-q:hover { background: #e0e7ff; color: #3730a3; }
.msg { display: flex; }
.msg.user { justify-content: flex-end; }
.bubble { max-width: 92%; padding: 8px 12px; border-radius: 12px; font-size: 13px; line-height: 1.6; }
.bubble.user { background: var(--brand-blue); color: #fff; border-bottom-right-radius: 4px; }
.bubble.assistant { background: #f3f4f6; color: #111827; border-bottom-left-radius: 4px; }
.bubble.assistant :deep(code) { background: #e5e7eb; padding: 0 4px; border-radius: 4px; font-size: 12px; }
.tool-line { font-size: 11px; color: #6366f1; }
.tool-line.fail { color: #dc2626; }
.confirm { background: #fff; border: 1px solid #fde68a; border-radius: 10px; padding: 8px 10px; margin-bottom: 6px; }
.confirm-title { font-size: 12px; font-weight: 600; color: #92400e; margin-bottom: 6px; }
.confirm-actions { display: flex; gap: 6px; }
.cursor { animation: blink 1s infinite; }
@keyframes blink { 50% { opacity: 0; } }
.composer { display: flex; gap: 8px; }
.foot-link { text-align: right; font-size: 12px; }
.foot-link a { color: var(--text-secondary); cursor: pointer; }
.foot-link a:hover { color: var(--brand-blue); }
</style>
